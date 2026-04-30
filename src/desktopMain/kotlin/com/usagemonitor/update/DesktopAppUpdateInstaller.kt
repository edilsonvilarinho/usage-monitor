package com.usagemonitor.update

import com.usagemonitor.domain.entity.AppUpdateInfo
import com.usagemonitor.presentation.viewmodel.AppUpdateInstaller
import com.usagemonitor.AutoStartManager.resolveExecutablePath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import java.nio.file.Files

class DesktopAppUpdateInstaller : AppUpdateInstaller {
    override val isSupported: Boolean = isWindows() || isLinux()

    override fun canInstall(update: AppUpdateInfo): Boolean {
        if (!isSupported) {
            return false
        }

        return when {
            isWindows() -> update.windowsInstallerDownloadUrl != null
            isLinux() -> update.linuxDebInstallerDownloadUrl != null && resolveExecutablePath() != null && isCommandAvailable("pkexec")
            else -> false
        }
    }

    override suspend fun prepareUpdateInstallation(update: AppUpdateInfo): Result<Unit> {
        if (!isSupported) {
            return Result.failure(
                IllegalStateException("Automatic updates are not supported on this platform.")
            )
        }

        return withContext(Dispatchers.IO) {
            when {
                isWindows() -> prepareWindowsUpdateInstallation(update)
                isLinux() -> prepareLinuxUpdateInstallation(update)
                else -> Result.failure(IllegalStateException("Automatic updates are not supported on this platform."))
            }
        }
    }
}

private fun isWindows(): Boolean {
    return System.getProperty("os.name")
        ?.contains("windows", ignoreCase = true) == true
}

private fun isLinux(): Boolean {
    return System.getProperty("os.name")
        ?.contains("linux", ignoreCase = true) == true
}

private fun sanitize(version: String): String {
    return version.replace(Regex("[^A-Za-z0-9._-]"), "_")
}

private fun prepareWindowsUpdateInstallation(update: AppUpdateInfo): Result<Unit> {
    val downloadUrl = update.windowsInstallerDownloadUrl ?: return Result.failure(
        IllegalStateException("No Windows MSI installer was published for version ${update.version}.")
    )

    return Result.runCatching {
        val updateDirectory = Files.createTempDirectory("usage-monitor-update-${sanitize(update.version)}").toFile()
        val installerFile = File(updateDirectory, "UsageMonitor-${sanitize(update.version)}.msi")
        val launcherScript = File(updateDirectory, "InstallUsageMonitorUpdate.ps1")

        downloadInstaller(downloadUrl, installerFile)
        launcherScript.writeText(
            buildWindowsLauncherScript(
                processId = ProcessHandle.current().pid(),
                installerPath = installerFile.absolutePath
            )
        )

        ProcessBuilder(
            "powershell.exe",
            "-NoProfile",
            "-ExecutionPolicy",
            "Bypass",
            "-WindowStyle",
            "Hidden",
            "-File",
            launcherScript.absolutePath
        )
            .directory(updateDirectory)
            .start()

        Unit
    }
}

private fun prepareLinuxUpdateInstallation(update: AppUpdateInfo): Result<Unit> {
    val downloadUrl = update.linuxDebInstallerDownloadUrl ?: return Result.failure(
        IllegalStateException("No Linux DEB package was published for version ${update.version}.")
    )
    val executablePath = resolveExecutablePath() ?: return Result.failure(
        IllegalStateException("Could not resolve the installed application launcher path.")
    )

    if (!isCommandAvailable("pkexec")) {
        return Result.failure(
            IllegalStateException("pkexec is required to install the Linux update automatically.")
        )
    }

    return Result.runCatching {
        val updateDirectory = Files.createTempDirectory("usage-monitor-update-${sanitize(update.version)}").toFile()
        val installerFile = File(updateDirectory, "UsageMonitor-${sanitize(update.version)}.deb")
        val launcherScript = File(updateDirectory, "install-usage-monitor-update.sh")

        downloadInstaller(downloadUrl, installerFile)
        launcherScript.writeText(
            buildLinuxLauncherScript(
                processId = ProcessHandle.current().pid(),
                installerPath = installerFile.absolutePath,
                executablePath = executablePath
            )
        )

        ProcessBuilder(
            "/bin/sh",
            launcherScript.absolutePath
        )
            .directory(updateDirectory)
            .start()

        Unit
    }
}

private fun downloadInstaller(downloadUrl: String, destination: File) {
    destination.outputStream().use { outputStream ->
        URI(downloadUrl).toURL().openStream().use { inputStream ->
            inputStream.copyTo(outputStream)
        }
    }

    if (!destination.exists() || destination.length() == 0L) {
        throw IllegalStateException("Downloaded installer is empty.")
    }
}

private fun buildWindowsLauncherScript(processId: Long, installerPath: String): String {
    val escapedInstallerPath = installerPath.replace("'", "''")

    return """
        ${'$'}targetPid = $processId
        ${'$'}installerPath = '$escapedInstallerPath'

        while (Get-Process -Id ${'$'}targetPid -ErrorAction SilentlyContinue) {
            Start-Sleep -Seconds 1
        }

        Start-Process -FilePath "msiexec.exe" -ArgumentList @('/i', ${'$'}installerPath)
    """.trimIndent()
}

private fun buildLinuxLauncherScript(processId: Long, installerPath: String, executablePath: String): String {
    val escapedInstallerPath = escapeShellValue(installerPath)
    val escapedExecutablePath = escapeShellValue(executablePath)

    return """
        #!/bin/sh
        set -eu

        target_pid=$processId
        installer_path='$escapedInstallerPath'
        launcher_path='$escapedExecutablePath'

        while kill -0 "${'$'}target_pid" 2>/dev/null; do
            sleep 1
        done

        dpkg_bin="${'$'}(command -v dpkg)"

        if [ -z "${'$'}dpkg_bin" ]; then
            exit 1
        fi

        pkexec "${'$'}dpkg_bin" -i "${'$'}installer_path"
        nohup "${'$'}launcher_path" >/dev/null 2>&1 &
    """.trimIndent()
}

private fun isCommandAvailable(command: String): Boolean {
    return runCatching {
        ProcessBuilder(
            if (isWindows()) "where" else "which",
            command
        )
            .redirectErrorStream(true)
            .start()
            .waitFor() == 0
    }.getOrDefault(false)
}

private fun escapeShellValue(value: String): String {
    return value.replace("'", "'\"'\"'")
}

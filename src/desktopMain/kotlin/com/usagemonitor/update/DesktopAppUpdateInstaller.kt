package com.usagemonitor.update

import com.usagemonitor.domain.entity.AppUpdateInfo
import com.usagemonitor.presentation.viewmodel.AppUpdateInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import java.nio.file.Files

class DesktopAppUpdateInstaller : AppUpdateInstaller {
    override val isSupported: Boolean = isWindows()

    override suspend fun prepareUpdateInstallation(update: AppUpdateInfo): Result<Unit> {
        if (!isSupported) {
            return Result.failure(
                IllegalStateException("Automatic Windows updates are not supported on this platform.")
            )
        }

        val downloadUrl = update.windowsInstallerDownloadUrl ?: return Result.failure(
            IllegalStateException("No Windows MSI installer was published for version ${update.version}.")
        )

        return withContext(Dispatchers.IO) {
            Result.runCatching {
                val updateDirectory = Files.createTempDirectory("usage-monitor-update-${sanitize(update.version)}").toFile()
                val installerFile = File(updateDirectory, "UsageMonitor-${sanitize(update.version)}.msi")
                val launcherScript = File(updateDirectory, "InstallUsageMonitorUpdate.ps1")

                downloadInstaller(downloadUrl, installerFile)
                launcherScript.writeText(
                    buildLauncherScript(
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
    }
}

private fun isWindows(): Boolean {
    return System.getProperty("os.name")
        ?.contains("windows", ignoreCase = true) == true
}

private fun sanitize(version: String): String {
    return version.replace(Regex("[^A-Za-z0-9._-]"), "_")
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

private fun buildLauncherScript(processId: Long, installerPath: String): String {
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

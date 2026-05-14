package com.usagemonitor.update

import com.usagemonitor.domain.entity.AppUpdateInfo
import com.usagemonitor.presentation.viewmodel.AppUpdateInstaller
import com.usagemonitor.presentation.viewmodel.PreparedUpdateAction
import com.usagemonitor.AutoStartManager.resolveExecutablePath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class DesktopAppUpdateInstaller(
    private val executablePathResolver: () -> String? = ::resolveExecutablePath,
    private val commandAvailabilityChecker: (String) -> Boolean = ::isCommandAvailable,
    private val debPackageInstallationChecker: (String) -> Boolean = ::isDebianPackageManagedInstallation,
    private val processLauncher: (List<String>, File?) -> Process = ::launchProcess
) : AppUpdateInstaller {
    override val isSupported: Boolean = isWindows() || isLinux()

    override fun canInstall(update: AppUpdateInfo): Boolean {
        if (!isSupported) {
            return false
        }

        return when {
            isWindows() -> update.windowsInstallerDownloadUrl != null
            isLinux() -> getLinuxAutomaticUpdateSupport(
                update = update,
                executablePathResolver = executablePathResolver,
                commandAvailabilityChecker = commandAvailabilityChecker,
                debPackageInstallationChecker = debPackageInstallationChecker
            ).isSupported
            else -> false
        }
    }

    override suspend fun prepareUpdateInstallation(update: AppUpdateInfo): Result<PreparedUpdateAction> {
        if (!isSupported) {
            return Result.failure(
                IllegalStateException("Automatic updates are not supported on this platform.")
            )
        }

        return withContext(Dispatchers.IO) {
            when {
                isWindows() -> prepareWindowsUpdateInstallation(
                    update = update,
                    processLauncher = processLauncher
                )

                isLinux() -> prepareLinuxUpdateInstallation(
                    update = update,
                    executablePathResolver = executablePathResolver,
                    commandAvailabilityChecker = commandAvailabilityChecker,
                    debPackageInstallationChecker = debPackageInstallationChecker,
                    processLauncher = processLauncher
                )

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

private fun prepareWindowsUpdateInstallation(
    update: AppUpdateInfo,
    processLauncher: (List<String>, File?) -> Process
): Result<PreparedUpdateAction> {
    val downloadUrl = update.windowsInstallerDownloadUrl ?: return Result.failure(
        IllegalStateException("No Windows installer was published for version ${update.version}.")
    )

    return Result.runCatching {
        val updateDirectory = Files.createTempDirectory("usage-monitor-update-${sanitize(update.version)}").toFile()
        val installerExtension = inferWindowsInstallerExtension(downloadUrl)
        val installerFile = File(updateDirectory, "UsageMonitor-${sanitize(update.version)}$installerExtension")
        val launcherScript = File(updateDirectory, "InstallUsageMonitorUpdate.ps1")

        downloadInstaller(downloadUrl, installerFile)
        launcherScript.writeText(
            buildWindowsLauncherScript(
                processId = ProcessHandle.current().pid(),
                installerPath = installerFile.absolutePath
            )
        )

        processLauncher(
            listOf(
                "powershell.exe",
                "-NoProfile",
                "-ExecutionPolicy",
                "Bypass",
                "-WindowStyle",
                "Hidden",
                "-File",
                launcherScript.absolutePath
            ),
            updateDirectory
        )

        PreparedUpdateAction.ExitAndInstall
    }
}

private fun prepareLinuxUpdateInstallation(
    update: AppUpdateInfo,
    executablePathResolver: () -> String?,
    commandAvailabilityChecker: (String) -> Boolean,
    debPackageInstallationChecker: (String) -> Boolean,
    processLauncher: (List<String>, File?) -> Process
): Result<PreparedUpdateAction> {
    val support = getLinuxAutomaticUpdateSupport(
        update = update,
        executablePathResolver = executablePathResolver,
        commandAvailabilityChecker = commandAvailabilityChecker,
        debPackageInstallationChecker = debPackageInstallationChecker
    )

    if (!support.isSupported) {
        return Result.failure(
            IllegalStateException(support.failureMessage ?: "Automatic Linux updates are not supported.")
        )
    }

    return Result.runCatching {
        val downloadUrl = update.linuxDebInstallerDownloadUrl!!
        val opener = support.opener!!
        val updateDirectory = Files.createTempDirectory("usage-monitor-update-${sanitize(update.version)}").toFile()
        val installerFile = File(updateDirectory, "UsageMonitor-${sanitize(update.version)}.deb")

        downloadInstaller(downloadUrl, installerFile)
        val openerProcess = processLauncher(
            opener.command + installerFile.absolutePath,
            updateDirectory
        )

        ensureProcessStartedSuccessfully(
            process = openerProcess,
            commandLabel = opener.displayName
        )

        PreparedUpdateAction.InstallerOpened
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

private fun ensureProcessStartedSuccessfully(process: Process, commandLabel: String) {
    if (!process.waitFor(5, TimeUnit.SECONDS)) {
        return
    }

    if (process.exitValue() == 0) {
        return
    }

    val output = process.inputStream.bufferedReader().use { it.readText().trim() }
    val details = output.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()
    throw IllegalStateException("$commandLabel could not open the Linux package installer$details")
}

internal fun buildWindowsLauncherScript(processId: Long, installerPath: String): String {
    val escapedInstallerPath = installerPath.replace("'", "''")
    val installerLaunchCommand = buildWindowsInstallerLaunchCommand(installerPath)

    return """
        ${'$'}targetPid = $processId
        ${'$'}installerPath = '$escapedInstallerPath'

        while (Get-Process -Id ${'$'}targetPid -ErrorAction SilentlyContinue) {
            Start-Sleep -Seconds 1
        }

        $installerLaunchCommand
    """.trimIndent()
}

private fun inferWindowsInstallerExtension(downloadUrl: String): String {
    val downloadPath = runCatching { URI(downloadUrl).path }.getOrDefault(downloadUrl)

    return when {
        downloadPath.endsWith(".msi", ignoreCase = true) -> ".msi"
        downloadPath.endsWith(".exe", ignoreCase = true) -> ".exe"
        else -> ".exe"
    }
}

private fun buildWindowsInstallerLaunchCommand(installerPath: String): String {
    return if (installerPath.endsWith(".msi", ignoreCase = true)) {
        """Start-Process -FilePath "msiexec.exe" -ArgumentList @('/i', ${'$'}installerPath)"""
    } else {
        "Start-Process -FilePath ${'$'}installerPath"
    }
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

private fun launchProcess(command: List<String>, directory: File?): Process {
    val processBuilder = ProcessBuilder(command)
        .redirectErrorStream(true)
    if (directory != null) {
        processBuilder.directory(directory)
    }
    return processBuilder.start()
}

private fun isDebianPackageManagedInstallation(executablePath: String): Boolean {
    return runCatching {
        ProcessBuilder(
            "dpkg",
            "-S",
            File(executablePath).canonicalPath
        )
            .redirectErrorStream(true)
            .start()
            .waitFor() == 0
    }.getOrDefault(false)
}

internal fun getLinuxAutomaticUpdateSupport(
    update: AppUpdateInfo,
    executablePathResolver: () -> String?,
    commandAvailabilityChecker: (String) -> Boolean,
    debPackageInstallationChecker: (String) -> Boolean
): LinuxAutomaticUpdateSupport {
    val downloadUrl = update.linuxDebInstallerDownloadUrl
    if (downloadUrl == null) {
        return LinuxAutomaticUpdateSupport(
            isSupported = false,
            failureMessage = "No Linux DEB package was published for version ${update.version}."
        )
    }

    val executablePath = executablePathResolver()
        ?: return LinuxAutomaticUpdateSupport(
            isSupported = false,
            failureMessage = "Could not resolve the installed application launcher path."
        )

    if (!commandAvailabilityChecker("dpkg")) {
        return LinuxAutomaticUpdateSupport(
            isSupported = false,
            failureMessage = "dpkg is required to install the Linux update automatically."
        )
    }

    if (!debPackageInstallationChecker(executablePath)) {
        return LinuxAutomaticUpdateSupport(
            isSupported = false,
            failureMessage = "Automatic Linux updates currently require the app to be installed from the published DEB package."
        )
    }

    val opener = findLinuxPackageOpener(commandAvailabilityChecker)
        ?: return LinuxAutomaticUpdateSupport(
            isSupported = false,
            failureMessage = "A graphical package opener (xdg-open or gio open) is required to start the Linux update installer."
        )

    return LinuxAutomaticUpdateSupport(
        isSupported = true,
        executablePath = executablePath,
        opener = opener
    )
}

internal data class LinuxAutomaticUpdateSupport(
    val isSupported: Boolean,
    val executablePath: String? = null,
    val opener: LinuxPackageOpener? = null,
    val failureMessage: String? = null
)

internal data class LinuxPackageOpener(
    val command: List<String>,
    val displayName: String
)

internal fun findLinuxPackageOpener(commandAvailabilityChecker: (String) -> Boolean): LinuxPackageOpener? {
    if (commandAvailabilityChecker("xdg-open")) {
        return LinuxPackageOpener(
            command = listOf("xdg-open"),
            displayName = "xdg-open"
        )
    }

    if (commandAvailabilityChecker("gio")) {
        return LinuxPackageOpener(
            command = listOf("gio", "open"),
            displayName = "gio open"
        )
    }

    return null
}

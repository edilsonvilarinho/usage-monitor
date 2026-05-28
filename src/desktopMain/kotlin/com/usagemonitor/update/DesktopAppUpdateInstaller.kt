package com.usagemonitor.update

import com.usagemonitor.domain.entity.AppUpdateInfo
import com.usagemonitor.presentation.viewmodel.AppUpdateInstaller
import com.usagemonitor.presentation.viewmodel.AutomaticUpdateStage
import com.usagemonitor.presentation.viewmodel.PreparedUpdateAction
import com.usagemonitor.AutoStartManager.resolveExecutablePath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import java.nio.file.Files
import kotlin.concurrent.thread
import java.util.concurrent.TimeUnit

private const val LINUX_INSTALL_TIMEOUT_MILLIS = 30 * 60 * 1_000L

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

    override suspend fun prepareUpdateInstallation(
        update: AppUpdateInfo,
        onStageChanged: (AutomaticUpdateStage) -> Unit
    ): Result<PreparedUpdateAction> {
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
                    processLauncher = processLauncher,
                    onStageChanged = onStageChanged
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

internal fun prepareLinuxUpdateInstallation(
    update: AppUpdateInfo,
    executablePathResolver: () -> String?,
    commandAvailabilityChecker: (String) -> Boolean,
    debPackageInstallationChecker: (String) -> Boolean,
    processLauncher: (List<String>, File?) -> Process,
    onStageChanged: (AutomaticUpdateStage) -> Unit
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
        val packageManager = support.packageManager!!
        val updateDirectory = Files.createTempDirectory("usage-monitor-update-${sanitize(update.version)}").toFile()
        val installerFile = File(updateDirectory, "UsageMonitor-${sanitize(update.version)}.deb")

        downloadInstaller(downloadUrl, installerFile)
        onStageChanged(AutomaticUpdateStage.INSTALLING)

        val installProcess = processLauncher(
            packageManager.command + installerFile.absolutePath,
            updateDirectory
        )

        ensurePackageInstallationSucceeded(
            process = installProcess,
            commandLabel = packageManager.displayName
        )

        onStageChanged(AutomaticUpdateStage.RESTARTING)
        relaunchInstalledApplication(
            executablePath = support.executablePath!!,
            processLauncher = processLauncher
        )

        PreparedUpdateAction.RestartAndExit
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

    val packageManager = findLinuxPackageManager(commandAvailabilityChecker)
        ?: return LinuxAutomaticUpdateSupport(
            isSupported = false,
            failureMessage = "PackageKit pkcon is required to install the Linux update automatically."
        )

    return LinuxAutomaticUpdateSupport(
        isSupported = true,
        executablePath = executablePath,
        packageManager = packageManager
    )
}

internal data class LinuxAutomaticUpdateSupport(
    val isSupported: Boolean,
    val executablePath: String? = null,
    val packageManager: LinuxPackageManager? = null,
    val failureMessage: String? = null
)

internal data class LinuxPackageManager(
    val command: List<String>,
    val displayName: String
)

internal fun findLinuxPackageManager(commandAvailabilityChecker: (String) -> Boolean): LinuxPackageManager? {
    if (commandAvailabilityChecker("pkcon")) {
        return LinuxPackageManager(
            command = listOf("pkcon", "--noninteractive", "install-local"),
            displayName = "pkcon install-local"
        )
    }

    return null
}

private fun ensurePackageInstallationSucceeded(process: Process, commandLabel: String) {
    val outputBuffer = StringBuilder()
    val outputReader = thread(start = true, isDaemon = true, name = "usage-monitor-linux-update-output") {
        process.inputStream.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                outputBuffer.appendLine(line)
            }
        }
    }

    if (!process.waitFor(LINUX_INSTALL_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
        process.destroy()
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
            process.destroyForcibly()
        }
        outputReader.join(1_000)
        throw IllegalStateException("$commandLabel timed out while installing the update.")
    }

    outputReader.join(1_000)
    if (process.exitValue() == 0) {
        return
    }

    val output = outputBuffer.toString().trim()
    val normalizedOutput = output.lowercase()
    val message = when {
        "cancelled" in normalizedOutput || "canceled" in normalizedOutput ->
            "$commandLabel cancelled the Linux update request."

        "not authorized" in normalizedOutput || "authentication" in normalizedOutput ->
            "$commandLabel could not authenticate the Linux update request."

        else -> {
            val details = output.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()
            "$commandLabel failed to install the Linux update$details"
        }
    }
    throw IllegalStateException(message)
}

private fun relaunchInstalledApplication(
    executablePath: String,
    processLauncher: (List<String>, File?) -> Process
) {
    val launcherFile = File(executablePath)
    if (!launcherFile.exists() || !launcherFile.isFile) {
        throw IllegalStateException("Installed application launcher was not found after the Linux update.")
    }

    val launcherProcess = processLauncher(
        listOf(launcherFile.absolutePath),
        launcherFile.parentFile
    )

    if (!launcherProcess.waitFor(5, TimeUnit.SECONDS)) {
        return
    }

    if (launcherProcess.exitValue() == 0) {
        return
    }

    val output = launcherProcess.inputStream.bufferedReader().use { it.readText().trim() }
    val details = output.takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()
    throw IllegalStateException("Installed application launcher could not be started after the Linux update$details")
}

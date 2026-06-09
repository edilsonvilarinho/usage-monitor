package com.usagemonitor.update

import com.usagemonitor.presentation.viewmodel.AppUpdateReleaseOpener
import java.awt.Desktop
import java.io.File
import java.net.URI

class DesktopAppUpdateReleaseOpener(
    private val processLauncher: (List<String>, File?) -> Process = ::launchProcess
) : AppUpdateReleaseOpener {
    override fun open(releasePageUrl: String): Result<Unit> {
        return Result.runCatching {
            val releaseUri = URI(releasePageUrl)

            if (Desktop.isDesktopSupported()) {
                val desktop = Desktop.getDesktop()
                if (desktop.isSupported(Desktop.Action.BROWSE)) {
                    desktop.browse(releaseUri)
                    return@runCatching
                }
            }

            val command = when {
                isWindows() -> listOf("rundll32", "url.dll,FileProtocolHandler", releasePageUrl)
                isLinux() -> listOf("xdg-open", releasePageUrl)
                else -> throw IllegalStateException("No browser opener is available for this platform.")
            }

            processLauncher(command, null)
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

private fun launchProcess(command: List<String>, directory: File?): Process {
    val processBuilder = ProcessBuilder(command)
        .redirectErrorStream(true)
    if (directory != null) {
        processBuilder.directory(directory)
    }
    return processBuilder.start()
}

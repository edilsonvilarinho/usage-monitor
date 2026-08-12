package com.usagemonitor.update

import com.usagemonitor.presentation.viewmodel.AppUpdateReleaseOpener
import java.awt.Desktop
import java.io.File
import java.net.URI

class DesktopAppUpdateReleaseOpener(
    private val processLauncher: (List<String>, File?) -> Process = ::launchProcess,
    // Costuras de teste: o browse nativo abriria um navegador real durante os testes.
    private val desktopBrowser: (URI) -> Boolean = ::browseWithDesktop,
    private val osNameProvider: () -> String = { System.getProperty("os.name").orEmpty() }
) : AppUpdateReleaseOpener {
    override fun open(releasePageUrl: String): Result<Unit> {
        return Result.runCatching {
            val releaseUri = URI(releasePageUrl)
            validateReleaseUri(releaseUri)

            if (desktopBrowser(releaseUri)) {
                return@runCatching
            }

            val osName = osNameProvider()
            val command = when {
                osName.contains("windows", ignoreCase = true) ->
                    listOf("rundll32", "url.dll,FileProtocolHandler", releasePageUrl)

                osName.contains("linux", ignoreCase = true) ->
                    listOf("xdg-open", releasePageUrl)

                osName.contains("mac", ignoreCase = true) ->
                    listOf("open", releasePageUrl)

                else -> throw IllegalStateException("No browser opener is available for this platform.")
            }

            processLauncher(command, null)
        }
    }

    private fun validateReleaseUri(releaseUri: URI) {
        val scheme = releaseUri.scheme?.lowercase()
        val host = releaseUri.host?.lowercase()
        val path = releaseUri.path.orEmpty()
        if (scheme != "https") {
            throw IllegalStateException("Release URL inválida: apenas HTTPS é aceito.")
        }
        if (host !in TRUSTED_RELEASE_HOSTS) {
            throw IllegalStateException("Release URL inválida: host não confiável.")
        }
        if (!path.startsWith("/edilsonvilarinho/usage-monitor/releases/", ignoreCase = true)) {
            throw IllegalStateException("Release URL inválida: caminho fora da release esperada.")
        }
    }

    private companion object {
        val TRUSTED_RELEASE_HOSTS = setOf("github.com", "www.github.com")
    }
}

/** Devolve `true` quando o browse nativo tratou a URI. */
private fun browseWithDesktop(releaseUri: URI): Boolean {
    if (!Desktop.isDesktopSupported()) {
        return false
    }
    val desktop = Desktop.getDesktop()
    if (!desktop.isSupported(Desktop.Action.BROWSE)) {
        return false
    }
    desktop.browse(releaseUri)
    return true
}

private fun launchProcess(command: List<String>, directory: File?): Process {
    val processBuilder = ProcessBuilder(command)
        .redirectErrorStream(true)
    if (directory != null) {
        processBuilder.directory(directory)
    }
    return processBuilder.start()
}

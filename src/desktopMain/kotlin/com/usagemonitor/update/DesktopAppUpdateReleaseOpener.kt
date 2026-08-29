package com.usagemonitor.update

import com.usagemonitor.browseWithDesktop
import com.usagemonitor.launchBrowserProcess
import com.usagemonitor.openInBrowser
import com.usagemonitor.presentation.viewmodel.AppUpdateReleaseOpener
import java.io.File
import java.net.URI

class DesktopAppUpdateReleaseOpener(
    private val processLauncher: (List<String>, File?) -> Process = ::launchBrowserProcess,
    // Costuras de teste: o browse nativo abriria um navegador real durante os testes.
    private val desktopBrowser: (URI) -> Boolean = ::browseWithDesktop,
    private val osNameProvider: () -> String = { System.getProperty("os.name").orEmpty() }
) : AppUpdateReleaseOpener {
    override fun open(releasePageUrl: String): Result<Unit> {
        return Result.runCatching {
            val releaseUri = URI(releasePageUrl)
            validateReleaseUri(releaseUri)

            // A abertura em si é comum a este e ao abridor da issue de bug; o que
            // é próprio daqui é a validação acima, que prende a URL à release.
            openInBrowser(
                uri = releaseUri,
                desktopBrowser = desktopBrowser,
                processLauncher = processLauncher,
                osNameProvider = osNameProvider
            )
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

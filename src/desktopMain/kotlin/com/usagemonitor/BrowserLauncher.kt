package com.usagemonitor

import java.awt.Desktop
import java.io.File
import java.net.URI

/**
 * Abre uma URI no navegador do sistema.
 *
 * Extraído do `DesktopAppUpdateReleaseOpener` quando apareceu o segundo
 * consumidor — a issue pré-preenchida do relatório de bug. Ele continua dono da
 * **validação** da URL dele; o que é comum aos dois é só *como* se abre um
 * navegador em cada plataforma, e essa parte não pode ter duas versões.
 *
 * As três costuras existem porque `Desktop.browse` abriria um navegador de
 * verdade durante a suíte.
 */
internal fun openInBrowser(
    uri: URI,
    desktopBrowser: (URI) -> Boolean = ::browseWithDesktop,
    processLauncher: (List<String>, File?) -> Process = ::launchBrowserProcess,
    osNameProvider: () -> String = { System.getProperty("os.name").orEmpty() }
) {
    if (desktopBrowser(uri)) {
        return
    }

    val url = uri.toString()
    val osName = osNameProvider()
    val command = when {
        osName.contains("windows", ignoreCase = true) ->
            listOf("rundll32", "url.dll,FileProtocolHandler", url)

        osName.contains("linux", ignoreCase = true) ->
            listOf("xdg-open", url)

        osName.contains("mac", ignoreCase = true) ->
            listOf("open", url)

        else -> throw IllegalStateException("No browser opener is available for this platform.")
    }

    processLauncher(command, null)
}

/** Devolve `true` quando o browse nativo tratou a URI. */
internal fun browseWithDesktop(uri: URI): Boolean {
    if (!Desktop.isDesktopSupported()) {
        return false
    }
    val desktop = Desktop.getDesktop()
    if (!desktop.isSupported(Desktop.Action.BROWSE)) {
        return false
    }
    desktop.browse(uri)
    return true
}

internal fun launchBrowserProcess(command: List<String>, directory: File?): Process {
    val processBuilder = ProcessBuilder(command)
        .redirectErrorStream(true)
    if (directory != null) {
        processBuilder.directory(directory)
    }
    return processBuilder.start()
}

package com.usagemonitor

import com.usagemonitor.data.datasource.restrictToOwnerReadWrite
import com.usagemonitor.presentation.viewmodel.BugReportSaveRequest
import com.usagemonitor.presentation.viewmodel.BugReportSaveResult
import com.usagemonitor.presentation.viewmodel.BugReportWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Window
import java.io.File
import javax.swing.JFileChooser

/**
 * Grava o pacote de diagnóstico no arquivo escolhido pelo usuário.
 *
 * Mesmo desenho do [DesktopUsageExportWriter], inclusive nas duas trocas de
 * thread: o `JFileChooser` é Swing e **tem** de rodar na EDT, e a escrita em
 * disco não pode rodar nela.
 *
 * `parentWindow` é uma função e não a janela porque a janela principal só existe
 * depois da composição.
 */
class DesktopBugReportWriter(
    private val parentWindow: () -> Window? = { null }
) : BugReportWriter {

    override suspend fun write(request: BugReportSaveRequest): BugReportSaveResult? {
        val target = withContext(Dispatchers.Main) { chooseFile(request.suggestedFileName) } ?: return null
        return withContext(Dispatchers.IO) { writeBugReportFiles(target, request) }
    }

    private fun chooseFile(suggestedFileName: String): File? {
        val chooser = JFileChooser().apply {
            dialogTitle = "Usage Monitor"
            selectedFile = File(suggestedFileName)
        }
        if (chooser.showSaveDialog(parentWindow()) != JFileChooser.APPROVE_OPTION) {
            return null
        }
        return chooser.selectedFile
    }
}

/**
 * Escreve o JSON e, quando existe, a captura ao lado dele.
 *
 * Separada do diálogo para ter teste: o `JFileChooser` não abre em CI, e o que
 * pode dar errado aqui é o par de arquivos, não a escolha do caminho.
 *
 * A imagem vai com o **mesmo nome base** do JSON e extensão `.png`. Um nome
 * independente faria o usuário arrastar para a issue dois arquivos sem relação
 * visível entre si, e nada diria que um é a tela do outro.
 *
 * Os dois nascem com acesso restrito ao dono: o pacote está a caminho de uma
 * issue pública, mas até o usuário decidir publicá-lo ele é dele.
 */
internal fun writeBugReportFiles(target: File, request: BugReportSaveRequest): BugReportSaveResult {
    target.parentFile?.mkdirs()
    target.writeText(request.json)
    restrictToOwnerReadWrite(target.toPath())

    val png = request.screenshotPng ?: return BugReportSaveResult(jsonPath = target.absolutePath)

    val screenshot = File(target.parentFile, "${target.nameWithoutExtension}.png")
    screenshot.writeBytes(png)
    restrictToOwnerReadWrite(screenshot.toPath())
    return BugReportSaveResult(
        jsonPath = target.absolutePath,
        screenshotPath = screenshot.absolutePath
    )
}

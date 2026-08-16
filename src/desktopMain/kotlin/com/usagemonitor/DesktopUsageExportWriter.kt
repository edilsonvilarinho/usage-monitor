package com.usagemonitor

import com.usagemonitor.presentation.ui.UsageExportRequest
import com.usagemonitor.presentation.viewmodel.UsageExportWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Window
import java.io.File
import javax.swing.JFileChooser
import javax.swing.SwingUtilities

/**
 * Grava a exportação num arquivo escolhido pelo usuário.
 *
 * O `JFileChooser` é Swing e **tem** de rodar na EDT; a escrita em disco não
 * pode rodar nela. Daí o diálogo ir para [SwingUtilities.invokeAndWait] e o
 * `writeText` para `Dispatchers.IO`.
 *
 * `parentWindow` é uma função, e não a janela: a janela principal só existe
 * depois da composição, e capturá-la na construção daria sempre `null`.
 */
class DesktopUsageExportWriter(
    private val parentWindow: () -> Window? = { null }
) : UsageExportWriter {

    override suspend fun write(request: UsageExportRequest): String? {
        val target = withContext(Dispatchers.Main) { chooseFile(request.suggestedFileName) } ?: return null

        withContext(Dispatchers.IO) {
            target.writeText(request.content)
        }
        return target.absolutePath
    }

    private fun chooseFile(suggestedFileName: String): File? {
        val chooser = JFileChooser().apply {
            dialogTitle = "Usage Monitor"
            selectedFile = File(suggestedFileName)
        }
        val result = chooser.showSaveDialog(parentWindow())
        if (result != JFileChooser.APPROVE_OPTION) {
            return null
        }
        return chooser.selectedFile
    }
}

package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.AnalysisView
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.UsageExportPayload
import com.usagemonitor.presentation.ui.UsageExportRequest
import com.usagemonitor.presentation.viewmodel.AnalysisUiState
import com.usagemonitor.presentation.viewmodel.AnalysisViewModel
import com.usagemonitor.presentation.viewmodel.CliExportOutcome
import com.usagemonitor.presentation.viewmodel.UsageExportWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.delay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Janela "Análise" (rodada AB): leitura sob demanda, troca de vista e o PDF (B21b). */
class AnalysisViewModelTest {

    private fun waitFor(condition: () -> Boolean) = runBlocking { withTimeout(5_000) { while (!condition()) delay(10) } }

    @Test
    fun `opens the account, switches view and exports the pdf with the bot file name`() {
        var written: UsageExportRequest? = null
        val viewModel = AnalysisViewModel(
            load = { key -> if (key == "ANTHROPIC:work") AnalysisFixture.analysis else null },
            dispatcher = Dispatchers.Default,
            exportWriter = UsageExportWriter { request -> written = request; "C:/tmp/" + request.suggestedFileName },
            pdfOf = { _, _ -> byteArrayOf(1, 2, 3) }
        )
        viewModel.openForAccount("ANTHROPIC:work", "Anthropic · Trabalho")
        waitFor { viewModel.uiState.value is AnalysisUiState.Success }
        viewModel.selectView(AnalysisView.HOURS)
        assertEquals(AnalysisView.HOURS, (viewModel.uiState.value as AnalysisUiState.Success).view)

        viewModel.exportPdf(AppLanguage.PT)
        waitFor { (viewModel.uiState.value as AnalysisUiState.Success).exportOutcome != null }
        assertEquals(CliExportOutcome.Saved("C:/tmp/analise-anthropic-2026-10-06-1230.pdf"), (viewModel.uiState.value as AnalysisUiState.Success).exportOutcome)
        assertTrue((written?.payload as UsageExportPayload.Binary).content.contentEquals(byteArrayOf(1, 2, 3)))
        viewModel.onDestroy()
    }

    @Test
    fun `an account without history ends empty and hides the export`() {
        val viewModel = AnalysisViewModel(load = { null })
        viewModel.openForAccount("CODEX", "Codex")
        waitFor { viewModel.uiState.value != AnalysisUiState.Loading }
        assertIs<AnalysisUiState.Empty>(viewModel.uiState.value)
        assertEquals(false, viewModel.canExport)
        viewModel.onDestroy()
    }
}

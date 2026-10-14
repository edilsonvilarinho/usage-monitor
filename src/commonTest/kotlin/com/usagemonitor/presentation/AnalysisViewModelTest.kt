package com.usagemonitor.presentation

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Duration.Companion.minutes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Janela "Análise" (rodada AB): leitura ao vivo (rodada AD) e o PDF (B21b). */
class AnalysisViewModelTest {

    private fun waitFor(condition: () -> Boolean) = runBlocking { withTimeout(5_000) { while (!condition()) delay(10) } }

    /** Tempo real para o laço do view model rodar (ele usa o `Dispatchers.Default`). */
    private fun pauseForBackgroundWork() = Thread.sleep(150)

    @Test
    fun `opens the account and exports the pdf with the bot file name`() {
        var written: UsageExportRequest? = null
        val viewModel = AnalysisViewModel(
            load = { key -> if (key == "ANTHROPIC:work") AnalysisFixture.analysis else null },
            dispatcher = Dispatchers.Default,
            exportWriter = UsageExportWriter { request -> written = request; "C:/tmp/" + request.suggestedFileName },
            pdfOf = { _, _ -> byteArrayOf(1, 2, 3) }
        )
        viewModel.openForAccount("ANTHROPIC:work", "Anthropic · Trabalho")
        waitFor { viewModel.uiState.value is AnalysisUiState.Success }

        viewModel.exportPdf(AppLanguage.PT)
        waitFor { (viewModel.uiState.value as AnalysisUiState.Success).exportOutcome != null }
        assertEquals(CliExportOutcome.Saved("C:/tmp/analise-anthropic-2026-10-06-1230.pdf"), (viewModel.uiState.value as AnalysisUiState.Success).exportOutcome)
        assertTrue((written?.payload as UsageExportPayload.Binary).content.contentEquals(byteArrayOf(1, 2, 3)))
        viewModel.onDestroy()
    }

    /** Rodada AD (A01): leitura nova da conta relê sem o clique; a primeira é a da abertura. */
    @Test
    fun `a new reading of the account reloads the analysis while the window is open`() {
        var loads = 0
        val readings = MutableStateFlow<Any?>(1)
        val viewModel = AnalysisViewModel(
            load = { loads++; AnalysisFixture.analysis.copy(generatedAt = AnalysisFixture.now + loads.minutes) },
            freshness = { readings },
            liveIntervalMillis = 60_000
        )
        viewModel.openForAccount("ANTHROPIC:work", "Anthropic · Trabalho")
        waitFor { viewModel.uiState.value is AnalysisUiState.Success }
        pauseForBackgroundWork()
        // A abertura lê uma vez e o primeiro valor do sinal, de novo (sem `drop`: ver o view model).
        val opened = loads

        readings.value = 2
        waitFor { loads == opened + 1 }
        waitFor { (viewModel.uiState.value as AnalysisUiState.Success).analysis.generatedAt == AnalysisFixture.now + (opened + 1).minutes }

        viewModel.close()
        readings.value = 3
        pauseForBackgroundWork()
        assertEquals(opened + 1, loads)
        viewModel.onDestroy()
    }

    /** O relógio também relê: ritmo da última hora e ritmo seguro andam sem coleta nova. */
    @Test
    fun `the clock reloads the analysis and stops when the window closes`() {
        var loads = 0
        val viewModel = AnalysisViewModel(load = { loads++; AnalysisFixture.analysis }, liveIntervalMillis = 20)
        viewModel.openForAccount("ANTHROPIC:work", "Anthropic · Trabalho")
        waitFor { loads >= 3 }
        viewModel.close()
        pauseForBackgroundWork()
        val stopped = loads
        pauseForBackgroundWork()
        assertEquals(stopped, loads)
        viewModel.onDestroy()
    }

    /** Leitura que falha mantém os números anteriores e publica só a falha; a próxima limpa. */
    @Test
    fun `a failed live reload keeps the numbers and the next one clears the failure`() {
        var fail = false
        val readings = MutableStateFlow<Any?>(1)
        val failedAt = AnalysisFixture.now + 5.minutes
        val viewModel = AnalysisViewModel(
            load = { if (fail) error("index locked") else AnalysisFixture.analysis },
            freshness = { readings },
            liveIntervalMillis = 60_000,
            clock = { failedAt }
        )
        viewModel.openForAccount("ANTHROPIC:work", "Anthropic · Trabalho")
        waitFor { viewModel.uiState.value is AnalysisUiState.Success }
        pauseForBackgroundWork()

        fail = true
        readings.value = 2
        waitFor { (viewModel.uiState.value as AnalysisUiState.Success).liveFailedAt != null }
        val failed = viewModel.uiState.value as AnalysisUiState.Success
        assertEquals(failedAt, failed.liveFailedAt)
        assertEquals(AnalysisFixture.analysis, failed.analysis)

        fail = false
        readings.value = 3
        waitFor { (viewModel.uiState.value as AnalysisUiState.Success).liveFailedAt == null }
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

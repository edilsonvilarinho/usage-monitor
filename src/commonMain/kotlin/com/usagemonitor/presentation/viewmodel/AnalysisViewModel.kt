package com.usagemonitor.presentation.viewmodel

import com.usagemonitor.domain.entity.AnalysisView
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.TelegramAnalysisImageLabels
import com.usagemonitor.presentation.ui.UsageExportPayload
import com.usagemonitor.presentation.ui.UsageExportRequest
import com.usagemonitor.domain.entity.SessionAnalysis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AnalysisUiState {
    data object Loading : AnalysisUiState
    /** A conta ainda não tem histórico, ou saiu do painel desde o clique. */
    data object Empty : AnalysisUiState
    data class Success(
        val analysis: SessionAnalysis,
        val view: AnalysisView,
        val isRefreshing: Boolean = false,
        /** Resultado do "Relatório PDF" (B21b); `null` sem exportação ou com o diálogo cancelado. */
        val exportOutcome: CliExportOutcome? = null
    ) : AnalysisUiState
}

/**
 * Janela "Análise" de uma conta (rodada AB, B20): a mesma análise do `/analise` do bot.
 *
 * Lê sob demanda, ao abrir e no "Atualizar": é uma pergunta pontual, não um painel ao vivo.
 * [load] recebe o `storageKey` da conta e vem de quem tem o histórico e o índice CLI —
 * a apresentação não importa `data`.
 */
class AnalysisViewModel(
    private val load: suspend (targetKey: String) -> SessionAnalysis?,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    /** Grava o PDF (B21b); `null` esconde o botão. */
    private val exportWriter: UsageExportWriter? = null,
    /** O PDF de uma página do `/analise pdf`, montado em `desktopMain` (PDFBox é JVM). */
    private val pdfOf: (suspend (SessionAnalysis, AppLanguage) -> ByteArray)? = null
) {
    /** Há como exportar? Decide se a janela mostra "Relatório PDF". */
    val canExport: Boolean
        get() = exportWriter != null && pdfOf != null

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var loadJob: Job? = null
    private var targetKey: String? = null
    private var view = AnalysisView.PANEL

    private val _uiState = MutableStateFlow<AnalysisUiState>(AnalysisUiState.Loading)
    val uiState: StateFlow<AnalysisUiState> = _uiState.asStateFlow()

    private val _accountLabel = MutableStateFlow<String?>(null)
    val accountLabel: StateFlow<String?> = _accountLabel.asStateFlow()

    fun openForAccount(targetKey: String, accountLabel: String) {
        this.targetKey = targetKey
        _accountLabel.value = accountLabel
        view = AnalysisView.PANEL
        _uiState.value = AnalysisUiState.Loading
        refresh()
    }

    fun selectView(selected: AnalysisView) {
        view = selected
        val current = _uiState.value
        if (current is AnalysisUiState.Success) _uiState.value = current.copy(view = selected)
    }

    fun refresh() {
        val key = targetKey ?: return
        loadJob?.cancel()
        val current = _uiState.value
        if (current is AnalysisUiState.Success) _uiState.value = current.copy(isRefreshing = true)
        loadJob = scope.launch {
            val analysis = try {
                load(key)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                null
            }
            _uiState.value = if (analysis == null) AnalysisUiState.Empty else AnalysisUiState.Success(analysis, view)
        }
    }

    /**
     * "Relatório PDF" (AB15 no app): o mesmo arquivo do bot, com o mesmo nome. Cancelar o
     * diálogo não é falha — nada é publicado.
     */
    fun exportPdf(language: AppLanguage) {
        val writer = exportWriter ?: return
        val build = pdfOf ?: return
        val current = _uiState.value as? AnalysisUiState.Success ?: return
        scope.launch {
            val outcome = try {
                val bytes = build(current.analysis, language)
                val name = TelegramAnalysisImageLabels.reportFileName(current.analysis, language)
                writer.write(UsageExportRequest(name, UsageExportPayload.Binary(bytes)))?.let { path -> CliExportOutcome.Saved(path) }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                CliExportOutcome.Failed(error.message ?: error::class.simpleName.orEmpty())
            } ?: return@launch
            val latest = _uiState.value as? AnalysisUiState.Success ?: return@launch
            _uiState.value = latest.copy(exportOutcome = outcome)
        }
    }

    fun onDestroy() {
        scope.cancel()
    }
}

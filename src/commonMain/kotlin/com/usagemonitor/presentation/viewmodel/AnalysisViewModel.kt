package com.usagemonitor.presentation.viewmodel

import com.usagemonitor.domain.entity.AnalysisView
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
    data class Success(val analysis: SessionAnalysis, val view: AnalysisView, val isRefreshing: Boolean = false) : AnalysisUiState
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
    dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
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

    fun onDestroy() {
        scope.cancel()
    }
}

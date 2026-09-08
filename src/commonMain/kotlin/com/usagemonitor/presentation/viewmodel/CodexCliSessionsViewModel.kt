package com.usagemonitor.presentation.viewmodel

import com.usagemonitor.data.export.UsageExportFormat
import com.usagemonitor.domain.entity.CodexCliSessionDetail
import com.usagemonitor.domain.entity.CodexCliSessionIndexReport
import com.usagemonitor.domain.entity.CodexCliSessionSummary
import com.usagemonitor.domain.usecase.GetCodexCliSessionDetailUseCase
import com.usagemonitor.domain.usecase.GetCodexCliSessionsUseCase
import com.usagemonitor.presentation.ui.exportRequestForCodexCliSessions
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

enum class CodexCliSessionRange {
    LAST_5H,
    LAST_7D,
    ALL
}

sealed interface CodexCliSessionsUiState {
    data object Loading : CodexCliSessionsUiState

    data class Error(val message: String) : CodexCliSessionsUiState

    data class Success(
        val sessions: List<CodexCliSessionSummary>,
        val range: CodexCliSessionRange,
        val readAt: Instant,
        val indexReport: CodexCliSessionIndexReport? = null,
        val indexWarning: String? = null,
        val detail: CodexCliSessionDetail? = null,
        val detailLoading: Boolean = false,
        val isRefreshing: Boolean = false,
        val exportOutcome: CodexCliExportOutcome? = null
    ) : CodexCliSessionsUiState
}

sealed interface CodexCliExportOutcome {
    data class Saved(val path: String) : CodexCliExportOutcome
    data class Failed(val message: String) : CodexCliExportOutcome
}

class CodexCliSessionsViewModel(
    private val getSessions: GetCodexCliSessionsUseCase,
    private val getDetail: GetCodexCliSessionDetailUseCase,
    private val exportWriter: UsageExportWriter? = null,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val clock: Clock = Clock.System,
    private val liveIntervalMillis: Long? = null,
    autoLoad: Boolean = true
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var loadJob: Job? = null
    private var detailJob: Job? = null
    private var liveJob: Job? = null
    private var exportJob: Job? = null
    private var range = CodexCliSessionRange.LAST_5H

    private val _uiState = MutableStateFlow<CodexCliSessionsUiState>(CodexCliSessionsUiState.Loading)
    val uiState: StateFlow<CodexCliSessionsUiState> = _uiState.asStateFlow()

    init {
        if (autoLoad) refresh()
    }

    fun refresh() {
        loadJob?.cancel()
        val requestedRange = range
        val current = _uiState.value
        if (current is CodexCliSessionsUiState.Success) {
            _uiState.value = current.copy(isRefreshing = true)
        } else {
            _uiState.value = CodexCliSessionsUiState.Loading
        }
        loadJob = scope.launch {
            val result = getSessions(cutoffFor(requestedRange))
            // A datasource may be inside a non-cancellable IO section when a
            // second range is selected. Never let that older response replace
            // the result requested most recently by the user.
            if (range != requestedRange) return@launch
            result.fold(
                onSuccess = { loaded ->
                    val latest = _uiState.value as? CodexCliSessionsUiState.Success
                    _uiState.value = CodexCliSessionsUiState.Success(
                        sessions = loaded.sessions,
                        range = requestedRange,
                        readAt = loaded.readAt,
                        indexReport = loaded.indexReport,
                        indexWarning = loaded.indexError?.message,
                        detail = latest?.detail,
                        detailLoading = latest?.detailLoading ?: false,
                        exportOutcome = latest?.exportOutcome,
                        isRefreshing = false
                    )
                },
                onFailure = { error ->
                    if (range != requestedRange) return@fold
                    _uiState.value = CodexCliSessionsUiState.Error(error.message ?: "Falha ao ler sessões do Codex CLI.")
                }
            )
        }
    }

    fun setRange(range: CodexCliSessionRange) {
        if (this.range == range) return
        this.range = range
        val current = _uiState.value
        if (current is CodexCliSessionsUiState.Success) {
            _uiState.value = current.copy(range = range, isRefreshing = true, detail = null, detailLoading = false)
        }
        refresh()
    }

    fun openSession(sessionId: String) {
        val current = _uiState.value as? CodexCliSessionsUiState.Success ?: return
        detailJob?.cancel()
        _uiState.value = current.copy(detailLoading = true)
        detailJob = scope.launch {
            getDetail(sessionId).fold(
                onSuccess = { detail ->
                    val state = _uiState.value as? CodexCliSessionsUiState.Success ?: return@fold
                    _uiState.value = state.copy(detail = detail, detailLoading = false)
                },
                onFailure = { error ->
                    val state = _uiState.value as? CodexCliSessionsUiState.Success ?: return@fold
                    _uiState.value = state.copy(detailLoading = false, indexWarning = error.message)
                }
            )
        }
    }

    fun closeDetail() {
        detailJob?.cancel()
        detailJob = null
        val current = _uiState.value as? CodexCliSessionsUiState.Success ?: return
        _uiState.value = current.copy(detail = null, detailLoading = false)
    }

    fun exportCurrent(format: UsageExportFormat) {
        val writer = exportWriter ?: return
        val current = _uiState.value as? CodexCliSessionsUiState.Success ?: return
        val request = exportRequestForCodexCliSessions(
            sessions = current.sessions,
            range = current.range,
            format = format,
            now = clock.now()
        )
        exportJob?.cancel()
        exportJob = scope.launch {
            val outcome = runCatching { writer.write(request) }.fold(
                onSuccess = { path -> path?.let { saved -> CodexCliExportOutcome.Saved(saved) } },
                onFailure = { error -> CodexCliExportOutcome.Failed(error.message ?: "Falha ao exportar.") }
            ) ?: return@launch
            val latest = _uiState.value as? CodexCliSessionsUiState.Success ?: return@launch
            _uiState.value = latest.copy(exportOutcome = outcome)
        }
    }

    fun openWindow() {
        // A janela sempre começa no recorte operacional padrão, mesmo quando
        // o ViewModel foi reutilizado depois de uma seleção anterior.
        range = CodexCliSessionRange.LAST_5H
        val current = _uiState.value
        if (current is CodexCliSessionsUiState.Success) {
            _uiState.value = current.copy(
                range = CodexCliSessionRange.LAST_5H,
                isRefreshing = true,
                detail = null
            )
        }
        refresh()
        if (liveIntervalMillis == null || liveJob != null) return
        liveJob = scope.launch {
            while (true) {
                delay(liveIntervalMillis)
                refresh()
                loadJob?.join()
                reloadOpenDetail()
            }
        }
    }

    fun closeWindow() {
        liveJob?.cancel()
        liveJob = null
        closeDetail()
    }

    fun onDestroy() {
        loadJob?.cancel()
        detailJob?.cancel()
        liveJob?.cancel()
        exportJob?.cancel()
        scope.cancel()
    }

    private suspend fun reloadOpenDetail() {
        val current = _uiState.value as? CodexCliSessionsUiState.Success ?: return
        val detail = current.detail ?: return
        val reloaded = getDetail(detail.summary.sessionId)
        val loaded = reloaded.getOrNull() ?: return
        val latest = _uiState.value as? CodexCliSessionsUiState.Success ?: return
        if (latest.detail?.summary?.sessionId == detail.summary.sessionId) {
            _uiState.value = latest.copy(detail = loaded)
        }
    }

    private fun cutoffFor(range: CodexCliSessionRange): Long? {
        val now = clock.now().toEpochMilliseconds()
        return when (range) {
            CodexCliSessionRange.LAST_5H -> now - 5L * 60L * 60L * 1000L
            CodexCliSessionRange.LAST_7D -> now - 7L * 24L * 60L * 60L * 1000L
            CodexCliSessionRange.ALL -> null
        }
    }
}

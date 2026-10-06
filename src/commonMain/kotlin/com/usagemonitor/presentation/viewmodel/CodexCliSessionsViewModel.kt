package com.usagemonitor.presentation.viewmodel

import com.usagemonitor.domain.entity.UsageExportFormat
import com.usagemonitor.domain.repository.UsageExportEncoder
import com.usagemonitor.domain.entity.CodexCliSessionDetail
import com.usagemonitor.domain.entity.CodexCliSessionIndexReport
import com.usagemonitor.domain.entity.CodexCliSessionSummary
import com.usagemonitor.domain.entity.breadcrumbFailureReasonOf
import com.usagemonitor.domain.repository.BreadcrumbRecorder
import com.usagemonitor.domain.repository.NoOpBreadcrumbRecorder
import com.usagemonitor.domain.usecase.GetCodexCliSessionDetailUseCase
import com.usagemonitor.domain.usecase.GetCodexCliSessionsUseCase
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.UsageExportRequest
import com.usagemonitor.presentation.ui.codexReportRequest
import com.usagemonitor.presentation.ui.exportRequestForCodexCliSessions
import com.usagemonitor.presentation.ui.report.reportForCodexCliSessions
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
import kotlin.time.Clock
import kotlin.time.Instant

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
        val exportOutcome: CodexCliExportOutcome? = null,
        /** Lista ou Resumo (#384) — o mesmo par de abas do modal do Anthropic. */
        val view: CliSessionsView = CliSessionsView.SESSIONS
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
    /** Serialização CSV/JSON; sem ela a exportação fica desligada, como sem [exportWriter]. */
    private val exportEncoder: UsageExportEncoder? = null,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val clock: Clock = Clock.System,
    private val liveIntervalMillis: Long? = null,
    autoLoad: Boolean = true,
    private val breadcrumbs: BreadcrumbRecorder = NoOpBreadcrumbRecorder
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var loadJob: Job? = null
    private var detailJob: Job? = null
    private var liveJob: Job? = null
    private var exportJob: Job? = null
    private var range = CodexCliSessionRange.LAST_5H
    private var lastSessionsFailureKey: String? = null
    private var lastIndexFailureKey: String? = null
    private var lastDetailFailureKey: String? = null

    private val _uiState = MutableStateFlow<CodexCliSessionsUiState>(CodexCliSessionsUiState.Loading)
    val uiState: StateFlow<CodexCliSessionsUiState> = _uiState.asStateFlow()

    init {
        if (autoLoad) refresh()
    }

    /**
     * Relê o índice. [showProgress] falso é a passada do laço ao vivo: ela roda a
     * cada 5 s, e marcar `isRefreshing` em todas fazia a barra piscar
     * "Atualizando…" sem parar — a tela parecia recarregar em vez de estar viva
     * (#384). O selo AO VIVO já diz que a leitura é contínua.
     */
    fun refresh(showProgress: Boolean = true) {
        loadJob?.cancel()
        val requestedRange = range
        val current = _uiState.value
        if (current is CodexCliSessionsUiState.Success) {
            if (showProgress) {
                _uiState.value = current.copy(isRefreshing = true)
            }
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
                    lastSessionsFailureKey = null
                    val indexError = loaded.indexError
                    if (indexError == null) {
                        lastIndexFailureKey = null
                    } else {
                        val failureKey = breadcrumbFailureReasonOf(indexError)
                        if (failureKey != lastIndexFailureKey) {
                            lastIndexFailureKey = failureKey
                            breadcrumbs.recordFailure("indexar sessões do Codex CLI", indexError)
                        }
                    }
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
                        isRefreshing = false,
                        view = latest?.view ?: CliSessionsView.SESSIONS
                    )
                },
                onFailure = { error ->
                    if (range != requestedRange) return@fold
                    val failureKey = breadcrumbFailureReasonOf(error)
                    if (failureKey != lastSessionsFailureKey) {
                        lastSessionsFailureKey = failureKey
                        breadcrumbs.recordFailure("carregar sessões do Codex CLI", error)
                    }
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
                    lastDetailFailureKey = null
                    val state = _uiState.value as? CodexCliSessionsUiState.Success ?: return@fold
                    _uiState.value = state.copy(detail = detail, detailLoading = false)
                },
                onFailure = { error ->
                    val failureKey = breadcrumbFailureReasonOf(error)
                    if (failureKey != lastDetailFailureKey) {
                        lastDetailFailureKey = failureKey
                        breadcrumbs.recordFailure("carregar detalhe de sessão do Codex CLI", error)
                    }
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
        val encoder = exportEncoder ?: return
        val current = _uiState.value as? CodexCliSessionsUiState.Success ?: return
        val request = exportRequestForCodexCliSessions(
            encoder = encoder,
            sessions = current.sessions,
            range = current.range,
            format = format,
            now = clock.now()
        )
        publishExport(writer, request)
    }

    private fun publishExport(writer: UsageExportWriter, request: UsageExportRequest) {
        exportJob?.cancel()
        exportJob = scope.launch {
            val outcome = runCatching { writer.write(request) }.fold(
                onSuccess = { path -> path?.let { saved -> CodexCliExportOutcome.Saved(saved) } },
                onFailure = { error ->
                    breadcrumbs.recordFailure("exportar sessões do Codex CLI", error)
                    CodexCliExportOutcome.Failed(error.message ?: "Falha ao exportar.")
                }
            ) ?: return@launch
            val latest = _uiState.value as? CodexCliSessionsUiState.Success ?: return@launch
            _uiState.value = latest.copy(exportOutcome = outcome)
        }
    }

    fun selectView(view: CliSessionsView) {
        val current = _uiState.value as? CodexCliSessionsUiState.Success ?: return
        _uiState.value = current.copy(view = view)
    }

    /** PDF do recorte da tela, no idioma do app (#384). */
    fun exportReport(language: AppLanguage) {
        val writer = exportWriter ?: return
        val current = _uiState.value as? CodexCliSessionsUiState.Success ?: return
        val now = clock.now()
        val request = codexReportRequest(
            document = reportForCodexCliSessions(current.sessions, current.range, language, now),
            range = current.range,
            now = now,
            language = language
        )
        publishExport(writer, request)
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
                refresh(showProgress = false)
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

package com.usagemonitor.presentation.viewmodel

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CliSessionRange
import com.usagemonitor.domain.entity.ComparisonRow
import com.usagemonitor.domain.entity.breadcrumbFailureReasonOf
import com.usagemonitor.domain.repository.BreadcrumbRecorder
import com.usagemonitor.domain.repository.NoOpBreadcrumbRecorder
import com.usagemonitor.domain.usecase.BuildModelComparisonUseCase
import com.usagemonitor.presentation.ui.comparisonReportRequest
import com.usagemonitor.presentation.ui.report.reportForComparison
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
import kotlin.time.Clock
import kotlin.time.Instant

sealed interface ComparisonUiState {
    data object Loading : ComparisonUiState
    data class Error(val message: String) : ComparisonUiState
    data class Success(
        val rows: List<ComparisonRow>,
        val range: CliSessionRange,
        val readAt: Instant,
        val isRefreshing: Boolean = false,
        val exportOutcome: CliExportOutcome? = null
    ) : ComparisonUiState
}

/**
 * Janela de comparação entre modelos e fontes (#386, direção P7 — mapa de calor).
 *
 * Lê sob demanda, ao abrir e ao trocar o recorte: a comparação é uma pergunta
 * pontual, não um painel ao vivo. As cotas vêm da última coleta do dashboard,
 * a mesma que a HUD mostra — nenhuma ida extra à rede.
 */
class ComparisonViewModel(
    private val buildComparison: BuildModelComparisonUseCase,
    private val dashboardState: StateFlow<UiState>,
    private val exportWriter: UsageExportWriter? = null,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val clock: Clock = Clock.System,
    private val breadcrumbs: BreadcrumbRecorder = NoOpBreadcrumbRecorder
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var loadJob: Job? = null
    private var exportJob: Job? = null
    private var range = CliSessionRange.LAST_7D
    private var lastFailureKey: String? = null

    private val _uiState = MutableStateFlow<ComparisonUiState>(ComparisonUiState.Loading)
    val uiState: StateFlow<ComparisonUiState> = _uiState.asStateFlow()

    fun openWindow() {
        refresh()
    }

    fun setRange(selected: CliSessionRange) {
        if (selected == range && _uiState.value is ComparisonUiState.Success) {
            return
        }
        range = selected
        refresh()
    }

    fun refresh() {
        loadJob?.cancel()
        val requested = range
        val current = _uiState.value
        if (current is ComparisonUiState.Success) {
            _uiState.value = current.copy(isRefreshing = true)
        }
        loadJob = scope.launch {
            val now = clock.now()
            val since = requested.durationMillis?.let { duration -> now.toEpochMilliseconds() - duration } ?: 0L
            val stats = (dashboardState.value as? UiState.Success)?.data.orEmpty()
            val result = buildComparison(since, stats)
            if (requested != range) return@launch
            result.fold(
                onSuccess = { rows ->
                    lastFailureKey = null
                    _uiState.value = ComparisonUiState.Success(rows = rows, range = requested, readAt = now)
                },
                onFailure = { error ->
                    val key = breadcrumbFailureReasonOf(error)
                    if (key != lastFailureKey) {
                        lastFailureKey = key
                        breadcrumbs.recordFailure("montar comparação entre modelos", error)
                    }
                    // Leitura que falha mantém os números anteriores (regra do app).
                    val previous = _uiState.value as? ComparisonUiState.Success
                    _uiState.value = previous?.copy(isRefreshing = false)
                        ?: ComparisonUiState.Error(error.message ?: "Falha ao ler o uso local.")
                }
            )
        }
    }

    /** PDF do recorte da tela, no idioma do app (#386). */
    fun exportReport(language: AppLanguage) {
        val writer = exportWriter ?: return
        val current = _uiState.value as? ComparisonUiState.Success ?: return
        val now = clock.now()
        val request = comparisonReportRequest(
            document = reportForComparison(current.rows, current.range, language, now),
            range = current.range,
            now = now,
            language = language
        )
        exportJob?.cancel()
        exportJob = scope.launch {
            val outcome = runCatching { writer.write(request) }.fold(
                onSuccess = { path -> path?.let { saved -> CliExportOutcome.Saved(saved) } },
                onFailure = { error ->
                    breadcrumbs.recordFailure("exportar comparação entre modelos", error)
                    CliExportOutcome.Failed(error.message ?: "Falha ao exportar.")
                }
            ) ?: return@launch
            val latest = _uiState.value as? ComparisonUiState.Success ?: return@launch
            _uiState.value = latest.copy(exportOutcome = outcome)
        }
    }

    fun onDestroy() {
        scope.cancel()
    }
}

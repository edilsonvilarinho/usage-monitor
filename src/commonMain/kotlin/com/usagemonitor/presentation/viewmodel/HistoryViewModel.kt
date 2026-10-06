package com.usagemonitor.presentation.viewmodel

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.HistoryRange
import com.usagemonitor.domain.entity.UsageAccountContext
import com.usagemonitor.domain.entity.UsageAccountKey
import com.usagemonitor.domain.entity.requiresUsageAccount
import com.usagemonitor.domain.repository.BreadcrumbRecorder
import com.usagemonitor.domain.repository.NoOpBreadcrumbRecorder
import com.usagemonitor.domain.usecase.GetUsageHistoryUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.update
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.report.historyReportRequest
import kotlin.time.Clock

class HistoryViewModel(
    private val getUsageHistory: GetUsageHistoryUseCase,
    private val enabledApis: StateFlow<Set<ApiSource>>,
    private val breadcrumbs: BreadcrumbRecorder = NoOpBreadcrumbRecorder,
    private val exportWriter: UsageExportWriter? = null,
    private val clock: Clock = Clock.System
) {
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loadJob: Job? = null
    private var exportJob: Job? = null
    val canWriteReport: Boolean get() = exportWriter != null
    @Volatile private var loadRequestId: Long = 0L

    private val _uiState = MutableStateFlow<HistoryUiState>(HistoryUiState.Loading)
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    private val selectedSource = MutableStateFlow<ApiSource?>(null)
    private val selectedRange = MutableStateFlow(HistoryRange.LAST_24_HOURS)
    private val selectedAccountsBySource = mutableMapOf<ApiSource, UsageAccountKey>()
    private val selectedQuotaView = MutableStateFlow(HistoryQuotaView.BOTH)

    init {
        refresh()
    }

    fun refresh() {
        reload(keepContent = true)
    }

    /**
     * [keepContent] mantém o `Success` da mesma fonte na tela durante a leitura
     * (issue #320). Cada troca de intervalo publicava `Loading`, e a janela
     * inteira piscava "Carregando histórico..." entre dois gráficos da mesma
     * fonte. Trocar de fonte continua passando por `Loading`: ali o conteúdo
     * anterior é de outra API e não pode ficar como se fosse desta.
     */
    private fun reload(keepContent: Boolean) {
        exportJob?.cancel()
        val requestId = ++loadRequestId
        loadJob?.cancel()
        val current = _uiState.value
        if (keepContent && current is HistoryUiState.Success && current.selectedSource == selectedSource.value) {
            _uiState.value = current.copy(selectedRange = selectedRange.value, isRefreshing = true, isExporting = false, exportOutcome = null)
        } else {
            _uiState.value = HistoryUiState.Loading
        }
        loadJob = viewModelScope.launch {
            loadHistory(requestId)
        }
    }

    fun openForSource(source: ApiSource, accountKey: UsageAccountKey? = null) {
        selectedSource.value = source
        if (accountKey != null && accountKey.source == source) {
            selectedAccountsBySource[source] = accountKey
        }
        reload(keepContent = false)
    }

    fun selectSource(source: ApiSource) {
        if (source == selectedSource.value) {
            return
        }

        selectedSource.value = source
        reload(keepContent = false)
    }

    fun selectRange(range: HistoryRange) {
        if (range == selectedRange.value) {
            return
        }

        selectedRange.value = range
        refresh()
    }

    /**
     * Troca a janela mostrada sem voltar ao banco: o relatório já traz as duas
     * séries, e reler o SQLite para filtrar o que está em memória faria a tela
     * piscar `Loading` por uma escolha puramente visual.
     */
    fun selectQuotaView(view: HistoryQuotaView) {
        if (view == selectedQuotaView.value) {
            return
        }

        selectedQuotaView.value = view
        val current = _uiState.value
        if (current is HistoryUiState.Success) {
            _uiState.value = current.copy(selectedQuotaView = view)
        }
    }

    fun selectAccount(account: UsageAccountContext) {
        val source = selectedSource.value ?: return
        if (account.key.source != source || selectedAccountsBySource[source] == account.key) {
            return
        }

        selectedAccountsBySource[source] = account.key
        refresh()
    }

    fun onDestroy() {
        exportJob?.cancel()
        loadJob?.cancel()
        viewModelScope.cancel()
    }

    /** Captura o estado estabilizado; filtros novos nunca descrevem dados antigos. */
    fun exportReport(language: AppLanguage) {
        val writer = exportWriter ?: return
        val snapshot = _uiState.value as? HistoryUiState.Success ?: return
        if (snapshot.report.isEmpty || snapshot.isRefreshing || snapshot.isExporting) return
        val requestId = loadRequestId
        _uiState.value = snapshot.copy(isExporting = true, exportOutcome = null)
        exportJob = viewModelScope.launch {
            var outcome: CliExportOutcome? = null
            try {
                val request = historyReportRequest(snapshot, language, clock.now())
                val path = writer.write(request)
                if (path != null) outcome = CliExportOutcome.Saved(path)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                breadcrumbs.recordFailure("exportar histórico PDF", error)
                outcome = CliExportOutcome.Failed(error.message ?: "erro desconhecido")
            } finally {
                _uiState.update { latest ->
                    if (loadRequestId == requestId && latest is HistoryUiState.Success && latest.report == snapshot.report) {
                        latest.copy(isExporting = false, exportOutcome = outcome)
                    } else latest
                }
            }
        }
    }

    private suspend fun loadHistory(requestId: Long) {
        val enabledSources = enabledApis.value.sortedBy { it.ordinal }
        try {
            if (enabledSources.isEmpty()) {
                selectedSource.value = null
                publishIfLatest(
                    requestId,
                    HistoryUiState.Empty(
                        availableSources = emptyList(),
                        selectedSource = null,
                        selectedRange = selectedRange.value
                    )
                )
                return
            }

            val resolvedSource = selectedSource.value?.takeIf { it in enabledSources }
                ?: enabledSources.first()
            selectedSource.value = resolvedSource
            val availableAccounts = if (resolvedSource.requiresUsageAccount) {
                getUsageHistory.listAccounts(resolvedSource)
            } else {
                emptyList()
            }
            val selectedAccount = resolveSelectedAccount(resolvedSource, availableAccounts)
            val report = getUsageHistory(
                source = resolvedSource,
                range = selectedRange.value,
                accountKey = selectedAccount?.key
            )

            publishIfLatest(
                requestId,
                HistoryUiState.Success(
                    availableSources = enabledSources,
                    selectedSource = resolvedSource,
                    selectedRange = selectedRange.value,
                    report = report,
                    availableAccounts = availableAccounts,
                    selectedAccount = selectedAccount,
                    selectedQuotaView = selectedQuotaView.value
                )
            )
        } catch (error: Throwable) {
            breadcrumbs.recordFailure("carregar histórico de uso", error)
            publishIfLatest(
                requestId,
                HistoryUiState.Error(
                    message = error.message ?: "erro desconhecido",
                    availableSources = enabledSources,
                    selectedSource = selectedSource.value,
                    selectedRange = selectedRange.value
                )
            )
        }
    }

    private fun publishIfLatest(requestId: Long, state: HistoryUiState) {
        if (requestId == loadRequestId) {
            _uiState.value = state
        }
    }

    private fun resolveSelectedAccount(
        source: ApiSource,
        availableAccounts: List<UsageAccountContext>
    ): UsageAccountContext? {
        val selectedKey = selectedAccountsBySource[source]
        val selected = availableAccounts.firstOrNull { account -> account.key == selectedKey }
        if (selected != null) {
            return selected
        }

        val latestAccount = availableAccounts.firstOrNull()
        if (latestAccount != null) {
            selectedAccountsBySource[source] = latestAccount.key
        } else {
            selectedAccountsBySource.remove(source)
        }
        return latestAccount
    }
}

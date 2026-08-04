package com.usagemonitor.presentation.viewmodel

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.HistoryRange
import com.usagemonitor.domain.entity.UsageAccountContext
import com.usagemonitor.domain.entity.UsageAccountKey
import com.usagemonitor.domain.entity.requiresUsageAccount
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

class HistoryViewModel(
    private val getUsageHistory: GetUsageHistoryUseCase,
    private val enabledApis: StateFlow<Set<ApiSource>>
) {
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loadJob: Job? = null
    @Volatile private var loadRequestId: Long = 0L

    private val _uiState = MutableStateFlow<HistoryUiState>(HistoryUiState.Loading)
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    private val selectedSource = MutableStateFlow<ApiSource?>(null)
    private val selectedRange = MutableStateFlow(HistoryRange.LAST_24_HOURS)
    private val selectedAccountsBySource = mutableMapOf<ApiSource, UsageAccountKey>()

    init {
        refresh()
    }

    fun refresh() {
        val requestId = ++loadRequestId
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            loadHistory(requestId)
        }
    }

    fun openForSource(source: ApiSource) {
        selectedSource.value = source
        refresh()
    }

    fun selectSource(source: ApiSource) {
        if (source == selectedSource.value) {
            return
        }

        selectedSource.value = source
        refresh()
    }

    fun selectRange(range: HistoryRange) {
        if (range == selectedRange.value) {
            return
        }

        selectedRange.value = range
        refresh()
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
        loadJob?.cancel()
        viewModelScope.cancel()
    }

    private suspend fun loadHistory(requestId: Long) {
        _uiState.value = HistoryUiState.Loading

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
                    selectedAccount = selectedAccount
                )
            )
        } catch (error: Throwable) {
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

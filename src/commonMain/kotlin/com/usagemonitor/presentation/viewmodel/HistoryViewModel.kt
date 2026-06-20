package com.usagemonitor.presentation.viewmodel

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.HistoryRange
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
            val report = getUsageHistory(resolvedSource, selectedRange.value)

            publishIfLatest(
                requestId,
                HistoryUiState.Success(
                    availableSources = enabledSources,
                    selectedSource = resolvedSource,
                    selectedRange = selectedRange.value,
                    report = report
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
}

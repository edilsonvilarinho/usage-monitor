package com.usagemonitor.presentation.viewmodel

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.HistoryRange
import com.usagemonitor.domain.usecase.GetUsageHistoryUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

    private val _uiState = MutableStateFlow<HistoryUiState>(HistoryUiState.Loading)
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    private var selectedSource: ApiSource? = null
    private var selectedRange: HistoryRange = HistoryRange.LAST_24_HOURS

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            loadHistory()
        }
    }

    fun selectSource(source: ApiSource) {
        if (source == selectedSource) {
            return
        }

        selectedSource = source
        refresh()
    }

    fun selectRange(range: HistoryRange) {
        if (range == selectedRange) {
            return
        }

        selectedRange = range
        refresh()
    }

    fun onDestroy() {
        viewModelScope.cancel()
    }

    private suspend fun loadHistory() {
        _uiState.value = HistoryUiState.Loading

        val enabledSources = enabledApis.value.sortedBy { it.ordinal }
        try {
            val sourcesWithHistory = enabledSources.filter { source ->
                !getUsageHistory(source, HistoryRange.LAST_30_DAYS).isEmpty
            }

            if (sourcesWithHistory.isEmpty()) {
                selectedSource = null
                _uiState.value = HistoryUiState.Empty(
                    availableSources = emptyList(),
                    selectedSource = null,
                    selectedRange = selectedRange
                )
                return
            }

            val resolvedSource = if (selectedSource in sourcesWithHistory) {
                selectedSource
            } else {
                sourcesWithHistory.first()
            }

            selectedSource = resolvedSource
            val report = getUsageHistory(resolvedSource!!, selectedRange)

            _uiState.value = HistoryUiState.Success(
                availableSources = sourcesWithHistory,
                selectedSource = resolvedSource,
                selectedRange = selectedRange,
                report = report
            )
        } catch (error: Throwable) {
            _uiState.value = HistoryUiState.Error(
                message = error.message ?: "erro desconhecido",
                availableSources = enabledSources,
                selectedSource = selectedSource,
                selectedRange = selectedRange
            )
        }
    }
}

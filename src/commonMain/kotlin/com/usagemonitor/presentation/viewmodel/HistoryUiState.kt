package com.usagemonitor.presentation.viewmodel

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageHistoryReport
import com.usagemonitor.domain.entity.HistoryRange
import com.usagemonitor.domain.entity.UsageAccountContext

sealed interface HistoryUiState {
    data object Loading : HistoryUiState

    data class Empty(
        val availableSources: List<ApiSource>,
        val selectedSource: ApiSource?,
        val selectedRange: HistoryRange,
        val availableAccounts: List<UsageAccountContext> = emptyList(),
        val selectedAccount: UsageAccountContext? = null
    ) : HistoryUiState

    data class Error(
        val message: String,
        val availableSources: List<ApiSource>,
        val selectedSource: ApiSource?,
        val selectedRange: HistoryRange,
        val availableAccounts: List<UsageAccountContext> = emptyList(),
        val selectedAccount: UsageAccountContext? = null
    ) : HistoryUiState

    data class Success(
        val availableSources: List<ApiSource>,
        val selectedSource: ApiSource,
        val selectedRange: HistoryRange,
        val report: ApiUsageHistoryReport,
        val availableAccounts: List<UsageAccountContext> = emptyList(),
        val selectedAccount: UsageAccountContext? = null
    ) : HistoryUiState
}

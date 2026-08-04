package com.usagemonitor.domain.repository

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageHistoryReport
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.HistoryRange
import com.usagemonitor.domain.entity.UsageAccountContext
import com.usagemonitor.domain.entity.UsageAccountKey
import kotlinx.datetime.Instant

interface UsageHistoryRepository {
    suspend fun recordSnapshot(stats: ApiUsageStats, capturedAt: Instant)

    suspend fun listAccounts(source: ApiSource): List<UsageAccountContext> = emptyList()

    suspend fun getHistoryReport(
        source: ApiSource,
        range: HistoryRange,
        now: Instant
    ): ApiUsageHistoryReport

    suspend fun getHistoryReport(
        source: ApiSource,
        accountKey: UsageAccountKey?,
        range: HistoryRange,
        now: Instant
    ): ApiUsageHistoryReport = getHistoryReport(source, range, now)
}

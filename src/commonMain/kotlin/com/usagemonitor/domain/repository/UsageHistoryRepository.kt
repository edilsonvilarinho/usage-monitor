package com.usagemonitor.domain.repository

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageHistoryReport
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.HistoryRange
import kotlinx.datetime.Instant

interface UsageHistoryRepository {
    suspend fun recordSnapshot(stats: ApiUsageStats, capturedAt: Instant)

    suspend fun getHistoryReport(
        source: ApiSource,
        range: HistoryRange,
        now: Instant
    ): ApiUsageHistoryReport
}

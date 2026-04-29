package com.usagemonitor.data.datasource

import com.usagemonitor.data.dto.UsageSnapshotRecord
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageStats
import kotlinx.datetime.Instant

interface UsageHistoryDataSource {
    suspend fun insertSnapshot(stats: ApiUsageStats, capturedAt: Instant)

    suspend fun readSnapshots(source: ApiSource, since: Instant): List<UsageSnapshotRecord>
}

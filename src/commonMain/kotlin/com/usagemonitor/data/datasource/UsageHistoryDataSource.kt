package com.usagemonitor.data.datasource

import com.usagemonitor.data.dto.UsageSnapshotRecord
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.UsageAccountContext
import com.usagemonitor.domain.entity.UsageAccountKey
import kotlinx.datetime.Instant

interface UsageHistoryDataSource {
    suspend fun insertSnapshot(stats: ApiUsageStats, capturedAt: Instant)

    suspend fun readSnapshots(source: ApiSource, since: Instant): List<UsageSnapshotRecord>

    suspend fun readSnapshots(
        source: ApiSource,
        accountKey: UsageAccountKey?,
        since: Instant
    ): List<UsageSnapshotRecord> = readSnapshots(source, since)

    suspend fun readAccounts(source: ApiSource): List<UsageAccountContext> = emptyList()
}

package com.usagemonitor.domain.usecase

import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.repository.UsageHistoryRepository
import kotlinx.datetime.Instant

class RecordUsageSnapshotUseCase(
    private val repository: UsageHistoryRepository
) {
    suspend operator fun invoke(stats: ApiUsageStats, capturedAt: Instant) {
        repository.recordSnapshot(stats, capturedAt)
    }
}

package com.usagemonitor.domain.usecase

import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.repository.DashboardCacheRepository
import kotlin.time.Instant

class SaveDashboardCacheUseCase(
    private val repository: DashboardCacheRepository
) {
    suspend operator fun invoke(stats: List<ApiUsageStats>, capturedAt: Instant): Result<Unit> {
        return Result.runCatching {
            repository.saveSnapshot(stats, capturedAt)
        }
    }
}

package com.usagemonitor.domain.usecase

import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.repository.DashboardCacheRepository

class GetCachedDashboardStatsUseCase(
    private val repository: DashboardCacheRepository
) {
    suspend operator fun invoke(): Result<List<ApiUsageStats>> {
        return Result.runCatching {
            repository.loadSnapshot()
        }
    }
}

package com.usagemonitor.data.datasource

import com.usagemonitor.domain.entity.ApiUsageStats
import kotlinx.datetime.Instant

interface DashboardCacheDataSource {
    suspend fun save(stats: List<ApiUsageStats>, capturedAt: Instant)

    suspend fun load(): List<ApiUsageStats>
}

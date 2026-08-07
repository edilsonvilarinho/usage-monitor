package com.usagemonitor.data.repository

import com.usagemonitor.data.datasource.DashboardCacheDataSource
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.repository.DashboardCacheRepository
import kotlinx.datetime.Instant

class DashboardCacheRepositoryImpl(
    private val dataSource: DashboardCacheDataSource
) : DashboardCacheRepository {

    override suspend fun saveSnapshot(stats: List<ApiUsageStats>, capturedAt: Instant) {
        dataSource.save(stats, capturedAt)
    }

    override suspend fun loadSnapshot(): List<ApiUsageStats> {
        return dataSource.load()
    }
}

package com.usagemonitor.domain.repository

import com.usagemonitor.domain.entity.ApiUsageStats

interface KiloRepository {
    suspend fun getUsage(): Result<ApiUsageStats>
}

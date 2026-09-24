package com.usagemonitor.domain.repository

import com.usagemonitor.domain.entity.ApiUsageStats

interface AntigravityRepository {
    suspend fun getUsage(): Result<ApiUsageStats>
}

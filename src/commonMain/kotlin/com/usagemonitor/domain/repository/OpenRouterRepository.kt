package com.usagemonitor.domain.repository

import com.usagemonitor.domain.entity.ApiUsageStats

interface OpenRouterRepository {
    suspend fun getUsage(): Result<ApiUsageStats>
}

package com.usagemonitor.domain.repository

import com.usagemonitor.domain.entity.ApiUsageStats

interface CodexRepository {
    suspend fun getUsage(): Result<ApiUsageStats>
}

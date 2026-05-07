package com.usagemonitor.domain.repository

import com.usagemonitor.domain.entity.ApiUsageStats

interface OpenCodeRepository {
    suspend fun getUsage(): Result<ApiUsageStats>
}

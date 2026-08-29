package com.usagemonitor.domain.repository

import com.usagemonitor.domain.entity.ApiUsageStats

interface OpenCodeGoRepository {
    suspend fun getUsage(): Result<ApiUsageStats>
}

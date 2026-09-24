package com.usagemonitor.domain.repository

import com.usagemonitor.domain.entity.ApiUsageStats

interface CursorRepository {
    suspend fun getUsage(): Result<ApiUsageStats>
}

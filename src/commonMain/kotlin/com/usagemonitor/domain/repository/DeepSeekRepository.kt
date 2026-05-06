package com.usagemonitor.domain.repository

import com.usagemonitor.domain.entity.ApiUsageStats

interface DeepSeekRepository {
    suspend fun getBalance(): Result<ApiUsageStats>
}

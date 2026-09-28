package com.usagemonitor.domain.repository

import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.CodexProfileRef

interface CodexRepository {
    suspend fun getUsage(): Result<ApiUsageStats>

    /** Uma conta Codex extra (issue #329); a padrão continua em [getUsage]. */
    suspend fun getUsage(profile: CodexProfileRef): Result<ApiUsageStats> = getUsage()
}

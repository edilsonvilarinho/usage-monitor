package com.usagemonitor.domain.usecase

import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.CodexProfileRef
import com.usagemonitor.domain.repository.CodexRepository

class GetCodexUsageUseCase(
    private val repository: CodexRepository
) {
    suspend operator fun invoke(profile: CodexProfileRef? = null): Result<ApiUsageStats> {
        return if (profile == null) repository.getUsage() else repository.getUsage(profile)
    }
}

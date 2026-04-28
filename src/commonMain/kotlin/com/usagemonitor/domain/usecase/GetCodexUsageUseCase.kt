package com.usagemonitor.domain.usecase

import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.repository.CodexRepository

class GetCodexUsageUseCase(
    private val repository: CodexRepository
) {
    suspend operator fun invoke(): Result<ApiUsageStats> {
        return repository.getUsage()
    }
}

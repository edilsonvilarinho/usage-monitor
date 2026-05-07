package com.usagemonitor.domain.usecase

import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.repository.OpenCodeRepository

class GetOpenCodeUsageUseCase(
    private val repository: OpenCodeRepository
) {
    suspend operator fun invoke(): Result<ApiUsageStats> {
        return repository.getUsage()
    }
}

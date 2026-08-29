package com.usagemonitor.domain.usecase

import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.repository.OpenCodeGoRepository

class GetOpenCodeGoUsageUseCase(private val repository: OpenCodeGoRepository) {
    suspend operator fun invoke(): Result<ApiUsageStats> = repository.getUsage()
}

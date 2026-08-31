package com.usagemonitor.domain.usecase

import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.repository.OpenRouterRepository

class GetOpenRouterUsageUseCase(private val repository: OpenRouterRepository) {
    suspend operator fun invoke(): Result<ApiUsageStats> = repository.getUsage()
}

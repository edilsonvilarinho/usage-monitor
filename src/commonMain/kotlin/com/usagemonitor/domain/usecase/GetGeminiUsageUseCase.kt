package com.usagemonitor.domain.usecase

import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.repository.GeminiRepository

class GetGeminiUsageUseCase(private val repository: GeminiRepository) {
    suspend operator fun invoke(): Result<ApiUsageStats> = repository.getUsage()
}

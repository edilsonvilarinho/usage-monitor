package com.usagemonitor.domain.usecase

import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.repository.DeepSeekRepository

class GetDeepSeekUsageUseCase(private val repository: DeepSeekRepository) {
    suspend operator fun invoke(): Result<ApiUsageStats> = repository.getBalance()
}

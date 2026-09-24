package com.usagemonitor.domain.usecase

import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.repository.AntigravityRepository

class GetAntigravityUsageUseCase(private val repository: AntigravityRepository) {
    suspend operator fun invoke(): Result<ApiUsageStats> = repository.getUsage()

    fun invalidateCachedReading() = repository.invalidateCachedReading()
}

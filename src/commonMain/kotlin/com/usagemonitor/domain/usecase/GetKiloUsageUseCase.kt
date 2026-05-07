package com.usagemonitor.domain.usecase

import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.repository.KiloRepository

class GetKiloUsageUseCase(
    private val repository: KiloRepository
) {
    suspend operator fun invoke(): Result<ApiUsageStats> {
        return repository.getUsage()
    }
}

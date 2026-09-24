package com.usagemonitor.domain.usecase

import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.repository.CursorRepository

class GetCursorUsageUseCase(private val repository: CursorRepository) {
    suspend operator fun invoke(): Result<ApiUsageStats> = repository.getUsage()
}

package com.usagemonitor.domain.usecase

import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.repository.MiniMaxRepository

/**
 * Use Case: busca as cotas de uso da MiniMax por modelo.
 * Segue o mesmo padrão do GetAnthropicUsageUseCase.
 */
class GetMiniMaxUsageUseCase(
    private val repository: MiniMaxRepository
) {
    suspend operator fun invoke(): Result<ApiUsageStats> {
        return repository.getUsage()
    }
}

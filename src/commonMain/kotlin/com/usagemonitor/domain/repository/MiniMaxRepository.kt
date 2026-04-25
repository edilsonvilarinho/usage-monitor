package com.usagemonitor.domain.repository

import com.usagemonitor.domain.entity.ApiUsageStats

/**
 * Contrato para obter os dados de uso da MiniMax.
 * Segue o mesmo princípio da InversaoDeDependência do AnthropicRepository.
 */
interface MiniMaxRepository {
    suspend fun getUsage(): Result<ApiUsageStats>
}

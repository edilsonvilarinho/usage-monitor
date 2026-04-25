package com.usagemonitor.data.repository

import com.usagemonitor.data.datasource.CredentialDataSource
import com.usagemonitor.data.datasource.RemoteApiDataSource
import com.usagemonitor.data.mapper.AnthropicMapper
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.repository.AnthropicRepository

/**
 * Implementação do contrato AnthropicRepository (definido no domain).
 *
 * Orquestra: credenciais → chamada HTTP → mapeamento para entidade.
 *
 * `Result.runCatching { }` é o equivalente Kotlin de um try/catch que
 * retorna Result.success() ou Result.failure() automaticamente.
 * Muito mais limpo que try/catch manual para operações que podem falhar.
 */
class AnthropicRepositoryImpl(
    private val credentialDataSource: CredentialDataSource,
    private val apiDataSource: RemoteApiDataSource
) : AnthropicRepository {

    override suspend fun getUsage(): Result<ApiUsageStats> {
        return Result.runCatching {
            val token = credentialDataSource.loadAnthropicAccessToken()
            val dto = apiDataSource.fetchAnthropicRateLimit(token)
            AnthropicMapper.toUsageStats(dto)
        }
    }
}

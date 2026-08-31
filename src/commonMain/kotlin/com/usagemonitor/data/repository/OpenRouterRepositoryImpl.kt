package com.usagemonitor.data.repository

import com.usagemonitor.data.datasource.RemoteApiDataSource
import com.usagemonitor.data.mapper.OpenRouterMapper
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.repository.OpenRouterRepository

class OpenRouterRepositoryImpl(
    private val apiDataSource: RemoteApiDataSource,
    private val apiKeyReader: () -> String?
) : OpenRouterRepository {

    override suspend fun getUsage(): Result<ApiUsageStats> {
        return Result.runCatching {
            val apiKey = apiKeyReader()?.takeIf { key -> key.isNotBlank() }
                ?: throw IllegalStateException("Chave da API OpenRouter não configurada. Abra Configurações > APIs e informe a chave.")
            val response = apiDataSource.fetchOpenRouterCredits(apiKey)
            OpenRouterMapper.toUsageStats(response)
        }
    }
}

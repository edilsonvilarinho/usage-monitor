package com.usagemonitor.data.repository

import com.usagemonitor.data.datasource.RemoteApiDataSource
import com.usagemonitor.data.mapper.DeepSeekMapper
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.repository.DeepSeekRepository

private const val ENV_VAR_NAME = "DEEPSEEK_API_KEY"

class DeepSeekRepositoryImpl(
    private val apiDataSource: RemoteApiDataSource,
    private val envVarReader: () -> String? = { System.getenv(ENV_VAR_NAME) }
) : DeepSeekRepository {

    override suspend fun getBalance(): Result<ApiUsageStats> {
        return Result.runCatching {
            val apiKey = envVarReader() ?: throw IllegalStateException(missingEnvVarMessage())
            val response = apiDataSource.fetchDeepSeekBalance(apiKey)
            DeepSeekMapper.toUsageStats(response)
        }
    }

    private fun missingEnvVarMessage(): String {
        return "Variável de ambiente $ENV_VAR_NAME não configurada.\n" +
            "Defina-a antes de iniciar a aplicação:\n" +
            "  Windows: set $ENV_VAR_NAME=sua_chave\n" +
            "  Linux/Mac: export $ENV_VAR_NAME=sua_chave"
    }
}

package com.usagemonitor.data.repository

import com.usagemonitor.data.datasource.RemoteApiDataSource
import com.usagemonitor.data.mapper.DeepSeekMapper
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.repository.DeepSeekRepository

class DeepSeekRepositoryImpl(
    private val apiDataSource: RemoteApiDataSource,
    private val apiKeyReader: () -> String?
) : DeepSeekRepository {

    override suspend fun getUsage(): Result<ApiUsageStats> {
        return Result.runCatching {
            val apiKey = apiKeyReader()?.takeIf { key -> key.isNotBlank() }
                ?: throw IllegalStateException("Chave da API DeepSeek não configurada. Abra Configurações > APIs e informe a chave.")
            val response = apiDataSource.fetchDeepSeekBalance(apiKey)
            DeepSeekMapper.toUsageStats(response)
        }
    }

}

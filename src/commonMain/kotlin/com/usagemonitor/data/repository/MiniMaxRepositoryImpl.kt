package com.usagemonitor.data.repository

import com.usagemonitor.data.datasource.RemoteApiDataSource
import com.usagemonitor.data.mapper.MiniMaxMapper
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.repository.MiniMaxRepository

/**
 * Implementação do contrato MiniMaxRepository.
 *
 * Lê a API Key exclusivamente da variável de ambiente MINIMAX_API_KEY.
 * É PROIBIDO ter a chave hardcoded aqui ou em qualquer outro ficheiro.
 */
private const val ENV_VAR_NAME = "MINIMAX_API_KEY"

class MiniMaxRepositoryImpl(
    private val apiDataSource: RemoteApiDataSource,
    // Costura de teste: permite injetar leitor da env var sem mexer em System.getenv global.
    private val envVarReader: () -> String? = { System.getenv(ENV_VAR_NAME) }
) : MiniMaxRepository {

    override suspend fun getUsage(): Result<ApiUsageStats> {
        return Result.runCatching {
            // Falha explícita se a variável de ambiente não estiver configurada
            val apiKey = envVarReader() ?: throw IllegalStateException(missingEnvVarMessage())

            val response = apiDataSource.fetchMiniMaxTokenPlan(apiKey)

            // status_code diferente de 0 indica erro na API MiniMax
            if (response.baseResp.statusCode != 0) {
                throw IllegalStateException(
                    "Erro na API MiniMax: ${response.baseResp.statusMsg} " +
                    "(código ${response.baseResp.statusCode})"
                )
            }

            MiniMaxMapper.toUsageStats(response)
        }
    }

    private fun missingEnvVarMessage(): String {
        return "Variável de ambiente $ENV_VAR_NAME não configurada.\n" +
            "Defina-a antes de iniciar a aplicação:\n" +
            "  Windows: set $ENV_VAR_NAME=sua_chave\n" +
            "  Linux/Mac: export $ENV_VAR_NAME=sua_chave"
    }
}

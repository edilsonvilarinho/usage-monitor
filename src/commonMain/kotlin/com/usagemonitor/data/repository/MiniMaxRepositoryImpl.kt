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
class MiniMaxRepositoryImpl(
    private val apiDataSource: RemoteApiDataSource
) : MiniMaxRepository {

    override suspend fun getUsage(): Result<ApiUsageStats> {
        return Result.runCatching {
            // Falha explícita se a variável de ambiente não estiver configurada
            val apiKey = System.getenv("MINIMAX_API_KEY")
                ?: throw IllegalStateException(
                    "Variável de ambiente MINIMAX_API_KEY não configurada.\n" +
                    "Defina-a antes de iniciar a aplicação:\n" +
                    "  Windows: set MINIMAX_API_KEY=sua_chave\n" +
                    "  Linux/Mac: export MINIMAX_API_KEY=sua_chave"
                )

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
}

package com.usagemonitor.data.repository

import com.usagemonitor.data.datasource.RemoteApiDataSource
import com.usagemonitor.data.mapper.MiniMaxMapper
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.repository.MiniMaxRepository

/**
 * Implementação do contrato MiniMaxRepository.
 *
 * A chave é fornecida por uma origem externa ao repositório; o desktop usa o
 * armazenamento local protegido e os testes podem injetar uma origem fake.
 */
private const val MINIMAX_NO_ACTIVE_PLAN_STATUS_CODE = 2062
private const val MINIMAX_NO_ACTIVE_PLAN_MARKER = "no active token plan subscription"

class MiniMaxRepositoryImpl(
    private val apiDataSource: RemoteApiDataSource,
    private val apiKeyReader: () -> String?
) : MiniMaxRepository {

    override suspend fun getUsage(): Result<ApiUsageStats> {
        return Result.runCatching {
            val apiKey = apiKeyReader()?.takeIf { key -> key.isNotBlank() }
                ?: throw IllegalStateException("Chave da API MiniMax não configurada. Abra Configurações > APIs e informe a chave.")

            val response = apiDataSource.fetchMiniMaxTokenPlan(apiKey)

            // status_code diferente de 0 indica erro na API MiniMax
            if (response.baseResp.statusCode != 0) {
                throw IllegalStateException(apiErrorMessage(response.baseResp.statusCode, response.baseResp.statusMsg))
            }

            MiniMaxMapper.toUsageStats(response)
        }
    }

    private fun apiErrorMessage(statusCode: Int, statusMessage: String): String {
        if (statusCode == MINIMAX_NO_ACTIVE_PLAN_STATUS_CODE ||
            statusMessage.contains(MINIMAX_NO_ACTIVE_PLAN_MARKER, ignoreCase = true)
        ) {
            return "MiniMax sem plano/token ativo. Ative um plano ou gere um token com assinatura válida e tente novamente."
        }

        return "Erro na API MiniMax: $statusMessage (código $statusCode)"
    }

}

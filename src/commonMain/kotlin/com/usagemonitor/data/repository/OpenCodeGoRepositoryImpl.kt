package com.usagemonitor.data.repository

import com.usagemonitor.data.datasource.RemoteApiDataSource
import com.usagemonitor.data.mapper.OpenCodeGoMapper
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.repository.OpenCodeGoRepository

/**
 * Mensagem de "conta sem assinatura Go".
 *
 * Ela é pública para os testes e para [com.usagemonitor.presentation.viewmodel.UiApiError]
 * a reconhecerem pelo mesmo texto — o app inteiro classifica erro por marcador de
 * mensagem, não por exceção tipada, e um segundo literal aqui abriria dois donos
 * para a mesma condição.
 */
const val OPEN_CODE_GO_NO_SUBSCRIPTION_MESSAGE =
    "OpenCode Go sem assinatura ativa para esta chave. Assine o plano Go ou desative esta integração."

const val OPEN_CODE_GO_API_KEY_MISSING_MESSAGE =
    "Chave da API OpenCode não configurada. Abra Configurações > APIs e informe a chave."

private const val OPEN_CODE_GO_FORBIDDEN_MARKER = "HTTP 403"
private const val OPEN_CODE_GO_ENTITLEMENT_MARKER = "EntitlementError"
private const val OPEN_CODE_GO_SUBSCRIPTION_MARKER = "subscription required"

class OpenCodeGoRepositoryImpl(
    private val apiDataSource: RemoteApiDataSource,
    private val apiKeyReader: () -> String?
) : OpenCodeGoRepository {

    override suspend fun getUsage(): Result<ApiUsageStats> {
        return Result.runCatching {
            val apiKey = apiKeyReader()?.takeIf { key -> key.isNotBlank() }
                ?: throw IllegalStateException(OPEN_CODE_GO_API_KEY_MISSING_MESSAGE)

            val response = try {
                apiDataSource.fetchOpenCodeGoUsage(apiKey)
            } catch (error: Throwable) {
                // O 403 de "sem assinatura Go" é um estado normal de quem só usa o
                // Zen pago: a chave é válida e não há nada a corrigir nela. Deixá-lo
                // cair no bloco genérico produziria um toast de falha a cada coleta
                // e mandaria o usuário revisar uma credencial que está correta.
                if (isMissingSubscriptionFailure(error.message)) {
                    throw IllegalStateException(OPEN_CODE_GO_NO_SUBSCRIPTION_MESSAGE, error)
                }
                throw error
            }

            OpenCodeGoMapper.toUsageStats(response)
        }
    }

    private fun isMissingSubscriptionFailure(message: String?): Boolean {
        if (message == null) return false
        if (!message.contains(OPEN_CODE_GO_FORBIDDEN_MARKER, ignoreCase = true)) return false
        return message.contains(OPEN_CODE_GO_ENTITLEMENT_MARKER, ignoreCase = true) ||
            message.contains(OPEN_CODE_GO_SUBSCRIPTION_MARKER, ignoreCase = true)
    }
}

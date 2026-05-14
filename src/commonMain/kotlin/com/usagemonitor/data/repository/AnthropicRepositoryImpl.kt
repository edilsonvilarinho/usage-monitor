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
            fetchUsageWithRecovery()
        }
    }

    private suspend fun fetchUsageWithRecovery(): ApiUsageStats {
        val cachedToken = credentialDataSource.loadAnthropicAccessToken()

        try {
            return fetchUsageForToken(cachedToken)
        } catch (error: Throwable) {
            if (!isScopeRequirementFailure(error)) {
                throw error
            }
        }

        credentialDataSource.invalidateAnthropicAccessTokenCache()
        val refreshedToken = credentialDataSource.loadAnthropicAccessToken()

        try {
            return fetchUsageForToken(refreshedToken)
        } catch (retryError: Throwable) {
            throw IllegalStateException(ANTHROPIC_REAUTH_GUIDANCE_MESSAGE, retryError)
        }
    }

    private suspend fun fetchUsageForToken(accessToken: String): ApiUsageStats {
        val dto = apiDataSource.fetchAnthropicUsage(accessToken)
        return AnthropicMapper.toUsageStats(dto)
    }

    private fun isScopeRequirementFailure(error: Throwable): Boolean {
        val message = error.message ?: return false
        return message.contains("Anthropic HTTP 403", ignoreCase = true) &&
            message.contains("permission_error", ignoreCase = true) &&
            message.contains("scope requirement", ignoreCase = true) &&
            message.contains("user:profile", ignoreCase = true)
    }

    companion object {
        const val ANTHROPIC_REAUTH_GUIDANCE_MESSAGE =
            "Sua sessão do Claude Code está sem a permissão esperada ou desatualizada. Feche o app, reautentique no Claude Code e abra o monitor novamente."
    }
}

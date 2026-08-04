package com.usagemonitor.data.repository

import com.usagemonitor.data.datasource.CredentialDataSource
import com.usagemonitor.data.datasource.RemoteApiDataSource
import com.usagemonitor.data.datasource.AnthropicSession
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
            fetchStableUsage()
        }
    }

    private suspend fun fetchStableUsage(): ApiUsageStats {
        var lastError: Throwable? = null

        for (attempt in 0..1) {
            val session = credentialDataSource.loadAnthropicSession()
            try {
                val stats = fetchUsageForSession(session)
                if (credentialDataSource.isAnthropicSessionCurrent(session)) {
                    return stats
                }
                lastError = IllegalStateException(ACCOUNT_CHANGED_DURING_FETCH_MESSAGE)
            } catch (error: Throwable) {
                val sessionChanged = !credentialDataSource.isAnthropicSessionCurrent(session)
                if (!sessionChanged && !isScopeRequirementFailure(error)) {
                    throw error
                }
                lastError = if (isScopeRequirementFailure(error)) {
                    IllegalStateException(ANTHROPIC_REAUTH_GUIDANCE_MESSAGE, error)
                } else {
                    IllegalStateException(ACCOUNT_CHANGED_DURING_FETCH_MESSAGE, error)
                }
            }
        }

        throw lastError ?: IllegalStateException(ACCOUNT_CHANGED_DURING_FETCH_MESSAGE)
    }

    private suspend fun fetchUsageForSession(session: AnthropicSession): ApiUsageStats {
        val dto = apiDataSource.fetchAnthropicUsage(session.accessToken)
        return AnthropicMapper.toUsageStats(dto).copy(accountContext = session.accountContext)
    }

    private fun isScopeRequirementFailure(error: Throwable): Boolean {
        val message = error.message ?: return false
        return message.contains("Anthropic HTTP 403", ignoreCase = true) &&
            message.contains("permission_error", ignoreCase = true) &&
            message.contains("scope requirement", ignoreCase = true) &&
            message.contains("user:profile", ignoreCase = true)
    }

    companion object {
        const val ACCOUNT_CHANGED_DURING_FETCH_MESSAGE =
            "A conta do Claude Code mudou durante a atualização. Aguarde o login terminar e atualize novamente."
        const val ANTHROPIC_REAUTH_GUIDANCE_MESSAGE =
            "Sua sessão do Claude Code está sem a permissão esperada ou desatualizada. Execute /logout e /login no Claude Code, conclua a autenticação e atualize novamente."
    }
}

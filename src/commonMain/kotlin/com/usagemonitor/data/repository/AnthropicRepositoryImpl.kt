package com.usagemonitor.data.repository

import com.usagemonitor.data.datasource.CredentialDataSource
import com.usagemonitor.data.datasource.RemoteApiDataSource
import com.usagemonitor.data.datasource.AnthropicSession
import com.usagemonitor.data.mapper.AnthropicMapper
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AnthropicProfileRef
import com.usagemonitor.domain.entity.UsageTargetKey
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
        return getUsage(AnthropicProfileRef.DEFAULT)
    }

    override suspend fun getUsage(profile: AnthropicProfileRef): Result<ApiUsageStats> {
        return Result.runCatching {
            fetchStableUsage(profile)
        }
    }

    private suspend fun fetchStableUsage(profile: AnthropicProfileRef): ApiUsageStats {
        var lastError: Throwable? = null

        for (attempt in 0..1) {
            val session = try {
                credentialDataSource.loadAnthropicSession(profile)
            } catch (error: Throwable) {
                lastError = error
                if (attempt == 0 && isConcurrentCredentialChange(error)) {
                    continue
                }
                throw error
            }
            try {
                val stats = fetchUsageForSession(profile, session)
                if (credentialDataSource.isAnthropicSessionCurrent(profile, session)) {
                    return stats
                }
                lastError = IllegalStateException(ACCOUNT_CHANGED_DURING_FETCH_MESSAGE)
            } catch (error: Throwable) {
                val sessionChanged = !credentialDataSource.isAnthropicSessionCurrent(profile, session)
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

    private suspend fun fetchUsageForSession(
        profile: AnthropicProfileRef,
        session: AnthropicSession
    ): ApiUsageStats {
        val dto = apiDataSource.fetchAnthropicUsage(session.accessToken)
        return AnthropicMapper.toUsageStats(dto).copy(
            targetKey = UsageTargetKey(ApiSource.ANTHROPIC, profile.id),
            accountContext = session.accountContext,
            profileLabel = profile.label
        )
    }

    private fun isScopeRequirementFailure(error: Throwable): Boolean {
        val message = error.message ?: return false
        return message.contains("Anthropic HTTP 403", ignoreCase = true) &&
            message.contains("permission_error", ignoreCase = true) &&
            message.contains("scope requirement", ignoreCase = true) &&
            message.contains("user:profile", ignoreCase = true)
    }

    private fun isConcurrentCredentialChange(error: Throwable): Boolean {
        val message = error.message ?: return false
        return message.contains("mudaram durante a renovação", ignoreCase = true) ||
            message.contains("changed during refresh", ignoreCase = true)
    }

    companion object {
        const val ACCOUNT_CHANGED_DURING_FETCH_MESSAGE =
            "A conta do Claude Code mudou durante a atualização. Aguarde o login terminar e atualize novamente."
        const val ANTHROPIC_REAUTH_GUIDANCE_MESSAGE =
            "Sua sessão do Claude Code está sem a permissão esperada ou desatualizada. Execute /logout e /login no Claude Code, conclua a autenticação e atualize novamente."
    }
}

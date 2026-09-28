package com.usagemonitor.data.repository

import com.usagemonitor.data.datasource.CodexAuthDataSource
import com.usagemonitor.data.datasource.CodexRolloutRateLimitDataSource
import com.usagemonitor.data.datasource.RemoteApiDataSource
import com.usagemonitor.data.mapper.CodexMapper
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.repository.CodexRepository
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Clock

class CodexRepositoryImpl(
    private val authDataSource: CodexAuthDataSource,
    private val apiDataSource: RemoteApiDataSource,
    /** Limites por modelo do rollout local (issue #324); `null` desliga a leitura. */
    private val rolloutRateLimits: CodexRolloutRateLimitDataSource? = null,
    private val clock: Clock = Clock.System
) : CodexRepository {

    override suspend fun getUsage(): Result<ApiUsageStats> {
        return Result.runCatching {
            fetchStableUsage()
        }
    }

    private suspend fun fetchStableUsage(): ApiUsageStats {
        for (attempt in 0..1) {
            val session = authDataSource.loadSession()
            val usageResponse = apiDataSource.fetchCodexFiveHourUsage(session)
            val stats = CodexMapper.toUsageStats(usageResponse)
                .copy(accountContext = session.accountContext)
            if (stats.quotas.isEmpty()) {
                throw IllegalStateException("A resposta do Codex não trouxe nenhuma janela utilizável.")
            }

            if (authDataSource.isSessionCurrent(session)) {
                return stats.copy(quotas = stats.quotas + modelLimitQuotas(usageResponse.planType))
            }
        }

        throw IllegalStateException(ACCOUNT_CHANGED_DURING_FETCH_MESSAGE)
    }

    /**
     * O rollout é complemento: se a leitura dele falhar, o card sai só com a
     * leitura ao vivo, como sempre saiu. Falha aqui não pode derrubar a fonte.
     */
    private suspend fun modelLimitQuotas(livePlanType: String): List<QuotaInfo> {
        val source = rolloutRateLimits ?: return emptyList()
        val limits = runCatching { source.latestRateLimits() }.getOrElse { failure ->
            if (failure is CancellationException) throw failure
            return emptyList()
        }
        return CodexMapper.modelLimitQuotas(limits, livePlanType, clock.now())
    }

    companion object {
        const val ACCOUNT_CHANGED_DURING_FETCH_MESSAGE =
            "A conta do Codex mudou durante a atualização. Aguarde o login terminar e atualize novamente."
    }
}

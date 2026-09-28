package com.usagemonitor.data.repository

import com.usagemonitor.data.datasource.CodexAuthDataSource
import com.usagemonitor.data.datasource.CodexRolloutRateLimitDataSource
import com.usagemonitor.data.datasource.RemoteApiDataSource
import com.usagemonitor.data.mapper.CodexMapper
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.CodexProfileRef
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.repository.CodexRepository
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Clock

/** As duas leituras locais de uma conta Codex: sessão (`auth.json` + `cap_sid`) e rollout. */
class CodexProfileSources(
    val auth: CodexAuthDataSource,
    val rolloutRateLimits: CodexRolloutRateLimitDataSource? = null
)

class CodexRepositoryImpl(
    private val authDataSource: CodexAuthDataSource,
    private val apiDataSource: RemoteApiDataSource,
    /** Limites por modelo do rollout local (issue #324); `null` desliga a leitura. */
    private val rolloutRateLimits: CodexRolloutRateLimitDataSource? = null,
    private val clock: Clock = Clock.System,
    /**
     * As leituras de uma conta extra (issue #329), pelo diretório dela; `null`
     * quando o perfil não existe mais no registro.
     */
    private val profileSources: (CodexProfileRef) -> CodexProfileSources? = { null }
) : CodexRepository {

    override suspend fun getUsage(): Result<ApiUsageStats> {
        return Result.runCatching {
            fetchStableUsage(CodexProfileSources(authDataSource, rolloutRateLimits))
        }
    }

    override suspend fun getUsage(profile: CodexProfileRef): Result<ApiUsageStats> {
        return Result.runCatching {
            val sources = profileSources(profile)
                ?: throw IllegalStateException("Perfil Codex não configurado: ${profile.label}.")
            // O alvo e o rótulo vão na leitura: é por eles que o card, a HUD e o
            // cache separam uma conta da outra ("Codex — trabalho").
            fetchStableUsage(sources).copy(
                targetKey = UsageTargetKey(ApiSource.CODEX, profile.id),
                profileLabel = profile.label
            )
        }
    }

    private suspend fun fetchStableUsage(sources: CodexProfileSources): ApiUsageStats {
        for (attempt in 0..1) {
            val session = sources.auth.loadSession()
            val usageResponse = apiDataSource.fetchCodexFiveHourUsage(session)
            val stats = CodexMapper.toUsageStats(usageResponse)
                .copy(accountContext = session.accountContext)
            if (stats.quotas.isEmpty()) {
                throw IllegalStateException("A resposta do Codex não trouxe nenhuma janela utilizável.")
            }

            if (sources.auth.isSessionCurrent(session)) {
                val extra = modelLimitQuotas(sources.rolloutRateLimits, usageResponse.planType)
                return stats.copy(quotas = stats.quotas + extra)
            }
        }

        throw IllegalStateException(ACCOUNT_CHANGED_DURING_FETCH_MESSAGE)
    }

    /**
     * O rollout é complemento: se a leitura dele falhar, o card sai só com a
     * leitura ao vivo, como sempre saiu. Falha aqui não pode derrubar a fonte.
     */
    private suspend fun modelLimitQuotas(source: CodexRolloutRateLimitDataSource?, livePlanType: String): List<QuotaInfo> {
        if (source == null) return emptyList()
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

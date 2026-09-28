package com.usagemonitor.data.mapper

import com.usagemonitor.domain.entity.codexPlanLabel
import com.usagemonitor.data.dto.CodexRolloutRateLimitWindowDto
import com.usagemonitor.data.parser.CodexRolloutRateLimit
import com.usagemonitor.domain.entity.CodexQuotaLabels
import com.usagemonitor.data.dto.CodexUsageResponse
import com.usagemonitor.data.dto.CodexUsageWindowDto
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageNotice
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.UsageUnit
import kotlinx.datetime.Instant

object CodexMapper {

    private const val PERCENT_SCALE = 100L
    /** `limit_id` do limite da conta, o mesmo que o `wham/usage` devolve. */
    private const val ACCOUNT_LIMIT_ID = "codex"
    private const val SECONDS_PER_DAY = 24L * 60L * 60L
    private const val FIVE_HOUR_SECONDS = 5L * 60L * 60L
    private const val SEVEN_DAY_SECONDS = 7L * SECONDS_PER_DAY
    private const val MONTHLY_MIN_SECONDS = 28L * SECONDS_PER_DAY
    private const val MONTHLY_MAX_SECONDS = 31L * SECONDS_PER_DAY

    fun toUsageStats(response: CodexUsageResponse): ApiUsageStats {
        val windows = listOfNotNull(
            response.rateLimit.primaryWindow,
            response.rateLimit.secondaryWindow
        )
        val quotasByPeriod = linkedMapOf<PeriodType, QuotaInfo>()
        var hasUnknownWindow = false
        var hasDuplicateWindow = false

        windows.forEach { window ->
            val mapping = mapWindow(window)
            if (mapping.periodType == PeriodType.REPORTED) {
                hasUnknownWindow = true
            }
            if (quotasByPeriod.containsKey(mapping.periodType)) {
                hasDuplicateWindow = true
            } else {
                quotasByPeriod[mapping.periodType] = mapping.quota
            }
        }

        val notices = buildSet {
            if (hasUnknownWindow || hasDuplicateWindow) {
                add(ApiUsageNotice.SOURCE_UNSTABLE)
            }
        }

        return ApiUsageStats(
            source = ApiSource.CODEX,
            apiName = "Codex",
            quotas = quotasByPeriod.values.sortedBy { quota -> periodRank(quota.periodType) },
            notices = notices,
            planLabel = codexPlanLabel(response.planType)
        )
    }

    private fun mapWindow(window: CodexUsageWindowDto): CodexWindowMapping {
        val periodType = periodTypeOf(window.limitWindowSeconds)
        val label = when (periodType) {
            PeriodType.INTERVAL -> "Codex 5h"
            PeriodType.WEEKLY -> "Codex 7d"
            PeriodType.MONTHLY -> "Codex mensal"
            PeriodType.REPORTED -> "Codex atual"
        }

        return CodexWindowMapping(
            periodType = periodType,
            quota = QuotaInfo(
                label = label,
                used = window.usedPercent.coerceIn(0L, PERCENT_SCALE),
                total = PERCENT_SCALE,
                periodEndAt = Instant.fromEpochSeconds(window.resetAt),
                periodType = periodType,
                unit = UsageUnit.PERCENTAGE,
                periodStartAt = Instant.fromEpochSeconds(window.resetAt - window.limitWindowSeconds)
                    .takeIf { window.limitWindowSeconds > 0L }
            )
        )
    }

    /**
     * Cotas dos limites por modelo lidos do rollout (issue #324), para somar às da
     * leitura ao vivo — nunca para substituí-las.
     *
     * Fica de fora: o `limit_id` `codex`, que é o limite da conta que o
     * `wham/usage` já devolve; limite de outro plano que o da leitura ao vivo, que
     * é o sinal de que o rollout é de outra conta; e janela sem percentual, sem
     * duração ou já vencida em [now]. O percentual é truncado, como na Anthropic:
     * 99,6% não pode aparecer como esgotado.
     */
    fun modelLimitQuotas(limits: List<CodexRolloutRateLimit>, livePlanType: String, now: Instant): List<QuotaInfo> {
        return limits
            .filter { rateLimit -> rateLimit.limits.limitId != ACCOUNT_LIMIT_ID }
            .filter { rateLimit -> rateLimit.limits.planType == null || rateLimit.limits.planType == livePlanType }
            .flatMap { rateLimit ->
                val name = rateLimit.limits.limitName?.takeIf { it.isNotBlank() } ?: rateLimit.limits.limitId.orEmpty()
                listOfNotNull(rateLimit.limits.primary, rateLimit.limits.secondary)
                    .mapNotNull { window -> modelLimitQuota(name, window, now) }
            }
    }

    private fun modelLimitQuota(name: String, window: CodexRolloutRateLimitWindowDto, now: Instant): QuotaInfo? {
        val usedPercent = window.usedPercent ?: return null
        val minutes = window.windowMinutes?.takeIf { it > 0L } ?: return null
        val resetsAt = window.resetsAt?.let(Instant::fromEpochSeconds) ?: return null
        if (name.isBlank() || resetsAt <= now) return null
        val seconds = minutes * 60L
        val periodType = periodTypeOf(seconds)
        val windowLabel = when (periodType) {
            PeriodType.INTERVAL -> "5h"
            PeriodType.WEEKLY -> "7d"
            PeriodType.MONTHLY, PeriodType.REPORTED -> "${minutes}m"
        }
        return QuotaInfo(
            label = CodexQuotaLabels.modelLimit(name, windowLabel),
            used = usedPercent.toLong().coerceIn(0L, PERCENT_SCALE),
            total = PERCENT_SCALE,
            periodEndAt = resetsAt,
            periodType = periodType,
            unit = UsageUnit.PERCENTAGE,
            periodStartAt = Instant.fromEpochSeconds(resetsAt.epochSeconds - seconds)
        )
    }

    private fun periodTypeOf(seconds: Long): PeriodType = when {
        seconds == FIVE_HOUR_SECONDS -> PeriodType.INTERVAL
        seconds == SEVEN_DAY_SECONDS -> PeriodType.WEEKLY
        seconds in MONTHLY_MIN_SECONDS..MONTHLY_MAX_SECONDS -> PeriodType.MONTHLY
        else -> PeriodType.REPORTED
    }

    private fun periodRank(periodType: PeriodType): Int {
        return when (periodType) {
            PeriodType.INTERVAL -> 0
            PeriodType.WEEKLY -> 1
            PeriodType.MONTHLY -> 2
            PeriodType.REPORTED -> 3
        }
    }

    private data class CodexWindowMapping(
        val periodType: PeriodType,
        val quota: QuotaInfo
    )
}

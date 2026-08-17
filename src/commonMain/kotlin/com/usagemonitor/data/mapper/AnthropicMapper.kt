package com.usagemonitor.data.mapper

import com.usagemonitor.data.dto.AnthropicUsageResponse
import com.usagemonitor.data.dto.AnthropicUsageWindow
import com.usagemonitor.domain.entity.AnthropicQuotaLabels
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageNotice
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.UsageUnit
import kotlinx.datetime.Instant
import kotlin.math.roundToLong

object AnthropicMapper {

    private const val MAX_CAPACITY_5H = 4500L
    private const val MAX_CAPACITY_7D = 45000L
    private const val SCALE = 100L

    fun toUsageStats(response: AnthropicUsageResponse): ApiUsageStats {
        val credits = resolveExtraCredits(
            extraUsage = response.extraUsage,
            spend = response.spend
        )

        val quotas = buildList {
            add(createQuota(
                label = AnthropicQuotaLabels.FIVE_HOUR,
                periodType = PeriodType.INTERVAL,
                window = response.fiveHour,
                maxCapacity = MAX_CAPACITY_5H
            ))

            add(createQuota(
                label = AnthropicQuotaLabels.SEVEN_DAY,
                periodType = PeriodType.WEEKLY,
                window = response.sevenDay,
                maxCapacity = MAX_CAPACITY_7D
            ))

            val extraCredits = credits.quota
            if (extraCredits != null) {
                add(extraCredits)
            }
        }

        // Cota de créditos ausente só vira aviso quando a própria resposta diz
        // que a conta tem créditos. Conta sem o recurso ligado não pode carregar
        // um alerta permanente por não ter o que exibir.
        val notices = if (credits.outcome.signalsFailure) {
            setOf(ApiUsageNotice.EXTRA_CREDITS_UNAVAILABLE)
        } else {
            emptySet()
        }

        return ApiUsageStats(
            source = ApiSource.ANTHROPIC,
            apiName = "Anthropic",
            quotas = quotas,
            notices = notices
        )
    }

    private fun createQuota(
        label: String,
        periodType: PeriodType,
        window: AnthropicUsageWindow,
        maxCapacity: Long
    ): QuotaInfo {
        val resetsAt = window.resetsAt
        val periodEndAt = if (resetsAt != null) {
            Instant.parse(resetsAt)
        } else {
            ANTHROPIC_UNKNOWN_RESET_AT
        }
        val used = window.utilization.toLong().coerceIn(0L, 100L)
        val rawUsed = (window.utilization * maxCapacity / 100).roundToLong()

        return QuotaInfo(
            label = label,
            used = used,
            total = SCALE,
            periodEndAt = periodEndAt,
            hasKnownResetAt = resetsAt != null,
            periodType = periodType,
            unit = UsageUnit.PERCENTAGE,
            rawUsed = rawUsed,
            rawTotal = maxCapacity
        )
    }
}

package com.usagemonitor.data.mapper

import com.usagemonitor.data.dto.AnthropicExtraUsage
import com.usagemonitor.data.dto.AnthropicSpend
import com.usagemonitor.data.dto.AnthropicUsageResponse
import com.usagemonitor.data.dto.AnthropicUsageWindow
import com.usagemonitor.domain.entity.AnthropicQuotaLabels
import com.usagemonitor.domain.entity.ApiSource
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
    private const val DEFAULT_CURRENCY = "USD"
    private val UNKNOWN_RESET_AT = Instant.parse("2100-01-01T00:00:00Z")

    fun toUsageStats(response: AnthropicUsageResponse): ApiUsageStats {
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

            val extraCredits = createExtraCreditsQuota(
                extraUsage = response.extraUsage,
                spend = response.spend
            )
            if (extraCredits != null) {
                add(extraCredits)
            }
        }

        return ApiUsageStats(
            source = ApiSource.ANTHROPIC,
            apiName = "Anthropic",
            quotas = quotas
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
            UNKNOWN_RESET_AT
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

    /**
     * Cota dos "Créditos de uso" — só existe quando a conta tem o recurso ligado.
     *
     * A unidade continua sendo PERCENTAGE, como nas janelas 5h/7d: a leitura é
     * consumo contra um limite, e não saldo remanescente. Os valores monetários
     * (unidades menores da moeda) vão em `rawUsed`/`rawTotal`.
     *
     * A resposta não traz a data do reinício mensal, então a cota fica sem
     * `periodEndAt` confiável (`hasKnownResetAt = false`) e o tipo é REPORTED.
     */
    private fun createExtraCreditsQuota(
        extraUsage: AnthropicExtraUsage?,
        spend: AnthropicSpend?
    ): QuotaInfo? {
        if (extraUsage == null || !extraUsage.isEnabled) {
            return null
        }

        val monthlyLimit = extraUsage.monthlyLimit ?: return null
        if (monthlyLimit <= 0L) {
            return null
        }

        val usedCredits = extraUsage.usedCredits ?: 0.0
        // `utilization` já vem pronto e mais preciso que `spend.percent`, que
        // chega arredondado. O cálculo local só cobre a ausência do campo.
        val utilization = extraUsage.utilization ?: (usedCredits * 100.0 / monthlyLimit)
        val currency = extraUsage.currency ?: spend?.used?.currency ?: DEFAULT_CURRENCY

        return QuotaInfo(
            label = AnthropicQuotaLabels.EXTRA_CREDITS,
            used = utilization.roundToLong().coerceIn(0L, 100L),
            total = SCALE,
            periodEndAt = UNKNOWN_RESET_AT,
            hasKnownResetAt = false,
            periodType = PeriodType.REPORTED,
            unit = UsageUnit.PERCENTAGE,
            rawUsed = usedCredits.roundToLong().coerceAtLeast(0L),
            rawTotal = monthlyLimit,
            currencyCode = currency
        )
    }
}

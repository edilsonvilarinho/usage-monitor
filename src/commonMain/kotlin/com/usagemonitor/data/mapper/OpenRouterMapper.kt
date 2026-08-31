package com.usagemonitor.data.mapper

import com.usagemonitor.data.dto.OpenRouterCreditsResponse
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.OpenRouterQuotaLabels
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.UsageUnit
import kotlinx.datetime.Instant

/**
 * Mesmo padrão do [DeepSeekMapper]: saldo pré-pago, `used = 0`, `total` é o
 * saldo inteiro (`total_credits - total_usage`), sem reset — a régua de risco
 * usa runway, não ratio de janela ([hasKnownResetAt] = false).
 */
object OpenRouterMapper {

    fun toUsageStats(response: OpenRouterCreditsResponse): ApiUsageStats {
        val balanceCents = parseToCents(response.data.totalCredits - response.data.totalUsage)

        return ApiUsageStats(
            source = ApiSource.OPENROUTER,
            apiName = "OpenRouter",
            quotas = listOf(
                QuotaInfo(
                    label = OpenRouterQuotaLabels.BALANCE,
                    used = 0L,
                    total = balanceCents,
                    rawUsed = balanceCents,
                    rawTotal = balanceCents,
                    periodEndAt = Instant.DISTANT_FUTURE,
                    hasKnownResetAt = false,
                    periodType = PeriodType.INTERVAL,
                    unit = UsageUnit.CURRENCY_USD
                )
            )
        )
    }

    private fun parseToCents(value: Double): Long {
        return (value * 100).toLong().coerceAtLeast(0L)
    }
}

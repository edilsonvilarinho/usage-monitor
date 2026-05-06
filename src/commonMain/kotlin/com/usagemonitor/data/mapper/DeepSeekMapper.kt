package com.usagemonitor.data.mapper

import com.usagemonitor.data.dto.DeepSeekBalanceResponse
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.UsageUnit
import kotlinx.datetime.Instant

object DeepSeekMapper {

    fun toUsageStats(response: DeepSeekBalanceResponse): ApiUsageStats {
        if (!response.isAvailable) {
            throw IllegalStateException("DeepSeek API: conta indisponível")
        }

        val balanceInfo = response.balanceInfos
            .firstOrNull { it.currency == "USD" }
            ?: response.balanceInfos.firstOrNull()
            ?: throw IllegalStateException("DeepSeek API: nenhuma informação de saldo disponível")

        val toppedUpCents = parseToCents(balanceInfo.toppedUpBalance)
        val grantedCents = parseToCents(balanceInfo.grantedBalance)

        val quotas = buildList {
            add(
                QuotaInfo(
                    label = "Saldo",
                    used = 0L,
                    total = toppedUpCents,
                    rawUsed = toppedUpCents,
                    rawTotal = toppedUpCents,
                    periodEndAt = Instant.DISTANT_FUTURE,
                    hasKnownResetAt = false,
                    periodType = PeriodType.INTERVAL,
                    unit = UsageUnit.CURRENCY_USD
                )
            )
            if (grantedCents > 0L) {
                add(
                    QuotaInfo(
                        label = "Gratuito",
                        used = 0L,
                        total = grantedCents,
                        rawUsed = grantedCents,
                        rawTotal = grantedCents,
                        periodEndAt = Instant.DISTANT_FUTURE,
                        hasKnownResetAt = false,
                        periodType = PeriodType.INTERVAL,
                        unit = UsageUnit.CURRENCY_USD
                    )
                )
            }
        }

        return ApiUsageStats(
            source = ApiSource.DEEPSEEK,
            apiName = "DeepSeek",
            quotas = quotas
        )
    }

    private fun parseToCents(value: String): Long {
        return ((value.toDoubleOrNull() ?: 0.0) * 100).toLong()
    }
}

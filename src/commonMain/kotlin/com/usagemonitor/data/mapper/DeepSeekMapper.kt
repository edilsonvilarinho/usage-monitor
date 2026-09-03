package com.usagemonitor.data.mapper

import com.usagemonitor.data.dto.DeepSeekBalanceResponse
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.DeepSeekQuotaLabels
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

        // A moeda é a do item escolhido acima, e vale para as duas cotas: os dois
        // saldos saem da mesma entrada de `balance_infos`. Sem propagá-la, o
        // default "USD" de QuotaInfo prevalecia e uma conta em yuan aparecia com
        // cifrão de dólar — erro de fator ~7 sem sinalização nenhuma (issue #195).
        // Isto também tira o silêncio da escolha "USD ou o primeiro": ela continua
        // decidindo qual saldo exibir, mas agora a tela diz qual.
        val currency = balanceInfo.currency

        val quotas = buildList {
            add(
                QuotaInfo(
                    label = DeepSeekQuotaLabels.BALANCE,
                    used = 0L,
                    total = toppedUpCents,
                    rawUsed = toppedUpCents,
                    rawTotal = toppedUpCents,
                    periodEndAt = Instant.DISTANT_FUTURE,
                    hasKnownResetAt = false,
                    periodType = PeriodType.INTERVAL,
                    unit = UsageUnit.CURRENCY_USD,
                    currencyCode = currency
                )
            )
            if (grantedCents > 0L) {
                add(
                    QuotaInfo(
                        label = DeepSeekQuotaLabels.GRANTED,
                        used = 0L,
                        total = grantedCents,
                        rawUsed = grantedCents,
                        rawTotal = grantedCents,
                        periodEndAt = Instant.DISTANT_FUTURE,
                        hasKnownResetAt = false,
                        periodType = PeriodType.INTERVAL,
                        unit = UsageUnit.CURRENCY_USD,
                        currencyCode = currency
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

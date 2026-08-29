package com.usagemonitor.data.mapper

import com.usagemonitor.data.dto.OpenCodeGoUsageResponse
import com.usagemonitor.data.dto.OpenCodeGoWindowDto
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.OpenCodeGoQuotaLabels
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.UsageUnit
import kotlinx.datetime.Instant

/**
 * Converte a resposta do OpenCode Go nas três cotas percentuais do domínio.
 *
 * O encaixe é o mesmo da Anthropic — percentual de 0 a 100 com `resetsAt` — e por
 * isso nenhum valor novo em [UsageUnit] ou [PeriodType] é necessário.
 *
 * Ao contrário da Anthropic, **não há `rawUsed`/`rawTotal`**: aquela conta
 * converte o percentual numa capacidade estimada em tokens, e aqui não existe
 * grandeza subjacente conhecida. Inventar uma capacidade faria a tooltip do card
 * mostrar um número de tokens que a API nunca informou.
 */
object OpenCodeGoMapper {

    private const val SCALE = 100L

    /**
     * Sentinela para janela sem `resetsAt`. Vem acompanhada de
     * `hasKnownResetAt = false`, que é o que tira a cota da régua de projeção —
     * sem reinício conhecido não há "vai esgotar antes de reiniciar".
     */
    internal val OPEN_CODE_GO_UNKNOWN_RESET_AT: Instant = Instant.parse("2100-01-01T00:00:00Z")

    fun toUsageStats(response: OpenCodeGoUsageResponse): ApiUsageStats {
        val usage = response.usage

        val quotas = buildList {
            createQuota(OpenCodeGoQuotaLabels.ROLLING, PeriodType.INTERVAL, usage.rolling)?.let(::add)
            createQuota(OpenCodeGoQuotaLabels.WEEKLY, PeriodType.WEEKLY, usage.weekly)?.let(::add)
            createQuota(OpenCodeGoQuotaLabels.MONTHLY, PeriodType.MONTHLY, usage.monthly)?.let(::add)
        }

        // Resposta sem nenhuma das três janelas não é um card vazio: é contrato
        // mudado, e falhar aqui preserva o último valor em cache em vez de
        // apagá-lo com uma leitura que não mediu nada.
        if (quotas.isEmpty()) {
            throw IllegalStateException(
                "OpenCode Go respondeu sem nenhuma janela de uso (rolling, weekly ou monthly)."
            )
        }

        return ApiUsageStats(
            source = ApiSource.OPENCODE_GO,
            apiName = "OpenCode Go",
            quotas = quotas
        )
    }

    private fun createQuota(
        label: String,
        periodType: PeriodType,
        window: OpenCodeGoWindowDto?
    ): QuotaInfo? {
        if (window == null) return null

        val resetsAt = window.resetsAt?.takeIf { it.isNotBlank() }
        val periodEndAt = if (resetsAt != null) {
            // Carimbo ilegível é tratado como carimbo ausente: a janela continua
            // valendo e só perde a projeção, que é melhor que derrubar as três.
            runCatching { Instant.parse(resetsAt) }.getOrNull() ?: OPEN_CODE_GO_UNKNOWN_RESET_AT
        } else {
            OPEN_CODE_GO_UNKNOWN_RESET_AT
        }
        val hasKnownResetAt = resetsAt != null && periodEndAt != OPEN_CODE_GO_UNKNOWN_RESET_AT

        return QuotaInfo(
            label = label,
            used = window.percent.toLong().coerceIn(0L, SCALE),
            total = SCALE,
            periodEndAt = periodEndAt,
            hasKnownResetAt = hasKnownResetAt,
            periodType = periodType,
            unit = UsageUnit.PERCENTAGE
        )
    }
}

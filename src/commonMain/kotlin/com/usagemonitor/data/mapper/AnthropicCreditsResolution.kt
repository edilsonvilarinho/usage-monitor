package com.usagemonitor.data.mapper

import com.usagemonitor.data.dto.AnthropicExtraUsage
import com.usagemonitor.data.dto.AnthropicSpend
import com.usagemonitor.domain.entity.AnthropicQuotaLabels
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.UsageUnit
import kotlinx.datetime.Instant
import kotlin.math.roundToLong

/**
 * Sentinela de reset para as cotas cuja janela a Anthropic não materializou.
 *
 * Vive aqui e não dentro de [AnthropicMapper] porque as duas leituras de cota —
 * as janelas e os créditos — precisam do mesmo valor, e duas constantes iguais
 * divergiriam na primeira alteração.
 */
internal val ANTHROPIC_UNKNOWN_RESET_AT: Instant = Instant.parse("2100-01-01T00:00:00Z")

private const val PERCENTAGE_SCALE = 100L

/**
 * Expoente monetário que a apresentação sabe formatar.
 *
 * `formatCents`/`formatCurrencyAmount` assumem duas casas decimais. Aceitar um
 * expoente diferente aqui produziria um valor formatado errado por um fator de
 * dez — pior que não exibir a linha.
 */
private const val SUPPORTED_MONEY_EXPONENT = 2

private const val DEFAULT_CURRENCY = "USD"

/**
 * Por que a leitura dos créditos de uso terminou como terminou.
 *
 * Os três desfechos sem cota são distintos de propósito: até agosto de 2026 o
 * mapeamento colapsava todos num `return null` silencioso, e quando a resposta
 * da Anthropic deixou de trazer os créditos por vários dias não sobrou rastro
 * nenhum — nem na tela, nem no cache, nem no histórico.
 */
enum class AnthropicCreditsOutcome {
    /** Caminho normal: `extra_usage` veio completo. */
    QUOTA_FROM_EXTRA_USAGE,

    /** `extra_usage` não fechou e `spend` — que traz os mesmos números — cobriu. */
    QUOTA_FROM_SPEND,

    /** `is_enabled` falso: a conta não tem créditos ligados. Esconder é o certo. */
    DISABLED,

    /** Nenhuma das duas fontes indica créditos nesta conta. */
    ABSENT,

    /** A fonte diz que há créditos mas não informou um limite utilizável. */
    LIMIT_ABSENT,

    /** Valor monetário em moeda com expoente que a UI não formata. */
    UNSUPPORTED_EXPONENT;

    /**
     * A conta sinaliza ter créditos e mesmo assim a leitura não fechou.
     *
     * [DISABLED] e [ABSENT] ficam de fora: ali a ausência da linha é a resposta
     * correta, e avisar transformaria o estado normal de quem não usa créditos
     * num alerta permanente.
     */
    val signalsFailure: Boolean
        get() = this == LIMIT_ABSENT || this == UNSUPPORTED_EXPONENT
}

/** Cota de créditos (quando pôde ser montada) e o motivo do desfecho. */
data class AnthropicCreditsResolution(
    val quota: QuotaInfo?,
    val outcome: AnthropicCreditsOutcome
)

/**
 * Resolve os "Créditos de uso" a partir das duas fontes da mesma resposta.
 *
 * `extra_usage` é a primária — traz `utilization` com precisão total e a moeda
 * real da conta. `spend` descreve o mesmo gasto em unidades menores e existe no
 * payload desde sempre; usá-lo como reserva é o que mantém a linha viva quando
 * a forma da fonte primária muda.
 *
 * A unidade continua sendo PERCENTAGE, como nas janelas 5h/7d: a leitura é
 * consumo contra um limite, não saldo remanescente. Os valores monetários vão
 * em `rawUsed`/`rawTotal`, na moeda de [QuotaInfo.currencyCode].
 *
 * A resposta não traz a data do reinício mensal, então a cota fica sem
 * `periodEndAt` confiável (`hasKnownResetAt = false`) e o tipo é REPORTED.
 */
fun resolveExtraCredits(
    extraUsage: AnthropicExtraUsage?,
    spend: AnthropicSpend?
): AnthropicCreditsResolution {
    // Desligado é resposta, não falha: quem não contratou créditos não tem linha.
    if (extraUsage != null && !extraUsage.isEnabled) {
        return AnthropicCreditsResolution(quota = null, outcome = AnthropicCreditsOutcome.DISABLED)
    }

    val monthlyLimit = extraUsage?.monthlyLimit?.takeIf { limit -> limit > 0L }
    if (extraUsage != null && monthlyLimit != null) {
        val usedCredits = extraUsage.usedCredits ?: 0.0
        // `utilization` já vem pronto e mais preciso que `spend.percent`, que
        // chega arredondado. O cálculo local só cobre a ausência do campo.
        val utilization = extraUsage.utilization ?: (usedCredits * 100.0 / monthlyLimit)
        val currency = extraUsage.currency ?: spend?.used?.currency ?: DEFAULT_CURRENCY

        return AnthropicCreditsResolution(
            quota = creditsQuota(
                utilization = utilization,
                usedMinorUnits = usedCredits.roundToLong(),
                limitMinorUnits = monthlyLimit,
                currencyCode = currency
            ),
            outcome = AnthropicCreditsOutcome.QUOTA_FROM_EXTRA_USAGE
        )
    }

    return resolveFromSpend(extraUsage = extraUsage, spend = spend)
}

private fun resolveFromSpend(
    extraUsage: AnthropicExtraUsage?,
    spend: AnthropicSpend?
): AnthropicCreditsResolution {
    if (spend == null || !spend.enabled) {
        // Sem `extra_usage` e sem `spend` ligado não há sinal de créditos nesta
        // conta. Com `extra_usage` habilitado e sem limite, há: a fonte se
        // contradisse, e isso precisa aparecer.
        val outcome = if (extraUsage == null) {
            AnthropicCreditsOutcome.ABSENT
        } else {
            AnthropicCreditsOutcome.LIMIT_ABSENT
        }
        return AnthropicCreditsResolution(quota = null, outcome = outcome)
    }

    val limit = spend.limit
    if (limit == null || limit.amountMinor <= 0L) {
        return AnthropicCreditsResolution(quota = null, outcome = AnthropicCreditsOutcome.LIMIT_ABSENT)
    }

    val used = spend.used
    val hasUnsupportedExponent = limit.exponent != SUPPORTED_MONEY_EXPONENT ||
        (used != null && used.exponent != SUPPORTED_MONEY_EXPONENT)
    if (hasUnsupportedExponent) {
        return AnthropicCreditsResolution(
            quota = null,
            outcome = AnthropicCreditsOutcome.UNSUPPORTED_EXPONENT
        )
    }

    val usedMinorUnits = used?.amountMinor ?: 0L
    // `spend.percent` chega arredondado; o de `extra_usage` (quando existe) é
    // exato, e o cálculo local sobre as unidades menores também.
    val utilization = extraUsage?.utilization ?: (usedMinorUnits * 100.0 / limit.amountMinor)

    return AnthropicCreditsResolution(
        quota = creditsQuota(
            utilization = utilization,
            usedMinorUnits = usedMinorUnits,
            limitMinorUnits = limit.amountMinor,
            currencyCode = extraUsage?.currency ?: limit.currency
        ),
        outcome = AnthropicCreditsOutcome.QUOTA_FROM_SPEND
    )
}

private fun creditsQuota(
    utilization: Double,
    usedMinorUnits: Long,
    limitMinorUnits: Long,
    currencyCode: String
): QuotaInfo {
    return QuotaInfo(
        label = AnthropicQuotaLabels.EXTRA_CREDITS,
        used = utilization.roundToLong().coerceIn(0L, PERCENTAGE_SCALE),
        total = PERCENTAGE_SCALE,
        periodEndAt = ANTHROPIC_UNKNOWN_RESET_AT,
        hasKnownResetAt = false,
        periodType = PeriodType.REPORTED,
        unit = UsageUnit.PERCENTAGE,
        rawUsed = usedMinorUnits.coerceAtLeast(0L),
        rawTotal = limitMinorUnits,
        currencyCode = currencyCode
    )
}

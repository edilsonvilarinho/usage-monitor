package com.usagemonitor.domain.entity

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

enum class HistoryRange {
    LAST_24_HOURS,
    LAST_7_DAYS,
    LAST_30_DAYS,
    TOTAL;

    fun windowStart(now: Instant): Instant {
        return when (this) {
            LAST_24_HOURS -> now.minus(24, kotlinx.datetime.DateTimeUnit.HOUR, TimeZone.UTC)
            LAST_7_DAYS -> now.minus(7, kotlinx.datetime.DateTimeUnit.DAY, TimeZone.UTC)
            LAST_30_DAYS -> now.minus(30, kotlinx.datetime.DateTimeUnit.DAY, TimeZone.UTC)
            TOTAL -> TOTAL_WINDOW_START
        }
    }

    /**
     * Início da janela **anterior**, de mesma duração, para o comparativo.
     *
     * `null` em [TOTAL]: "tudo" não tem período anterior contra o que comparar,
     * e inventar um daria um número sem significado.
     */
    fun previousWindowStart(now: Instant): Instant? {
        return when (this) {
            LAST_24_HOURS -> now.minus(48, kotlinx.datetime.DateTimeUnit.HOUR, TimeZone.UTC)
            LAST_7_DAYS -> now.minus(14, kotlinx.datetime.DateTimeUnit.DAY, TimeZone.UTC)
            LAST_30_DAYS -> now.minus(60, kotlinx.datetime.DateTimeUnit.DAY, TimeZone.UTC)
            TOTAL -> null
        }
    }

    private companion object {
        val TOTAL_WINDOW_START: Instant = Instant.fromEpochMilliseconds(Long.MIN_VALUE)
    }
}

/**
 * Consumo desta janela contra o da janela anterior de mesma duração.
 *
 * Só compara o **delta** — o quanto foi consumido dentro de cada janela — e não
 * o valor acumulado da cota: o acumulado zera no reset e a comparação viraria
 * uma função de quando o reset caiu, não de quanto se usou.
 */
data class UsagePeriodComparison(
    val currentDelta: Long,
    val previousDelta: Long
) {
    /**
     * Variação relativa. `null` quando a janela anterior não teve consumo —
     * dividir por zero produziria "infinito por cento", que não informa nada.
     */
    val changeRatio: Double?
        get() {
            if (previousDelta <= 0L) {
                return null
            }
            return (currentDelta - previousDelta).toDouble() / previousDelta.toDouble()
        }

    val isIncrease: Boolean
        get() = currentDelta > previousDelta
}

private const val RESET_DETECTION_TOLERANCE_MS = 300_000L

/**
 * O `resets_at` devolvido pelas APIs de uso (ex.: Anthropic) sofre jitter
 * de até ~1s entre polls dentro da MESMA janela, sem reset real. Como a
 * menor janela real (5h) é muito maior que esse jitter, só tratamos como
 * mudança de período diferenças acima de [RESET_DETECTION_TOLERANCE_MS].
 */
fun isSamePeriod(a: Instant, b: Instant): Boolean {
    return kotlin.math.abs(a.toEpochMilliseconds() - b.toEpochMilliseconds()) <= RESET_DETECTION_TOLERANCE_MS
}

data class UsageHistoryPoint(
    val capturedAt: Instant,
    val used: Long,
    val total: Long,
    val rawUsed: Long,
    val rawTotal: Long,
    val periodEndAt: Instant
) {
    val displayUsed: Long
        get() = if (rawUsed > 0L) rawUsed else used

    val displayTotal: Long
        get() = if (rawTotal > 0L) rawTotal else total

    val normalizedUsage: Float
        get() = if (displayTotal > 0L) {
            (displayUsed.toFloat() / displayTotal.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
}

sealed interface UsageForecast {
    data object InsufficientData : UsageForecast
    data object NoGrowth : UsageForecast
    data object ResetsBeforeExhaustion : UsageForecast
    data class EstimatedExhaustionAt(val instant: Instant) : UsageForecast
}

/** Nível de risco de a cota estourar antes do reset da sessão, com base na projeção de consumo. */
enum class UsageRiskLevel { ON_TRACK, AT_RISK, WILL_EXCEED }

data class QuotaRiskSummary(
    val level: UsageRiskLevel,
    val estimatedExhaustionAt: Instant? // não nulo apenas quando level != ON_TRACK
)

/**
 * Deriva o nível de risco a partir do forecast. `referenceAt` deve ser o mesmo instante-base
 * usado para projetar [UsageForecast.EstimatedExhaustionAt.instant] (o `capturedAt` do último
 * ponto), não o relógio atual — mantém a proporção tempo-até-estourar/tempo-até-reset consistente
 * com o cálculo original.
 */
fun UsageForecast.riskSummary(referenceAt: Instant, periodEndAt: Instant): QuotaRiskSummary? {
    return when (this) {
        UsageForecast.ResetsBeforeExhaustion ->
            QuotaRiskSummary(level = UsageRiskLevel.ON_TRACK, estimatedExhaustionAt = null)
        is UsageForecast.EstimatedExhaustionAt -> {
            val msToExhaustion = (instant.toEpochMilliseconds() - referenceAt.toEpochMilliseconds()).coerceAtLeast(0L)
            val msToReset = (periodEndAt.toEpochMilliseconds() - referenceAt.toEpochMilliseconds()).coerceAtLeast(1L)
            val ratio = msToExhaustion.toDouble() / msToReset.toDouble()
            val level = if (ratio < 0.5) UsageRiskLevel.WILL_EXCEED else UsageRiskLevel.AT_RISK
            QuotaRiskSummary(level = level, estimatedExhaustionAt = instant)
        }
        UsageForecast.NoGrowth, UsageForecast.InsufficientData -> null
    }
}

/** Chave estável para correlacionar uma [QuotaInfo] com sua [UsageHistorySeries] correspondente. */
data class QuotaSeriesKey(val label: String, val periodType: PeriodType)

val QuotaInfo.seriesKey: QuotaSeriesKey
    get() = QuotaSeriesKey(label = label, periodType = periodType)

data class UsageHistorySeries(
    val quotaLabel: String,
    val periodType: PeriodType,
    val unit: UsageUnit,
    val points: List<UsageHistoryPoint>,
    val currentDisplayUsed: Long,
    val currentDisplayTotal: Long,
    val deltaDisplayUsed: Long,
    val averageDisplayConsumptionPerHour: Double,
    val currentPeriodEndAt: Instant,
    val forecast: UsageForecast,
    val riskSummary: QuotaRiskSummary?,
    /** Esta janela contra a anterior; `null` em "Total" ou sem dado anterior. */
    val comparison: UsagePeriodComparison? = null
) {
    val seriesKey: QuotaSeriesKey
        get() = QuotaSeriesKey(label = quotaLabel, periodType = periodType)
}

data class ApiUsageHistoryReport(
    val source: ApiSource,
    val range: HistoryRange,
    val lastUpdatedAt: Instant?,
    val series: List<UsageHistorySeries>,
    val accountContext: UsageAccountContext? = null
) {
    val isEmpty: Boolean
        get() = series.isEmpty()
}

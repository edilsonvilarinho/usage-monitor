package com.usagemonitor.domain.entity

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
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

    private companion object {
        val TOTAL_WINDOW_START: Instant = Instant.fromEpochMilliseconds(Long.MIN_VALUE)
    }
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

/**
 * Soma os incrementos positivos entre pontos consecutivos, ignorando quedas
 * (resets de janela) — é o consumo acumulado ao longo do recorte, atravessando
 * múltiplos ciclos de reset. Para [UsageUnit.CURRENCY_USD] soma quedas
 * (saldo diminui com uso, então "consumo" é o inverso da variação).
 *
 * Usado tanto para calcular `UsageHistorySeries.deltaDisplayUsed` (recorte
 * completo) quanto para recalcular o mesmo total sobre uma sub-faixa
 * selecionada manualmente no gráfico (comparação por arraste).
 */
fun cumulativePositiveDelta(points: List<UsageHistoryPoint>, unit: UsageUnit): Long {
    var delta = 0L
    for (index in 1 until points.size) {
        val diff = points[index].displayUsed - points[index - 1].displayUsed
        if (unit == UsageUnit.CURRENCY_USD) {
            if (diff < 0L) {
                delta += -diff
            }
        } else if (diff > 0L) {
            delta += diff
        }
    }
    return delta
}

/** Granularidade dos buckets de calendário usados por [bucketedConsumption]. */
enum class UsageBucketGranularity { HOUR, DAY, WEEK }

/** Total de consumo acumulado num bucket de calendário (ver [bucketedConsumption]). */
data class UsageBucketTotal(val bucketStart: Instant, val delta: Long)

/**
 * Particiona o consumo acumulado (mesma regra de "incremento positivo" de
 * [cumulativePositiveDelta]) em buckets de calendário (hora/dia/semana),
 * atribuindo cada delta ao bucket do ponto final do par. A soma de todos os
 * buckets devolvidos é exatamente igual a `cumulativePositiveDelta(points, unit)`
 * — nenhum consumo é perdido ou duplicado nas fronteiras.
 *
 * Buckets sem nenhum incremento positivo não aparecem na lista — quem exibe
 * o gráfico decide se preenche as lacunas com zero.
 */
fun bucketedConsumption(
    points: List<UsageHistoryPoint>,
    unit: UsageUnit,
    granularity: UsageBucketGranularity,
    timeZone: TimeZone = TimeZone.of("America/Sao_Paulo")
): List<UsageBucketTotal> {
    if (points.size < 2) {
        return emptyList()
    }

    val totalsByBucket = linkedMapOf<Instant, Long>()
    for (index in 1 until points.size) {
        val diff = points[index].displayUsed - points[index - 1].displayUsed
        val positive = if (unit == UsageUnit.CURRENCY_USD) {
            if (diff < 0L) -diff else 0L
        } else {
            if (diff > 0L) diff else 0L
        }
        if (positive == 0L) {
            continue
        }

        val bucketStart = truncateToBucket(points[index].capturedAt, granularity, timeZone)
        totalsByBucket[bucketStart] = (totalsByBucket[bucketStart] ?: 0L) + positive
    }

    return totalsByBucket
        .map { (bucketStart, delta) -> UsageBucketTotal(bucketStart, delta) }
        .sortedBy { it.bucketStart }
}

/** Trunca [instant] para o início do bucket de calendário correspondente. */
fun truncateToBucket(
    instant: Instant,
    granularity: UsageBucketGranularity,
    timeZone: TimeZone
): Instant {
    val local = instant.toLocalDateTime(timeZone)
    return when (granularity) {
        UsageBucketGranularity.HOUR ->
            LocalDateTime(local.date, LocalTime(local.hour, 0)).toInstant(timeZone)
        UsageBucketGranularity.DAY ->
            local.date.atStartOfDayIn(timeZone)
        UsageBucketGranularity.WEEK -> {
            val daysSinceMonday = local.date.dayOfWeek.ordinal
            val weekStart = local.date.minus(daysSinceMonday, kotlinx.datetime.DateTimeUnit.DAY)
            weekStart.atStartOfDayIn(timeZone)
        }
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
    val riskSummary: QuotaRiskSummary?
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

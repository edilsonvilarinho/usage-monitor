package com.usagemonitor.domain.entity

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Análise da sessão de uma conta (rodada AB): o que o `/analise` do bot e a janela
 * "Análise" do app mostram. Tudo sai de duas fontes medidas:
 *
 * - **ritmo** (pontos percentuais por hora) do histórico de cotas: a subida entre
 *   leituras seguidas, atribuída à leitura mais nova — a mesma regra de
 *   [positiveDeltaOf], em que o reinício contribui zero;
 * - **vazão** (tokens/s) do índice CLI: soma de tokens sobre soma de tempo de um
 *   intervalo, que é a diferença de dois recortes "desde" ([OutputThroughput] guarda
 *   as somas, então a diferença é exata e nunca média de razões).
 *
 * `null` é "não medido"; zero é "medido e parado".
 */
data class SessionAnalysis(
    val accountLabel: String,
    val source: ApiSource,
    val generatedAt: Instant,
    val fiveHour: AnalysisWindowView?,
    val weekly: AnalysisWindowView?,
    /** 1 h, 3 h, 5 h, 24 h e 7 d, nessa ordem. */
    val intervals: List<AnalysisIntervalRow>,
    /** As 24 horas cheias até agora (a última, parcial), da mais velha à mais nova. */
    val hours: List<AnalysisBucket>,
    /** 7 dias × 24 horas (BRT) de pontos da 5h; [heatDays] dá a data de cada linha. */
    val heat: List<List<Double>>,
    val heatDays: List<LocalDate>,
    /** Pontos da semanal gastos em cada um dos últimos 7 dias locais. */
    val days: List<AnalysisDay>,
    /** A janela 5h corrente em fatias de 30 min, desde o início nominal. */
    val slices: List<AnalysisBucket>,
    val previous: AnalysisWindowComparison?,
    /** Vazão por modelo na última hora, do mais rápido ao mais lento. */
    val modelSpeeds: List<AnalysisModelSpeed>,
    /**
     * Fim da última sincronização do índice que deu a vazão, quando ele não acompanha a
     * janela ao vivo (Codex, 10 min; rodada AD). `null` = não informado.
     */
    val throughputIndexedAt: Instant? = null
)

/** Uma janela (5h ou semanal) agora. */
data class AnalysisWindowView(
    val quotaLabel: String,
    val periodType: PeriodType,
    val percent: Int,
    val startsAt: Instant?,
    val resetsAt: Instant?,
    /** Pontos por hora na última hora. */
    val paceNow: Double?,
    /** Pontos por hora desde o início nominal da janela. */
    val paceAverage: Double?,
    /** O ritmo que levaria a cota a 100% exatamente no reinício. */
    val safePace: Double?,
    /** Onde a cota chega no reinício mantendo [paceNow] (ou, sem ele, [paceAverage]); estimativa. */
    val projectedAtReset: Int?
)

enum class AnalysisSpan(val millis: Long) {
    HOUR_1(1.hours.inWholeMilliseconds),
    HOURS_3(3.hours.inWholeMilliseconds),
    HOURS_5(5.hours.inWholeMilliseconds),
    HOURS_24(24.hours.inWholeMilliseconds),
    DAYS_7(7 * 24.hours.inWholeMilliseconds)
}

data class AnalysisIntervalRow(val span: AnalysisSpan, val pacePerHour: Double?, val tokensPerSecond: Double?)

/** Um trecho de tempo: pontos da 5h subidos nele e a vazão dele. */
data class AnalysisBucket(val start: Instant, val percentPoints: Double?, val tokensPerSecond: Double?)

data class AnalysisDay(val date: LocalDate, val percentPoints: Double?)

data class AnalysisModelSpeed(val model: String, val tokensPerSecond: Double)

/**
 * A janela 5h corrente contra a anterior **no mesmo ponto** ([elapsedMillis] desde o
 * início de cada uma): comparar a corrente pela metade com a anterior inteira diria
 * sempre que agora está mais devagar.
 */
data class AnalysisWindowComparison(
    val elapsedMillis: Long,
    val currentPercent: Int,
    val previousPercent: Int?,
    val currentPace: Double?,
    val previousPace: Double?,
    val currentPeakHour: Double?,
    val previousPeakHour: Double?,
    val currentTokensPerSecond: Double?,
    val previousTokensPerSecond: Double?
)

/** Recortes "desde" de que [buildSessionAnalysis] precisa; quem lê o índice CLI pede só estes. */
fun analysisThroughputCuts(series: List<UsageHistorySeries>, now: Instant): Set<Long> {
    val cuts = mutableSetOf<Long>()
    AnalysisSpan.entries.forEach { span -> cuts += now.toEpochMilliseconds() - span.millis }
    hourStarts(now).forEach { start -> cuts += start.toEpochMilliseconds() }
    val fiveHour = mainSeries(series, PeriodType.INTERVAL)
    val window = fiveHour?.let { item -> openWindowStart(item, now) }
    if (window != null) {
        sliceStarts(window, now).forEach { start -> cuts += start.toEpochMilliseconds() }
        previousWindowStart(fiveHour, window)?.let { previousStart ->
            cuts += previousStart.toEpochMilliseconds()
            cuts += (previousStart + (now - window)).toEpochMilliseconds()
        }
        cuts += window.toEpochMilliseconds()
    }
    return cuts.filter { cut -> cut < now.toEpochMilliseconds() }.toSet()
}

/**
 * Monta a análise. [cumulative] é a vazão por modelo **desde** cada recorte de
 * [analysisThroughputCuts]; recorte ausente (o índice falhou) deixa a vazão nula.
 */
fun buildSessionAnalysis(
    accountLabel: String,
    source: ApiSource,
    series: List<UsageHistorySeries>,
    cumulative: Map<Long, Map<String, OutputThroughput>>,
    now: Instant,
    timeZone: TimeZone = TimeZone.of(ACTIVITY_TIME_ZONE_ID)
): SessionAnalysis {
    val fiveHourSeries = mainSeries(series, PeriodType.INTERVAL)
    val weeklySeries = mainSeries(series, PeriodType.WEEKLY)
    val throughput = ThroughputCuts(cumulative, now)
    val fivePoints = fiveHourSeries?.points.orEmpty().sortedBy { point -> point.capturedAt }
    val weeklyPoints = weeklySeries?.points.orEmpty().sortedBy { point -> point.capturedAt }
    val windowStart = fiveHourSeries?.let { item -> openWindowStart(item, now) }

    val intervals = AnalysisSpan.entries.map { span ->
        val from = now - span.millis.milliseconds
        val points = risePoints(fivePoints, from, now)
        AnalysisIntervalRow(span, points?.let { value -> value / (span.millis / MILLIS_PER_HOUR) }, throughput.between(from, now))
    }
    val hours = hourStarts(now).map { start ->
        val end = minOf(start + 1.hours, now)
        AnalysisBucket(start, risePoints(fivePoints, start, end), throughput.between(start, end))
    }
    val heatDays = (6 downTo 0).map { back -> (now - (back * 24).hours).toLocalDateTime(timeZone).date }
    val heat = heatDays.map { day ->
        (0 until 24).map { hour ->
            risesOf(fivePoints).filter { (at, _) ->
                val local = at.toLocalDateTime(timeZone)
                local.date == day && local.hour == hour
            }.sumOf { (_, rise) -> rise }
        }
    }
    val days = heatDays.map { day ->
        val rises = risesOf(weeklyPoints).filter { (at, _) -> at.toLocalDateTime(timeZone).date == day }
        AnalysisDay(day, if (weeklyPoints.size < 2) null else rises.sumOf { (_, rise) -> rise })
    }
    val slices = windowStart?.let { start ->
        sliceStarts(start, now).map { sliceStart ->
            val end = minOf(sliceStart + 30.minutes, now)
            AnalysisBucket(sliceStart, risePoints(fivePoints, sliceStart, end), throughput.between(sliceStart, end))
        }
    }.orEmpty()

    return SessionAnalysis(
        accountLabel = accountLabel,
        source = source,
        generatedAt = now,
        fiveHour = fiveHourSeries?.let { item -> windowView(item, fivePoints, now, FIVE_HOURS_MILLIS) },
        weekly = weeklySeries?.let { item -> windowView(item, weeklyPoints, now, SEVEN_DAYS_MILLIS) },
        intervals = intervals,
        hours = hours,
        heat = heat,
        heatDays = heatDays,
        days = days,
        slices = slices,
        previous = if (fiveHourSeries != null && windowStart != null) {
            compareWithPrevious(fiveHourSeries, fivePoints, windowStart, now, throughput)
        } else {
            null
        },
        modelSpeeds = throughput.byModel(now - 1.hours, now)
    )
}

private fun windowView(series: UsageHistorySeries, points: List<UsageHistoryPoint>, now: Instant, lengthMillis: Long): AnalysisWindowView? {
    val last = points.lastOrNull() ?: return null
    val percent = percentOf(last)
    val resetsAt = last.periodEndAt.takeIf { last.hasKnownResetAt && it > now }
    val startsAt = resetsAt?.let { at -> at - lengthMillis.milliseconds }
    val paceNow = risePoints(points, now - 1.hours, now)
    val elapsedHours = startsAt?.let { start -> (now - start).inWholeMilliseconds / MILLIS_PER_HOUR }?.takeIf { hours -> hours > 0.0 }
    val paceAverage = elapsedHours?.let { hours -> percent / hours }
    val hoursLeft = resetsAt?.let { at -> (at - now).inWholeMilliseconds / MILLIS_PER_HOUR }?.takeIf { hours -> hours > 0.0 }
    val safePace = hoursLeft?.let { hours -> (100 - percent).coerceAtLeast(0) / hours }
    val pace = paceNow ?: paceAverage
    val projected = if (hoursLeft != null && pace != null) (percent + pace * hoursLeft).toInt().coerceAtMost(100) else null
    return AnalysisWindowView(series.quotaLabel, series.periodType, percent, startsAt, resetsAt, paceNow, paceAverage, safePace, projected)
}

private fun compareWithPrevious(
    series: UsageHistorySeries,
    points: List<UsageHistoryPoint>,
    windowStart: Instant,
    now: Instant,
    throughput: ThroughputCuts
): AnalysisWindowComparison {
    val elapsed = now - windowStart
    val hours = elapsed.inWholeMilliseconds / MILLIS_PER_HOUR
    val current = points.lastOrNull()?.let(::percentOf) ?: 0
    val previousStart = previousWindowStart(series, windowStart)
    val previousEnd = previousStart?.let { start -> start + elapsed }
    val previousPoints = if (previousStart != null && previousEnd != null) {
        points.filter { point -> point.capturedAt >= previousStart && point.capturedAt <= previousEnd }
    } else {
        emptyList()
    }
    val previousPercent = previousPoints.lastOrNull()?.let(::percentOf)
    return AnalysisWindowComparison(
        elapsedMillis = elapsed.inWholeMilliseconds,
        currentPercent = current,
        previousPercent = previousPercent,
        currentPace = if (hours > 0.0) current / hours else null,
        previousPace = previousPercent?.let { percent -> if (hours > 0.0) percent / hours else null },
        currentPeakHour = peakHour(points, windowStart, now),
        previousPeakHour = if (previousStart != null && previousEnd != null && previousPoints.size >= 2) peakHour(points, previousStart, previousEnd) else null,
        currentTokensPerSecond = throughput.between(windowStart, now),
        previousTokensPerSecond = if (previousStart != null && previousEnd != null) throughput.between(previousStart, previousEnd) else null
    )
}

/** O maior consumo em uma hora corrida dentro do trecho, por janelas de 1 h desde o início. */
private fun peakHour(points: List<UsageHistoryPoint>, from: Instant, until: Instant): Double? {
    var start = from
    var peak: Double? = null
    while (start < until) {
        val value = risePoints(points, start, minOf(start + 1.hours, until))
        if (value != null) peak = maxOf(peak ?: 0.0, value)
        start += 1.hours
    }
    return peak
}

/** A série principal do período: percentual com janela; sem grupo de modelos antes (Codex tem limites por modelo). */
private fun mainSeries(series: List<UsageHistorySeries>, periodType: PeriodType): UsageHistorySeries? =
    series.filter { item -> item.periodType == periodType && item.unit != UsageUnit.CURRENCY_USD && item.points.isNotEmpty() }
        .sortedWith(compareBy<UsageHistorySeries> { item -> CodexQuotaLabels.groupOf(item.quotaLabel) != null }.thenBy { item -> item.quotaLabel })
        .firstOrNull()

private fun openWindowStart(series: UsageHistorySeries, now: Instant): Instant? {
    val last = series.points.maxByOrNull { point -> point.capturedAt } ?: return null
    if (!last.hasKnownResetAt || last.periodEndAt <= now) return null
    return last.periodEndAt - FIVE_HOURS_MILLIS.milliseconds
}

/** Início da janela 5h anterior: o último reinício conhecido antes do início da corrente. */
private fun previousWindowStart(series: UsageHistorySeries, currentStart: Instant): Instant? {
    val previousEnd = series.points.filter { point -> point.hasKnownResetAt && point.periodEndAt <= currentStart }
        .maxOfOrNull { point -> point.periodEndAt } ?: return null
    return previousEnd - FIVE_HOURS_MILLIS.milliseconds
}

private fun hourStarts(now: Instant): List<Instant> {
    val currentHour = Instant.fromEpochMilliseconds(now.toEpochMilliseconds() / HOUR_MILLIS * HOUR_MILLIS)
    return (23 downTo 0).map { back -> currentHour - back.hours }
}

private fun sliceStarts(windowStart: Instant, now: Instant): List<Instant> {
    val starts = mutableListOf<Instant>()
    var start = windowStart
    while (start < now) {
        starts += start
        start += 30.minutes
    }
    return starts
}

private fun percentOf(point: UsageHistoryPoint): Int = (point.normalizedUsage * 100f).toInt()

/** A subida de cada leitura sobre a anterior (só para cima: o reinício contribui zero). */
private fun risesOf(points: List<UsageHistoryPoint>): List<Pair<Instant, Double>> =
    (1 until points.size).map { index ->
        val rise = (points[index].normalizedUsage - points[index - 1].normalizedUsage) * 100.0
        points[index].capturedAt to rise.coerceAtLeast(0.0)
    }

/** Pontos subidos com a leitura mais nova em `(from, until]`; `null` sem nenhum par de leituras ali. */
private fun risePoints(points: List<UsageHistoryPoint>, from: Instant, until: Instant): Double? {
    val inside = risesOf(points).filter { (at, _) -> at > from && at <= until }
    return if (inside.isEmpty()) null else inside.sumOf { (_, rise) -> rise }
}

/** As somas "desde" de cada recorte; o intervalo é a diferença, por modelo e com piso zero. */
private class ThroughputCuts(private val cumulative: Map<Long, Map<String, OutputThroughput>>, private val now: Instant) {
    private fun at(instant: Instant): Map<String, OutputThroughput>? =
        if (instant >= now) emptyMap() else cumulative[instant.toEpochMilliseconds()]

    private fun models(from: Instant, until: Instant): Map<String, OutputThroughput>? {
        val start = at(from) ?: return null
        val end = at(until) ?: return null
        return start.mapValues { (model, total) ->
            val later = end[model] ?: OutputThroughput(0L, 0L)
            OutputThroughput((total.outputTokens - later.outputTokens).coerceAtLeast(0L), (total.generationMillis - later.generationMillis).coerceAtLeast(0L))
        }.filterValues { value -> value.generationMillis > 0L }
    }

    fun between(from: Instant, until: Instant): Double? =
        models(from, until)?.values?.takeIf { values -> values.isNotEmpty() }?.reduce { sum, value -> sum + value }?.tokensPerSecond

    fun byModel(from: Instant, until: Instant): List<AnalysisModelSpeed> =
        models(from, until).orEmpty().mapNotNull { (model, value) -> value.tokensPerSecond?.let { speed -> AnalysisModelSpeed(model, speed) } }
            .sortedWith(compareByDescending<AnalysisModelSpeed> { speed -> speed.tokensPerSecond }.thenBy { speed -> speed.model })
}

private const val MILLIS_PER_HOUR = 3_600_000.0
private const val HOUR_MILLIS = 3_600_000L
private val FIVE_HOURS_MILLIS = 5.hours.inWholeMilliseconds
private val SEVEN_DAYS_MILLIS = (7 * 24).hours.inWholeMilliseconds

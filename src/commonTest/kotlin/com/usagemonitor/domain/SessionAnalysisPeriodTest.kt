package com.usagemonitor.domain

import com.usagemonitor.domain.entity.AnalysisPeriod
import com.usagemonitor.domain.entity.AnalysisPeriodSummary
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.OutputThroughput
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.SessionAnalysis
import com.usagemonitor.domain.entity.UsageForecast
import com.usagemonitor.domain.entity.UsageHistoryPoint
import com.usagemonitor.domain.entity.UsageHistorySeries
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.domain.entity.analysisThroughputCuts
import com.usagemonitor.domain.entity.buildSessionAnalysis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Período da "Análise" (#538, A02). Agora 2026-10-06 15:30Z (12:30 BRT).
 * 5h: início 30/09 07:00Z (pico 60), 05/10 07:00Z (pico 100), 06/10 07:00Z (pico 90) e a
 * corrente 06/10 12:00Z (pico 62, aberta). Semanal: fechadas com início em 28/08, 04/09,
 * 11/09, 18/09 e 25/09 (picos 90, 40, 100, 80, 100) e a aberta de 02/10 (pico 41).
 * Vazão constante de 50 tokens/s em todos os recortes.
 */
class SessionAnalysisPeriodTest {

    private val now = Instant.parse("2026-10-06T15:30:00Z")

    private fun point(at: Instant, percent: Int, reset: Instant) = UsageHistoryPoint(at, percent.toLong(), 100L, 0L, 0L, reset)

    private fun series(label: String, period: PeriodType, points: List<UsageHistoryPoint>) = UsageHistorySeries(
        quotaLabel = label, periodType = period, unit = UsageUnit.PERCENTAGE, points = points,
        currentDisplayUsed = points.last().used, currentDisplayTotal = 100L, deltaDisplayUsed = 0L,
        averageDisplayConsumptionPerHour = 0.0, currentPeriodEndAt = points.last().periodEndAt,
        forecast = UsageForecast.InsufficientData, riskSummary = null
    )

    /** Pontos de uma janela 5h que começa em [start], um a cada 30 min; reinício em start + 5 h. */
    private fun fiveHourWindow(start: Instant, percents: List<Int>) = percents.mapIndexed { index, percent ->
        point(start + (30 * index).minutes, percent, start + 5.hours)
    }

    private val fiveHour = series(
        "Claude 5h", PeriodType.INTERVAL,
        fiveHourWindow(Instant.parse("2026-09-30T07:00:00Z"), listOf(10, 30, 60)) +
            fiveHourWindow(Instant.parse("2026-10-05T07:00:00Z"), listOf(10, 50, 100)) +
            fiveHourWindow(Instant.parse("2026-10-06T07:00:00Z"), listOf(0, 10, 20, 30, 40, 50, 60, 71, 80, 90)) +
            fiveHourWindow(Instant.parse("2026-10-06T12:00:00Z"), listOf(0, 6, 15, 24, 33, 41, 52, 62))
    )

    private val weekly = series(
        "Claude 7d", PeriodType.WEEKLY,
        listOf(
            point(Instant.parse("2026-08-29T00:00:00Z"), 90, Instant.parse("2026-09-04T22:44:00Z")),
            point(Instant.parse("2026-09-05T00:00:00Z"), 40, Instant.parse("2026-09-11T22:44:00Z")),
            point(Instant.parse("2026-09-12T00:00:00Z"), 100, Instant.parse("2026-09-18T22:44:00Z")),
            point(Instant.parse("2026-09-19T00:00:00Z"), 80, Instant.parse("2026-09-25T22:44:00Z")),
            point(Instant.parse("2026-09-26T00:00:00Z"), 100, Instant.parse("2026-10-02T22:44:00Z")),
            point(Instant.parse("2026-10-03T00:00:00Z"), 30, Instant.parse("2026-10-09T22:44:00Z")),
            point(Instant.parse("2026-10-06T12:00:00Z"), 35, Instant.parse("2026-10-09T22:44:00Z")),
            point(Instant.parse("2026-10-06T15:30:00Z"), 41, Instant.parse("2026-10-09T22:44:00Z"))
        )
    )

    /** 50 tokens/s desde qualquer recorte: 3 000 tokens e 60 s por minuto. */
    private fun cumulative(cuts: Set<Long>) = cuts.associateWith { since ->
        val minutes = (now.toEpochMilliseconds() - since) / 60_000L
        mapOf("opus" to OutputThroughput(minutes * 3_000L, minutes * 60_000L))
    }

    private fun analysis(period: AnalysisPeriod): SessionAnalysis {
        val cuts = analysisThroughputCuts(listOf(fiveHour, weekly), now, period)
        return buildSessionAnalysis("Anthropic · Padrão", ApiSource.ANTHROPIC, listOf(fiveHour, weekly), cumulative(cuts), now, period = period)
    }

    @Test
    fun `NOW carries no period summary, bars or rate`() {
        val result = analysis(AnalysisPeriod.NOW)
        assertEquals(AnalysisPeriod.NOW, result.period)
        assertNull(result.fiveHourSummary)
        assertNull(result.weeklySummary)
        assertTrue(result.periodBars.isEmpty())
        assertNull(result.periodRate)
    }

    @Test
    fun `HOURS_24 summarizes only the 5h windows that started in the last 24 hours`() {
        // Janela de 05/10 (pico 100) começa antes do corte de 24 h: não entra.
        assertEquals(AnalysisPeriodSummary(windowCount = 1, exhaustedCount = 0, meanPeakPercent = 90), analysis(AnalysisPeriod.HOURS_24).fiveHourSummary)
    }

    @Test
    fun `HOURS_24 has no weekly window closed in the period, so the weekly summary is null`() {
        assertNull(analysis(AnalysisPeriod.HOURS_24).weeklySummary)
    }

    @Test
    fun `DAYS_30 counts the closed 5h windows and the exhausted peak among them`() {
        // 60, 100 e 90: média 83,33 → 83; um esgotou.
        assertEquals(AnalysisPeriodSummary(windowCount = 3, exhaustedCount = 1, meanPeakPercent = 83), analysis(AnalysisPeriod.DAYS_30).fiveHourSummary)
    }

    @Test
    fun `DAYS_30 counts the closed weekly windows of the period and their exhausted peaks`() {
        // Fechadas com reinício depois de 06/09 15:30Z (11/09, 18/09, 25/09, 02/10): picos 40, 100, 80, 100 → média 80; dois esgotaram.
        assertEquals(AnalysisPeriodSummary(windowCount = 4, exhaustedCount = 2, meanPeakPercent = 80), analysis(AnalysisPeriod.DAYS_30).weeklySummary)
    }

    @Test
    fun `two readings of one weekly window with resets 30 s apart count as one window`() {
        // Mesma janela semanal lida duas vezes: o reinício oscila 30 s entre as leituras (tolerância de reinício de 5 min).
        val jitter = series(
            "Claude 7d", PeriodType.WEEKLY,
            listOf(
                point(Instant.parse("2026-09-19T00:00:00Z"), 80, Instant.parse("2026-09-25T22:44:00Z")),
                point(Instant.parse("2026-09-20T00:00:00Z"), 85, Instant.parse("2026-09-25T22:44:30Z"))
            )
        )
        val cuts = analysisThroughputCuts(listOf(fiveHour, jitter), now, AnalysisPeriod.DAYS_30)
        val result = buildSessionAnalysis("Anthropic · Padrão", ApiSource.ANTHROPIC, listOf(fiveHour, jitter), cumulative(cuts), now, period = AnalysisPeriod.DAYS_30)
        assertEquals(1, result.weeklySummary?.windowCount)
    }

    @Test
    fun `DAYS_7 counts the previous week when its reset fell within the last 7 days`() {
        // Semana de 27/09 a 04/10 12:00Z: reinício há 2 dias, pico 100 → entra no resumo semanal de 7 dias.
        val previousWeek = series("Claude 7d", PeriodType.WEEKLY, listOf(point(Instant.parse("2026-09-27T00:00:00Z"), 100, Instant.parse("2026-10-04T12:00:00Z"))))
        val cuts = analysisThroughputCuts(listOf(fiveHour, previousWeek), now, AnalysisPeriod.DAYS_7)
        val result = buildSessionAnalysis("Anthropic · Padrão", ApiSource.ANTHROPIC, listOf(fiveHour, previousWeek), cumulative(cuts), now, period = AnalysisPeriod.DAYS_7)
        assertEquals(AnalysisPeriodSummary(windowCount = 1, exhaustedCount = 1, meanPeakPercent = 100), result.weeklySummary)
    }

    @Test
    fun `ALL reaches the weekly windows that DAYS_30 leaves out`() {
        // Picos 90, 40, 100, 80, 100 → média 82.
        assertEquals(AnalysisPeriodSummary(windowCount = 5, exhaustedCount = 2, meanPeakPercent = 82), analysis(AnalysisPeriod.ALL).weeklySummary)
    }

    @Test
    fun `DAYS_30 bars are one per weekly window and the open window is the current bar`() {
        val bars = analysis(AnalysisPeriod.DAYS_30).periodBars
        assertEquals(listOf(40, 100, 80, 100, 41), bars.map { bar -> bar.peakPercent })
        assertEquals(listOf(false, false, false, false, true), bars.map { bar -> bar.current })
        assertEquals(Instant.parse("2026-09-04T22:44:00Z"), bars.first().start)
    }

    @Test
    fun `DAYS_7 bars are one per local day with the 5h peak and null where no point was read`() {
        val bars = analysis(AnalysisPeriod.DAYS_7).periodBars
        assertEquals(7, bars.size)
        // Meia-noite BRT de 30/09 = 03:00Z.
        assertEquals(Instant.parse("2026-09-30T03:00:00Z"), bars.first().start)
        assertEquals(listOf(60, null, null, null, null, 100, 90), bars.map { bar -> bar.peakPercent })
        assertEquals(listOf(false, false, false, false, false, false, true), bars.map { bar -> bar.current })
    }

    @Test
    fun `DAYS_30 rate is the 5h rise over the 720 hours with the throughput since the period start`() {
        // Subidas: 50 (30/09) + 90 (05/10) + 90 (06/10 manhã) + 62 (06/10 tarde) = 292 pontos.
        val rate = analysis(AnalysisPeriod.DAYS_30).periodRate!!
        assertEquals(AnalysisPeriod.DAYS_30, rate.period)
        assertEquals(292.0 / 720.0, rate.pacePerHour!!, 1e-6)
        assertEquals(50.0, rate.tokensPerSecond!!, 1e-6)
    }

    @Test
    fun `ALL rate divides the 5h rise by the hours since the first reading, not by 2160`() {
        // Primeira leitura da série semanal: 29/08 00:00Z → 927,5 h até agora (não 2160 h).
        assertEquals(292.0 / 927.5, analysis(AnalysisPeriod.ALL).periodRate?.pacePerHour ?: Double.NaN, 1e-6)
    }

    @Test
    fun `ALL bars start at the first reading and include the weekly windows before the 30 day cut`() {
        assertEquals(listOf(90, 40, 100, 80, 100, 41), analysis(AnalysisPeriod.ALL).periodBars.map { bar -> bar.peakPercent })
    }

    @Test
    fun `only the 30 day and all periods carry a period rate`() {
        assertNull(analysis(AnalysisPeriod.HOURS_24).periodRate)
        assertNull(analysis(AnalysisPeriod.DAYS_7).periodRate)
    }

    @Test
    fun `the period start is a throughput cut only for the 30 day and all periods`() {
        val cuts30 = analysisThroughputCuts(listOf(fiveHour, weekly), now, AnalysisPeriod.DAYS_30)
        val cutsAll = analysisThroughputCuts(listOf(fiveHour, weekly), now, AnalysisPeriod.ALL)
        val cutsNow = analysisThroughputCuts(listOf(fiveHour, weekly), now)
        assertTrue((now - (30 * 24).hours).toEpochMilliseconds() in cuts30)
        // Corte de ALL é a primeira leitura da série (29/08 00:00Z), não agora menos 90 d.
        assertTrue(Instant.parse("2026-08-29T00:00:00Z").toEpochMilliseconds() in cutsAll)
        assertTrue((now - (30 * 24).hours).toEpochMilliseconds() !in cutsNow)
    }
}

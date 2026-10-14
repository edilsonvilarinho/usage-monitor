package com.usagemonitor.domain

import com.usagemonitor.domain.entity.AnalysisSpan
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.OutputThroughput
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.UsageForecast
import com.usagemonitor.domain.entity.UsageHistoryPoint
import com.usagemonitor.domain.entity.UsageHistorySeries
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.domain.entity.analysisThroughputCuts
import com.usagemonitor.domain.entity.buildSessionAnalysis
import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Análise da sessão (rodada AB, B04). Cenário: agora 15:30Z (12:30 BRT); a 5h corrente
 * começou 12:00Z e reinicia 17:00Z, a anterior foi 07:00Z–12:00Z; vazão constante de
 * 50 tokens/s em todos os recortes.
 */
class SessionAnalysisTest {

    private val now = Instant.parse("2026-10-06T15:30:00Z")
    private val currentReset = Instant.parse("2026-10-06T17:00:00Z")
    private val previousReset = Instant.parse("2026-10-06T12:00:00Z")
    private val weeklyReset = Instant.parse("2026-10-09T22:44:00Z")

    private fun point(at: Instant, percent: Int, reset: Instant) = UsageHistoryPoint(at, percent.toLong(), 100L, 0L, 0L, reset)

    private fun series(label: String, period: PeriodType, points: List<UsageHistoryPoint>) = UsageHistorySeries(
        quotaLabel = label, periodType = period, unit = UsageUnit.PERCENTAGE, points = points,
        currentDisplayUsed = points.last().used, currentDisplayTotal = 100L, deltaDisplayUsed = 0L,
        averageDisplayConsumptionPerHour = 0.0, currentPeriodEndAt = points.last().periodEndAt,
        forecast = UsageForecast.InsufficientData, riskSummary = null
    )

    private val fiveHour = series(
        "Claude 5h", PeriodType.INTERVAL,
        listOf(0, 10, 20, 30, 40, 50, 60, 71, 80, 90).mapIndexed { index, percent ->
            point(Instant.parse("2026-10-06T07:00:00Z") + (30 * index).minutes, percent, previousReset)
        } + listOf(0, 6, 15, 24, 33, 41, 52, 62).mapIndexed { index, percent ->
            point(Instant.parse("2026-10-06T12:00:00Z") + (30 * index).minutes, percent, currentReset)
        }
    )
    private val weekly = series(
        "Claude 7d", PeriodType.WEEKLY,
        listOf(
            point(Instant.parse("2026-10-05T15:30:00Z"), 30, weeklyReset),
            point(Instant.parse("2026-10-06T12:00:00Z"), 35, weeklyReset),
            point(Instant.parse("2026-10-06T15:30:00Z"), 41, weeklyReset)
        )
    )

    /** 50 tokens/s desde qualquer recorte: 3 000 tokens e 60 s por minuto. */
    private fun cumulative(cuts: Set<Long>) = cuts.associateWith { since ->
        val minutes = (now.toEpochMilliseconds() - since) / 60_000L
        mapOf("opus" to OutputThroughput(minutes * 3_000L, minutes * 60_000L))
    }

    private fun analysis() = analysisThroughputCuts(listOf(fiveHour, weekly), now).let { cuts ->
        buildSessionAnalysis("Anthropic · Padrão", ApiSource.ANTHROPIC, listOf(fiveHour, weekly), cumulative(cuts), now)
    }

    private fun near(expected: Double, actual: Double?) = assertTrue(actual != null && abs(expected - actual) < 0.05, "esperado $expected, veio $actual")

    @Test
    fun `the open window has its pace, the safe pace and where it ends`() {
        val window = analysis().fiveHour!!
        assertEquals(62, window.percent)
        near(21.0, window.paceNow)
        near(62.0 / 3.5, window.paceAverage)
        near(38.0 / 1.5, window.safePace)
        assertEquals(93, window.projectedAtReset)
        assertEquals(Instant.parse("2026-10-06T12:00:00Z"), window.startsAt)
    }

    @Test
    fun `intervals, hours and slices carry pace and the exact throughput`() {
        val result = analysis()
        assertEquals(AnalysisSpan.entries, result.intervals.map { row -> row.span })
        near(21.0, result.intervals.first().pacePerHour)
        near(50.0, result.intervals.first().tokensPerSecond)
        assertEquals(24, result.hours.size)
        assertEquals(7, result.slices.size)
        near(6.0, result.slices.first().percentPoints)
        near(50.0, result.slices.first().tokensPerSecond)
        assertEquals("opus", result.modelSpeeds.single().model)
    }

    @Test
    fun `the current window is compared with the previous one at the same point`() {
        val previous = analysis().previous!!
        assertEquals(3.5.hours.inWholeMilliseconds, previous.elapsedMillis)
        assertEquals(62, previous.currentPercent)
        assertEquals(71, previous.previousPercent)
        near(50.0, previous.previousTokensPerSecond)
    }

    @Test
    fun `days and heat are local and a missing throughput stays unmeasured`() {
        val result = buildSessionAnalysis("Anthropic · Padrão", ApiSource.ANTHROPIC, listOf(fiveHour, weekly), emptyMap(), now)
        assertEquals(LocalDate(2026, 10, 6), result.days.last().date)
        near(11.0, result.days.last().percentPoints)
        assertEquals(0.0, result.days[result.days.size - 2].percentPoints)
        assertEquals(7, result.heat.size)
        assertTrue(result.heat.all { row -> row.size == 24 })
        assertNull(result.intervals.first().tokensPerSecond)
        assertTrue(result.modelSpeeds.isEmpty())
        assertEquals(41, result.weekly!!.percent)
    }
}

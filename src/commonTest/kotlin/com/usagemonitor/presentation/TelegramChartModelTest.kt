package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageHistoryReport
import com.usagemonitor.domain.entity.HistoryRange
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.UsageForecast
import com.usagemonitor.domain.entity.UsageHistoryPoint
import com.usagemonitor.domain.entity.UsageHistorySeries
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.presentation.ui.buildTelegramChart
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** Gráfico do `/grafico` (#398, Y6): do histórico às linhas, sem desenhar. */
class TelegramChartModelTest {

    private val now = Instant.parse("2026-10-07T18:00:00Z")

    private fun point(hoursAgo: Int, used: Long, resetAt: Instant) = UsageHistoryPoint(
        capturedAt = now - hoursAgo.hours, used = used, total = 100L, rawUsed = used, rawTotal = 100L, periodEndAt = resetAt
    )

    private fun series(label: String, unit: UsageUnit, points: List<UsageHistoryPoint>) = UsageHistorySeries(
        quotaLabel = label, periodType = PeriodType.INTERVAL, unit = unit, points = points,
        currentDisplayUsed = points.lastOrNull()?.displayUsed ?: 0L, currentDisplayTotal = 100L, deltaDisplayUsed = 0L,
        averageDisplayConsumptionPerHour = 0.0, currentPeriodEndAt = now, forecast = UsageForecast.InsufficientData, riskSummary = null
    )

    private fun report(vararg series: UsageHistorySeries) =
        ApiUsageHistoryReport(source = ApiSource.ANTHROPIC, range = HistoryRange.LAST_24_HOURS, lastUpdatedAt = now, series = series.toList())

    @Test
    fun `one line per quota with the reset where the window rolled over`() {
        val firstWindow = now - 4.hours
        val secondWindow = now + 1.hours
        val fiveHour = series(
            "Sessão 5h", UsageUnit.PERCENTAGE,
            listOf(point(30, 90, now - 25.hours), point(8, 60, firstWindow), point(5, 92, firstWindow), point(3, 4, secondWindow), point(1, 30, secondWindow))
        )
        val chart = buildTelegramChart(listOf("Anthropic" to report(fiveHour)), HistoryRange.LAST_24_HOURS, now)

        val line = chart.lines.single()
        assertEquals("Anthropic · Sessão 5h", line.label)
        // O ponto de 30 h atrás fica fora do intervalo de 24 h.
        assertEquals(4, line.points.size)
        assertEquals(listOf((now - 3.hours).toEpochMilliseconds()), line.resets)
        assertEquals(30, line.lastPercent)
    }

    @Test
    fun `money and empty series stay out of the chart`() {
        val balance = series("Saldo", UsageUnit.CURRENCY_USD, listOf(point(2, 40, now + 1.hours)))
        val old = series("Semanal", UsageUnit.PERCENTAGE, listOf(point(40, 10, now + 1.hours)))

        val chart = buildTelegramChart(listOf("DeepSeek" to report(balance, old)), HistoryRange.LAST_24_HOURS, now)

        assertTrue(chart.isEmpty)
    }
}

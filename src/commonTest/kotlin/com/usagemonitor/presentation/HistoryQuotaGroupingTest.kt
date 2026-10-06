package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageHistoryReport
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.HistoryRange
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.UsageForecast
import com.usagemonitor.domain.entity.UsageHistoryPoint
import com.usagemonitor.domain.entity.UsageHistorySeries
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.presentation.ui.buildGenericHistoryGroups
import com.usagemonitor.presentation.ui.quotaViewLabels
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

private val CAPTURED = Instant.parse("2026-10-06T12:00:00Z")

private fun series(label: String, period: PeriodType) = UsageHistorySeries(
    quotaLabel = label, periodType = period, unit = UsageUnit.PERCENTAGE,
    points = listOf(UsageHistoryPoint(CAPTURED, 41, 100, 0, 0, CAPTURED)),
    currentDisplayUsed = 41, currentDisplayTotal = 100, deltaDisplayUsed = 12,
    averageDisplayConsumptionPerHour = 2.0, currentPeriodEndAt = CAPTURED,
    forecast = UsageForecast.ResetsBeforeExhaustion, riskSummary = null
)

class HistoryQuotaGroupingTest {

    private val goSeries = listOf(
        series("Go 5h", PeriodType.INTERVAL),
        series("Go semanal", PeriodType.WEEKLY),
        series("Go mensal", PeriodType.MONTHLY)
    )

    @Test
    fun `OpenCode Go windows share one card with the monthly quota`() {
        val group = buildGenericHistoryGroups(goSeries).single()

        assertEquals("Go", group.baseLabel)
        assertEquals("Go 5h", group.chartSeries.quotaLabel)
        assertEquals("Go semanal", group.weeklySummary?.quotaLabel)
        assertEquals("Go mensal", group.monthlySummary?.quotaLabel)
    }

    @Test
    fun `selector names three windows as all`() {
        val report = ApiUsageHistoryReport(ApiSource.OPENCODE_GO, HistoryRange.LAST_7_DAYS, CAPTURED, goSeries, null, null, CAPTURED)

        assertEquals(listOf("5h", "Semanal", "Todas"), quotaViewLabels(report, AppLanguage.PT))
    }

    @Test
    fun `anthropic keeps both when there is no monthly quota`() {
        val groups = buildGenericHistoryGroups(listOf(series("Claude 5h", PeriodType.INTERVAL), series("Claude 7d", PeriodType.WEEKLY)))

        assertNull(groups.single().monthlySummary)
    }
}

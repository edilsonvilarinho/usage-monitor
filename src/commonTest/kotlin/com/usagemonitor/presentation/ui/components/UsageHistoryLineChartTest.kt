package com.usagemonitor.presentation.ui.components

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.domain.entity.UsageHistoryPoint
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UsageHistoryLineChartTest {

    @Test
    fun `buildTimelineFractions uses real timestamp spacing`() {
        val points = listOf(
            historyPoint("2026-05-06T18:00:00Z", 469),
            historyPoint("2026-05-06T18:30:00Z", 468),
            historyPoint("2026-05-06T20:00:00Z", 466)
        )

        val fractions = buildTimelineFractions(points)

        assertEquals(0f, fractions[0])
        assertEquals(0.25f, fractions[1])
        assertEquals(1f, fractions[2])
    }

    @Test
    fun `buildCurrencyAxis keeps small balance drifts from filling the whole chart`() {
        val axis = buildValueAxis(
            points = listOf(
                historyPoint("2026-05-06T18:00:00Z", 469),
                historyPoint("2026-05-06T18:30:00Z", 468),
                historyPoint("2026-05-06T20:00:00Z", 466)
            ),
            unit = UsageUnit.CURRENCY_USD
        )

        assertNotNull(axis)
        assertEquals(469f, axis.max)
        assertTrue(axis.max - axis.min >= 100f)
        assertTrue(axis.min >= 0f)
    }

    @Test
    fun `buildValueAxis for requests keeps low absolute usage readable`() {
        val axis = buildValueAxis(
            points = listOf(
                historyPoint("2026-05-06T18:00:00Z", 16),
                historyPoint("2026-05-06T19:00:00Z", 17),
                historyPoint("2026-05-06T20:00:00Z", 18)
            ),
            unit = UsageUnit.REQUESTS
        )

        assertNotNull(axis)
        assertEquals(18f, axis.max)
        assertTrue(axis.max - axis.min >= 10f)
        assertTrue(axis.min >= 0f)
    }

    @Test
    fun `buildPlotPoints centers a single point for hover and marker`() {
        val points = listOf(historyPoint("2026-05-06T18:00:00Z", 81, total = 100))

        val plotPoints = buildPlotPoints(
            points = points,
            chartWidth = 240f,
            chartHeight = 120f,
            axis = null
        )

        assertEquals(1, plotPoints.size)
        assertEquals(120f, plotPoints.first().x)
        assertTrue(kotlin.math.abs(plotPoints.first().y - 22.8f) < 0.001f)
    }

    @Test
    fun `findClosestPlotPointIndex prefers later point when timestamps overlap on same x`() {
        val points = listOf(
            historyPoint("2026-05-06T18:00:00Z", 10, total = 100),
            historyPoint("2026-05-06T18:00:00Z", 20, total = 100),
            historyPoint("2026-05-06T19:00:00Z", 30, total = 100)
        )
        val plotPoints = buildPlotPoints(
            points = points,
            chartWidth = 200f,
            chartHeight = 100f,
            axis = null
        )

        val hoveredIndex = findClosestPlotPointIndex(plotPoints, pointerX = 0f)

        assertEquals(1, hoveredIndex)
    }

    @Test
    fun `buildHistoryTooltipModel formats request usage and delta`() {
        val points = listOf(
            historyPoint("2026-05-06T18:00:00Z", 40, total = 100, periodEndAt = "2026-05-06T23:00:00Z"),
            historyPoint("2026-05-06T19:00:00Z", 52, total = 100, periodEndAt = "2026-05-06T23:00:00Z")
        )
        val activePoint = ChartPlotPoint(
            index = 1,
            point = points[1],
            x = 120f,
            y = 48f
        )

        val tooltip = buildHistoryTooltipModel(
            activePoint = activePoint,
            points = points,
            unit = UsageUnit.REQUESTS,
            language = AppLanguage.PT,
            title = "Codex 5h",
            subtitle = "Quota intervalar"
        )

        assertNotNull(tooltip)
        assertEquals("Codex 5h", tooltip.title)
        assertTrue(tooltip.subtitle.contains("Quota intervalar"))
        assertEquals("52/100 req (52%)", tooltip.metrics[0].value)
        assertEquals("+12 req", tooltip.metrics[1].value)
        assertEquals("06/05 20:00 BRT", tooltip.metrics[2].value)
    }

    @Test
    fun `buildHistoryTooltipModel returns null without active point`() {
        val tooltip = buildHistoryTooltipModel(
            activePoint = null,
            points = emptyList(),
            unit = UsageUnit.PERCENTAGE,
            language = AppLanguage.EN,
            title = "Anthropic",
            subtitle = null
        )

        assertNull(tooltip)
    }

    @Test
    fun `clampTooltipLeft keeps bubble inside chart bounds`() {
        assertEquals(8f, clampTooltipLeft(desiredCenterX = 12f, tooltipWidth = 100f, containerWidth = 240f))
        assertEquals(132f, clampTooltipLeft(desiredCenterX = 220f, tooltipWidth = 100f, containerWidth = 240f))
        assertEquals(70f, clampTooltipLeft(desiredCenterX = 120f, tooltipWidth = 100f, containerWidth = 240f))
    }

    private fun historyPoint(
        capturedAt: String,
        displayUsed: Long,
        total: Long = displayUsed,
        periodEndAt: String = "9999-12-31T23:59:59Z"
    ): UsageHistoryPoint {
        return UsageHistoryPoint(
            capturedAt = Instant.parse(capturedAt),
            used = 0L,
            total = total,
            rawUsed = displayUsed,
            rawTotal = total,
            periodEndAt = Instant.parse(periodEndAt)
        )
    }
}

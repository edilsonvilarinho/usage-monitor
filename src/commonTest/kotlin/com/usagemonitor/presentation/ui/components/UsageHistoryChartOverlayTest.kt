package com.usagemonitor.presentation.ui.components

import androidx.compose.ui.graphics.Color
import com.usagemonitor.domain.entity.UsageHistoryPoint
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UsageHistoryChartOverlayTest {

    private fun point(at: String, used: Long) = UsageHistoryPoint(
        capturedAt = Instant.parse(at),
        used = used,
        total = 100L,
        rawUsed = 0L,
        rawTotal = 0L,
        periodEndAt = Instant.parse("2026-09-30T00:00:00Z")
    )

    private val reference = listOf(
        point("2026-09-27T00:00:00Z", 10L),
        point("2026-09-27T10:00:00Z", 60L)
    )

    @Test
    fun `overlay is placed by timestamp on the reference time span`() {
        val overlay = listOf(
            point("2026-09-27T00:00:00Z", 40L),
            point("2026-09-27T05:00:00Z", 45L),
            point("2026-09-27T10:00:00Z", 50L)
        )

        val plot = buildOverlayPlotPoints(overlay, reference, chartWidth = 228f, chartHeight = 100f, horizontalInsetPx = 14f)

        assertEquals(listOf(14f, 114f, 214f), plot.map { it.x })
        assertEquals(listOf(60f, 55f, 50f), plot.map { it.y })
    }

    @Test
    fun `overlay points outside the reference span are dropped, as under zoom`() {
        val overlay = listOf(
            point("2026-09-26T23:00:00Z", 30L),
            point("2026-09-27T05:00:00Z", 45L),
            point("2026-09-27T11:00:00Z", 55L)
        )

        val plot = buildOverlayPlotPoints(overlay, reference, chartWidth = 228f, chartHeight = 100f, horizontalInsetPx = 14f)

        assertEquals(listOf(Instant.parse("2026-09-27T05:00:00Z")), plot.map { it.point.capturedAt })
    }

    @Test
    fun `overlay without a reference span draws nothing`() {
        val plot = buildOverlayPlotPoints(reference, reference.take(1), chartWidth = 228f, chartHeight = 100f)

        assertTrue(plot.isEmpty())
    }

    @Test
    fun `hovered overlay value is matched by time, not by index`() {
        // A sobreposta tem um ponto a mais no começo: casar por índice poria
        // o valor das 00h ao lado do ponto das 10h da principal.
        val overlay = listOf(
            point("2026-09-27T00:00:00Z", 40L),
            point("2026-09-27T02:00:00Z", 42L),
            point("2026-09-27T10:00:00Z", 50L)
        )
        val overlayPlot = buildOverlayPlotPoints(overlay, reference, chartWidth = 228f, chartHeight = 100f)
        val active = ChartPlotPoint(index = 1, point = reference[1], x = 214f, y = 40f)

        val matched = findOverlayPointAt(overlayPlot, active)

        assertEquals(Instant.parse("2026-09-27T10:00:00Z"), matched?.point?.capturedAt)
    }

    @Test
    fun `tooltip names the primary usage and lists the overlay right below it`() {
        val model = HistoryTooltipModel(
            title = "Claude",
            subtitle = "27/09 10:00",
            metrics = listOf(
                TooltipMetric("Uso", "60/100 % (60%)"),
                TooltipMetric("Variação", "+50 %"),
                TooltipMetric("Janela", "30/09")
            )
        )
        val overlay = HistoryChartOverlay(points = emptyList(), label = "7d", color = Color.Red)
        val overlayPoint = ChartPlotPoint(index = 0, point = point("2026-09-27T10:00:00Z", 50L), x = 0f, y = 0f)

        val merged = withOverlayMetrics(model, "5h", listOf(overlay to overlayPoint))

        assertEquals(listOf("5h", "7d", "Variação", "Janela"), merged.metrics.map { it.label })
        assertEquals("50%", merged.metrics[1].value)
    }

    @Test
    fun `tooltip is unchanged without overlays`() {
        val model = HistoryTooltipModel(title = null, subtitle = "x", metrics = listOf(TooltipMetric("Uso", "1 %")))

        assertEquals(model, withOverlayMetrics(model, "5h", emptyList()))
    }
}

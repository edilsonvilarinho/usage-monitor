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
    fun `buildPlotPoints keeps first and last point away from chart edges`() {
        val points = listOf(
            historyPoint("2026-05-06T18:00:00Z", 20, total = 100),
            historyPoint("2026-05-06T20:00:00Z", 80, total = 100)
        )

        val plotPoints = buildPlotPoints(
            points = points,
            chartWidth = 200f,
            chartHeight = 120f,
            axis = null
        )

        val inset = resolvePlotHorizontalInset(200f)
        assertEquals(inset, plotPoints.first().x)
        assertEquals(200f - inset, plotPoints.last().x)
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
        assertEquals("+12 req (+30%)", tooltip.metrics[1].value)
        assertEquals("06/05 20:00 BRT", tooltip.metrics[2].value)
    }

    @Test
    fun `buildHistoryTooltipModel formats token delta from pinned anchor`() {
        val points = listOf(
            historyPoint("2026-05-06T18:00:00Z", 1_000, total = 4_000),
            historyPoint("2026-05-06T19:00:00Z", 1_300, total = 4_000),
            historyPoint("2026-05-06T20:00:00Z", 1_600, total = 4_000)
        )
        val tooltip = buildHistoryTooltipModel(
            activePoint = ChartPlotPoint(index = 2, point = points[2], x = 0f, y = 0f),
            points = points,
            unit = UsageUnit.TOKENS,
            language = AppLanguage.PT,
            title = "Claude 5h",
            subtitle = "Quota intervalar",
            comparisonPoint = points[0]
        )

        assertNotNull(tooltip)
        assertEquals("+600 tok (+60%)", tooltip.metrics[1].value)
    }

    @Test
    fun `buildHistoryTooltipModel formats currency delta from pinned anchor`() {
        val points = listOf(
            historyPoint("2026-05-06T18:00:00Z", 2_350),
            historyPoint("2026-05-06T19:00:00Z", 2_125)
        )
        val tooltip = buildHistoryTooltipModel(
            activePoint = ChartPlotPoint(index = 1, point = points[1], x = 0f, y = 0f),
            points = points,
            unit = UsageUnit.CURRENCY_USD,
            language = AppLanguage.PT,
            title = "Saldo",
            subtitle = null,
            comparisonPoint = points[0]
        )

        assertNotNull(tooltip)
        assertEquals("-\$2.25 (-10%)", tooltip.metrics[1].value)
    }

    @Test
    fun `buildHistoryTooltipModel formats percentage delta in points and relative variation`() {
        val points = listOf(
            percentageHistoryPoint("2026-05-06T18:00:00Z", used = 50, total = 100),
            percentageHistoryPoint("2026-05-06T19:00:00Z", used = 62, total = 100)
        )
        val tooltip = buildHistoryTooltipModel(
            activePoint = ChartPlotPoint(index = 1, point = points[1], x = 0f, y = 0f),
            points = points,
            unit = UsageUnit.PERCENTAGE,
            language = AppLanguage.PT,
            title = "Anthropic",
            subtitle = null,
            comparisonPoint = points[0]
        )

        assertNotNull(tooltip)
        assertEquals("+12 p.p. (+24%)", tooltip.metrics[1].value)
    }

    @Test
    fun `buildHistoryTooltipModel marks base unavailable when anchor is zero`() {
        val points = listOf(
            historyPoint("2026-05-06T18:00:00Z", 0, total = 100),
            historyPoint("2026-05-06T19:00:00Z", 15, total = 100)
        )
        val tooltip = buildHistoryTooltipModel(
            activePoint = ChartPlotPoint(index = 1, point = points[1], x = 0f, y = 0f),
            points = points,
            unit = UsageUnit.REQUESTS,
            language = AppLanguage.PT,
            title = "Codex 5h",
            subtitle = null,
            comparisonPoint = points[0]
        )

        assertNotNull(tooltip)
        assertEquals("+15 req (base indisponível)", tooltip.metrics[1].value)
    }

    @Test
    fun `detectHistoryRangeAnnotations marks reset when period end changes`() {
        val annotations = detectHistoryRangeAnnotations(
            points = listOf(
                historyPoint("2026-05-06T18:00:00Z", 20, total = 100, periodEndAt = "2026-05-06T23:00:00Z"),
                historyPoint("2026-05-06T19:00:00Z", 35, total = 100, periodEndAt = "2026-05-06T23:00:00Z"),
                historyPoint("2026-05-06T20:00:00Z", 5, total = 100, periodEndAt = "2026-05-07T04:00:00Z")
            ),
            unit = UsageUnit.REQUESTS
        )

        assertNotNull(annotations)
        assertEquals(listOf(2), annotations.resetIndices)
    }

    @Test
    fun `detectHistoryRangeAnnotations marks reset when non monetary usage drops`() {
        val annotations = detectHistoryRangeAnnotations(
            points = listOf(
                historyPoint("2026-05-06T18:00:00Z", 45, total = 100, periodEndAt = "2026-05-06T23:00:00Z"),
                historyPoint("2026-05-06T19:00:00Z", 30, total = 100, periodEndAt = "2026-05-06T23:00:00Z")
            ),
            unit = UsageUnit.PERCENTAGE
        )

        assertNotNull(annotations)
        assertEquals(listOf(1), annotations.resetIndices)
    }

    @Test
    fun `detectHistoryRangeAnnotations ignores millisecond jitter in period end`() {
        val annotations = detectHistoryRangeAnnotations(
            points = listOf(
                historyPoint("2026-05-06T18:00:00Z", 4500, total = 4500, periodEndAt = "2026-05-06T23:00:00.100Z"),
                historyPoint("2026-05-06T18:10:00Z", 4500, total = 4500, periodEndAt = "2026-05-06T22:59:59.500Z"),
                historyPoint("2026-05-06T18:20:00Z", 4500, total = 4500, periodEndAt = "2026-05-06T23:00:00.900Z")
            ),
            unit = UsageUnit.REQUESTS
        )

        assertNotNull(annotations)
        assertEquals(emptyList(), annotations.resetIndices)
    }

    @Test
    fun `detectHistoryRangeAnnotations still detects a real reset beyond the jitter tolerance`() {
        val annotations = detectHistoryRangeAnnotations(
            points = listOf(
                historyPoint("2026-05-06T18:00:00Z", 4500, total = 4500, periodEndAt = "2026-05-06T23:00:00Z"),
                historyPoint("2026-05-06T23:05:00Z", 0, total = 4500, periodEndAt = "2026-05-07T04:00:00Z")
            ),
            unit = UsageUnit.REQUESTS
        )

        assertNotNull(annotations)
        assertEquals(listOf(1), annotations.resetIndices)
    }

    @Test
    fun `buildHistoryIntervalSummaryModel formats default interval summary`() {
        val summary = buildHistoryIntervalSummaryModel(
            points = listOf(
                historyPoint("2026-05-06T18:00:00Z", 40, total = 100),
                historyPoint("2026-05-06T19:00:00Z", 70, total = 100)
            ),
            unit = UsageUnit.REQUESTS,
            language = AppLanguage.PT
        )

        assertNotNull(summary)
        assertEquals(null, summary.headline)
        assertEquals("Arraste no gráfico para comparar dois pontos.", summary.supportingText)
        assertEquals("Início do recorte", summary.metrics[0].label)
        assertEquals("40/100 req (40%)", summary.metrics[0].value)
        assertEquals("Atual", summary.metrics[1].label)
        assertEquals("70/100 req (70%)", summary.metrics[1].value)
        assertEquals("Variação no recorte", summary.metrics[2].label)
        assertEquals("+30 req (+75%)", summary.metrics[2].value)
    }

    @Test
    fun `buildHistoryIntervalSummaryModel formats comparison summary`() {
        val summary = buildHistoryIntervalSummaryModel(
            points = listOf(
                historyPoint("2026-05-06T18:00:00Z", 40, total = 100),
                historyPoint("2026-05-06T19:00:00Z", 55, total = 100),
                historyPoint("2026-05-06T20:00:00Z", 70, total = 100)
            ),
            unit = UsageUnit.REQUESTS,
            language = AppLanguage.PT,
            selection = PinnedHistorySelection(
                chartKey = "codex:req:24h",
                anchorIndex = 0,
                currentIndex = 2
            )
        )

        assertNotNull(summary)
        assertEquals("Comparando 15:00 -> 17:00", summary.headline)
        assertEquals("Faixa selecionada no gráfico.", summary.supportingText)
        assertEquals("Início da faixa", summary.metrics[0].label)
        assertEquals("40/100 req (40%)", summary.metrics[0].value)
        assertEquals("Fim da faixa", summary.metrics[1].label)
        assertEquals("70/100 req (70%)", summary.metrics[1].value)
        assertEquals("Consumido na faixa", summary.metrics[2].label)
        assertEquals("30 req", summary.metrics[2].value)
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

    @Test
    fun `frame pointer maps to plot coordinates only inside plot bounds`() {
        assertEquals(40f, framePointerToPlotPointerX(framePointerX = 88f, plotStartX = 48f, plotWidth = 240f))
        assertEquals(null, framePointerToPlotPointerX(framePointerX = 20f, plotStartX = 48f, plotWidth = 240f))
        assertEquals(null, framePointerToPlotPointerX(framePointerX = 320f, plotStartX = 48f, plotWidth = 240f))
    }

    @Test
    fun `frame pointer coercion clamps drag updates to nearest plot edge`() {
        assertEquals(0f, coerceFramePointerToPlotPointerX(framePointerX = 20f, plotStartX = 48f, plotWidth = 240f))
        assertEquals(240f, coerceFramePointerToPlotPointerX(framePointerX = 320f, plotStartX = 48f, plotWidth = 240f))
    }

    @Test
    fun `drag above threshold pins selection from anchor to current point`() {
        val plotPoints = buildPlotPoints(
            points = listOf(
                historyPoint("2026-05-06T18:00:00Z", 10, total = 100),
                historyPoint("2026-05-06T19:00:00Z", 20, total = 100),
                historyPoint("2026-05-06T20:00:00Z", 30, total = 100)
            ),
            chartWidth = 200f,
            chartHeight = 100f,
            axis = null
        )
        val started = startHistoryDragSelectionSession(
            chartKey = "codex:req:24h",
            plotPoints = plotPoints,
            pointerDownX = 0f,
            existingSelection = null
        )
        val updated = updateHistoryDragSelectionSession(
            session = started,
            plotPoints = plotPoints,
            pointerX = 200f,
            dragThresholdPx = 10f
        )

        assertEquals(2, updated.currentIndex)
        assertTrue(updated.hasDragged)

        val outcome = resolveHistorySelectionGestureOutcome(updated)

        assertEquals(
            HistorySelectionGestureOutcome.Pin(
                PinnedHistorySelection(
                    chartKey = "codex:req:24h",
                    anchorIndex = 0,
                    currentIndex = 2
                )
            ),
            outcome
        )
    }

    @Test
    fun `click without drag clears pinned selection on same chart`() {
        val plotPoints = buildPlotPoints(
            points = listOf(
                historyPoint("2026-05-06T18:00:00Z", 10, total = 100),
                historyPoint("2026-05-06T19:00:00Z", 20, total = 100)
            ),
            chartWidth = 160f,
            chartHeight = 100f,
            axis = null
        )
        val started = startHistoryDragSelectionSession(
            chartKey = "codex:req:24h",
            plotPoints = plotPoints,
            pointerDownX = 160f,
            existingSelection = PinnedHistorySelection(
                chartKey = "codex:req:24h",
                anchorIndex = 0,
                currentIndex = 1
            )
        )

        val outcome = resolveHistorySelectionGestureOutcome(started)

        assertEquals(HistorySelectionGestureOutcome.Clear, outcome)
    }

    @Test
    fun `drag on another chart replaces previous pinned selection`() {
        val plotPoints = buildPlotPoints(
            points = listOf(
                historyPoint("2026-05-06T18:00:00Z", 5, total = 100),
                historyPoint("2026-05-06T19:00:00Z", 15, total = 100),
                historyPoint("2026-05-06T20:00:00Z", 25, total = 100)
            ),
            chartWidth = 240f,
            chartHeight = 100f,
            axis = null
        )
        val started = startHistoryDragSelectionSession(
            chartKey = "anthropic:pct:24h",
            plotPoints = plotPoints,
            pointerDownX = 120f,
            existingSelection = PinnedHistorySelection(
                chartKey = "codex:req:24h",
                anchorIndex = 0,
                currentIndex = 1
            )
        )
        val updated = updateHistoryDragSelectionSession(
            session = started,
            plotPoints = plotPoints,
            pointerX = 240f,
            dragThresholdPx = 10f
        )

        val outcome = resolveHistorySelectionGestureOutcome(updated)

        assertEquals(
            HistorySelectionGestureOutcome.Pin(
                PinnedHistorySelection(
                    chartKey = "anthropic:pct:24h",
                    anchorIndex = 1,
                    currentIndex = 2
                )
            ),
            outcome
        )
    }

    @Test
    fun `gesture without drag on another chart keeps previous pinned selection untouched`() {
        val plotPoints = buildPlotPoints(
            points = listOf(
                historyPoint("2026-05-06T18:00:00Z", 5, total = 100),
                historyPoint("2026-05-06T19:00:00Z", 15, total = 100)
            ),
            chartWidth = 200f,
            chartHeight = 100f,
            axis = null
        )
        val started = startHistoryDragSelectionSession(
            chartKey = "anthropic:pct:24h",
            plotPoints = plotPoints,
            pointerDownX = 0f,
            existingSelection = PinnedHistorySelection(
                chartKey = "codex:req:24h",
                anchorIndex = 0,
                currentIndex = 1
            )
        )

        val outcome = resolveHistorySelectionGestureOutcome(started)

        assertEquals(HistorySelectionGestureOutcome.NoChange, outcome)
    }

    @Test
    fun `finalizeDragSelectionSession pins selection without explicit release semantics`() {
        val controller = HistoryChartSelectionController()
        val session = DragSelectionSession(
            chartKey = "codex:req:24h",
            anchorIndex = 0,
            currentIndex = 2,
            pointerDownX = 0f,
            startedWithPinnedSelection = false,
            hasDragged = true
        )

        finalizeDragSelectionSession(session, controller)

        assertEquals(
            PinnedHistorySelection(
                chartKey = "codex:req:24h",
                anchorIndex = 0,
                currentIndex = 2
            ),
            controller.selection
        )
    }

    @Test
    fun `zoomedPoints keeps only points inside the requested time window`() {
        val points = listOf(
            historyPoint("2026-05-06T18:00:00Z", 10),
            historyPoint("2026-05-06T19:00:00Z", 20),
            historyPoint("2026-05-06T20:00:00Z", 30),
            historyPoint("2026-05-06T21:00:00Z", 40)
        )

        val zoomed = zoomedPoints(points, 0.3f..0.7f)

        assertEquals(listOf(points[1], points[2]), zoomed)
    }

    @Test
    fun `zoomedPoints returns everything when range is the full window`() {
        val points = listOf(
            historyPoint("2026-05-06T18:00:00Z", 10),
            historyPoint("2026-05-06T19:00:00Z", 20)
        )

        assertEquals(points, zoomedPoints(points, 0f..1f))
    }

    @Test
    fun `zoomedPoints falls back to full list when the window matches no point`() {
        val points = listOf(
            historyPoint("2026-05-06T18:00:00Z", 10),
            historyPoint("2026-05-06T19:00:00Z", 20)
        )

        val zoomed = zoomedPoints(points, 0.2f..0.3f)

        assertEquals(points, zoomed)
    }

    @Test
    fun `applyChartScroll zooms in around the pointer fraction`() {
        val zoomed = applyChartScroll(
            current = 0f..1f,
            scrollDeltaY = -1f,
            scrollDeltaX = 0f,
            pointerFraction = 0.8f,
            shiftPressed = false
        )

        assertTrue(zoomed.endInclusive - zoomed.start < 1f)
        assertTrue(zoomed.start <= 0.8f && zoomed.endInclusive >= 0.8f)
    }

    @Test
    fun `applyChartScroll zoom out widens an existing window back toward full range`() {
        val zoomedIn = 0.4f..0.5f

        val zoomedOut = applyChartScroll(
            current = zoomedIn,
            scrollDeltaY = 1f,
            scrollDeltaX = 0f,
            pointerFraction = 0.45f,
            shiftPressed = false
        )

        assertTrue(zoomedOut.endInclusive - zoomedOut.start > zoomedIn.endInclusive - zoomedIn.start)
    }

    @Test
    fun `applyChartScroll never zooms narrower than the minimum window`() {
        val zoomed = applyChartScroll(
            current = 0.45f..0.55f,
            scrollDeltaY = -1f,
            scrollDeltaX = 0f,
            pointerFraction = 0.5f,
            shiftPressed = false,
            minWidth = 0.05f
        )

        assertTrue(zoomed.endInclusive - zoomed.start >= 0.05f)
    }

    @Test
    fun `applyChartScroll clamps to the valid range at the left edge`() {
        val zoomed = applyChartScroll(
            current = 0f..0.2f,
            scrollDeltaY = -1f,
            scrollDeltaX = 0f,
            pointerFraction = 0f,
            shiftPressed = false
        )

        assertTrue(zoomed.start >= 0f)
        assertTrue(zoomed.endInclusive <= 1f)
    }

    @Test
    fun `applyChartScroll pans without changing window width when shift is pressed`() {
        val current = 0.3f..0.5f

        val panned = applyChartScroll(
            current = current,
            scrollDeltaY = 1f,
            scrollDeltaX = 0f,
            pointerFraction = 0.4f,
            shiftPressed = true
        )

        val originalWidth = current.endInclusive - current.start
        val pannedWidth = panned.endInclusive - panned.start
        assertTrue(kotlin.math.abs(pannedWidth - originalWidth) < 0.001f)
        assertTrue(panned.start > current.start)
    }

    @Test
    fun `applyChartScroll pan clamps at the right edge`() {
        val panned = applyChartScroll(
            current = 0.8f..1f,
            scrollDeltaY = 0f,
            scrollDeltaX = 5f,
            pointerFraction = 0.9f,
            shiftPressed = false
        )

        assertEquals(1f, panned.endInclusive)
    }

    @Test
    fun `clusterResetIndices groups indices whose plot x is closer than the pixel gap`() {
        val plotPoints = listOf(
            ChartPlotPoint(index = 0, point = historyPoint("2026-05-06T18:00:00Z", 10), x = 0f, y = 0f),
            ChartPlotPoint(index = 1, point = historyPoint("2026-05-06T18:01:00Z", 10), x = 5f, y = 0f),
            ChartPlotPoint(index = 2, point = historyPoint("2026-05-06T18:02:00Z", 10), x = 9f, y = 0f),
            ChartPlotPoint(index = 3, point = historyPoint("2026-05-06T19:00:00Z", 10), x = 100f, y = 0f)
        )

        val clusters = clusterResetIndices(
            resetIndices = listOf(1, 2, 3),
            plotPoints = plotPoints,
            minPixelGap = 10f
        )

        assertEquals(
            listOf(
                ResetMarker(representativeIndex = 2, count = 2),
                ResetMarker(representativeIndex = 3, count = 1)
            ),
            clusters
        )
    }

    @Test
    fun `clusterResetIndices keeps far apart resets separate`() {
        val plotPoints = listOf(
            ChartPlotPoint(index = 0, point = historyPoint("2026-05-06T18:00:00Z", 10), x = 0f, y = 0f),
            ChartPlotPoint(index = 1, point = historyPoint("2026-05-06T19:00:00Z", 10), x = 50f, y = 0f),
            ChartPlotPoint(index = 2, point = historyPoint("2026-05-06T20:00:00Z", 10), x = 100f, y = 0f)
        )

        val clusters = clusterResetIndices(
            resetIndices = listOf(0, 1, 2),
            plotPoints = plotPoints,
            minPixelGap = 10f
        )

        assertEquals(
            listOf(
                ResetMarker(representativeIndex = 0, count = 1),
                ResetMarker(representativeIndex = 1, count = 1),
                ResetMarker(representativeIndex = 2, count = 1)
            ),
            clusters
        )
    }

    @Test
    fun `clusterResetIndices returns empty list when there are no resets`() {
        assertEquals(emptyList(), clusterResetIndices(emptyList(), emptyList(), minPixelGap = 10f))
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

    private fun percentageHistoryPoint(
        capturedAt: String,
        used: Long,
        total: Long,
        periodEndAt: String = "9999-12-31T23:59:59Z"
    ): UsageHistoryPoint {
        return UsageHistoryPoint(
            capturedAt = Instant.parse(capturedAt),
            used = used,
            total = total,
            rawUsed = 0L,
            rawTotal = 0L,
            periodEndAt = Instant.parse(periodEndAt)
        )
    }
}

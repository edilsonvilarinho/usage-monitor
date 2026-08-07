package com.usagemonitor.presentation.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.UsageHistoryPoint
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.domain.entity.cumulativePositiveDelta
import com.usagemonitor.domain.entity.isSamePeriod
import com.usagemonitor.presentation.ui.formatCumulativeConsumption
import com.usagemonitor.presentation.ui.theme.AppShapes
import kotlinx.coroutines.delay
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private const val HISTORY_TOOLTIP_PADDING_PX = 8f
private const val HISTORY_TOOLTIP_OFFSET_PX = 10f
private const val HISTORY_DRAG_SELECTION_THRESHOLD_PX = 10f
private const val HISTORY_PLOT_HORIZONTAL_INSET_PX = 14f
private val HISTORY_PLOT_HEIGHT = 120.dp
private val HISTORY_TOOLTIP_BAND_HEIGHT = 48.dp
private val HISTORY_FRAME_HEIGHT = HISTORY_PLOT_HEIGHT + HISTORY_TOOLTIP_BAND_HEIGHT
private val HISTORY_ANNOTATION_LABEL_WIDTH = 64.dp
private const val HISTORY_RESET_CLUSTER_GAP_PX = 24f
private const val HISTORY_MIN_ZOOM_WIDTH_FRACTION = 0.05f
private const val HISTORY_ZOOM_STEP_FACTOR = 0.85f
private const val HISTORY_PAN_SENSITIVITY = 0.1f

internal data class HistoryRangeAnnotations(
    val startIndex: Int,
    val endIndex: Int,
    val resetIndices: List<Int>
)

internal data class HistoryIntervalSummaryModel(
    val headline: String? = null,
    val supportingText: String,
    val metrics: List<TooltipMetric>
)

internal data class PinnedHistorySelection(
    val chartKey: String,
    val anchorIndex: Int,
    val currentIndex: Int
)

internal class HistoryChartSelectionController(
    initialSelection: PinnedHistorySelection? = null
) {
    var selection by mutableStateOf(initialSelection)
        private set

    fun pin(chartKey: String, anchorIndex: Int, currentIndex: Int) {
        selection = PinnedHistorySelection(
            chartKey = chartKey,
            anchorIndex = anchorIndex,
            currentIndex = currentIndex
        )
    }

    fun clear() {
        selection = null
    }

    fun selectionFor(chartKey: String, pointCount: Int): PinnedHistorySelection? {
        val currentSelection = selection ?: return null
        if (currentSelection.chartKey != chartKey || !currentSelection.isValidFor(pointCount)) {
            return null
        }
        return currentSelection
    }
}

internal data class DragSelectionSession(
    val chartKey: String,
    val anchorIndex: Int?,
    val currentIndex: Int?,
    val pointerDownX: Float,
    val startedWithPinnedSelection: Boolean,
    val hasDragged: Boolean = false
)

internal sealed interface HistorySelectionGestureOutcome {
    data object NoChange : HistorySelectionGestureOutcome
    data object Clear : HistorySelectionGestureOutcome
    data class Pin(val selection: PinnedHistorySelection) : HistorySelectionGestureOutcome
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun UsageHistoryLineChart(
    points: List<UsageHistoryPoint>,
    unit: UsageUnit,
    language: AppLanguage,
    chartSelectionKey: String,
    selectionController: HistoryChartSelectionController? = null,
    modifier: Modifier = Modifier,
    tooltipTitle: String? = null,
    tooltipSubtitle: String? = null
) {
    val lineColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.88f)
    val fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val axisTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val chartIndicatorColor = MaterialTheme.colorScheme.primary
    val chartIndicatorHaloColor = MaterialTheme.colorScheme.surface
    val renderPoints = filteredPoints(points, unit)

    var revealed by remember { mutableStateOf(false) }
    var frameSize by remember(renderPoints) { mutableStateOf(IntSize.Zero) }
    var plotSize by remember(renderPoints) { mutableStateOf(IntSize.Zero) }
    var tooltipSize by remember(renderPoints) { mutableStateOf(IntSize.Zero) }
    var hoveredIndex by remember(renderPoints, chartSelectionKey) { mutableStateOf<Int?>(null) }
    var dragSession by remember(renderPoints, chartSelectionKey) { mutableStateOf<DragSelectionSession?>(null) }
    var zoomRange by remember(renderPoints, chartSelectionKey) { mutableStateOf(0f..1f) }
    val density = LocalDensity.current

    val revealFraction by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "lineReveal"
    )

    val windowedPoints = remember(renderPoints, zoomRange) {
        zoomedPoints(renderPoints, zoomRange)
    }

    LaunchedEffect(renderPoints, chartSelectionKey) {
        revealed = false
        hoveredIndex = null
        dragSession = null
        tooltipSize = IntSize.Zero
        delay(40)
        revealed = true
    }

    LaunchedEffect(zoomRange, chartSelectionKey) {
        hoveredIndex = null
        dragSession = null
        val currentSelection = selectionController?.selection
        if (currentSelection != null &&
            currentSelection.chartKey == chartSelectionKey &&
            !currentSelection.isValidFor(windowedPoints.size)
        ) {
            selectionController.clear()
        }
    }

    val timeLabels = buildTimeReferenceLabels(windowedPoints)
    val valueAxis = buildValueAxis(windowedPoints, unit)
    val valueLabels = buildValueLabels(valueAxis, unit)
    val plotInset = remember(plotSize) {
        resolvePlotHorizontalInset(plotSize.width.toFloat())
    }
    val plotPoints = remember(windowedPoints, valueAxis, plotSize) {
        buildPlotPoints(
            points = windowedPoints,
            chartWidth = plotSize.width.toFloat(),
            chartHeight = plotSize.height.toFloat(),
            axis = valueAxis,
            horizontalInsetPx = plotInset
        )
    }
    val pinnedSelection = selectionController?.selectionFor(chartSelectionKey, windowedPoints.size)
    val rangeAnnotations = remember(windowedPoints, unit) {
        detectHistoryRangeAnnotations(windowedPoints, unit)
    }
    val resetClusterPoints = remember(rangeAnnotations, plotPoints) {
        clusterResetIndices(
            resetIndices = rangeAnnotations?.resetIndices.orEmpty(),
            plotPoints = plotPoints,
            minPixelGap = HISTORY_RESET_CLUSTER_GAP_PX
        ).mapNotNull { marker ->
            plotPoints.getOrNull(marker.representativeIndex)?.let { point -> marker to point }
        }
    }
    val activeIndex = pinnedSelection?.currentIndex ?: hoveredIndex
    val activePoint = activeIndex?.let { index -> plotPoints.getOrNull(index) }
    val comparisonPoint = pinnedSelection?.anchorIndex?.let(windowedPoints::getOrNull)
    val tooltipModel = remember(
        activePoint,
        comparisonPoint,
        windowedPoints,
        unit,
        language,
        tooltipTitle,
        tooltipSubtitle
    ) {
        buildHistoryTooltipModel(
            activePoint = activePoint,
            points = windowedPoints,
            unit = unit,
            language = language,
            title = tooltipTitle,
            subtitle = tooltipSubtitle,
            comparisonPoint = comparisonPoint
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(HISTORY_FRAME_HEIGHT)
                .onSizeChanged { frameSize = it }
        ) {
            if (zoomRange != 0f..1f) {
                TextButton(
                    onClick = { zoomRange = 0f..1f },
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Text(
                        text = if (language == AppLanguage.PT) "Ver tudo" else "View all",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            if (valueLabels.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .height(HISTORY_PLOT_HEIGHT),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    valueLabels.forEach { label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = axisTextColor
                        )
                    }
                }
            }

            val startPadding = if (valueLabels.isNotEmpty()) 48.dp else 0.dp
            val startPaddingPx = with(density) { startPadding.toPx() }
            val plotTop = (frameSize.height - plotSize.height).coerceAtLeast(0)

            Box(
                modifier = Modifier
                    .padding(start = startPadding)
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(HISTORY_PLOT_HEIGHT)
                    .onSizeChanged { plotSize = it }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 1.75.dp.toPx()
                    val gridStroke = 1.dp.toPx()
                    val activeMarkerRadius = 4.dp.toPx()
                    val activeMarkerHaloRadius = 7.dp.toPx()
                    val anchorMarkerRadius = 3.dp.toPx()
                    val anchorMarkerHaloRadius = 6.dp.toPx()
                    val anchorPoint = pinnedSelection
                        ?.anchorIndex
                        ?.let { anchorIndex -> plotPoints.getOrNull(anchorIndex) }
                    val resetPathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))
                    val rangeStartPoint = rangeAnnotations?.startIndex?.let(plotPoints::getOrNull)
                    val rangeEndPoint = rangeAnnotations?.endIndex?.let(plotPoints::getOrNull)

                    drawLine(
                        color = gridColor,
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = gridStroke
                    )
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, size.height * 0.5f),
                        end = Offset(size.width, size.height * 0.5f),
                        strokeWidth = gridStroke
                    )
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, size.height),
                        end = Offset(size.width, size.height),
                        strokeWidth = gridStroke
                    )

                    if (anchorPoint != null && activePoint != null && anchorPoint.index != activePoint.index) {
                        val bandLeft = minOf(anchorPoint.x, activePoint.x)
                        val bandRight = maxOf(anchorPoint.x, activePoint.x)
                        drawRect(
                            color = chartIndicatorColor.copy(alpha = 0.10f),
                            topLeft = Offset(bandLeft, 0f),
                            size = Size(bandRight - bandLeft, size.height)
                        )
                    }

                    resetClusterPoints.forEach { (_, resetPoint) ->
                        drawLine(
                            color = chartIndicatorColor.copy(alpha = 0.4f),
                            start = Offset(resetPoint.x, 0f),
                            end = Offset(resetPoint.x, size.height),
                            strokeWidth = gridStroke * 1.5f,
                            pathEffect = resetPathEffect
                        )
                    }

                    if (plotPoints.size > 1) {
                        val path = Path()
                        val fillPath = Path()

                        plotPoints.forEachIndexed { index, point ->
                            if (index == 0) {
                                path.moveTo(point.x, point.y)
                                fillPath.moveTo(point.x, size.height)
                                fillPath.lineTo(point.x, point.y)
                            } else {
                                path.lineTo(point.x, point.y)
                                fillPath.lineTo(point.x, point.y)
                            }
                        }

                        val lastPoint = plotPoints.last()
                        fillPath.lineTo(lastPoint.x, size.height)
                        fillPath.close()

                        clipRect(right = size.width * revealFraction) {
                            if (unit == UsageUnit.CURRENCY_USD) {
                                drawPath(path = fillPath, color = fillColor)
                            }
                            drawPath(
                                path = path,
                                color = lineColor,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                        }
                    }

                    if (rangeStartPoint != null &&
                        rangeStartPoint.index != activePoint?.index &&
                        rangeStartPoint.index != anchorPoint?.index
                    ) {
                        drawCircle(
                            color = chartIndicatorHaloColor.copy(alpha = 0.95f),
                            radius = activeMarkerHaloRadius,
                            center = Offset(rangeStartPoint.x, rangeStartPoint.y)
                        )
                        drawCircle(
                            color = lineColor.copy(alpha = 0.9f),
                            radius = activeMarkerRadius,
                            center = Offset(rangeStartPoint.x, rangeStartPoint.y),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }

                    if (rangeEndPoint != null &&
                        rangeEndPoint.index != activePoint?.index &&
                        rangeEndPoint.index != anchorPoint?.index
                    ) {
                        drawCircle(
                            color = chartIndicatorHaloColor.copy(alpha = 0.95f),
                            radius = activeMarkerHaloRadius,
                            center = Offset(rangeEndPoint.x, rangeEndPoint.y)
                        )
                        drawCircle(
                            color = lineColor.copy(alpha = 0.92f),
                            radius = activeMarkerRadius,
                            center = Offset(rangeEndPoint.x, rangeEndPoint.y)
                        )
                    }

                    if (activePoint != null) {
                        drawLine(
                            color = chartIndicatorColor.copy(alpha = 0.18f),
                            start = Offset(activePoint.x, 0f),
                            end = Offset(activePoint.x, size.height),
                            strokeWidth = gridStroke
                        )
                        drawCircle(
                            color = chartIndicatorHaloColor,
                            radius = activeMarkerHaloRadius,
                            center = Offset(activePoint.x, activePoint.y)
                        )
                        drawCircle(
                            color = chartIndicatorColor,
                            radius = activeMarkerRadius,
                            center = Offset(activePoint.x, activePoint.y)
                        )
                    }

                    if (anchorPoint != null && anchorPoint.index != activePoint?.index) {
                        drawLine(
                            color = chartIndicatorColor.copy(alpha = 0.12f),
                            start = Offset(anchorPoint.x, 0f),
                            end = Offset(anchorPoint.x, size.height),
                            strokeWidth = gridStroke
                        )
                        drawCircle(
                            color = chartIndicatorHaloColor.copy(alpha = 0.9f),
                            radius = anchorMarkerHaloRadius,
                            center = Offset(anchorPoint.x, anchorPoint.y)
                        )
                        drawCircle(
                            color = chartIndicatorColor.copy(alpha = 0.72f),
                            radius = anchorMarkerRadius,
                            center = Offset(anchorPoint.x, anchorPoint.y)
                        )
                    }
                }

                HistoryRangeAnnotationLabels(
                    startPoint = rangeAnnotations?.startIndex?.let(plotPoints::getOrNull),
                    endPoint = rangeAnnotations?.endIndex?.let(plotPoints::getOrNull),
                    resetClusters = resetClusterPoints,
                    plotWidth = plotSize.width.toFloat(),
                    plotInset = plotInset,
                    language = language
                )
            }

            if (activePoint != null && tooltipModel != null) {
                val tooltipLeft = clampTooltipLeft(
                    desiredCenterX = activePoint.x,
                    tooltipWidth = tooltipSize.width.toFloat(),
                    containerWidth = plotSize.width.toFloat(),
                    horizontalPadding = plotInset
                )
                val tooltipTop = buildHistoryTooltipTop(
                    pointY = activePoint.y + plotTop,
                    tooltipHeight = tooltipSize.height.toFloat(),
                    frameHeight = frameSize.height.toFloat()
                )

                Box(
                    modifier = Modifier
                        .padding(start = startPadding)
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    x = tooltipLeft.roundToInt(),
                                    y = tooltipTop.roundToInt()
                                )
                            }
                            .onSizeChanged { tooltipSize = it }
                    ) {
                        HistoryTooltipBubble(
                            model = tooltipModel
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onPointerEvent(PointerEventType.Scroll) { event ->
                        val change = event.changes.firstOrNull() ?: return@onPointerEvent
                        val plotPointerX = coerceFramePointerToPlotPointerX(
                            framePointerX = change.position.x,
                            plotStartX = startPaddingPx,
                            plotWidth = plotSize.width.toFloat()
                        ) ?: return@onPointerEvent
                        val pointerIndex = findClosestPlotPointIndex(plotPoints, plotPointerX)
                        val pointerFraction = pointerIndex
                            ?.let { index -> buildTimelineFractions(windowedPoints).getOrNull(index) }
                            ?: 0.5f
                        zoomRange = applyChartScroll(
                            current = zoomRange,
                            scrollDeltaY = change.scrollDelta.y,
                            scrollDeltaX = change.scrollDelta.x,
                            pointerFraction = pointerFraction,
                            shiftPressed = event.keyboardModifiers.isShiftPressed
                        )
                        change.consume()
                    }
                    .onPointerEvent(PointerEventType.Enter) { event ->
                        if (pinnedSelection != null || dragSession != null) {
                            return@onPointerEvent
                        }

                        val framePointerX = event.changes.firstOrNull()?.position?.x ?: return@onPointerEvent
                        val plotPointerX = framePointerToPlotPointerX(
                            framePointerX = framePointerX,
                            plotStartX = startPaddingPx,
                            plotWidth = plotSize.width.toFloat()
                        ) ?: return@onPointerEvent
                        hoveredIndex = findClosestPlotPointIndex(plotPoints, plotPointerX)
                    }
                    .onPointerEvent(PointerEventType.Press) { event ->
                        val framePointerX = event.changes.firstOrNull()?.position?.x ?: return@onPointerEvent
                        val plotPointerX = framePointerToPlotPointerX(
                            framePointerX = framePointerX,
                            plotStartX = startPaddingPx,
                            plotWidth = plotSize.width.toFloat()
                        ) ?: return@onPointerEvent

                        dragSession = startHistoryDragSelectionSession(
                            chartKey = chartSelectionKey,
                            plotPoints = plotPoints,
                            pointerDownX = plotPointerX,
                            existingSelection = selectionController?.selection
                        )
                    }
                    .onPointerEvent(PointerEventType.Move) { event ->
                        val primaryPressed = event.buttons.isPrimaryPressed ||
                            event.changes.any { change -> change.pressed }
                        val change = event.changes.firstOrNull() ?: return@onPointerEvent
                        val plotPointerX = coerceFramePointerToPlotPointerX(
                            framePointerX = change.position.x,
                            plotStartX = startPaddingPx,
                            plotWidth = plotSize.width.toFloat()
                        )

                        if (primaryPressed) {
                            val activePlotPointerX = plotPointerX ?: return@onPointerEvent
                            val session = dragSession ?: startHistoryDragSelectionSession(
                                chartKey = chartSelectionKey,
                                plotPoints = plotPoints,
                                pointerDownX = coerceFramePointerToPlotPointerX(
                                    framePointerX = change.previousPosition.x,
                                    plotStartX = startPaddingPx,
                                    plotWidth = plotSize.width.toFloat()
                                ) ?: activePlotPointerX,
                                existingSelection = selectionController?.selection
                            )
                            val updatedSession = updateHistoryDragSelectionSession(
                                session = session,
                                plotPoints = plotPoints,
                                pointerX = activePlotPointerX,
                                dragThresholdPx = HISTORY_DRAG_SELECTION_THRESHOLD_PX
                            )
                            dragSession = updatedSession
                            if (updatedSession.hasDragged &&
                                updatedSession.anchorIndex != null &&
                                updatedSession.currentIndex != null
                            ) {
                                hoveredIndex = null
                                selectionController?.pin(
                                    chartKey = chartSelectionKey,
                                    anchorIndex = updatedSession.anchorIndex,
                                    currentIndex = updatedSession.currentIndex
                                )
                            }
                            return@onPointerEvent
                        }

                        finalizeDragSelectionSession(
                            session = dragSession,
                            selectionController = selectionController,
                            onCleared = { hoveredIndex = null }
                        )
                        dragSession = null

                        if (selectionController?.selectionFor(chartSelectionKey, renderPoints.size) != null) {
                            return@onPointerEvent
                        }

                        if (plotPointerX == null) {
                            hoveredIndex = null
                            return@onPointerEvent
                        }

                        hoveredIndex = findClosestPlotPointIndex(plotPoints, plotPointerX)
                    }
                    .onPointerEvent(PointerEventType.Release) {
                        finalizeDragSelectionSession(
                            session = dragSession,
                            selectionController = selectionController,
                            onCleared = { hoveredIndex = null }
                        )
                        dragSession = null
                    }
                    .onPointerEvent(PointerEventType.Exit) {
                        finalizeDragSelectionSession(
                            session = dragSession,
                            selectionController = selectionController,
                            onCleared = { hoveredIndex = null }
                        )
                        dragSession = null
                        if (selectionController?.selectionFor(chartSelectionKey, renderPoints.size) == null) {
                            hoveredIndex = null
                        }
                    }
            )
        }

        if (timeLabels.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = if (valueLabels.isNotEmpty()) 48.dp else 0.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                timeLabels.forEachIndexed { index, label ->
                    Text(
                        modifier = Modifier.weight(1f),
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = axisTextColor,
                        textAlign = when (index) {
                            0 -> TextAlign.Start
                            1 -> TextAlign.Center
                            else -> TextAlign.End
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryRangeAnnotationLabels(
    startPoint: ChartPlotPoint?,
    endPoint: ChartPlotPoint?,
    resetClusters: List<Pair<ResetMarker, ChartPlotPoint>>,
    plotWidth: Float,
    plotInset: Float,
    language: AppLanguage
) {
    val density = LocalDensity.current
    val labelWidthPx = with(density) { HISTORY_ANNOTATION_LABEL_WIDTH.toPx() }
    val showStartLabel = startPoint != null &&
        (endPoint == null || abs(endPoint.x - startPoint.x) >= labelWidthPx)

    Box(modifier = Modifier.fillMaxSize()) {
        if (showStartLabel) {
            val point = startPoint!!
            HistoryAnnotationLabel(
                text = if (language == AppLanguage.PT) "Início" else "Start",
                left = clampTooltipLeft(
                    desiredCenterX = point.x,
                    tooltipWidth = labelWidthPx,
                    containerWidth = plotWidth,
                    horizontalPadding = plotInset
                )
            )
        }

        endPoint?.let { point ->
            HistoryAnnotationLabel(
                text = if (language == AppLanguage.PT) "Agora" else "Now",
                left = clampTooltipLeft(
                    desiredCenterX = point.x,
                    tooltipWidth = labelWidthPx,
                    containerWidth = plotWidth,
                    horizontalPadding = plotInset
                )
            )
        }

        val occupiedX = mutableListOf<Float>()
        if (showStartLabel) {
            startPoint?.let { occupiedX += it.x }
        }
        endPoint?.let { occupiedX += it.x }

        resetClusters.forEach { (marker, point) ->
            val collides = occupiedX.any { x -> abs(point.x - x) < labelWidthPx }
            if (!collides) {
                HistoryAnnotationLabel(
                    text = resetLabelText(marker.count),
                    left = clampTooltipLeft(
                        desiredCenterX = point.x,
                        tooltipWidth = labelWidthPx,
                        containerWidth = plotWidth,
                        horizontalPadding = plotInset
                    ),
                    topPadding = 22.dp
                )
                occupiedX += point.x
            }
        }
    }
}

private fun resetLabelText(count: Int): String {
    return if (count > 1) "Reset ×$count" else "Reset"
}

@Composable
private fun HistoryAnnotationLabel(
    text: String,
    left: Float,
    topPadding: androidx.compose.ui.unit.Dp = 4.dp
) {
    Surface(
        modifier = Modifier
            .width(HISTORY_ANNOTATION_LABEL_WIDTH)
            .offset {
                IntOffset(
                    x = left.roundToInt(),
                    y = topPadding.roundToPx()
                )
            },
        shape = AppShapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
        tonalElevation = 1.dp
    ) {
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 3.dp),
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun HistoryTooltipBubble(
    model: HistoryTooltipModel,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.widthIn(max = 230.dp),
        shape = AppShapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 10.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            model.title?.takeIf { it.isNotBlank() }?.let { title ->
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                text = model.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            model.metrics.forEach { metric ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = metric.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = metric.value,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.End
                    )
                }
            }
        }
    }
}

internal data class ValueAxis(
    val min: Float,
    val max: Float
)

internal data class ChartPlotPoint(
    val index: Int,
    val point: UsageHistoryPoint,
    val x: Float,
    val y: Float
)

internal data class ResetMarker(
    val representativeIndex: Int,
    val count: Int
)

internal data class HistoryTooltipModel(
    val title: String?,
    val subtitle: String,
    val metrics: List<TooltipMetric>
)

internal fun filteredPoints(points: List<UsageHistoryPoint>, unit: UsageUnit): List<UsageHistoryPoint> {
    return if (unit == UsageUnit.CURRENCY_USD) {
        points.filter { it.displayUsed > 0L }
    } else {
        points
    }
}

internal fun buildTimelineFractions(points: List<UsageHistoryPoint>): List<Float> {
    if (points.isEmpty()) {
        return emptyList()
    }
    if (points.size == 1) {
        return listOf(0f)
    }

    val start = points.first().capturedAt.toEpochMilliseconds()
    val end = points.last().capturedAt.toEpochMilliseconds()
    if (end <= start) {
        val maxIndex = max(points.lastIndex, 1)
        return points.indices.map { index -> index.toFloat() / maxIndex.toFloat() }
    }

    val total = (end - start).toFloat()
    return points.map { point ->
        ((point.capturedAt.toEpochMilliseconds() - start) / total).coerceIn(0f, 1f)
    }
}

internal fun buildValueAxis(points: List<UsageHistoryPoint>, unit: UsageUnit): ValueAxis? {
    val values = points.map { it.displayUsed }
    if (values.isEmpty()) {
        return null
    }

    return when (unit) {
        UsageUnit.CURRENCY_USD -> buildAbsoluteAxis(values, minimumDisplayRange = 100f)
        UsageUnit.REQUESTS -> buildAbsoluteAxis(values, minimumDisplayRange = 10f)
        else -> null
    }
}

internal fun buildPlotPoints(
    points: List<UsageHistoryPoint>,
    chartWidth: Float,
    chartHeight: Float,
    axis: ValueAxis?,
    horizontalInsetPx: Float = HISTORY_PLOT_HORIZONTAL_INSET_PX
): List<ChartPlotPoint> {
    if (points.isEmpty() || chartWidth <= 0f || chartHeight <= 0f) {
        return emptyList()
    }

    val plotValues = if (axis != null) {
        val range = (axis.max - axis.min).coerceAtLeast(1f)
        points.map { point ->
            ((point.displayUsed.toFloat() - axis.min) / range).coerceIn(0f, 1f)
        }
    } else {
        points.map { it.normalizedUsage }
    }
    val xFractions = buildTimelineFractions(points)
    val inset = resolvePlotHorizontalInset(chartWidth, horizontalInsetPx)
    val usableWidth = (chartWidth - inset * 2f).coerceAtLeast(0f)

    return points.mapIndexed { index, point ->
        val xFraction = if (points.size == 1) 0.5f else xFractions[index]
        val y = chartHeight - (plotValues[index] * chartHeight)
        ChartPlotPoint(
            index = index,
            point = point,
            x = if (points.size == 1) {
                chartWidth * xFraction
            } else {
                inset + (usableWidth * xFraction)
            },
            y = y
        )
    }
}

internal fun resolvePlotHorizontalInset(
    chartWidth: Float,
    preferredInsetPx: Float = HISTORY_PLOT_HORIZONTAL_INSET_PX
): Float {
    if (chartWidth <= 0f) {
        return 0f
    }

    return preferredInsetPx.coerceAtMost((chartWidth / 2f) - 1f).coerceAtLeast(0f)
}

internal fun findClosestPlotPointIndex(plotPoints: List<ChartPlotPoint>, pointerX: Float): Int? {
    if (plotPoints.isEmpty()) {
        return null
    }

    var closestPoint = plotPoints.first()
    var bestDistance = abs(pointerX - closestPoint.x)

    for (point in plotPoints.drop(1)) {
        val distance = abs(pointerX - point.x)
        if (distance <= bestDistance) {
            closestPoint = point
            bestDistance = distance
        }
    }

    return closestPoint.index
}

internal fun clampTooltipLeft(
    desiredCenterX: Float,
    tooltipWidth: Float,
    containerWidth: Float,
    horizontalPadding: Float = HISTORY_TOOLTIP_PADDING_PX
): Float {
    if (containerWidth <= 0f) {
        return horizontalPadding
    }

    val unclampedLeft = desiredCenterX - (tooltipWidth / 2f)
    val minLeft = horizontalPadding
    val maxLeft = (containerWidth - tooltipWidth - horizontalPadding).coerceAtLeast(minLeft)
    return unclampedLeft.coerceIn(minLeft, maxLeft)
}

internal fun framePointerToPlotPointerX(
    framePointerX: Float,
    plotStartX: Float,
    plotWidth: Float
): Float? {
    if (plotWidth <= 0f) {
        return null
    }

    val translatedPointerX = framePointerX - plotStartX
    if (translatedPointerX < 0f || translatedPointerX > plotWidth) {
        return null
    }

    return translatedPointerX
}

internal fun coerceFramePointerToPlotPointerX(
    framePointerX: Float,
    plotStartX: Float,
    plotWidth: Float
): Float? {
    if (plotWidth <= 0f) {
        return null
    }

    return (framePointerX - plotStartX).coerceIn(0f, plotWidth)
}

internal fun startHistoryDragSelectionSession(
    chartKey: String,
    plotPoints: List<ChartPlotPoint>,
    pointerDownX: Float,
    existingSelection: PinnedHistorySelection?
): DragSelectionSession {
    val anchorIndex = findClosestPlotPointIndex(plotPoints, pointerDownX)
    return DragSelectionSession(
        chartKey = chartKey,
        anchorIndex = anchorIndex,
        currentIndex = anchorIndex,
        pointerDownX = pointerDownX,
        startedWithPinnedSelection = existingSelection?.chartKey == chartKey
    )
}

internal fun updateHistoryDragSelectionSession(
    session: DragSelectionSession,
    plotPoints: List<ChartPlotPoint>,
    pointerX: Float,
    dragThresholdPx: Float = HISTORY_DRAG_SELECTION_THRESHOLD_PX
): DragSelectionSession {
    val currentIndex = findClosestPlotPointIndex(plotPoints, pointerX) ?: session.currentIndex
    val hasDragged = session.hasDragged || abs(pointerX - session.pointerDownX) >= dragThresholdPx
    return session.copy(
        currentIndex = currentIndex,
        hasDragged = hasDragged
    )
}

internal fun finalizeDragSelectionSession(
    session: DragSelectionSession?,
    selectionController: HistoryChartSelectionController?,
    onCleared: () -> Unit = {}
) {
    val activeSession = session ?: return
    when (val outcome = resolveHistorySelectionGestureOutcome(activeSession)) {
        HistorySelectionGestureOutcome.Clear -> {
            selectionController?.clear()
            onCleared()
        }

        HistorySelectionGestureOutcome.NoChange -> Unit

        is HistorySelectionGestureOutcome.Pin -> {
            selectionController?.pin(
                chartKey = outcome.selection.chartKey,
                anchorIndex = outcome.selection.anchorIndex,
                currentIndex = outcome.selection.currentIndex
            )
            onCleared()
        }
    }
}

internal fun resolveHistorySelectionGestureOutcome(
    session: DragSelectionSession
): HistorySelectionGestureOutcome {
    if (session.hasDragged && session.anchorIndex != null && session.currentIndex != null) {
        return HistorySelectionGestureOutcome.Pin(
            PinnedHistorySelection(
                chartKey = session.chartKey,
                anchorIndex = session.anchorIndex,
                currentIndex = session.currentIndex
            )
        )
    }

    if (session.startedWithPinnedSelection) {
        return HistorySelectionGestureOutcome.Clear
    }

    return HistorySelectionGestureOutcome.NoChange
}

internal fun buildHistoryTooltipModel(
    activePoint: ChartPlotPoint?,
    points: List<UsageHistoryPoint>,
    unit: UsageUnit,
    language: AppLanguage,
    title: String?,
    subtitle: String?,
    comparisonPoint: UsageHistoryPoint? = null
): HistoryTooltipModel? {
    if (activePoint == null) {
        return null
    }

    val point = activePoint.point
    val fallbackComparisonPoint = points.getOrNull(activePoint.index - 1)
    val timestamp = formatTooltipTimestamp(point.capturedAt)
    val contextualSubtitle = if (subtitle.isNullOrBlank()) {
        timestamp
    } else {
        "$subtitle · $timestamp"
    }

    return HistoryTooltipModel(
        title = title,
        subtitle = contextualSubtitle,
        metrics = buildHistoryTooltipMetrics(
            point = point,
            comparisonPoint = comparisonPoint ?: fallbackComparisonPoint,
            unit = unit,
            language = language
        )
    )
}

internal fun buildHistoryTooltipMetrics(
    point: UsageHistoryPoint,
    comparisonPoint: UsageHistoryPoint?,
    unit: UsageUnit,
    language: AppLanguage
): List<TooltipMetric> {
    val usageLabel = when (unit) {
        UsageUnit.CURRENCY_USD -> if (language == AppLanguage.PT) "Saldo" else "Balance"
        else -> if (language == AppLanguage.PT) "Uso" else "Usage"
    }
    val changeLabel = if (language == AppLanguage.PT) "Variação" else "Change"
    val windowLabel = if (language == AppLanguage.PT) "Janela" else "Window"

    return buildList {
        add(
            TooltipMetric(
                label = usageLabel,
                value = formatTooltipUsageValue(point = point, unit = unit, language = language)
            )
        )
        add(
            TooltipMetric(
                label = changeLabel,
                value = formatTooltipDeltaValue(
                    point = point,
                    comparisonPoint = comparisonPoint,
                    unit = unit,
                    language = language
                )
            )
        )
        add(
            TooltipMetric(
                label = windowLabel,
                value = formatTooltipWindowValue(
                    instant = point.periodEndAt,
                    unit = unit,
                    language = language
                )
            )
        )
    }
}

internal fun buildTimeReferenceLabels(points: List<UsageHistoryPoint>): List<String> {
    if (points.isEmpty()) {
        return emptyList()
    }

    val middlePoint = points[points.lastIndex / 2]
    return listOf(
        formatTimeReference(points.first().capturedAt, points.first().capturedAt, points.last().capturedAt),
        formatTimeReference(middlePoint.capturedAt, points.first().capturedAt, points.last().capturedAt),
        formatTimeReference(points.last().capturedAt, points.first().capturedAt, points.last().capturedAt)
    )
}

internal fun detectHistoryRangeAnnotations(
    points: List<UsageHistoryPoint>,
    unit: UsageUnit
): HistoryRangeAnnotations? {
    if (points.isEmpty()) {
        return null
    }

    val resetIndices = mutableListOf<Int>()
    for (index in 1 until points.size) {
        val previous = points[index - 1]
        val current = points[index]
        val periodChanged = !isSamePeriod(current.periodEndAt, previous.periodEndAt)
        val usageDropped = unit != UsageUnit.CURRENCY_USD && current.displayUsed < previous.displayUsed
        if (periodChanged || usageDropped) {
            resetIndices += index
        }
    }

    return HistoryRangeAnnotations(
        startIndex = 0,
        endIndex = points.lastIndex,
        resetIndices = resetIndices
    )
}

internal fun clusterResetIndices(
    resetIndices: List<Int>,
    plotPoints: List<ChartPlotPoint>,
    minPixelGap: Float
): List<ResetMarker> {
    if (resetIndices.isEmpty()) {
        return emptyList()
    }

    val sortedIndices = resetIndices.sorted()
    val clusters = mutableListOf<MutableList<Int>>()

    for (index in sortedIndices) {
        val point = plotPoints.getOrNull(index) ?: continue
        val lastCluster = clusters.lastOrNull()
        val lastPoint = lastCluster?.lastOrNull()?.let(plotPoints::getOrNull)
        if (lastCluster != null && lastPoint != null && abs(point.x - lastPoint.x) < minPixelGap) {
            lastCluster += index
        } else {
            clusters += mutableListOf(index)
        }
    }

    return clusters.map { cluster ->
        ResetMarker(representativeIndex = cluster.last(), count = cluster.size)
    }
}

internal fun zoomedPoints(
    points: List<UsageHistoryPoint>,
    zoomRange: ClosedFloatingPointRange<Float>
): List<UsageHistoryPoint> {
    if (points.isEmpty() || zoomRange == 0f..1f) {
        return points
    }

    val fractions = buildTimelineFractions(points)
    val filtered = points.filterIndexed { index, _ ->
        fractions[index] >= zoomRange.start && fractions[index] <= zoomRange.endInclusive
    }
    return filtered.ifEmpty { points }
}

internal fun applyChartScroll(
    current: ClosedFloatingPointRange<Float>,
    scrollDeltaY: Float,
    scrollDeltaX: Float,
    pointerFraction: Float,
    shiftPressed: Boolean,
    minWidth: Float = HISTORY_MIN_ZOOM_WIDTH_FRACTION
): ClosedFloatingPointRange<Float> {
    val width = current.endInclusive - current.start
    val effectivePanDelta = if (shiftPressed) scrollDeltaY else scrollDeltaX
    val isPan = shiftPressed || abs(scrollDeltaX) > abs(scrollDeltaY)

    if (isPan) {
        if (effectivePanDelta == 0f) {
            return current
        }
        val panDelta = effectivePanDelta * width * HISTORY_PAN_SENSITIVITY
        var newStart = current.start + panDelta
        var newEnd = current.endInclusive + panDelta
        if (newStart < 0f) {
            newEnd -= newStart
            newStart = 0f
        }
        if (newEnd > 1f) {
            newStart -= (newEnd - 1f)
            newEnd = 1f
        }
        return newStart.coerceAtLeast(0f)..newEnd.coerceAtMost(1f)
    }

    if (scrollDeltaY == 0f) {
        return current
    }

    val zoomingIn = scrollDeltaY < 0f
    val zoomFactor = if (zoomingIn) HISTORY_ZOOM_STEP_FACTOR else 1f / HISTORY_ZOOM_STEP_FACTOR
    val newWidth = (width * zoomFactor).coerceIn(minWidth, 1f)
    val anchor = pointerFraction.coerceIn(0f, 1f)
    var newStart = anchor - (anchor - current.start) * (newWidth / width)
    var newEnd = newStart + newWidth
    if (newStart < 0f) {
        newEnd -= newStart
        newStart = 0f
    }
    if (newEnd > 1f) {
        newStart -= (newEnd - 1f)
        newEnd = 1f
    }
    return newStart.coerceAtLeast(0f)..newEnd.coerceAtMost(1f)
}

internal fun buildHistoryIntervalSummaryModel(
    points: List<UsageHistoryPoint>,
    unit: UsageUnit,
    language: AppLanguage,
    selection: PinnedHistorySelection? = null
): HistoryIntervalSummaryModel? {
    if (points.isEmpty()) {
        return null
    }

    if (selection != null && selection.isValidFor(points.size)) {
        val comparisonHeadline = buildHistoryComparisonHeadline(selection, points, language)
        val startIndex = minOf(selection.anchorIndex, selection.currentIndex)
        val endIndex = maxOf(selection.anchorIndex, selection.currentIndex)
        val rangeSlice = points.subList(startIndex, endIndex + 1)
        val cumulativeDelta = cumulativePositiveDelta(rangeSlice, unit)
        return HistoryIntervalSummaryModel(
            headline = comparisonHeadline,
            supportingText = if (language == AppLanguage.PT) {
                "Faixa selecionada no gráfico."
            } else {
                "Range selected on the chart."
            },
            metrics = listOf(
                TooltipMetric(
                    label = if (language == AppLanguage.PT) "Início da faixa" else "Range start",
                    value = formatTooltipUsageValue(rangeSlice.first(), unit, language)
                ),
                TooltipMetric(
                    label = if (language == AppLanguage.PT) "Fim da faixa" else "Range end",
                    value = formatTooltipUsageValue(rangeSlice.last(), unit, language)
                ),
                TooltipMetric(
                    label = if (language == AppLanguage.PT) "Consumido na faixa" else "Consumed in range",
                    value = formatCumulativeConsumption(
                        delta = cumulativeDelta.toDouble(),
                        total = points.last().displayTotal,
                        unit = unit,
                        language = language
                    )
                )
            )
        )
    }

    val firstPoint = points.first()
    val lastPoint = points.last()
    return HistoryIntervalSummaryModel(
        supportingText = if (language == AppLanguage.PT) {
            "Arraste no gráfico para comparar dois pontos."
        } else {
            "Drag across the chart to compare two points."
        },
        metrics = listOf(
            TooltipMetric(
                label = if (language == AppLanguage.PT) "Início do recorte" else "Range start",
                value = formatTooltipUsageValue(firstPoint, unit, language)
            ),
            TooltipMetric(
                label = if (language == AppLanguage.PT) "Atual" else "Current",
                value = formatTooltipUsageValue(lastPoint, unit, language)
            ),
            TooltipMetric(
                label = if (language == AppLanguage.PT) "Variação no recorte" else "Change in range",
                value = formatTooltipDeltaValue(
                    point = lastPoint,
                    comparisonPoint = firstPoint,
                    unit = unit,
                    language = language
                )
            )
        )
    )
}

private fun PinnedHistorySelection.isValidFor(pointCount: Int): Boolean {
    return anchorIndex in 0 until pointCount && currentIndex in 0 until pointCount
}

private fun buildHistoryComparisonHeadline(
    selection: PinnedHistorySelection,
    points: List<UsageHistoryPoint>,
    language: AppLanguage
): String {
    val anchorPoint = points[selection.anchorIndex]
    val currentPoint = points[selection.currentIndex]
    val startReference = formatTimeReference(anchorPoint.capturedAt, points.first().capturedAt, points.last().capturedAt)
    val endReference = formatTimeReference(currentPoint.capturedAt, points.first().capturedAt, points.last().capturedAt)
    return if (language == AppLanguage.PT) {
        "Comparando $startReference -> $endReference"
    } else {
        "Comparing $startReference -> $endReference"
    }
}

private fun buildAbsoluteAxis(values: List<Long>, minimumDisplayRange: Float): ValueAxis? {
    val minValue = values.minOrNull()?.toFloat() ?: return null
    val maxValue = values.maxOrNull()?.toFloat() ?: return null
    val rawRange = (maxValue - minValue).coerceAtLeast(0f)
    val displayRange = max(rawRange * 1.25f, minimumDisplayRange)
    val axisMax = maxValue.coerceAtLeast(minValue)
    val axisMin = (axisMax - displayRange).coerceAtLeast(0f)

    return ValueAxis(
        min = axisMin,
        max = axisMax
    )
}

private fun buildHistoryTooltipTop(
    pointY: Float,
    tooltipHeight: Float,
    frameHeight: Float
): Float {
    if (tooltipHeight <= 0f || frameHeight <= 0f) {
        return HISTORY_TOOLTIP_PADDING_PX
    }

    val desiredTop = pointY - tooltipHeight - HISTORY_TOOLTIP_OFFSET_PX
    val maxTop = (frameHeight - tooltipHeight - HISTORY_TOOLTIP_PADDING_PX)
        .coerceAtLeast(HISTORY_TOOLTIP_PADDING_PX)
    return desiredTop.coerceIn(HISTORY_TOOLTIP_PADDING_PX, maxTop)
}

private fun formatTimeReference(instant: Instant, rangeStart: Instant, rangeEnd: Instant): String {
    val totalHours = (rangeEnd.toEpochMilliseconds() - rangeStart.toEpochMilliseconds()) / 3_600_000.0
    val localDateTime = instant.toLocalDateTime(TimeZone.of("America/Sao_Paulo"))

    return if (totalHours > 48.0) {
        "${localDateTime.date.dayOfMonth.toString().padStart(2, '0')}/${localDateTime.date.monthNumber.toString().padStart(2, '0')}"
    } else {
        "${localDateTime.hour.toString().padStart(2, '0')}:${localDateTime.minute.toString().padStart(2, '0')}"
    }
}

private fun formatTooltipUsageValue(
    point: UsageHistoryPoint,
    unit: UsageUnit,
    language: AppLanguage
): String {
    return when (unit) {
        UsageUnit.CURRENCY_USD -> formatCurrencyValue(point.displayUsed)
        UsageUnit.REQUESTS -> {
            if (point.displayTotal > 0L) {
                val percentage = formatPercentage(point.displayUsed, point.displayTotal)
                "${formatCountValue(point.displayUsed)}/${formatCountValue(point.displayTotal)} req ($percentage)"
            } else {
                "${formatCountValue(point.displayUsed)} req"
            }
        }

        UsageUnit.PERCENTAGE -> {
            if (point.total > 0L) {
                "${point.used}/${point.total} % (${formatPercentage(point.used, point.total)})"
            } else {
                "${point.used} %"
            }
        }

        UsageUnit.TOKENS -> {
            if (point.displayTotal > 0L) {
                val percentage = formatPercentage(point.displayUsed, point.displayTotal)
                "${formatCountValue(point.displayUsed)}/${formatCountValue(point.displayTotal)} tok ($percentage)"
            } else {
                "${formatCountValue(point.displayUsed)} tok"
            }
        }
    }
}

private fun formatTooltipDeltaValue(
    point: UsageHistoryPoint,
    comparisonPoint: UsageHistoryPoint?,
    unit: UsageUnit,
    language: AppLanguage
): String {
    if (comparisonPoint == null) {
        return if (language == AppLanguage.PT) "Sem base anterior" else "No previous point"
    }

    val baseUnavailableLabel = if (language == AppLanguage.PT) "base indisponível" else "base unavailable"
    return when (unit) {
        UsageUnit.CURRENCY_USD -> {
            val delta = point.displayUsed - comparisonPoint.displayUsed
            val absolute = formatSignedCurrencyValue(delta)
            appendRelativeVariation(
                absoluteValue = absolute,
                deltaValue = delta.toDouble(),
                baseValue = comparisonPoint.displayUsed.toDouble(),
                baseUnavailableLabel = baseUnavailableLabel
            )
        }

        UsageUnit.REQUESTS -> {
            val delta = point.displayUsed - comparisonPoint.displayUsed
            val absolute = "${formatSignedCountValue(delta)} req"
            appendRelativeVariation(
                absoluteValue = absolute,
                deltaValue = delta.toDouble(),
                baseValue = comparisonPoint.displayUsed.toDouble(),
                baseUnavailableLabel = baseUnavailableLabel
            )
        }

        UsageUnit.PERCENTAGE -> {
            val delta = point.used - comparisonPoint.used
            val absolute = "${formatSignedCountValue(delta)} p.p."
            appendRelativeVariation(
                absoluteValue = absolute,
                deltaValue = delta.toDouble(),
                baseValue = comparisonPoint.used.toDouble(),
                baseUnavailableLabel = baseUnavailableLabel
            )
        }

        UsageUnit.TOKENS -> {
            val delta = point.displayUsed - comparisonPoint.displayUsed
            val absolute = "${formatSignedCountValue(delta)} tok"
            appendRelativeVariation(
                absoluteValue = absolute,
                deltaValue = delta.toDouble(),
                baseValue = comparisonPoint.displayUsed.toDouble(),
                baseUnavailableLabel = baseUnavailableLabel
            )
        }
    }
}

private fun appendRelativeVariation(
    absoluteValue: String,
    deltaValue: Double,
    baseValue: Double,
    baseUnavailableLabel: String
): String {
    if (baseValue <= 0.0) {
        return "$absoluteValue ($baseUnavailableLabel)"
    }

    val relativePercent = (deltaValue * 100.0 / baseValue).roundToInt()
    val relativePrefix = if (relativePercent > 0) "+" else ""
    return "$absoluteValue (${relativePrefix}${relativePercent}%)"
}

private fun formatTooltipWindowValue(
    instant: Instant,
    unit: UsageUnit,
    language: AppLanguage
): String {
    val localDateTime = instant.toLocalDateTime(TimeZone.of("America/Sao_Paulo"))
    if (unit == UsageUnit.CURRENCY_USD && localDateTime.year >= 9999) {
        return if (language == AppLanguage.PT) "Sem expiração" else "No expiry"
    }

    return formatTooltipTimestamp(instant)
}

private fun formatTooltipTimestamp(instant: Instant): String {
    val localDateTime = instant.toLocalDateTime(TimeZone.of("America/Sao_Paulo"))
    val day = localDateTime.date.dayOfMonth.toString().padStart(2, '0')
    val month = localDateTime.date.monthNumber.toString().padStart(2, '0')
    val hour = localDateTime.hour.toString().padStart(2, '0')
    val minute = localDateTime.minute.toString().padStart(2, '0')
    return "$day/$month $hour:$minute BRT"
}

private fun formatCentsLabel(cents: Long): String {
    val dollars = cents / 100
    val remainder = abs(cents % 100)
    return "\$${dollars}.${remainder.toString().padStart(2, '0')}"
}

private fun buildValueLabels(axis: ValueAxis?, unit: UsageUnit): List<String> {
    if (axis == null) {
        return emptyList()
    }

    val middle = ((axis.max + axis.min) / 2f).toLong()
    return when (unit) {
        UsageUnit.CURRENCY_USD -> listOf(
            formatCentsLabel(axis.max.toLong()),
            formatCentsLabel(middle),
            formatCentsLabel(axis.min.toLong())
        )

        UsageUnit.REQUESTS -> listOf(
            formatCountValue(axis.max.toLong()),
            formatCountValue(middle),
            formatCountValue(axis.min.toLong())
        )

        else -> emptyList()
    }
}

private fun formatCurrencyValue(cents: Long): String {
    val sign = if (cents < 0L) "-" else ""
    val absoluteCents = abs(cents)
    val dollars = absoluteCents / 100
    val remainder = absoluteCents % 100
    return "${sign}\$${dollars}.${remainder.toString().padStart(2, '0')}"
}

private fun formatSignedCurrencyValue(cents: Long): String {
    val prefix = if (cents > 0L) "+" else ""
    return prefix + formatCurrencyValue(cents)
}

private fun formatCountValue(value: Long): String {
    return when {
        value >= 1_000_000L -> "${trimDecimal(value / 1_000_000.0)}M"
        value >= 1_000L -> "${trimDecimal(value / 1_000.0)}K"
        else -> value.toString()
    }
}

private fun formatSignedCountValue(value: Long): String {
    val prefix = if (value > 0L) "+" else ""
    return prefix + formatCountValue(value)
}

private fun formatPercentage(used: Long, total: Long): String {
    if (total <= 0L) {
        return "—"
    }

    val percentage = (used * 100.0 / total.toDouble()).roundToInt()
    return "$percentage%"
}

private fun trimDecimal(value: Double): String {
    val text = "%.1f".format(value)
    return text.removeSuffix(".0").removeSuffix(",0")
}

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.UsageHistoryPoint
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.presentation.ui.theme.AppShapes

internal const val HISTORY_TOOLTIP_PADDING_PX = 8f
internal const val HISTORY_TOOLTIP_OFFSET_PX = 10f
internal const val HISTORY_PLOT_HORIZONTAL_INSET_PX = 14f
private val HISTORY_PLOT_HEIGHT = 120.dp
private val HISTORY_TOOLTIP_BAND_HEIGHT = 48.dp
private val HISTORY_FRAME_HEIGHT = HISTORY_PLOT_HEIGHT + HISTORY_TOOLTIP_BAND_HEIGHT
// 64dp cabia "Reinício" na fonte de sistema anterior; a IBM Plex Mono é mais
// larga e a palavra passou a quebrar letra a letra dentro do emblema.
private val HISTORY_ANNOTATION_LABEL_WIDTH = 84.dp
private const val HISTORY_RESET_CLUSTER_GAP_PX = 24f
internal const val HISTORY_MIN_ZOOM_WIDTH_FRACTION = 0.05f
internal const val HISTORY_ZOOM_STEP_FACTOR = 0.85f
internal const val HISTORY_PAN_SENSITIVITY = 0.1f

internal data class HistoryRangeAnnotations(
    val startIndex: Int,
    val endIndex: Int,
    val resetIndices: List<Int>
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun UsageHistoryLineChart(
    points: List<UsageHistoryPoint>,
    unit: UsageUnit,
    language: AppLanguage,
    chartSelectionKey: String,
    modifier: Modifier = Modifier,
    tooltipTitle: String? = null,
    tooltipSubtitle: String? = null,
    /**
     * Cor da série.
     *
     * Entra por parâmetro porque a identidade é da **fonte**: o histórico da
     * Anthropic é azul, o do Codex é verde-água, e os dois desenhavam a mesma
     * linha `primary` — a cor que o card do dashboard usa para distingui-los não
     * chegava até aqui.
     */
    accentColor: Color = MaterialTheme.colorScheme.primary,
    /**
     * Pontos da janela **anterior**, de mesma duração (issue #215). Vazio
     * some com a linha de referência — mesma condição de
     * `UsageHistorySeries.comparison` ser `null`.
     *
     * **Desenhada só sem zoom.** O zoom recorta [points] por fração de
     * índice; aplicar o mesmo recorte aqui exigiria os dois pontos terem a
     * mesma densidade de amostragem, que não é garantida — a leitura de cada
     * janela é independente. Sem essa garantia, a linha tracejada poderia
     * descrever um trecho de tempo diferente do que a corrente mostra
     * ampliada, e um comparativo que compara períodos diferentes é pior que
     * nenhum. "Ver tudo" devolve a comparação.
     */
    previousPoints: List<UsageHistoryPoint> = emptyList()
) {
    val lineColor = accentColor
    val fillColor = accentColor.copy(alpha = 0.12f)
    // A grade é a mesma cor de borda do resto do app, e não o texto com alpha:
    // duas linhas de referência com tons diferentes numa tela só.
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val axisTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val chartIndicatorColor = accentColor
    val chartIndicatorHaloColor = MaterialTheme.colorScheme.surface
    val renderPoints = filteredPoints(points, unit)

    var revealed by remember { mutableStateOf(false) }
    var frameSize by remember(renderPoints) { mutableStateOf(IntSize.Zero) }
    var plotSize by remember(renderPoints) { mutableStateOf(IntSize.Zero) }
    var tooltipSize by remember(renderPoints) { mutableStateOf(IntSize.Zero) }
    var hoveredIndex by remember(renderPoints, chartSelectionKey) { mutableStateOf<Int?>(null) }
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
        tooltipSize = IntSize.Zero
        delay(40)
        revealed = true
    }

    LaunchedEffect(zoomRange, chartSelectionKey) {
        hoveredIndex = null
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
    val previousRenderPoints = remember(previousPoints, unit) { filteredPoints(previousPoints, unit) }
    // Só sem zoom — ver o comentário de `previousPoints`.
    val previousPlotPoints = remember(previousRenderPoints, valueAxis, plotSize, zoomRange) {
        if (zoomRange != 0f..1f) {
            emptyList()
        } else {
            buildPlotPoints(
                points = previousRenderPoints,
                chartWidth = plotSize.width.toFloat(),
                chartHeight = plotSize.height.toFloat(),
                axis = valueAxis,
                horizontalInsetPx = plotInset
            )
        }
    }
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
    val activePoint = hoveredIndex?.let { index -> plotPoints.getOrNull(index) }
    val tooltipModel = remember(
        activePoint,
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
            subtitle = tooltipSubtitle
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
                AppButton(
                    label = if (language == AppLanguage.PT) "Ver tudo" else "View all",
                    onClick = { zoomRange = 0f..1f },
                    tone = AppButtonTone.GHOST,
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            }

            if (valueLabels.isNotEmpty()) {
                HistoryValueAxisLabels(
                    labels = valueLabels,
                    color = axisTextColor,
                    modifier = Modifier.align(Alignment.BottomStart)
                )
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
                    drawHistoryPlot(
                        colors = HistoryPlotColors(
                            line = lineColor,
                            fill = fillColor,
                            grid = gridColor,
                            indicator = chartIndicatorColor,
                            indicatorHalo = chartIndicatorHaloColor
                        ),
                        plotPoints = plotPoints,
                        previousPlotPoints = previousPlotPoints,
                        resetClusterPoints = resetClusterPoints,
                        rangeAnnotations = rangeAnnotations,
                        activePoint = activePoint,
                        revealFraction = revealFraction
                    )
                }

                HistoryRangeAnnotationLabels(
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
                    .historyChartPointerInput(
                        plotStartX = startPaddingPx,
                        plotWidth = { plotSize.width.toFloat() },
                        plotPoints = plotPoints,
                        windowedPoints = windowedPoints,
                        zoomRange = { zoomRange },
                        onZoomRangeChange = { range -> zoomRange = range },
                        onHoveredIndexChange = { index -> hoveredIndex = index }
                    )
            )
        }

        if (timeLabels.isNotEmpty()) {
            HistoryTimeAxisLabels(
                labels = timeLabels,
                color = axisTextColor,
                startPadding = if (valueLabels.isNotEmpty()) 48.dp else 0.dp
            )
        }

        // A cor não basta para dizer "isto é o período anterior" — o
        // traçado sozinho não carrega a legenda, e por escrito é a mesma
        // regra que já vale para todo estado deste sistema.
        if (previousPlotPoints.size > 1) {
            Text(
                text = if (language == AppLanguage.PT) {
                    "Tracejado: mesmo ponto do período anterior"
                } else {
                    "Dashed: same point last period"
                },
                style = MaterialTheme.typography.labelSmall,
                color = axisTextColor
            )
        }
    }
}



@Composable
private fun HistoryValueAxisLabels(labels: List<String>, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.height(HISTORY_PLOT_HEIGHT),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        labels.forEach { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
        }
    }
}

@Composable
private fun HistoryTimeAxisLabels(labels: List<String>, color: Color, startPadding: Dp) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = startPadding),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        labels.forEachIndexed { index, label ->
            Text(
                modifier = Modifier.weight(1f),
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                textAlign = when (index) {
                    0 -> TextAlign.Start
                    1 -> TextAlign.Center
                    else -> TextAlign.End
                }
            )
        }
    }
}

/**
 * Roda do mouse amplia e desloca; o ponteiro escolhe o ponto em foco.
 *
 * `zoomRange` e `plotWidth` entram como leitura, não como valor: dois eventos
 * de rolagem podem chegar entre duas recomposições, e o segundo precisa partir
 * do recorte que o primeiro acabou de gravar.
 */
@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.historyChartPointerInput(
    plotStartX: Float,
    plotWidth: () -> Float,
    plotPoints: List<ChartPlotPoint>,
    windowedPoints: List<UsageHistoryPoint>,
    zoomRange: () -> ClosedFloatingPointRange<Float>,
    onZoomRangeChange: (ClosedFloatingPointRange<Float>) -> Unit,
    onHoveredIndexChange: (Int?) -> Unit
): Modifier = this
    .onPointerEvent(PointerEventType.Scroll) { event ->
        val change = event.changes.firstOrNull() ?: return@onPointerEvent
        val plotPointerX = coerceFramePointerToPlotPointerX(
            framePointerX = change.position.x,
            plotStartX = plotStartX,
            plotWidth = plotWidth()
        ) ?: return@onPointerEvent
        val pointerIndex = findClosestPlotPointIndex(plotPoints, plotPointerX)
        val pointerFraction = pointerIndex
            ?.let { index -> buildTimelineFractions(windowedPoints).getOrNull(index) }
            ?: 0.5f
        onZoomRangeChange(
            applyChartScroll(
                current = zoomRange(),
                scrollDeltaY = change.scrollDelta.y,
                scrollDeltaX = change.scrollDelta.x,
                pointerFraction = pointerFraction,
                shiftPressed = event.keyboardModifiers.isShiftPressed
            )
        )
        change.consume()
    }
    .onPointerEvent(PointerEventType.Enter) { event ->
        val framePointerX = event.changes.firstOrNull()?.position?.x ?: return@onPointerEvent
        val plotPointerX = framePointerToPlotPointerX(
            framePointerX = framePointerX,
            plotStartX = plotStartX,
            plotWidth = plotWidth()
        ) ?: return@onPointerEvent
        onHoveredIndexChange(findClosestPlotPointIndex(plotPoints, plotPointerX))
    }
    .onPointerEvent(PointerEventType.Move) { event ->
        val change = event.changes.firstOrNull() ?: return@onPointerEvent
        val plotPointerX = coerceFramePointerToPlotPointerX(
            framePointerX = change.position.x,
            plotStartX = plotStartX,
            plotWidth = plotWidth()
        )
        onHoveredIndexChange(plotPointerX?.let { pointerX -> findClosestPlotPointIndex(plotPoints, pointerX) })
    }
    .onPointerEvent(PointerEventType.Exit) {
        onHoveredIndexChange(null)
    }

private class HistoryPlotColors(
    val line: Color,
    val fill: Color,
    val grid: Color,
    val indicator: Color,
    val indicatorHalo: Color
)

/**
 * O desenho do gráfico: grade, marcas de reinício, linha do período anterior,
 * curva com preenchimento e os marcadores de início, fim e ponto em foco.
 *
 * Fora do composable porque é só pintura — nenhum estado nasce aqui —, e o
 * corpo inteiro dentro do `Canvas` era o que levava `UsageHistoryLineChart`
 * acima do limite de 300 linhas (#306).
 */
private fun DrawScope.drawHistoryPlot(
    colors: HistoryPlotColors,
    plotPoints: List<ChartPlotPoint>,
    previousPlotPoints: List<ChartPlotPoint>,
    resetClusterPoints: List<Pair<ResetMarker, ChartPlotPoint>>,
    rangeAnnotations: HistoryRangeAnnotations?,
    activePoint: ChartPlotPoint?,
    revealFraction: Float
) {
    val lineColor = colors.line
    val fillColor = colors.fill
    val gridColor = colors.grid
    val chartIndicatorColor = colors.indicator
    val chartIndicatorHaloColor = colors.indicatorHalo
    val strokeWidth = 1.75.dp.toPx()
    val gridStroke = 1.dp.toPx()
    val activeMarkerRadius = 4.dp.toPx()
    val activeMarkerHaloRadius = 7.dp.toPx()
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

    resetClusterPoints.forEach { (_, resetPoint) ->
        drawLine(
            color = chartIndicatorColor.copy(alpha = 0.4f),
            start = Offset(resetPoint.x, 0f),
            end = Offset(resetPoint.x, size.height),
            strokeWidth = gridStroke * 1.5f,
            pathEffect = resetPathEffect
        )
    }

    // Linha de referência do período anterior (issue #215):
    // tracejada e em tom neutro — nunca a cor de acento —,
    // porque ela não é a série que a tela está medindo, é só
    // contexto para ler a corrente contra ela. Desenhada
    // antes da linha atual para ficar atrás dela.
    if (previousPlotPoints.size > 1) {
        val previousPath = Path()
        previousPlotPoints.forEachIndexed { index, point ->
            if (index == 0) {
                previousPath.moveTo(point.x, point.y)
            } else {
                previousPath.lineTo(point.x, point.y)
            }
        }
        clipRect(right = size.width * revealFraction) {
            drawPath(
                path = previousPath,
                color = gridColor,
                style = Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round,
                    pathEffect = resetPathEffect
                )
            )
        }
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
            // Massa sob a curva principal (issue #223): o
            // preenchimento chapado nasceu só pro saldo do
            // DeepSeek e nunca foi generalizado — a leitura
            // percentual (a mais vista da tela) ficava só no
            // traço de 1-2px sobre a grade. `fillColor` já é
            // opacidade fixa sobre `accentColor`, sem
            // gradiente; geometria de `fillPath` já era
            // unit-agnostic, só o desenho estava condicionado.
            drawPath(path = fillPath, color = fillColor)
            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
    }

    if (rangeStartPoint != null && rangeStartPoint.index != activePoint?.index) {
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

    if (rangeEndPoint != null && rangeEndPoint.index != activePoint?.index) {
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
}

@Composable
private fun HistoryRangeAnnotationLabels(
    resetClusters: List<Pair<ResetMarker, ChartPlotPoint>>,
    plotWidth: Float,
    plotInset: Float,
    language: AppLanguage
) {
    val density = LocalDensity.current
    val labelWidthPx = with(density) { HISTORY_ANNOTATION_LABEL_WIDTH.toPx() }

    Box(modifier = Modifier.fillMaxSize()) {
        val occupiedX = mutableListOf<Float>()

        resetClusters.forEach { (marker, point) ->
            val collides = occupiedX.any { x -> abs(point.x - x) < labelWidthPx }
            if (!collides) {
                HistoryAnnotationLabel(
                    text = resetLabelText(marker.count, language),
                    left = clampTooltipLeft(
                        desiredCenterX = point.x,
                        tooltipWidth = labelWidthPx,
                        containerWidth = plotWidth,
                        horizontalPadding = plotInset
                    )
                )
                occupiedX += point.x
            }
        }
    }
}

private fun resetLabelText(count: Int, language: AppLanguage): String {
    val label = if (language == AppLanguage.PT) "Reinício" else "Reset"
    return if (count > 1) "$label ×$count" else label
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
        shape = AppShapes.extraSmall,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(AppBorderWidth, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 3.dp),
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun HistoryTooltipBubble(
    model: HistoryTooltipModel,
    modifier: Modifier = Modifier
) {
    // Mesma bolha das outras três: `AppTooltipSurface`. Duas tooltips sobre o
    // mesmo tipo de gráfico não podem flutuar em alturas diferentes, e era a
    // anatomia repetida por extenso que deixava isso acontecer.
    AppTooltipSurface(modifier = modifier.widthIn(max = 230.dp)) {
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

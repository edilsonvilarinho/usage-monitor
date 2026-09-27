package com.usagemonitor.presentation.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.UsageHistoryPoint
import com.usagemonitor.presentation.ui.theme.AppShapes

/*
 * Pintura, entrada de ponteiro e rótulos de `UsageHistoryLineChart`, fora do
 * arquivo do composable: com 756 linhas ele não tinha espaço para o gráfico de
 * várias séries (issue #320) sem passar do teto de 800.
 */

// 64dp cabia "Reinício" na fonte de sistema anterior; a IBM Plex Mono é mais
// larga e a palavra passou a quebrar letra a letra dentro do emblema.
private val HISTORY_ANNOTATION_LABEL_WIDTH = 84.dp

/**
 * Roda do mouse amplia e desloca; o ponteiro escolhe o ponto em foco.
 *
 * `zoomRange` e `plotWidth` entram como leitura, não como valor: dois eventos
 * de rolagem podem chegar entre duas recomposições, e o segundo precisa partir
 * do recorte que o primeiro acabou de gravar.
 */
@OptIn(ExperimentalComposeUiApi::class)
internal fun Modifier.historyChartPointerInput(
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

internal class HistoryPlotColors(
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
internal fun DrawScope.drawHistoryPlot(
    colors: HistoryPlotColors,
    plotPoints: List<ChartPlotPoint>,
    previousPlotPoints: List<ChartPlotPoint>,
    overlayLines: List<Pair<List<ChartPlotPoint>, Color>>,
    overlayActivePoints: List<Pair<ChartPlotPoint, Color>>,
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

    // Séries sobrepostas (issue #320): traço cheio da cor própria, sem
    // preenchimento — duas massas translúcidas empilhadas virariam uma mancha
    // e esconderiam qual área é de qual série. Antes da principal, atrás dela.
    overlayLines.forEach { (overlayPoints, overlayColor) ->
        if (overlayPoints.size > 1) {
            val overlayPath = Path()
            overlayPoints.forEachIndexed { index, point ->
                if (index == 0) {
                    overlayPath.moveTo(point.x, point.y)
                } else {
                    overlayPath.lineTo(point.x, point.y)
                }
            }
            clipRect(right = size.width * revealFraction) {
                drawPath(
                    path = overlayPath,
                    color = overlayColor,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
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

    overlayActivePoints.forEach { (point, overlayColor) ->
        drawCircle(
            color = chartIndicatorHaloColor,
            radius = activeMarkerHaloRadius,
            center = Offset(point.x, point.y)
        )
        drawCircle(
            color = overlayColor,
            radius = activeMarkerRadius,
            center = Offset(point.x, point.y)
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
internal fun HistoryRangeAnnotationLabels(
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

internal fun resetLabelText(count: Int, language: AppLanguage): String {
    val label = if (language == AppLanguage.PT) "Reinício" else "Reset"
    return if (count > 1) "$label ×$count" else label
}

@Composable
internal fun HistoryAnnotationLabel(
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
internal fun HistoryTooltipBubble(
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

/**
 * Legenda das séries quando há mais de uma (issue #320): traço curto da cor e o
 * nome ao lado. A cor sozinha não diz qual linha é qual — o nome escrito é o que
 * informa, e o traço só liga o nome à linha.
 */
@Composable
internal fun HistoryChartLegend(
    entries: List<Pair<String, Color>>,
    textColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically
    ) {
        entries.forEach { (label, color) ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(14.dp)
                        .height(2.dp)
                        .background(color)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * A bolha do ponto em foco, posicionada pelo gráfico. Fora do composable do
 * gráfico pelo limite de 300 linhas por função.
 */
@Composable
internal fun HistoryTooltipLayer(
    model: HistoryTooltipModel,
    left: Float,
    top: Float,
    onSizeChanged: (IntSize) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .offset { IntOffset(x = left.roundToInt(), y = top.roundToInt()) }
                .onSizeChanged(onSizeChanged)
        ) {
            HistoryTooltipBubble(model = model)
        }
    }
}

package com.usagemonitor.presentation.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.usagemonitor.presentation.ui.theme.AppShapes

private val CHART_HEIGHT = 148.dp
private val AXIS_LABEL_WIDTH = 54.dp
private val TOOLTIP_WIDTH = 150.dp
private const val LINE_STROKE_PX = 2.2f
private const val GRID_STROKE_PX = 1f
// 0.32 era o topo de um gradiente que se apagava até a base. Com o preenchimento
// chapado — o sistema visual não tem degradê —, aquele valor pinta a área toda no
// tom mais forte, e a mancha passa a pesar mais que a própria linha, que é onde o
// dado está.
private const val AREA_TOP_ALPHA = 0.14f
private const val GRID_ALPHA = 0.35f
private const val MIN_BAR_WIDTH_PX = 3f
private const val BAR_GAP_FRACTION = 0.28f
private const val PIXELS_PER_POINT = 6f
private const val MARKER_RADIUS_PX = 3.5f

/**
 * Uma série de valores por turno. Genérica: o gráfico não conhece o domínio.
 * `null` é turno não medido: a linha quebra ali e a bolha mostra "—".
 */
data class TurnSeries(
    val label: String,
    val values: List<Long?>,
    val color: Color,
    val binMode: BinMode = BinMode.LAST
)

/**
 * Gráfico de séries por turno, com área, grade rotulada e leitura no hover.
 *
 * `UsageHistoryLineChart` está acoplado a `UsageHistoryPoint` (timestamps,
 * resets, zoom) e não serve aqui: o eixo X é o índice do turno, não o tempo.
 *
 * Séries maiores que a largura disponível são condensadas em bins — sem isso
 * uma sessão de 251 turnos vira um pente de barras de 1 px. A escala usa o
 * percentil 99 para que um pico isolado não achate o resto.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TurnSeriesChart(
    series: List<TurnSeries>,
    modifier: Modifier = Modifier,
    stacked: Boolean = false,
    height: Dp = CHART_HEIGHT,
    valueFormatter: (Long) -> String = { value -> value.toString() },
    highlightDrops: Boolean = false,
    emptyLabel: String = "sem dados"
) {
    val visibleSeries = series.filter { entry -> entry.values.isNotEmpty() }
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = GRID_ALPHA)
    val plotBackground = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
    val markerColor = MaterialTheme.colorScheme.error

    Column(modifier = modifier.fillMaxWidth().appModalRevealRow(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (visibleSeries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height)
                    .clip(AppShapes.medium),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = emptyLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Column
        }

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val density = LocalDensity.current
            val plotWidthPx = with(density) { (maxWidth - AXIS_LABEL_WIDTH).toPx() }
            val targetBins = (plotWidthPx / PIXELS_PER_POINT).roundToInt().coerceAtLeast(2)

            val binned = visibleSeries.map { entry ->
                entry.copy(values = binSeries(entry.values, targetBins, entry.binMode))
            }
            val pointCount = binned.maxOf { entry -> entry.values.size }
            val ceiling = ceilingFor(binned, stacked)
            val drops = if (highlightDrops) dropIndices(binned.first().values) else emptyList()

            var hoveredIndex by remember { mutableStateOf<Int?>(null) }

            Row(modifier = Modifier.fillMaxWidth()) {
                AxisLabels(
                    ceiling = ceiling,
                    height = height,
                    valueFormatter = valueFormatter
                )

                Box(modifier = Modifier.weight(1f)) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(height)
                            .clip(AppShapes.medium)
                            .onPointerEvent(PointerEventType.Move) { event ->
                                val x = event.changes.first().position.x
                                hoveredIndex = indexAt(x, size.width.toFloat(), pointCount)
                            }
                            .onPointerEvent(PointerEventType.Exit) { hoveredIndex = null }
                            .semantics {
                                contentDescription = binned.joinToString(separator = ", ") { entry ->
                                    "${entry.label}: ${entry.values.size} pontos"
                                }
                            }
                    ) {
                        drawRect(color = plotBackground)
                        drawGrid(gridColor)

                        if (stacked) {
                            drawStackedBars(binned, ceiling)
                        } else {
                            for (entry in binned) {
                                drawSeriesArea(entry, ceiling)
                            }
                            for (entry in binned) {
                                drawSeriesLine(entry, ceiling)
                            }
                        }

                        drawDropMarkers(drops, binned.first().values, ceiling, markerColor)
                        hoveredIndex?.let { index -> drawHoverGuide(index, pointCount, gridColor) }
                    }

                    hoveredIndex?.let { index ->
                        ChartTooltip(
                            index = index,
                            pointCount = pointCount,
                            series = binned,
                            valueFormatter = valueFormatter,
                            isDrop = index in drops
                        )
                    }
                }
            }
        }

        TurnSeriesLegend(visibleSeries, showDropHint = highlightDrops)
    }
}

@Composable
private fun AxisLabels(
    ceiling: Long,
    height: Dp,
    valueFormatter: (Long) -> String
) {
    Column(
        modifier = Modifier.width(AXIS_LABEL_WIDTH).height(height).padding(end = 6.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.End
    ) {
        for (value in listOf(ceiling, ceiling / 2, 0L)) {
            Text(
                text = valueFormatter(value),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ChartTooltip(
    index: Int,
    pointCount: Int,
    series: List<TurnSeries>,
    valueFormatter: (Long) -> String,
    isDrop: Boolean
) {
    val density = LocalDensity.current
    val tooltipWidthPx = with(density) { TOOLTIP_WIDTH.toPx() }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val plotWidthPx = with(density) { maxWidth.toPx() }
        val anchorX = if (pointCount <= 1) {
            plotWidthPx / 2f
        } else {
            plotWidthPx * index.toFloat() / (pointCount - 1).toFloat()
        }
        val clampedX = (anchorX - tooltipWidthPx / 2f).coerceIn(0f, (plotWidthPx - tooltipWidthPx).coerceAtLeast(0f))

        AppTooltipSurface(
            modifier = Modifier
                .width(TOOLTIP_WIDTH)
                .offset { IntOffset(clampedX.roundToInt(), 0) }
        ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(
                    text = if (isDrop) "#${index + 1} · compactação" else "#${index + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDrop) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                for (entry in series) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = entry.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = entry.color
                        )
                        Text(
                            text = entry.values.getOrNull(index)?.let(valueFormatter) ?: "—",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TurnSeriesLegend(series: List<TurnSeries>, showDropHint: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (entry in series) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(8.dp).clip(AppShapes.small)) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRect(color = entry.color)
                    }
                }
                Text(
                    text = entry.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (showDropHint) {
            Text(
                text = "▼ compactação",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

private fun ceilingFor(series: List<TurnSeries>, stacked: Boolean): Long {
    if (!stacked) {
        return scaleCeiling(series.flatMap { entry -> entry.values.filterNotNull() })
    }

    val length = series.maxOf { entry -> entry.values.size }
    val totals = (0 until length).map { index ->
        series.sumOf { entry -> entry.values.getOrNull(index) ?: 0L }
    }
    return scaleCeiling(totals)
}

private fun indexAt(x: Float, width: Float, pointCount: Int): Int? {
    if (width <= 0f || pointCount <= 0) {
        return null
    }
    val fraction = (x / width).coerceIn(0f, 1f)
    return (fraction * (pointCount - 1)).roundToInt().coerceIn(0, pointCount - 1)
}

private fun DrawScope.drawGrid(color: Color) {
    val dash = PathEffect.dashPathEffect(floatArrayOf(4f, 6f))
    for (fraction in listOf(0.25f, 0.5f, 0.75f)) {
        val y = size.height * fraction
        drawLine(
            color = color,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = GRID_STROKE_PX,
            pathEffect = dash
        )
    }
    drawLine(
        color = color,
        start = Offset(0f, size.height),
        end = Offset(size.width, size.height),
        strokeWidth = GRID_STROKE_PX
    )
}

/**
 * Um traçado por trecho contínuo de valores medidos: o `null` interrompe a
 * linha em vez de levá-la ao chão, que se leria como zero.
 */
private fun DrawScope.seriesPaths(values: List<Long?>, ceiling: Long, closeToBaseline: Boolean): List<Path> {
    if (values.isEmpty()) {
        return emptyList()
    }
    val stepX = if (values.size == 1) 0f else size.width / (values.size - 1).toFloat()
    fun xAt(index: Int) = if (values.size == 1) size.width / 2f else stepX * index

    val runs = mutableListOf<List<Int>>()
    var current = mutableListOf<Int>()
    values.forEachIndexed { index, value ->
        if (value == null) {
            if (current.isNotEmpty()) runs += current
            current = mutableListOf()
        } else {
            current += index
        }
    }
    if (current.isNotEmpty()) runs += current

    return runs.map { run ->
        val path = Path()
        run.forEachIndexed { position, index ->
            val y = size.height - heightFor(values[index] ?: 0L, ceiling)
            if (position == 0) path.moveTo(xAt(index), y) else path.lineTo(xAt(index), y)
        }
        if (closeToBaseline) {
            path.lineTo(xAt(run.last()), size.height)
            path.lineTo(xAt(run.first()), size.height)
            path.close()
        }
        path
    }
}

private fun DrawScope.drawSeriesArea(series: TurnSeries, ceiling: Long) {
    // Preenchimento chapado, não gradiente: o sistema visual não tem degradê em
    // lugar nenhum, e o fade fazia a mesma série parecer mais fraca embaixo — onde
    // o valor é maior, porque a área cresce da linha até a base.
    for (path in seriesPaths(series.values, ceiling, closeToBaseline = true)) {
        drawPath(path = path, color = series.color.copy(alpha = AREA_TOP_ALPHA))
    }
}

private fun DrawScope.drawSeriesLine(series: TurnSeries, ceiling: Long) {
    for (path in seriesPaths(series.values, ceiling, closeToBaseline = false)) {
        drawPath(path = path, color = series.color, style = Stroke(width = LINE_STROKE_PX, cap = StrokeCap.Round))
    }
}

private fun DrawScope.drawStackedBars(series: List<TurnSeries>, ceiling: Long) {
    if (ceiling <= 0L) {
        return
    }

    val length = series.maxOf { entry -> entry.values.size }
    if (length <= 0) {
        return
    }

    val slotWidth = size.width / length.toFloat()
    val barWidth = (slotWidth * (1f - BAR_GAP_FRACTION)).coerceAtLeast(MIN_BAR_WIDTH_PX)

    for (index in 0 until length) {
        var bottom = size.height
        for (entry in series) {
            val value = entry.values.getOrNull(index) ?: 0L
            if (value <= 0L) {
                continue
            }
            val barHeight = heightFor(value, ceiling)
            drawRect(
                color = entry.color,
                topLeft = Offset(slotWidth * index + (slotWidth - barWidth) / 2f, bottom - barHeight),
                size = Size(barWidth, barHeight)
            )
            bottom -= barHeight
        }
    }
}

/** Marca as quedas da série — cada uma é uma compactação do contexto. */
private fun DrawScope.drawDropMarkers(
    drops: List<Int>,
    values: List<Long?>,
    ceiling: Long,
    color: Color
) {
    if (drops.isEmpty() || values.size < 2) {
        return
    }

    val stepX = size.width / (values.size - 1).toFloat()
    for (index in drops) {
        val x = stepX * index
        val y = size.height - heightFor(values[index] ?: continue, ceiling)
        drawCircle(color = color, radius = MARKER_RADIUS_PX, center = Offset(x, y))
    }
}

private fun DrawScope.drawHoverGuide(index: Int, pointCount: Int, color: Color) {
    if (pointCount <= 1) {
        return
    }
    val x = size.width * index.toFloat() / (pointCount - 1).toFloat()
    drawLine(
        color = color,
        start = Offset(x, 0f),
        end = Offset(x, size.height),
        strokeWidth = GRID_STROKE_PX
    )
}

/** Valores acima do teto são clipados: o pico isolado não pode achatar o resto. */
private fun DrawScope.heightFor(value: Long, ceiling: Long): Float {
    if (ceiling <= 0L) {
        return 0f
    }
    val fraction = (value.toDouble() / ceiling.toDouble()).coerceAtMost(1.0)
    return fraction.toFloat() * size.height
}

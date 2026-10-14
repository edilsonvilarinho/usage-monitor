package com.usagemonitor.presentation.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.components.AppTooltipSurface
import com.usagemonitor.presentation.ui.theme.AppSpacing
import kotlin.math.roundToInt

internal const val ANALYSIS_HOURS_CHART_TAG = "analysisHoursChart"
internal const val ANALYSIS_HOURS_TOOLTIP_TAG = "analysisHoursTooltip"

private val CHART_HEIGHT = 112.dp
private val TOOLTIP_WIDTH = 200.dp
private val TOOLTIP_GAP = 4.dp
private const val BAR_SHARE = 0.7f
private const val OTHER_BAR_ALPHA = 0.72f
private const val LABEL_EVERY = 3

/** A barra sob o ponteiro; `null` fora da área. */
internal fun analysisBarAt(x: Float, width: Float, count: Int): Int? {
    if (count <= 0 || width <= 0f || x < 0f || x > width) return null
    return (x / width * count).toInt().coerceIn(0, count - 1)
}

/**
 * Altura da barra sobre a maior do gráfico, de 0 a 1. `null` = não medido (desenha lacuna
 * tracejada); 0 = medido e parado (traço rente) — nunca colapsar os dois.
 */
internal fun analysisBarFraction(value: Double?, max: Double): Float? {
    if (value == null) return null
    if (max <= 0.0) return 0f
    return (value / max).toFloat().coerceIn(0f, 1f)
}

/**
 * Últimas 24 h da janela "Análise" (rodada AD, AD1): barras com os pontos da 5h subidos em
 * cada hora, a vazão (tok/s) como linha por cima e a hora sob o ponteiro numa bolha. A hora
 * de pico é a frase do cabeçalho e a descrição do Canvas para o leitor de tela.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun AnalysisHoursChart(bars: List<AnalysisHourBar>, barColor: Color, lineColor: Color, language: AppLanguage) {
    val peak = AnalysisPanelModel.peakIndex(bars)
    val summary = AnalysisPanelModel.peakLabel(bars, language)
    val maxPoints = bars.mapNotNull { bar -> bar.points }.maxOrNull() ?: 0.0
    val maxSpeed = bars.mapNotNull { bar -> bar.tokensPerSecond }.maxOrNull() ?: 0.0
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val trackColor = MaterialTheme.colorScheme.outlineVariant
    val hoverColor = MaterialTheme.colorScheme.surfaceVariant
    var hovered by remember(bars) { mutableStateOf<Int?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val density = LocalDensity.current
            val plotWidthPx = with(density) { maxWidth.toPx() }
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CHART_HEIGHT)
                    .testTag(ANALYSIS_HOURS_CHART_TAG)
                    // Enter também: o primeiro movimento sobre a área chega como Enter.
                    .onPointerEvent(PointerEventType.Enter) { event -> hovered = analysisBarAt(event.changes.first().position.x, size.width.toFloat(), bars.size) }
                    .onPointerEvent(PointerEventType.Move) { event -> hovered = analysisBarAt(event.changes.first().position.x, size.width.toFloat(), bars.size) }
                    .onPointerEvent(PointerEventType.Exit) { hovered = null }
                    .semantics { contentDescription = summary }
            ) {
                if (bars.isEmpty()) return@Canvas
                val slot = size.width / bars.size
                val barWidth = slot * BAR_SHARE
                // A linha da vazão ocupa os 85% de baixo: no topo ela encostaria na barra de pico.
                val lineTop = size.height * 0.15f
                hovered?.let { index -> drawRect(color = hoverColor, topLeft = Offset(index * slot, 0f), size = Size(slot, size.height)) }
                val baseline = 1.dp.toPx()
                bars.forEachIndexed { index, bar ->
                    val left = index * slot + (slot - barWidth) / 2f
                    val fraction = analysisBarFraction(bar.points, maxPoints)
                    when {
                        fraction == null -> drawLine(
                            color = trackColor,
                            start = Offset(left, size.height - baseline),
                            end = Offset(left + barWidth, size.height - baseline),
                            strokeWidth = baseline,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 2.dp.toPx()))
                        )
                        fraction <= 0f -> drawRect(color = trackColor, topLeft = Offset(left, size.height - baseline), size = Size(barWidth, baseline))
                        else -> {
                            val height = (fraction * size.height).coerceAtLeast(baseline * 1.5f)
                            drawRoundRect(
                                color = if (peak == null || index == peak) barColor else barColor.copy(alpha = OTHER_BAR_ALPHA),
                                topLeft = Offset(left, size.height - height),
                                size = Size(barWidth, height),
                                cornerRadius = CornerRadius(2.dp.toPx())
                            )
                        }
                    }
                }
                if (maxSpeed > 0.0) {
                    val path = Path()
                    var open = false
                    bars.forEachIndexed { index, bar ->
                        val speed = bar.tokensPerSecond
                        if (speed == null) {
                            open = false
                            return@forEachIndexed
                        }
                        val x = index * slot + slot / 2f
                        val y = lineTop + (size.height - lineTop) * (1f - (speed / maxSpeed).toFloat())
                        if (open) path.lineTo(x, y) else path.moveTo(x, y)
                        open = true
                        drawCircle(color = lineColor, radius = 2.dp.toPx(), center = Offset(x, y))
                    }
                    drawPath(path = path, color = lineColor, style = Stroke(width = 1.5.dp.toPx()))
                }
            }
            hovered?.let { index ->
                val tooltipWidthPx = with(density) { TOOLTIP_WIDTH.toPx() }
                val anchorX = plotWidthPx * (index + 0.5f) / bars.size
                val left = (anchorX - tooltipWidthPx / 2f).coerceIn(0f, (plotWidthPx - tooltipWidthPx).coerceAtLeast(0f))
                // Acima das barras e com altura zero no layout: por cima delas a bolha
                // capturaria o ponteiro e piscaria a cada quadro (o mesmo do histórico, #392).
                AppTooltipSurface(
                    modifier = Modifier
                        .width(TOOLTIP_WIDTH)
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            layout(placeable.width, 0) {
                                placeable.place(left.roundToInt(), -placeable.height - TOOLTIP_GAP.roundToPx())
                            }
                        }
                        .testTag(ANALYSIS_HOURS_TOOLTIP_TAG)
                ) {
                    Text(
                        text = AnalysisPanelModel.hourTooltip(bars[index], language),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs)
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            bars.forEachIndexed { index, bar ->
                Box(Modifier.weight(1f)) {
                    if (index % LABEL_EVERY == 0 || index == bars.lastIndex) {
                        Text(
                            text = bar.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (index == peak) MaterialTheme.colorScheme.onSurface else axisColor,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
        Text(
            text = legend(maxPoints, maxSpeed, language),
            style = MaterialTheme.typography.labelSmall,
            color = axisColor
        )
    }
}

/** A escala escrita: barras e linha têm máximos próprios, e o leitor precisa saber quais. */
private fun legend(maxPoints: Double, maxSpeed: Double, language: AppLanguage): String {
    val pt = language == AppLanguage.PT
    val bars = if (pt) "barras: pp da 5h por hora (máx ${maxPoints.roundToInt()})" else "bars: 5h pp per hour (max ${maxPoints.roundToInt()})"
    if (maxSpeed <= 0.0) return bars + if (pt) " · tok/s sem medição" else " · tok/s not measured"
    return bars + if (pt) " · linha: tok/s (máx ${maxSpeed.roundToInt()})" else " · line: tok/s (max ${maxSpeed.roundToInt()})"
}

package com.usagemonitor.presentation.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.Alignment
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
internal const val ANALYSIS_SPEED_STRIP_TAG = "analysisSpeedStrip"

private val CHART_HEIGHT = 112.dp
private val SPEED_HEIGHT = 28.dp
private val AXIS_WIDTH = 36.dp
private const val GRID_ALPHA = 0.6f
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
 * Últimas 24 h da janela "Análise" (rodada AD, AD1; uma escala por gráfico desde a #463): barras
 * com os pontos da 5h subidos em cada hora e o eixo escrito; a vazão (tok/s) numa faixa própria
 * embaixo, com a escala dela — antes as duas grandezas dividiam o mesmo espaço com máximos
 * diferentes, e só a legenda dizia isso. A hora sob o ponteiro abre uma bolha; a hora de pico é a
 * frase do cabeçalho e a descrição do Canvas para o leitor de tela.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun AnalysisHoursChart(bars: List<AnalysisHourBar>, barColor: Color, lineColor: Color, language: AppLanguage) {
    val peak = AnalysisPanelModel.peakIndex(bars)
    val summary = AnalysisPanelModel.peakLabel(bars, language)
    val maxPoints = analysisAxisMax(bars.mapNotNull { bar -> bar.points }.maxOrNull() ?: 0.0)
    val maxSpeed = bars.mapNotNull { bar -> bar.tokensPerSecond }.maxOrNull() ?: 0.0
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val trackColor = MaterialTheme.colorScheme.outlineVariant
    val hoverColor = MaterialTheme.colorScheme.surfaceVariant
    val labelStyle = MaterialTheme.typography.labelSmall
    var hovered by remember(bars) { mutableStateOf<Int?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.width(AXIS_WIDTH).height(CHART_HEIGHT), verticalArrangement = Arrangement.SpaceBetween) {
                listOf(maxPoints, maxPoints / 2, 0).forEach { value ->
                    Text("$value%", style = labelStyle, color = axisColor, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth().padding(end = AppSpacing.xs))
                }
            }
            BoxWithConstraints(Modifier.weight(1f)) {
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
                    hovered?.let { index -> drawRect(color = hoverColor, topLeft = Offset(index * slot, 0f), size = Size(slot, size.height)) }
                    val baseline = 1.dp.toPx()
                    listOf(0f, 0.5f, 1f).forEach { level ->
                        drawLine(trackColor.copy(alpha = GRID_ALPHA), Offset(0f, size.height * level), Offset(size.width, size.height * level), baseline)
                    }
                    bars.forEachIndexed { index, bar ->
                        val left = index * slot + (slot - barWidth) / 2f
                        val fraction = analysisBarFraction(bar.points, maxPoints.toDouble())
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
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(AXIS_WIDTH))
            bars.forEachIndexed { index, bar ->
                Box(Modifier.weight(1f)) {
                    if (index % LABEL_EVERY == 0 || index == bars.lastIndex) {
                        Text(
                            text = bar.label,
                            style = labelStyle,
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
        if (maxSpeed > 0.0) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("tok/s", style = labelStyle, color = axisColor, modifier = Modifier.width(AXIS_WIDTH))
                Canvas(Modifier.weight(1f).height(SPEED_HEIGHT).testTag(ANALYSIS_SPEED_STRIP_TAG)) {
                    val slot = size.width / bars.size
                    val path = Path()
                    var open = false
                    bars.forEachIndexed { index, bar ->
                        val speed = bar.tokensPerSecond
                        if (speed == null) {
                            open = false
                            return@forEachIndexed
                        }
                        val x = index * slot + slot / 2f
                        val y = size.height - size.height * (speed / maxSpeed).toFloat()
                        if (open) path.lineTo(x, y) else path.moveTo(x, y)
                        open = true
                        drawCircle(color = lineColor, radius = 2.dp.toPx(), center = Offset(x, y.coerceIn(2.dp.toPx(), size.height - 2.dp.toPx())))
                    }
                    drawPath(path = path, color = lineColor, style = Stroke(width = 1.5.dp.toPx()))
                }
            }
        }
        Text(
            text = legend(maxSpeed, language),
            style = labelStyle,
            color = axisColor
        )
    }
}

/** Topo do eixo das barras: o maior valor arredondado para cima num número par (o meio fica inteiro). */
internal fun analysisAxisMax(maxValue: Double): Int {
    val ceiling = kotlin.math.ceil(maxValue).toInt().coerceAtLeast(2)
    return if (ceiling % 2 == 0) ceiling else ceiling + 1
}

/** Uma escala por grandeza: as barras são % da cota 5h, a vazão tem faixa e máximo próprios. */
private fun legend(maxSpeed: Double, language: AppLanguage): String {
    val bars = localized(language, "barras: % da cota 5h gasta em cada hora", "bars: % of the 5h quota spent in each hour")
    if (maxSpeed <= 0.0) return bars + localized(language, " · tok/s sem medição", " · tok/s not measured")
    return bars + localized(language, " · tok/s em faixa própria, máx ${maxSpeed.roundToInt()}", " · tok/s on its own strip, max ${maxSpeed.roundToInt()}")
}

package com.usagemonitor.presentation.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.PacePathPoint
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.components.color
import com.usagemonitor.presentation.ui.theme.AppGargantuaTokens
import com.usagemonitor.presentation.ui.theme.AppSpacing

internal const val PACE_DIAGONAL_TAG = "paceDiagonal"

private val PLOT_HEIGHT = 168.dp
private val AXIS_WIDTH = 36.dp
private const val REGION_ALPHA_ABOVE = 0.08f
private const val REGION_ALPHA_BELOW = 0.06f
private const val PREVIOUS_ALPHA = 0.45f

/**
 * Diagonal do ritmo (#463, direção AH1): eixo X é o tempo da janela que já passou, eixo Y a
 * cota usada, ambos escritos. A linha dourada tracejada chega a 100% exatamente no reinício —
 * acima dela a cota esgota antes, abaixo cabe —, e as duas regiões vêm com a frase, nunca só
 * com a cor. As outras janelas ficam atrás, em cinza; a projeção é tracejada.
 *
 * Estático: nada anima, porque o desenho é o dado. O leitor de tela lê a frase do modelo.
 */
@Composable
internal fun PaceDiagonalChart(model: PaceDiagonalModel, lineColor: Color, language: AppLanguage, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val haloColor = MaterialTheme.colorScheme.surface
    val okColor = AppTone.OK.color()
    val criticalColor = AppTone.CRITICAL.color()
    val labelStyle = MaterialTheme.typography.labelSmall
    val pointStyle = labelStyle.copy(color = MaterialTheme.colorScheme.onSurface)
    val above = PaceDiagonalLabels.above(language)
    val below = PaceDiagonalLabels.below(language)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.width(AXIS_WIDTH).height(PLOT_HEIGHT), verticalArrangement = Arrangement.SpaceBetween) {
                listOf("100%", "50%", "0%").forEach { label ->
                    Text(label, style = labelStyle, color = axisColor, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                }
            }
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(PLOT_HEIGHT)
                    .testTag(PACE_DIAGONAL_TAG)
                    .semantics { contentDescription = model.description }
            ) {
                val inset = 4.dp.toPx()
                val left = inset
                val right = size.width - inset
                val top = 0f
                val bottom = size.height
                fun x(fraction: Double) = left + (right - left) * fraction.toFloat()
                fun y(percent: Double) = bottom - (bottom - top) * (percent / 100.0).toFloat()

                drawRegions(left, right, top, bottom, criticalColor, okColor)
                listOf(0.0, 50.0, 100.0).forEach { value ->
                    drawLine(gridColor, Offset(left, y(value)), Offset(right, y(value)), 1.dp.toPx())
                }
                drawCaption(measurer, above, Offset(left + 6.dp.toPx(), top + 4.dp.toPx()), labelStyle.copy(color = criticalColor), alignEnd = false)
                drawCaption(measurer, below, Offset(right - 6.dp.toPx(), bottom - 18.dp.toPx()), labelStyle.copy(color = okColor), alignEnd = true)

                model.previous.forEach { path -> drawPath(path, ::x, ::y, axisColor.copy(alpha = PREVIOUS_ALPHA), 1.dp.toPx(), dashed = false) }
                drawLine(
                    color = AppGargantuaTokens.gold,
                    start = Offset(x(0.0), y(0.0)),
                    end = Offset(x(1.0), y(100.0)),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))
                )
                if (model.projection.size >= 2) {
                    drawPath(model.projection, ::x, ::y, if (model.projectionCritical) criticalColor else lineColor, 1.5.dp.toPx(), dashed = true)
                }
                drawPath(model.current, ::x, ::y, lineColor, 2.25.dp.toPx(), dashed = false)
                model.current.lastOrNull()?.let { last ->
                    val center = Offset(x(last.elapsedFraction), y(last.percent))
                    drawCircle(haloColor, radius = 5.5.dp.toPx(), center = center)
                    drawCircle(lineColor, radius = 3.5.dp.toPx(), center = center)
                    model.pointLabel?.let { label -> drawPointLabel(measurer, label, center, pointStyle) }
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(AXIS_WIDTH))
            model.axisLabels.forEachIndexed { index, label ->
                Text(
                    text = label,
                    style = labelStyle,
                    color = axisColor,
                    textAlign = when (index) {
                        0 -> TextAlign.Start
                        model.axisLabels.lastIndex -> TextAlign.End
                        else -> TextAlign.Center
                    },
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Text(
            text = PaceDiagonalLabels.timeAxis(language),
            style = labelStyle,
            color = axisColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun DrawScope.drawRegions(left: Float, right: Float, top: Float, bottom: Float, aboveColor: Color, belowColor: Color) {
    val aboveRegion = Path().apply {
        moveTo(left, bottom)
        lineTo(left, top)
        lineTo(right, top)
        close()
    }
    val belowRegion = Path().apply {
        moveTo(left, bottom)
        lineTo(right, bottom)
        lineTo(right, top)
        close()
    }
    drawPath(aboveRegion, aboveColor.copy(alpha = REGION_ALPHA_ABOVE))
    drawPath(belowRegion, belowColor.copy(alpha = REGION_ALPHA_BELOW))
}

private fun DrawScope.drawPath(
    points: List<PacePathPoint>,
    x: (Double) -> Float,
    y: (Double) -> Float,
    color: Color,
    width: Float,
    dashed: Boolean
) {
    if (points.isEmpty()) return
    val path = Path()
    points.forEachIndexed { index, point ->
        if (index == 0) path.moveTo(x(point.elapsedFraction), y(point.percent)) else path.lineTo(x(point.elapsedFraction), y(point.percent))
    }
    val effect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())) else null
    drawPath(path, color, style = Stroke(width = width, pathEffect = effect))
}

private fun DrawScope.drawCaption(measurer: TextMeasurer, text: String, anchor: Offset, style: TextStyle, alignEnd: Boolean) {
    val layout = measurer.measure(text, style)
    val leftX = if (alignEnd) anchor.x - layout.size.width else anchor.x
    drawText(layout, topLeft = Offset(leftX.coerceAtLeast(0f), anchor.y))
}

/** À esquerda do ponto; acima dele, ou abaixo quando o ponto está perto do topo. */
private fun DrawScope.drawPointLabel(measurer: TextMeasurer, text: String, point: Offset, style: TextStyle) {
    val layout = measurer.measure(text, style)
    val gap = 8.dp.toPx()
    val leftX = (point.x - gap - layout.size.width).coerceAtLeast(0f)
    val topY = if (point.y - gap - layout.size.height < 0f) point.y + gap else point.y - gap - layout.size.height
    drawText(layout, topLeft = Offset(leftX, topY.coerceIn(0f, (size.height - layout.size.height).coerceAtLeast(0f))))
}

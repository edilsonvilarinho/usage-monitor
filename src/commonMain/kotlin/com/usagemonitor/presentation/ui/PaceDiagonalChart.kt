package com.usagemonitor.presentation.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
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
import kotlin.math.roundToInt

internal const val PACE_DIAGONAL_TAG = "paceDiagonal"

private val PLOT_HEIGHT = 168.dp
private val AXIS_WIDTH = 36.dp
private const val REGION_ALPHA_ABOVE = 0.08f
private const val REGION_ALPHA_BELOW = 0.06f
private val POINT_HALO_RADIUS = 5.5.dp

/**
 * Placa atrás dos textos do gráfico: a linha que passa por baixo continua visível, apagada, e a letra
 * deixa de ser cortada por ela.
 */
private const val LABEL_PLATE_ALPHA = 0.8f
private val LABEL_PLATE_PADDING_X = 3.dp
private val LABEL_PLATE_PADDING_Y = 1.dp
private val LABEL_PLATE_RADIUS = 4.dp

/**
 * Diagonal do ritmo (#463, direção AH1; #538, A05a): eixo X é o tempo da janela que já passou, eixo Y a
 * cota usada, escrita até [PaceDiagonalModel.yMax]. A linha dourada tracejada chega a 100% exatamente no
 * reinício — acima dela a cota esgota antes, abaixo cabe —, e as duas regiões vêm com a frase, nunca só
 * com a cor. O marcador "agora" e os rótulos de fim e de esgotamento são texto de tela, não desenho do
 * Canvas, para o leitor de tela achar; a legenda vem embaixo. As outras janelas ficam atrás, em cinza;
 * a projeção é tracejada.
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
    val yMax = model.yMax.coerceAtLeast(100.0)
    val nowFraction = model.current.lastOrNull()?.elapsedFraction?.coerceIn(0.0, 1.0)
    val nowWeight = (nowFraction ?: 0.0).toFloat().coerceIn(0.01f, 0.99f)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.width(AXIS_WIDTH).height(PLOT_HEIGHT), verticalArrangement = Arrangement.SpaceBetween) {
                listOf(yMax, yMax / 2.0, 0.0).forEach { value ->
                    Text("${value.roundToInt()}%", style = labelStyle, color = axisColor, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
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
                fun y(percent: Double) = bottom - (bottom - top) * (percent / yMax).toFloat()
                val hundredY = y(100.0)

                drawRegions(left, right, top, bottom, hundredY, criticalColor, okColor)
                listOf(0.0, 50.0, 100.0).forEach { value ->
                    drawLine(gridColor, Offset(left, y(value)), Offset(right, y(value)), 1.dp.toPx())
                }
                if (nowFraction != null) {
                    drawLine(
                        color = axisColor,
                        start = Offset(x(nowFraction), top),
                        end = Offset(x(nowFraction), bottom),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx()))
                    )
                }
                val aboveCaption = measureLabel(measurer, above, Offset(left + 6.dp.toPx(), top + 4.dp.toPx()), labelStyle.copy(color = criticalColor), alignEnd = false)
                val belowCaption = measureLabel(measurer, below, Offset(right - 6.dp.toPx(), bottom - 18.dp.toPx()), labelStyle.copy(color = okColor), alignEnd = true)

                val previousAlpha = previousPathAlpha(model.previous.size)
                model.previous.forEach { path -> drawPath(path, ::x, ::y, axisColor.copy(alpha = previousAlpha), 1.dp.toPx(), dashed = false) }
                drawLine(
                    color = AppGargantuaTokens.gold,
                    start = Offset(x(0.0), y(0.0)),
                    end = Offset(x(1.0), hundredY),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))
                )
                if (model.projection.size >= 2) {
                    drawPath(model.projection, ::x, ::y, if (model.projectionCritical) criticalColor else lineColor, 1.5.dp.toPx(), dashed = true)
                }
                drawPath(model.current, ::x, ::y, lineColor, 2.25.dp.toPx(), dashed = false)
                val crossing = exhaustionFraction(model.projection)
                if (model.exhaustionLabel != null && crossing != null) {
                    drawCircle(criticalColor, radius = 3.5.dp.toPx(), center = Offset(x(crossing), hundredY))
                }
                val last = model.current.lastOrNull()
                var pointLabel: PlacedLabel? = null
                if (last != null) {
                    val center = Offset(x(last.elapsedFraction), y(last.percent))
                    drawCircle(haloColor, radius = POINT_HALO_RADIUS.toPx(), center = center)
                    drawCircle(lineColor, radius = 3.5.dp.toPx(), center = center)
                    val text = model.pointLabel
                    if (text != null) {
                        pointLabel = placePointLabel(measurer, text, center, pointStyle, avoid = listOf(aboveCaption.box, belowCaption.box))
                    }
                }
                // Textos por último, sobre placa da superfície (#477): desenhados antes, a projeção e as
                // janelas anteriores cortavam as letras.
                listOfNotNull(aboveCaption, belowCaption, pointLabel).forEach { placed -> drawPlacedLabel(placed, haloColor) }
            }
        }
        if (model.nowLabel != null) {
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.width(AXIS_WIDTH))
                Spacer(Modifier.weight(nowWeight))
                Text(model.nowLabel, style = labelStyle, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, modifier = Modifier.weight(1f - nowWeight))
            }
        }
        if (model.exhaustionLabel != null || model.endLabel != null) {
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.width(AXIS_WIDTH))
                if (model.exhaustionLabel != null) {
                    Text(model.exhaustionLabel, style = labelStyle, color = criticalColor, maxLines = 1, modifier = Modifier.weight(1f))
                }
                if (model.endLabel != null) {
                    Text(model.endLabel, style = labelStyle, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
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
        Column(Modifier.fillMaxWidth().padding(top = AppSpacing.xs), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
            model.legend.forEach { entry ->
                Text(entry, style = labelStyle, color = axisColor, maxLines = 1)
            }
        }
    }
}

/** Fração do tempo em que a projeção cruza os 100 %; nulo quando não cruza (cabe, ou já passou de 100 no agora). */
private fun exhaustionFraction(projection: List<PacePathPoint>): Double? =
    projection.zipWithNext().firstNotNullOfOrNull { (from, to) ->
        if (from.percent < 100.0 && to.percent >= 100.0) {
            from.elapsedFraction + (100.0 - from.percent) / (to.percent - from.percent) * (to.elapsedFraction - from.elapsedFraction)
        } else {
            null
        }
    }

private fun DrawScope.drawRegions(left: Float, right: Float, top: Float, bottom: Float, hundredY: Float, aboveColor: Color, belowColor: Color) {
    val aboveRegion = Path().apply {
        moveTo(left, bottom)
        lineTo(left, top)
        lineTo(right, top)
        lineTo(right, hundredY)
        close()
    }
    val belowRegion = Path().apply {
        moveTo(left, bottom)
        lineTo(right, bottom)
        lineTo(right, hundredY)
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

/** Um texto medido e a placa que o envolve; [box] é o que os outros textos evitam. */
private class PlacedLabel(val layout: TextLayoutResult, val box: PaceLabelBox)

private fun DrawScope.plateSize(layout: TextLayoutResult): Pair<Float, Float> =
    layout.size.width + LABEL_PLATE_PADDING_X.toPx() * 2 to layout.size.height + LABEL_PLATE_PADDING_Y.toPx() * 2

/** Legenda fixa: o texto no mesmo lugar de antes, a placa em volta dele. */
private fun DrawScope.measureLabel(measurer: TextMeasurer, text: String, anchor: Offset, style: TextStyle, alignEnd: Boolean): PlacedLabel {
    val layout = measurer.measure(text, style)
    val (width, height) = plateSize(layout)
    val textLeft = if (alignEnd) anchor.x - layout.size.width else anchor.x
    val plateLeft = (textLeft - LABEL_PLATE_PADDING_X.toPx()).coerceAtLeast(0f)
    return PlacedLabel(layout, PaceLabelBox(plateLeft, anchor.y - LABEL_PLATE_PADDING_Y.toPx(), width, height))
}

/** Rótulo do ponto mais novo, posto onde não encosta nas legendas nem no ponto ([placePacePointLabel]). */
private fun DrawScope.placePointLabel(measurer: TextMeasurer, text: String, point: Offset, style: TextStyle, avoid: List<PaceLabelBox>): PlacedLabel {
    val layout = measurer.measure(text, style)
    val (width, height) = plateSize(layout)
    val box = placePacePointLabel(
        pointX = point.x,
        pointY = point.y,
        labelWidth = width,
        labelHeight = height,
        gap = 8.dp.toPx(),
        pointRadius = POINT_HALO_RADIUS.toPx(),
        areaWidth = size.width,
        areaHeight = size.height,
        avoid = avoid
    )
    return PlacedLabel(layout, box)
}

private fun DrawScope.drawPlacedLabel(placed: PlacedLabel, plateColor: Color) {
    val box = placed.box
    drawRoundRect(
        color = plateColor.copy(alpha = LABEL_PLATE_ALPHA),
        topLeft = Offset(box.left, box.top),
        size = Size(box.width, box.height),
        cornerRadius = CornerRadius(LABEL_PLATE_RADIUS.toPx())
    )
    drawText(placed.layout, topLeft = Offset(box.left + LABEL_PLATE_PADDING_X.toPx(), box.top + LABEL_PLATE_PADDING_Y.toPx()))
}

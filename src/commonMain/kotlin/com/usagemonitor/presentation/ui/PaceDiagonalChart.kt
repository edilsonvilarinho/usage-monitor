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

internal const val PACE_DIAGONAL_TAG = "paceDiagonal"

private val PLOT_HEIGHT = 168.dp
private val AXIS_WIDTH = 36.dp
private const val REGION_ALPHA_ABOVE = 0.08f
private const val REGION_ALPHA_BELOW = 0.06f
private const val PREVIOUS_ALPHA = 0.45f
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
                val aboveCaption = measureLabel(measurer, above, Offset(left + 6.dp.toPx(), top + 4.dp.toPx()), labelStyle.copy(color = criticalColor), alignEnd = false)
                val belowCaption = measureLabel(measurer, below, Offset(right - 6.dp.toPx(), bottom - 18.dp.toPx()), labelStyle.copy(color = okColor), alignEnd = true)

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
                val pointLabel = model.current.lastOrNull()?.let { last ->
                    val center = Offset(x(last.elapsedFraction), y(last.percent))
                    drawCircle(haloColor, radius = POINT_HALO_RADIUS.toPx(), center = center)
                    drawCircle(lineColor, radius = 3.5.dp.toPx(), center = center)
                    model.pointLabel?.let { label ->
                        placePointLabel(measurer, label, center, pointStyle, avoid = listOf(aboveCaption.box, belowCaption.box))
                    }
                }
                // Textos por último, sobre placa da superfície (#477): desenhados antes, a projeção e as
                // janelas anteriores cortavam as letras.
                listOfNotNull(aboveCaption, belowCaption, pointLabel).forEach { placed -> drawPlacedLabel(placed, haloColor) }
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

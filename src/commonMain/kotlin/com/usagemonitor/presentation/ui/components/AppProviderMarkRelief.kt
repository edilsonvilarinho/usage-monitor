package com.usagemonitor.presentation.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.Painter
import kotlin.math.round

/** As cores do relevo da marca (AK1, #517), derivadas do tingimento de quem usa. */
internal class ProviderMarkRelief(val top: Color, val bottom: Color)

/** Claro em cima, escuro embaixo: a luz vem de cima à esquerda, como nos arcos de vidro (−135°). */
internal fun providerMarkRelief(tint: Color): ProviderMarkRelief = ProviderMarkRelief(
    top = lerp(tint, Color.White.copy(alpha = tint.alpha), 0.2f),
    bottom = lerp(tint, Color.Black.copy(alpha = tint.alpha), 0.3f)
)

/** Espessura da borda acesa e da sombreada, em pixels, para uma marca de [sizePx]: 5%, nunca menos de 1. */
internal fun providerMarkReliefDepth(sizePx: Float): Float = maxOf(1f, round(sizePx * 0.05f))

private val RELIEF_HIGHLIGHT = Color(0xFFFFF8E6).copy(alpha = 0.9f)
private val RELIEF_SHADE = Color.Black.copy(alpha = 0.6f)
private val RELIEF_SHADOW = Color.Black.copy(alpha = 0.45f)

/**
 * A marca com volume (AK1): sombra curta deslocada para baixo à direita, corpo
 * em gradiente vertical, a borda de cima à esquerda acesa e a de baixo à
 * direita na sombra. As bordas são a silhueta menos ela mesma deslocada
 * ([BlendMode.DstOut]), aplicadas só sobre o corpo ([BlendMode.SrcAtop]).
 * Sem blur, como o cenário Gargantua.
 */
internal fun DrawScope.drawProviderMarkRelief(painter: Painter, tint: Color) {
    val relief = providerMarkRelief(tint)
    val depth = providerMarkReliefDepth(size.minDimension)
    val bounds = Rect(Offset(-depth * 2, -depth * 2), Offset(size.width + depth * 2, size.height + depth * 2))
    translate(depth * 0.6f, depth * 1.6f) { drawSilhouette(painter, RELIEF_SHADOW.copy(alpha = RELIEF_SHADOW.alpha * tint.alpha)) }
    drawIntoCanvas { canvas ->
        canvas.saveLayer(bounds, Paint())
        drawSilhouette(painter, Color.White)
        drawRect(Brush.verticalGradient(listOf(relief.top, relief.bottom)), blendMode = BlendMode.SrcIn)
        drawReliefEdge(painter, bounds, RELIEF_HIGHLIGHT, depth)
        drawReliefEdge(painter, bounds, RELIEF_SHADE, -depth)
        canvas.restore()
    }
}

/** A borda do lado oposto a [shift]: a silhueta menos ela mesma deslocada [shift] nos dois eixos. */
private fun DrawScope.drawReliefEdge(painter: Painter, bounds: Rect, color: Color, shift: Float) {
    drawIntoCanvas { canvas ->
        canvas.saveLayer(bounds, Paint().apply { blendMode = BlendMode.SrcAtop })
        drawSilhouette(painter, color)
        canvas.saveLayer(bounds, Paint().apply { blendMode = BlendMode.DstOut })
        translate(shift, shift) { drawSilhouette(painter, Color.White) }
        canvas.restore()
        canvas.restore()
    }
}

private fun DrawScope.drawSilhouette(painter: Painter, color: Color) {
    with(painter) { draw(size, colorFilter = ColorFilter.tint(color)) }
}

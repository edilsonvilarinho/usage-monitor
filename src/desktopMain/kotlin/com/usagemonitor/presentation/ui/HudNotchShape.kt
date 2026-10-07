package com.usagemonitor.presentation.ui

import androidx.compose.foundation.layout.size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.usagemonitor.HUD_NOTCH_RADIUS
import com.usagemonitor.HUD_NOTCH_SHOULDER
import com.usagemonitor.HudEdge

/**
 * A silhueta do notch: reta e rente na borda da tela, cantos redondos do lado de
 * dentro e **ombros côncavos** ligando os dois — o que faz ele ler como parte da
 * borda, como o notch de hardware que o Codenotch imita, e não como pílula
 * flutuando rente a ela. Isenta do teto de 10dp de raio: é forma, não painel.
 */
internal class HudNotchShape(private val edge: HudEdge) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { HUD_NOTCH_RADIUS.toPx() }
        // Desenhado para a borda de cima em (along, across) e levado às outras
        // bordas por reflexão/rotação dos pontos — de controle inclusive, que é
        // afim e portanto preserva as curvas.
        val along = if (edge.isHorizontal) size.width else size.height
        val across = if (edge.isHorizontal) size.height else size.width
        // A faixa recolhida (#400) tem 10dp: com o ombro inteiro de 8dp o corpo
        // voltaria para trás dele. Abaixo de 16dp o ombro encolhe junto; o notch,
        // de 44dp para cima, não muda.
        val s = minOf(with(density) { HUD_NOTCH_SHOULDER.toPx() }, across / 2)
        val radius = minOf(r, (along - 2 * s) / 2, across / 2).coerceAtLeast(0f)
        val map: (Float, Float) -> Offset = when (edge) {
            HudEdge.TOP -> { a, c -> Offset(a, c) }
            HudEdge.BOTTOM -> { a, c -> Offset(a, size.height - c) }
            HudEdge.LEFT -> { a, c -> Offset(c, a) }
            HudEdge.RIGHT -> { a, c -> Offset(size.width - c, a) }
        }
        val path = Path()
        fun moveTo(a: Float, c: Float) = map(a, c).let { point -> path.moveTo(point.x, point.y) }
        fun lineTo(a: Float, c: Float) = map(a, c).let { point -> path.lineTo(point.x, point.y) }
        fun quadTo(ca: Float, cc: Float, a: Float, c: Float) {
            val control = map(ca, cc)
            val end = map(a, c)
            path.quadraticTo(control.x, control.y, end.x, end.y)
        }
        moveTo(0f, 0f)
        lineTo(along, 0f)
        // Ombro côncavo: da borda da tela até o corpo do notch.
        quadTo(along - s, 0f, along - s, s)
        lineTo(along - s, across - radius)
        quadTo(along - s, across, along - s - radius, across)
        lineTo(s + radius, across)
        quadTo(s, across, s, across - radius)
        lineTo(s, s)
        quadTo(s, 0f, 0f, 0f)
        path.close()
        return Outline.Generic(path)
    }

    override fun equals(other: Any?): Boolean = other is HudNotchShape && other.edge == edge

    override fun hashCode(): Int = edge.hashCode()
}

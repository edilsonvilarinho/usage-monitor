package com.usagemonitor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import com.usagemonitor.domain.entity.UsageRiskLevel

/** Lado do ícone quando o recurso do app não pôde ser carregado. */
private const val FALLBACK_ICON_SIDE = 32f

/** Raio do ponto, como fração do menor lado do ícone. */
private const val BADGE_RADIUS_FRACTION = 0.22f

/** Contorno escuro sob o ponto; sem ele o vermelho some numa bandeja escura. */
private const val BADGE_OUTLINE_SCALE = 1.35f

/**
 * Ícone da bandeja: o ícone do app com um ponto de risco no canto.
 *
 * Trocar o ícone inteiro por uma bola colorida seria mais simples e custaria a
 * identidade do app na bandeja, onde ele divide espaço com uma dúzia de outros.
 *
 * [equals] é sobrescrito de propósito: o `Tray` reconstrói a imagem AWT quando o
 * painter muda, e um painter novo a cada recomposição faria isso sem parar.
 */
internal class TrayRiskIconPainter(
    private val base: Painter?,
    private val riskLevel: UsageRiskLevel?
) : Painter() {

    override val intrinsicSize: Size
        get() = base?.intrinsicSize ?: Size(FALLBACK_ICON_SIDE, FALLBACK_ICON_SIDE)

    override fun DrawScope.onDraw() {
        if (base != null) {
            with(base) { draw(size) }
        }

        val color = trayRiskColor(riskLevel) ?: return
        val radius = size.minDimension * BADGE_RADIUS_FRACTION
        val center = Offset(size.width - radius, size.height - radius)

        drawCircle(color = Color(0xFF101010), radius = radius * BADGE_OUTLINE_SCALE, center = center)
        drawCircle(color = color, radius = radius, center = center)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is TrayRiskIconPainter) {
            return false
        }
        return base == other.base && riskLevel == other.riskLevel
    }

    override fun hashCode(): Int {
        return 31 * (base?.hashCode() ?: 0) + (riskLevel?.hashCode() ?: 0)
    }
}

/**
 * Cor do ponto por nível de risco. `null` — inclusive em [UsageRiskLevel.ON_TRACK] —
 * significa ícone limpo: um ponto verde permanente vira decoração e o olho para
 * de registrá-lo.
 *
 * Os valores são os mesmos de `colorFor` nos cards; repetidos aqui porque aquela
 * função é `internal` de `presentation.ui.components` e depende do tema, que a
 * bandeja não tem.
 */
internal fun trayRiskColor(level: UsageRiskLevel?): Color? {
    return when (level) {
        UsageRiskLevel.AT_RISK -> Color(0xFFFFC107)
        UsageRiskLevel.WILL_EXCEED -> Color(0xFFF44336)
        UsageRiskLevel.ON_TRACK, null -> null
    }
}

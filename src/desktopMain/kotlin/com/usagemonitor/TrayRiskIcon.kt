package com.usagemonitor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import com.usagemonitor.domain.entity.UsageRiskLevel

/** Lado do ícone quando o recurso do app não pôde ser carregado. */
private const val FALLBACK_ICON_SIDE = 32f

/** Raio do ponto, como fração do menor lado do ícone. */
private const val BADGE_RADIUS_FRACTION = 0.22f

/** Contorno escuro sob o ponto; sem ele o vermelho some numa bandeja escura. */
private const val BADGE_OUTLINE_SCALE = 1.35f

/** Espessura do anel de uso, como fração do menor lado do ícone. */
private const val RING_STROKE_FRACTION = 0.14f

/**
 * Ícone da bandeja: o ícone do app com um ponto de risco no canto e, se o
 * usuário ligou, o anel de uso em volta (issue #328).
 *
 * Trocar o ícone inteiro por uma bola colorida seria mais simples e custaria a
 * identidade do app na bandeja, onde ele divide espaço com uma dúzia de outros.
 *
 * [equals] é sobrescrito de propósito: o `Tray` reconstrói a imagem AWT quando o
 * painter muda, e um painter novo a cada recomposição faria isso sem parar.
 */
internal class TrayRiskIconPainter(
    private val base: Painter?,
    private val riskLevel: UsageRiskLevel?,
    /** Fração do anel (0 a 1), ou `null` para ícone sem anel. */
    private val ringFraction: Float? = null
) : Painter() {

    override val intrinsicSize: Size
        get() = base?.intrinsicSize ?: Size(FALLBACK_ICON_SIDE, FALLBACK_ICON_SIDE)

    override fun DrawScope.onDraw() {
        if (base != null) {
            with(base) { draw(size) }
        }
        if (ringFraction != null) {
            drawUsageRing(ringFraction)
        }

        val color = trayRiskColor(riskLevel) ?: return
        val radius = size.minDimension * BADGE_RADIUS_FRACTION
        val center = Offset(size.width - radius, size.height - radius)

        drawCircle(color = Color(0xFF101010), radius = radius * BADGE_OUTLINE_SCALE, center = center)
        drawCircle(color = color, radius = radius, center = center)
    }

    /**
     * Trilho escuro na volta inteira e arco por cima, do topo no sentido horário.
     * O trilho é o que deixa o arco legível sobre qualquer bandeja, clara ou
     * escura, do mesmo jeito que o contorno do ponto.
     */
    private fun DrawScope.drawUsageRing(fraction: Float) {
        val stroke = size.minDimension * RING_STROKE_FRACTION
        val inset = stroke / 2f
        val arcSize = Size(size.width - stroke, size.height - stroke)
        val topLeft = Offset(inset, inset)
        drawArc(
            color = Color(0xFF101010),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke)
        )
        drawArc(
            color = trayRingColor(fraction),
            startAngle = -90f,
            sweepAngle = 360f * fraction.coerceIn(0f, 1f),
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke)
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is TrayRiskIconPainter) {
            return false
        }
        return base == other.base && riskLevel == other.riskLevel && ringFraction == other.ringFraction
    }

    override fun hashCode(): Int {
        val baseHash = 31 * (base?.hashCode() ?: 0) + (riskLevel?.hashCode() ?: 0)
        return 31 * baseHash + (ringFraction?.hashCode() ?: 0)
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

/**
 * Cor do arco pelos cortes de 75 e 90 que os alertas da bandeja já usam. Abaixo
 * deles, cinza claro e não verde: o anel está lá o tempo todo, e verde permanente
 * vira decoração, pelo mesmo motivo que o ponto some em `ON_TRACK`.
 */
internal fun trayRingColor(fraction: Float): Color {
    return when {
        fraction >= 0.9f -> Color(0xFFF44336)
        fraction >= 0.75f -> Color(0xFFFFC107)
        else -> Color(0xFFE0E0E0)
    }
}

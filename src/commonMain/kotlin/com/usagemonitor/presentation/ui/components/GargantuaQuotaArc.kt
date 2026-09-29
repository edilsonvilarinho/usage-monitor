package com.usagemonitor.presentation.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** A luz vem do alto à esquerda, como na referência de vidro do protótipo. */
private const val LIGHT_DEGREES = -135.0

/** Paradas do gradiente de varredura que simula o reflexo ao longo do tubo. */
private const val REFLECTION_STOPS = 24

/** Pulsos de plasma por arco, igualmente espaçados no ciclo. */
private const val FLOW_PULSES = 3

/**
 * Arco de quota como tubo de vidro com plasma dentro. O vidro (parede e
 * reflexos) é a trilha; o plasma no tom semântico é o dado. Os pulsos que
 * correm ficam dentro do sweep e se apagam nas pontas: o comprimento do arco
 * nunca muda com a animação.
 */
internal fun DrawScope.drawGargantuaQuotaArc(
    color: Color,
    sweep: Float,
    radius: Float,
    stroke: Float,
    hasForecast: Boolean,
    glow: Float,
    flow: Float?,
    /** Opacidade do vidro; só fica abaixo de 1 enquanto o indicador nasce. */
    glass: Float = 1f
) {
    val topLeft = center - Offset(radius, radius)
    val arcSize = Size(radius * 2, radius * 2)
    if (glass > 0f) drawGlassTube(radius, stroke, hasForecast, glass)
    if (sweep <= 0f) return
    drawArc(color.copy(alpha = glow), -90f, sweep, false, topLeft, arcSize,
        style = Stroke(stroke * 2.4f, cap = StrokeCap.Round))
    drawArc(color, -90f, sweep, false, topLeft, arcSize, style = Stroke(stroke * 0.9f, cap = StrokeCap.Round))
    drawArc(lerp(color, Color.White, 0.6f), -90f, sweep, false, topLeft, arcSize,
        style = Stroke(stroke * 0.35f, cap = StrokeCap.Round))
    if (flow != null) drawPlasmaPulses(sweep, radius, stroke, flow)
    // O reflexo do vidro passa por cima do plasma, só onde há plasma.
    val inner = radius - stroke * 0.3f
    drawArc(
        reflection(maxAlpha = 0.4f * glass, power = 2), -90f, sweep, false,
        center - Offset(inner, inner), Size(inner * 2, inner * 2),
        style = Stroke(stroke * 0.16f)
    )
}

/** Parede translúcida, reflexo forte na borda de dentro e fraco na de fora. */
private fun DrawScope.drawGlassTube(radius: Float, stroke: Float, hasForecast: Boolean, glass: Float) {
    val dash = if (hasForecast) null else PathEffect.dashPathEffect(floatArrayOf(stroke, stroke * 1.4f))
    drawCircle(Color.White.copy(alpha = 0.06f * glass), radius, style = Stroke(stroke * 1.4f, pathEffect = dash))
    drawCircle(reflection(maxAlpha = 0.28f * glass, power = 3), radius - stroke / 2, style = Stroke(stroke * 0.18f))
    drawCircle(reflection(maxAlpha = 0.12f * glass, power = 1), radius + stroke / 2, style = Stroke(stroke * 0.14f))
}

/** Branco cuja opacidade segue a luz: máximo voltado para a fonte, zero do lado oposto. */
private fun DrawScope.reflection(maxAlpha: Float, power: Int): Brush {
    val stops = Array(REFLECTION_STOPS + 1) { index ->
        val fraction = index / REFLECTION_STOPS.toFloat()
        val lit = (0.5 + 0.5 * cos(fraction * 2 * PI - LIGHT_DEGREES * PI / 180)).toFloat()
        var shaded = 1f
        repeat(power) { shaded *= lit }
        fraction to Color.White.copy(alpha = maxAlpha * shaded)
    }
    return Brush.sweepGradient(*stops, center = center)
}

/** Pontos de luz viajando no plasma; somem perto das pontas para o laço não saltar. */
private fun DrawScope.drawPlasmaPulses(sweep: Float, radius: Float, stroke: Float, flow: Float) {
    val size = stroke * 0.96f
    for (pulse in 0 until FLOW_PULSES) {
        val position = (flow + pulse / FLOW_PULSES.toFloat()) % 1f
        val fade = sin(position * PI).toFloat()
        if (fade <= 0.01f) continue
        val angle = (position * sweep - 90f) * PI / 180
        val point = center + Offset(radius * cos(angle).toFloat(), radius * sin(angle).toFloat())
        drawCircle(
            Brush.radialGradient(listOf(Color.White.copy(alpha = 0.9f * fade), Color.Transparent), point, size),
            size, point
        )
    }
}

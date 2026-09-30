package com.usagemonitor.presentation.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.usagemonitor.presentation.ui.theme.AppGargantuaTokens
import com.usagemonitor.presentation.ui.theme.LocalAppMotionPolicy
import kotlin.math.PI
import kotlin.math.sin

/**
 * Brilho do contorno em `0.85..1` (M1 · horizonte de eventos), [elapsedMillis]
 * depois do início do laço de `horizonBreathMillis`. O quadro zero é o meio da
 * respiração, e é ele que fica parado sem a política contínua.
 */
fun gargantuaHorizonBreath(elapsedMillis: Long): Float {
    val period = AppGargantuaTokens.horizonBreathMillis
    val phase = (elapsedMillis.mod(period.toLong())).toFloat() / period
    val wave = 0.5f + 0.5f * sin(phase * 2f * PI.toFloat())
    return HORIZON_BREATH_FLOOR + (1f - HORIZON_BREATH_FLOOR) * wave
}

/**
 * As três paradas do anel de fótons, do lado que se afasta (brasa, embaixo à
 * esquerda) ao que se aproxima (quente, em cima à direita) — o mesmo Doppler do
 * disco do anel. No tema escuro a luz é a do Gargantua; no claro o dourado some
 * contra a superfície, e o contorno usa os tons escuros da mesma paleta.
 */
fun gargantuaHorizonRimColors(dark: Boolean, breath: Float): List<Color> {
    val k = breath.coerceIn(0f, 1f)
    return if (dark) {
        listOf(
            AppGargantuaTokens.ember.copy(alpha = 0.35f * k),
            AppGargantuaTokens.gold.copy(alpha = 0.40f * k),
            AppGargantuaTokens.hot.copy(alpha = 0.60f * k)
        )
    } else {
        listOf(
            AppGargantuaTokens.ember.copy(alpha = 0.55f * k),
            AppGargantuaTokens.dust.copy(alpha = 0.70f * k),
            AppGargantuaTokens.gold.copy(alpha = 0.95f * k)
        )
    }
}

/** O filete de dentro do anel de fótons: quase apagado, só no tema escuro. */
fun gargantuaHorizonInnerGlow(breath: Float): Color {
    return AppGargantuaTokens.hot.copy(alpha = 0.08f * breath.coerceIn(0f, 1f))
}

/**
 * O corpo da HUD como o próprio horizonte de eventos (M1, rodada de opções em
 * HTML): no tema escuro o fundo vira o núcleo escuro e o brilho de topo sai; em
 * qualquer tema a borda é um anel de fótons de 1dp com Doppler. O brilho respira
 * só com `continuous && !reduced`; sem isso fica o quadro zero. Nada muda de
 * tamanho: é pintura dentro da mesma caixa.
 */
@Composable
fun Modifier.gargantuaHorizonBody(shape: Shape): Modifier {
    val surface = MaterialTheme.colorScheme.surface
    val dark = surface.luminance() < 0.5f
    val policy = LocalAppMotionPolicy.current
    // Lido só no desenho: o laço repinta a borda sem recompor o notch.
    val elapsed = horizonElapsedState(policy.continuous && !policy.reduced)
    val clipped = this.clip(shape)
    val body = if (dark) clipped.background(AppGargantuaTokens.horizon) else clipped.background(surface).appSheen()
    return body.drawWithContent {
        drawContent()
        val breath = gargantuaHorizonBreath(elapsed.value.toLong())
        val outline = shape.createOutline(size, layoutDirection, this)
        if (dark) {
            drawOutline(outline, gargantuaHorizonInnerGlow(breath), style = Stroke(width = HORIZON_INNER_GLOW.toPx()))
        }
        // O traço é centrado no contorno e o corte pela forma leva a metade de
        // fora: 2dp de traço dão o 1dp de borda de sempre.
        val rim = Brush.linearGradient(
            colors = gargantuaHorizonRimColors(dark, breath),
            start = Offset(0f, size.height),
            end = Offset(size.width, 0f)
        )
        drawOutline(outline, rim, style = Stroke(width = HORIZON_RIM.toPx()))
    }
}

@Composable
private fun horizonElapsedState(enabled: Boolean): State<Float> {
    if (!enabled) return rememberUpdatedState(0f)
    val period = AppGargantuaTokens.horizonBreathMillis
    return rememberInfiniteTransition(label = "horizonBreath").animateFloat(
        initialValue = 0f,
        targetValue = period.toFloat(),
        animationSpec = infiniteRepeatable(tween(period, easing = LinearEasing)),
        label = "horizonBreathMillis"
    )
}

/** O piso da respiração: o contorno nunca apaga mais que isso. */
private const val HORIZON_BREATH_FLOOR = 0.85f

/**
 * Largura do traço do filete de dentro. O corte pela forma deixa só a metade de
 * dentro, 2,5dp de luz rente à borda.
 */
private val HORIZON_INNER_GLOW = 5.dp

/** Traço do anel de fótons antes do corte: metade fica visível, 1dp. */
private val HORIZON_RIM = 2.dp

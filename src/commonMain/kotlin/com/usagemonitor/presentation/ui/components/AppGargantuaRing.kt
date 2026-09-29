package com.usagemonitor.presentation.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.usagemonitor.presentation.ui.theme.AppGargantuaTokens
import com.usagemonitor.presentation.ui.theme.AppMotion
import com.usagemonitor.presentation.ui.theme.LocalAppMotionPolicy
import com.usagemonitor.presentation.ui.theme.appSpringSpec
import com.usagemonitor.presentation.ui.theme.appTween
import kotlin.math.PI
import kotlin.math.sin

/**
 * Gargantua ocupa o miolo; os arcos externos continuam sendo dados de quota.
 * O disco dourado é ambiente e discreto, a órbita azul é sessão ativa, cada
 * arco é plasma num tubo de vidro, e só a cota em atenção respira. Nenhum movimento muda o
 * comprimento de um arco.
 */
@Composable
fun AppGargantuaRing(
    arcs: List<AppRingArc>,
    description: String,
    modifier: Modifier = Modifier,
    size: Dp = AppGargantuaTokens.size,
    stroke: Dp = 2.5.dp,
    gap: Dp = 1.5.dp,
    active: Boolean = false,
    attention: Boolean = false,
    attentionIndex: Int = 0,
    refreshing: Boolean = false
) {
    val policy = LocalAppMotionPolicy.current
    val moving = policy.continuous && !policy.reduced
    // O estado é lido só no desenho: a órbita não recompõe texto nem a janela.
    val phase = gargantuaPhase(moving, if (refreshing) AppGargantuaTokens.refreshMillis else AppGargantuaTokens.orbitMillis)
    val activity = gargantuaPhase(moving && active, AppGargantuaTokens.activeMillis)
    val breath = gargantuaPhase(moving && attention, AppGargantuaTokens.attentionMillis)
    val flow = gargantuaPhase(moving, AppGargantuaTokens.flowMillis)
    val colors = List(MAX_RING_ARCS) { index ->
        animateColorAsState(
            targetValue = arcs.getOrNull(index)?.tone?.color() ?: Color.Transparent,
            animationSpec = appTween(AppMotion.slow),
            label = "gargantuaQuotaColor$index"
        )
    }
    val sweeps = List(MAX_RING_ARCS) { index ->
        val target = arcs.getOrNull(index)?.fraction?.coerceIn(0f, 1f) ?: 0f
        val sweep = remember { Animatable(if (policy.reduced) target else 0f) }
        LaunchedEffect(target, policy) {
            sweep.animateTo(target, appSpringSpec(AppMotion.Springs.GENTLE, policy, 0.001f))
        }
        sweep.asState()
    }
    val activeColor = AppTone.INFO.color()
    Canvas(modifier.size(size).semantics { contentDescription = description }) {
        val strokePx = stroke.toPx()
        val gapPx = gap.toPx()
        val coreSpace = this.size.minDimension / 2f - arcs.size.coerceIn(1, MAX_RING_ARCS) * (strokePx + gapPx)
        drawGargantuaCore(coreSpace, phase.value)
        arcs.take(MAX_RING_ARCS).forEachIndexed { index, arc ->
            val radius = this.size.minDimension / 2f - strokePx / 2 - index * (strokePx + gapPx)
            if (radius <= 0f) return@forEachIndexed
            val glow = if (attention && index == attentionIndex && moving) {
                0.18f + 0.2f * ((sin(breath.value * PI * 2).toFloat() + 1f) / 2f)
            } else 0.25f
            drawGargantuaQuotaArc(
                color = colors[index].value,
                sweep = sweeps[index].value.coerceIn(0f, 1f) * 360f,
                radius = radius,
                stroke = strokePx,
                hasForecast = arc.hasForecast,
                glow = glow,
                flow = if (moving) flow.value else null
            )
        }
        if (active) drawGargantuaActivity(activity.value, strokePx, gapPx, activeColor)
    }
}

@Composable
private fun gargantuaPhase(enabled: Boolean, durationMillis: Int): State<Float> {
    if (!enabled) return rememberUpdatedState(0f)
    return rememberInfiniteTransition(label = "gargantuaMotion").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis, easing = LinearEasing)),
        label = "gargantuaPhase"
    )
}

/** A marca fica dentro do horizonte, sem cruzar o disco de acreção. */
fun appGargantuaMarkSize(size: Dp, stroke: Dp, gap: Dp, arcs: Int): Dp {
    val innerRadius = size / 2 - (stroke + gap) * arcs.coerceIn(1, MAX_RING_ARCS)
    return (innerRadius * 0.88f).coerceAtLeast(6.dp)
}

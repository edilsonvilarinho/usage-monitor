package com.usagemonitor.presentation.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import com.usagemonitor.presentation.ui.theme.AppMotionPolicy
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
    refreshing: Boolean = false,
    /** Nascimento ou colapso em andamento; parado por padrão. */
    frame: GargantuaFrame = GargantuaFrame.Settled
) {
    val policy = LocalAppMotionPolicy.current
    val moving = policy.continuous && !policy.reduced
    // O estado é lido só no desenho: a órbita não recompõe texto nem a janela.
    val phase = gargantuaPhase(moving, if (refreshing) AppGargantuaTokens.refreshMillis else AppGargantuaTokens.orbitMillis)
    val activity = gargantuaPhase(moving && active, AppGargantuaTokens.activeMillis)
    val breath = gargantuaPhase(moving && attention, AppGargantuaTokens.attentionMillis)
    val flow = gargantuaPhase(moving, AppGargantuaTokens.flowMillis)
    // Cota sem projeção: anel de detritos em órbita (J7), só quando algum arco precisa.
    val debris = gargantuaPhase(moving && arcs.any { arc -> !arc.hasForecast }, AppGargantuaTokens.debrisCycleMillis)
    // Coletando: ondas gravitacionais saem do anel (contínuo, atrás da política).
    val ripple = gargantuaPhase(moving && refreshing, AppGargantuaTokens.rippleMillis)
    // Coleta concluída: uma onda final, uma vez. Finita, então só "Reduzir" a desliga.
    val completion = remember { Animatable(1f) }
    var wasRefreshing by remember { mutableStateOf(refreshing) }
    LaunchedEffect(refreshing, policy) {
        val wave = shouldPlayRefreshWave(wasRefreshing, refreshing, policy)
        wasRefreshing = refreshing
        if (wave) {
            completion.snapTo(0f)
            completion.animateTo(1f, tween(AppGargantuaTokens.refreshWaveMillis, easing = LinearEasing))
        } else {
            completion.snapTo(1f)
        }
    }
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
    Box(modifier.size(size).semantics { contentDescription = description }) {
        Canvas(
            Modifier.matchParentSize().graphicsLayer {
                scaleX = frame.scale
                scaleY = frame.scale
                alpha = frame.alpha
            }
        ) {
            val strokePx = stroke.toPx()
            val gapPx = gap.toPx()
            val coreSpace = this.size.minDimension / 2f - arcs.size.coerceIn(1, MAX_RING_ARCS) * (strokePx + gapPx)
            if (frame.core > 0f) {
                scale(frame.core, pivot = center) { drawGargantuaCore(coreSpace, phase.value) }
            }
            arcs.take(MAX_RING_ARCS).forEachIndexed { index, arc ->
                val radius = this.size.minDimension / 2f - strokePx / 2 - index * (strokePx + gapPx)
                if (radius <= 0f) return@forEachIndexed
                val glow = if (attention && index == attentionIndex && moving) {
                    0.18f + 0.2f * ((sin(breath.value * PI * 2).toFloat() + 1f) / 2f)
                } else 0.25f
                drawGargantuaQuotaArc(
                    color = colors[index].value,
                    sweep = sweeps[index].value.coerceIn(0f, 1f) * frame.arcs * 360f,
                    radius = radius,
                    stroke = strokePx,
                    hasForecast = arc.hasForecast,
                    glow = glow,
                    flow = if (moving) flow.value else null,
                    glass = frame.glass,
                    debris = debris.value
                )
            }
            if (active) drawGargantuaActivity(activity.value, strokePx, gapPx, activeColor)
        }
        // Clarão e ondas ficam fora da escala: o indicador encolhe, a luz não.
        if (!frame.settled) {
            Canvas(Modifier.matchParentSize()) { drawGargantuaTransitionLight(frame) }
        }
        val rippling = moving && refreshing
        if (rippling || completion.value < 1f) {
            Canvas(Modifier.matchParentSize()) {
                drawGargantuaRefreshLight(if (rippling) ripple.value else null, completion.value)
            }
        }
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

/**
 * A onda final toca quando a coleta **termina** — `refreshing` cai de verdadeiro
 * a falso —, não quando começa nem na primeira composição. Vale também para
 * coleta que falhou (o view model desmarca o alvo nos dois casos): a onda diz
 * "o app olhou agora", não "o número mudou". Com "Reduzir animações", nada.
 */
fun shouldPlayRefreshWave(wasRefreshing: Boolean, refreshing: Boolean, policy: AppMotionPolicy): Boolean {
    return wasRefreshing && !refreshing && !policy.reduced
}

package com.usagemonitor.presentation.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.usagemonitor.presentation.ui.theme.AppMotion
import com.usagemonitor.presentation.ui.theme.AppSurfaceLadders
import com.usagemonitor.presentation.ui.theme.LocalAppMotionPolicy
import com.usagemonitor.presentation.ui.theme.appSpring
import com.usagemonitor.presentation.ui.theme.appTween

/** Um arco do anel: quanto da cota foi usado e em que tom. */
@Immutable
data class AppRingArc(
    /** 0..1; o anel não dá mais de uma volta. */
    val fraction: Float,
    val tone: AppTone,
    /**
     * Sem projeção, a trilha do arco é **tracejada**: cor nenhuma pode sugerir um
     * estado que ninguém calculou, e o tracejado diz "aqui não há veredito" sem
     * depender de tom.
     */
    val hasForecast: Boolean = true
)

/**
 * Anel de uso: um arco por cota, concêntricos — o de fora é a primeira cota da
 * API (a janela curta), os de dentro as seguintes.
 *
 * É o desenho do Codenotch, trocado num ponto: lá é um anel por fornecedor com a
 * pior janela; aqui é um arco **por cota**, porque um anel com o percentual da
 * pior janela esconde a outra — a 7d estourada atrás de uma 5h em 12%.
 *
 * **Nunca informa sozinho.** Quem o usa põe o percentual e a palavra do estado ao
 * lado; a [description] leva as duas coisas para a semântica.
 *
 * Movimento:
 * - Cada arco anda pela mola `GENTLE`, sem rebote — arco que passa do valor e
 *   volta mostra um percentual que não é verdade.
 * - [active] (sessão CLI com turno nos últimos 5 min) desenha um arco fino
 *   girando por dentro, e [attention] faz o anel de fora pulsar; os dois **só**
 *   com `AppMotionPolicy.continuous`, que é desligada em testes e geradores. Sem
 *   ela o estado continua dito: o arco ativo fica parado e o pulso some, e a
 *   palavra ao lado continua lá.
 */
@Composable
fun AppUsageRing(
    arcs: List<AppRingArc>,
    description: String,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    stroke: Dp = 3.dp,
    gap: Dp = 1.5.dp,
    active: Boolean = false,
    attention: Boolean = false
) {
    val policy = LocalAppMotionPolicy.current
    val ladder = AppSurfaceLadders.current
    // A trilha é a camada de pressão com um pouco mais de peso: visível como
    // "o que falta" sem competir com o arco, que é o dado.
    val track = ladder.pressedLayer.copy(alpha = ladder.pressedLayer.alpha * RING_TRACK_WEIGHT)
    val accents = arcs.map { arc -> arc.tone.color() }
    val activeColor = AppTone.INFO.color()

    // Um `animate*AsState` por posição de arco, sempre três: número fixo de
    // chamadas, para a ordem dos estados lembrados não depender de quantas cotas
    // a conta tem.
    val sweeps = List(MAX_RING_ARCS) { index ->
        val target = arcs.getOrNull(index)?.fraction?.coerceIn(0f, 1f) ?: 0f
        animateFloatAsState(
            targetValue = target,
            animationSpec = appSpring(AppMotion.Springs.GENTLE, visibilityThreshold = 0.001f),
            label = "appUsageRingSweep$index"
        )
    }
    val colors = List(MAX_RING_ARCS) { index ->
        animateColorAsState(
            targetValue = accents.getOrNull(index) ?: Color.Transparent,
            animationSpec = appTween(AppMotion.normal),
            label = "appUsageRingColor$index"
        )
    }

    val spin = if (active && policy.continuous) {
        val transition = rememberInfiniteTransition(label = "appUsageRingSpin")
        val angle by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(RING_SPIN_MILLIS, easing = LinearEasing)),
            label = "appUsageRingSpinAngle"
        )
        angle
    } else {
        // Parado: o arco ativo continua desenhado, só não gira.
        -90f
    }
    val pulse = if (attention && policy.continuous) {
        val transition = rememberInfiniteTransition(label = "appUsageRingPulse")
        val value by transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(RING_PULSE_MILLIS), RepeatMode.Reverse),
            label = "appUsageRingPulseAlpha"
        )
        value
    } else {
        1f
    }

    Canvas(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = description }
    ) {
        val strokePx = stroke.toPx()
        val gapPx = gap.toPx()
        val dash = PathEffect.dashPathEffect(floatArrayOf(strokePx, strokePx * 1.4f))
        arcs.take(MAX_RING_ARCS).forEachIndexed { index, arc ->
            val inset = strokePx / 2 + index * (strokePx + gapPx)
            val diameter = this.size.minDimension - inset * 2
            if (diameter <= 0f) {
                return@forEachIndexed
            }
            val topLeft = Offset(inset, inset)
            val arcSize = Size(diameter, diameter)
            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(
                    width = strokePx,
                    pathEffect = if (arc.hasForecast) null else dash
                )
            )
            val sweep = sweeps[index].value * 360f
            if (sweep > 0f) {
                val alpha = if (index == 0) pulse else 1f
                drawArc(
                    color = colors[index].value.copy(alpha = colors[index].value.alpha * alpha),
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }
        }
        if (active) {
            // O arco de sessão ativa mora por dentro do último anel de cota, fino,
            // no tom de informação — não é consumo, é "alguém está trabalhando".
            val used = arcs.size.coerceIn(1, MAX_RING_ARCS)
            val inset = strokePx / 2 + used * (strokePx + gapPx) + gapPx
            val diameter = this.size.minDimension - inset * 2
            if (diameter > 0f) {
                drawArc(
                    color = activeColor,
                    startAngle = spin,
                    sweepAngle = ACTIVE_ARC_SWEEP,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(diameter, diameter),
                    style = Stroke(width = strokePx * 0.6f, cap = StrokeCap.Round)
                )
            }
        }
    }
}

/** Os anéis concêntricos que cabem em 28dp sem virar alvo de tiro. */
const val MAX_RING_ARCS = 3

private const val RING_TRACK_WEIGHT = 1.6f
private const val RING_SPIN_MILLIS = 1_400
private const val RING_PULSE_MILLIS = 900
private const val ACTIVE_ARC_SWEEP = 90f

package com.usagemonitor.presentation.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.usagemonitor.presentation.ui.theme.AppMotion
import com.usagemonitor.presentation.ui.theme.AppSurfaceLadders
import com.usagemonitor.presentation.ui.theme.LocalAppMotionPolicy
import com.usagemonitor.presentation.ui.theme.appSpringSpec
import com.usagemonitor.presentation.ui.theme.appTween
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

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
 * Anel de uso: um arco por cota, concêntricos — o índice 0 de [arcs] é o de
 * fora. Quem ordena é quem chama: a HUD põe a janela mais longa por fora
 * (`HudAccount.rings`, issue #278).
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
 *   volta mostra um percentual que não é verdade. Na primeira composição ele se
 *   desenha a partir de zero, de fora para dentro (issue #322).
 * - [active] (sessão CLI com turno nos últimos 5 min) desenha um cometa fino
 *   girando **em órbita por fora** do anel, e [attention] faz respirar um halo
 *   atrás do arco de [attentionIndex] — o da cota em foco; os dois **só** com
 *   `AppMotionPolicy.continuous`, que é desligada em testes e geradores. Sem ela
 *   o estado continua dito: o cometa fica parado e o halo some, e a palavra ao
 *   lado continua lá.
 *
 * A órbita passa [appUsageRingOrbitReach] além de [size], fora dos limites do
 * `Canvas`: quem põe o anel na tela deixa esse espaço livre em volta. Ela morava
 * por dentro do último arco de cota, e o miolo que sobrava para a marca do
 * fornecedor caía quase à metade — a conta trabalhando era justamente a que
 * ficava com o ícone menor.
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
    attention: Boolean = false,
    /** O arco que respira em [attention]; fora do intervalo, nenhum. */
    attentionIndex: Int = 0
) {
    val policy = LocalAppMotionPolicy.current
    val ladder = AppSurfaceLadders.current
    // A trilha é a camada de pressão com um pouco mais de peso: visível como
    // "o que falta" sem competir com o arco, que é o dado.
    val track = ladder.pressedLayer.copy(alpha = ladder.pressedLayer.alpha * RING_TRACK_WEIGHT)
    val accents = arcs.map { arc -> arc.tone.color() }
    val activeColor = AppTone.INFO.color()

    // Um estado por posição de arco, sempre três: número fixo de chamadas, para
    // a ordem dos estados lembrados não depender de quantas cotas a conta tem.
    //
    // O arco surgia cheio na primeira composição, e o anel parecia colado na tela
    // em vez de medir alguma coisa (issue #322). Agora ele parte de zero, com um
    // escalonamento curto de fora para dentro; cada leitura seguinte anda da
    // posição atual pela mesma mola. Com "Reduzir animações" nasce no valor.
    val sweeps = List(MAX_RING_ARCS) { index ->
        val target = arcs.getOrNull(index)?.fraction?.coerceIn(0f, 1f) ?: 0f
        val sweep = remember { Animatable(if (policy.reduced) target else 0f) }
        var entered by remember { mutableStateOf(policy.reduced) }
        LaunchedEffect(target, policy) {
            if (!entered) {
                delay(index * AppMotion.stagger)
                entered = true
            }
            sweep.animateTo(target, appSpringSpec(AppMotion.Springs.GENTLE, policy, visibilityThreshold = 0.001f))
        }
        sweep.asState()
    }
    val colors = List(MAX_RING_ARCS) { index ->
        animateColorAsState(
            targetValue = accents.getOrNull(index) ?: Color.Transparent,
            animationSpec = appTween(AppMotion.slow),
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
        // Parado: o cometa continua desenhado, só não gira.
        -90f
    }
    // Atenção respira em vez de piscar (issue #322): o arco oscilava 0,35↔1 em
    // 900ms e lia como alarme. Agora ele fica entre 0,8 e 1, e o que pulsa é um
    // halo largo e translúcido atrás dele, num ciclo lento que acelera e freia
    // suave nas duas pontas.
    val breath = if (attention && policy.continuous) {
        val transition = rememberInfiniteTransition(label = "appUsageRingPulse")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(RING_BREATH_MILLIS, easing = FastOutSlowInEasing),
                RepeatMode.Reverse
            ),
            label = "appUsageRingPulseAlpha"
        )
        value
    } else {
        null
    }
    val pulse = if (breath == null) 1f else RING_BREATH_MIN_ALPHA + (1f - RING_BREATH_MIN_ALPHA) * breath

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
                val focused = index == attentionIndex
                if (focused && breath != null) {
                    // O halo: o mesmo arco com traço mais largo e quase
                    // transparente. Passa meio traço do arco para cada lado —
                    // menos que a órbita, e cabe no mesmo respiro.
                    drawArc(
                        color = colors[index].value.copy(alpha = RING_HALO_MAX_ALPHA * breath),
                        startAngle = -90f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx * RING_HALO_WIDTH, cap = StrokeCap.Round)
                    )
                }
                val alpha = if (focused) pulse else 1f
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
            // A sessão ativa orbita por fora do anel de cota, fina, no tom de
            // informação — não é consumo, é "alguém está trabalhando". Por fora
            // ela não disputa o miolo com a marca do fornecedor.
            //
            // Cometa, não segmento (issue #322): a cauda se dissolve até sumir e
            // a cabeça leva um ponto. O segmento chapado de 90° girando em 1,4s
            // lia como indicador de carregamento; o cometa lento lê "em curso".
            val orbitStroke = strokePx * ACTIVE_ARC_STROKE_FRACTION
            val outset = gapPx + orbitStroke / 2
            val diameter = this.size.minDimension + outset * 2
            val radius = diameter / 2f
            val tail = Brush.sweepGradient(
                0f to activeColor.copy(alpha = 0f),
                ACTIVE_ARC_SWEEP / 360f to activeColor,
                1f to activeColor.copy(alpha = 0f),
                center = center
            )
            rotate(degrees = spin) {
                drawArc(
                    brush = tail,
                    startAngle = 0f,
                    sweepAngle = ACTIVE_ARC_SWEEP,
                    useCenter = false,
                    topLeft = Offset(-outset, -outset),
                    size = Size(diameter, diameter),
                    style = Stroke(width = orbitStroke, cap = StrokeCap.Butt)
                )
                val head = Math.toRadians(ACTIVE_ARC_SWEEP.toDouble())
                drawCircle(
                    color = activeColor,
                    radius = orbitStroke * ACTIVE_HEAD_RADIUS_FRACTION,
                    center = Offset(
                        center.x + radius * cos(head).toFloat(),
                        center.y + radius * sin(head).toFloat()
                    )
                )
            }
        }
    }
}

/** Os anéis concêntricos que cabem em 28dp sem virar alvo de tiro. */
const val MAX_RING_ARCS = 3

/**
 * Quanto a órbita de sessão ativa passa da borda do anel, de cada lado — até a
 * borda de fora da **cabeça** do cometa, que é mais larga que o traço. Sem a
 * cabeça a conta ficava 0,375dp curta no anel da HUD, e os testes que afirmam o
 * encaixe no respiro do notch mediriam a órbita menor do que ela é.
 */
fun appUsageRingOrbitReach(stroke: Dp, gap: Dp): Dp {
    val orbitStroke = stroke * ACTIVE_ARC_STROKE_FRACTION
    return gap + orbitStroke / 2 + orbitStroke * ACTIVE_HEAD_RADIUS_FRACTION
}

private const val RING_TRACK_WEIGHT = 1.6f
private const val RING_SPIN_MILLIS = 2_400
private const val RING_BREATH_MILLIS = 1_600
private const val RING_BREATH_MIN_ALPHA = 0.8f
private const val RING_HALO_WIDTH = 2f
private const val RING_HALO_MAX_ALPHA = 0.28f
private const val ACTIVE_ARC_SWEEP = 130f
private const val ACTIVE_ARC_STROKE_FRACTION = 0.6f
/** A cabeça do cometa, um pouco mais larga que o traço para ler como ponto. */
private const val ACTIVE_HEAD_RADIUS_FRACTION = 0.75f

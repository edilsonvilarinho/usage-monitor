package com.usagemonitor.presentation.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onPlaced
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.usagemonitor.HUD_RETRACTED_STRIP
import com.usagemonitor.HudEdge
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.components.GargantuaIrisFrame
import com.usagemonitor.presentation.ui.components.appDepth
import com.usagemonitor.presentation.ui.components.color
import com.usagemonitor.presentation.ui.components.gargantuaHorizonBody
import com.usagemonitor.presentation.ui.components.gargantuaIrisCloseFrame
import com.usagemonitor.presentation.ui.components.gargantuaIrisCloseProgressFor
import com.usagemonitor.presentation.ui.components.gargantuaIrisOpenFrame
import com.usagemonitor.presentation.ui.components.gargantuaIrisOpenProgressFor
import com.usagemonitor.presentation.ui.theme.AppDepth
import com.usagemonitor.presentation.ui.theme.AppGargantuaTokens
import com.usagemonitor.presentation.ui.theme.LocalAppMotionPolicy
import com.usagemonitor.presentation.ui.theme.appTweenSpec
import kotlin.math.hypot
import kotlin.math.roundToInt

/** A faixa do notch recolhido (#400); é por ela que testes e leitor de tela a acham. */
internal const val HUD_RETRACTED_STRIP_TAG = "hudRetractedStrip"

/**
 * "Barra HUD recolhida · Anthropic — Padrão: Atenção · Codex: Normal". Cor e
 * forma do ponto não informam sozinhas: a palavra de cada conta vai aqui.
 */
internal fun hudRetractedStripDescription(accounts: List<HudAccount>, language: AppLanguage): String {
    val head = if (language == AppLanguage.PT) "Barra HUD recolhida" else "HUD bar retracted"
    return (listOf(head) + accounts.map { account -> "${account.label}: ${account.statusLabel}" }).joinToString(" · ")
}

/**
 * A faixa do modo "recolher quando parada" (issue #400): o corpo do notch com
 * [HUD_RETRACTED_STRIP] de espessura e o comprimento dele, com **um ponto por
 * conta** na altura do anel dela. A forma do ponto diz o pior risco junto com a
 * cor — círculo em dia, losango em atenção, triângulo crítico, anel vazio sem
 * projeção —, porque cor nunca informa sozinha.
 *
 * Fica por baixo do notch: aberto, o notch a cobre inteira.
 */
@Composable
internal fun HudRetractedStrip(
    accounts: List<HudAccount>,
    edge: HudEdge,
    length: Dp,
    /** Centro do anel de cada conta ao longo da faixa, em px; `null` antes do primeiro layout. */
    dotAlong: (Int) -> Float?,
    language: AppLanguage,
    interaction: MutableInteractionSource,
    modifier: Modifier = Modifier
) {
    val shape = remember(edge) { HudNotchShape(edge) }
    val size = if (edge.isHorizontal) DpSize(length, HUD_RETRACTED_STRIP) else DpSize(HUD_RETRACTED_STRIP, length)
    val tones = accounts.map { account -> account.tone }
    val colors = tones.map { tone -> tone.color() }
    val description = hudRetractedStripDescription(accounts, language)
    Box(
        modifier = modifier
            .requiredSize(size)
            .testTag(HUD_RETRACTED_STRIP_TAG)
            .appDepth(AppDepth.DIALOG, shape)
            .gargantuaHorizonBody(shape)
            .hoverable(interaction)
            .drawWithContent {
                drawContent()
                val radius = HUD_STRIP_DOT_RADIUS.toPx()
                val alongLength = if (edge.isHorizontal) this.size.width else this.size.height
                tones.forEachIndexed { index, tone ->
                    // Antes do primeiro layout dos anéis: espaçados por igual.
                    val along = dotAlong(index) ?: (alongLength * (index + 0.5f) / tones.size)
                    val across = HUD_RETRACTED_STRIP.toPx() / 2
                    val center = when (edge) {
                        HudEdge.TOP -> Offset(along, across)
                        HudEdge.BOTTOM -> Offset(along, this.size.height - across)
                        HudEdge.LEFT -> Offset(across, along)
                        HudEdge.RIGHT -> Offset(this.size.width - across, along)
                    }
                    drawRiskDot(tone, colors[index], center, radius)
                }
            }
            .semantics { contentDescription = description }
    )
}

/**
 * As partes do modo recolher (#400): a faixa por baixo do notch, com o modo
 * ligado, e o alfinete além da mão, com o notch aberto.
 */
@Composable
internal fun HudRetractParts(
    autoRetract: Boolean,
    onToggleAutoRetract: (() -> Unit)?,
    showHandles: Boolean,
    accounts: List<HudAccount>,
    edge: HudEdge,
    notchSize: DpSize,
    ringCenters: Map<Int, Float>,
    rootCoordinates: () -> LayoutCoordinates?,
    language: AppLanguage,
    hover: HudHoverSources
) {
    if (autoRetract) {
        // Onde a faixa começa no contêiner: os pontos ficam na altura dos anéis.
        val stripStart = remember { floatArrayOf(0f) }
        HudRetractedStrip(
            accounts = accounts,
            edge = edge,
            length = if (edge.isHorizontal) notchSize.width else notchSize.height,
            dotAlong = { index -> ringCenters[index]?.minus(stripStart[0]) },
            language = language,
            interaction = hover.strip,
            modifier = Modifier.layoutId(HudRetractPart.STRIP).onPlaced { coordinates ->
                val root = rootCoordinates()
                if (root != null && root.isAttached && coordinates.isAttached) {
                    val origin = root.localPositionOf(coordinates, Offset.Zero)
                    stripStart[0] = if (edge.isHorizontal) origin.x else origin.y
                }
            }
        )
    }
    if (onToggleAutoRetract != null) {
        AnimatedVisibility(
            visible = showHandles,
            modifier = Modifier.layoutId(HudRetractPart.HANDLE),
            enter = handleEnter(edge, atStart = true),
            exit = handleExit(edge, atStart = true)
        ) {
            HudRetractHandle(autoRetract = autoRetract, language = language, onClick = onToggleAutoRetract, interaction = hover.retract)
        }
    }
}

/** As partes do modo recolher no layout do notch: enum próprio, nenhum valor novo no `HudNotchPart`. */
internal enum class HudRetractPart { HANDLE, STRIP }

/** Losango em atenção, triângulo crítico, anel vazio sem projeção, círculo no resto. */
private fun DrawScope.drawRiskDot(tone: AppTone, color: Color, center: Offset, radius: Float) {
    when (tone) {
        AppTone.WARNING -> drawPath(polygon(center, radius * 1.25f, listOf(0f to -1f, 1f to 0f, 0f to 1f, -1f to 0f)), color)
        AppTone.CRITICAL -> drawPath(polygon(center, radius * 1.3f, listOf(0f to -1f, 0.95f to 0.75f, -0.95f to 0.75f)), color)
        AppTone.NEUTRAL -> drawCircle(color, radius * 0.85f, center, style = Stroke(width = radius * 0.5f))
        AppTone.OK, AppTone.INFO -> drawCircle(color, radius, center)
    }
}

private fun polygon(center: Offset, scale: Float, points: List<Pair<Float, Float>>): Path = Path().apply {
    points.forEachIndexed { index, (x, y) ->
        if (index == 0) moveTo(center.x + x * scale, center.y + y * scale) else lineTo(center.x + x * scale, center.y + y * scale)
    }
    close()
}

private val HUD_STRIP_DOT_RADIUS = 3.dp

/**
 * O quadro da íris (Z2) que segue [revealed]: expande em
 * [AppGargantuaTokens.irisOpenMillis] e recolhe em
 * [AppGargantuaTokens.irisCloseMillis]. Inverter no meio parte do raio em que
 * o disco está, e o tempo é o que falta dele. Com "Reduzir animações" o tween
 * vira `snap()`: corte seco.
 */
@Composable
internal fun rememberHudIrisFrame(revealed: Boolean): () -> GargantuaIrisFrame {
    val policy = LocalAppMotionPolicy.current
    val progress = remember { Animatable(1f) }
    var opening by remember { mutableStateOf(revealed) }
    val frame = { if (opening) gargantuaIrisOpenFrame(progress.value) else gargantuaIrisCloseFrame(progress.value) }
    LaunchedEffect(revealed) {
        if (opening == revealed && progress.value >= 1f) return@LaunchedEffect
        val radius = frame().radius
        val start = if (revealed) gargantuaIrisOpenProgressFor(radius) else gargantuaIrisCloseProgressFor(radius)
        opening = revealed
        progress.snapTo(start)
        val total = if (revealed) AppGargantuaTokens.irisOpenMillis else AppGargantuaTokens.irisCloseMillis
        val remaining = ((1f - start) * total).roundToInt().coerceAtLeast(1)
        progress.animateTo(1f, appTweenSpec(remaining, policy, LinearEasing))
    }
    return frame
}

/**
 * Recorta o notch pela íris do eclipse: um disco centrado no meio do notch,
 * rente à borda da tela, com [GargantuaIrisFrame.radius] do alcance. O recorte
 * é de camada, então também tira o ponteiro do que ficou de fora — fechado, o
 * notch não recebe hover e quem recebe é a faixa por baixo. Aberto e parado, sem
 * recorte nenhum: a sombra do notch passa da caixa e não pode ser cortada.
 *
 * O fio de luz quente da borda do disco é desenhado **fora** do recorte, senão
 * metade dele sumiria.
 */
internal fun Modifier.hudEclipseIris(edge: HudEdge, frame: () -> GargantuaIrisFrame): Modifier =
    this
        .drawWithContent {
            drawContent()
            val current = frame()
            if (current.rimAlpha > 0f) {
                val center = irisCenter(edge, size)
                drawCircle(
                    color = AppGargantuaTokens.gold.copy(alpha = current.rimAlpha),
                    radius = current.radius * irisReach(edge, size, this),
                    center = center,
                    style = Stroke(width = IRIS_RIM_WIDTH.toPx())
                )
            }
        }
        .graphicsLayer {
            val current = frame()
            clip = !current.settled
            shape = HudIrisShape(edge, current.radius)
        }

/** O meio do notch ao longo da borda, na própria borda da tela. */
private fun irisCenter(edge: HudEdge, size: Size): Offset = when (edge) {
    HudEdge.TOP -> Offset(size.width / 2, 0f)
    HudEdge.BOTTOM -> Offset(size.width / 2, size.height)
    HudEdge.LEFT -> Offset(0f, size.height / 2)
    HudEdge.RIGHT -> Offset(size.width, size.height / 2)
}

/** O raio que cobre o notch inteiro a partir do centro, com folga para os cantos. */
private fun irisReach(edge: HudEdge, size: Size, density: Density): Float {
    val along = if (edge.isHorizontal) size.width else size.height
    val across = if (edge.isHorizontal) size.height else size.width
    val slack = with(density) { IRIS_REACH_SLACK.toPx() }
    return hypot(along / 2 + slack, across + slack)
}

private class HudIrisShape(private val edge: HudEdge, private val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = radius * irisReach(edge, size, density)
        val center = irisCenter(edge, size)
        return Outline.Generic(Path().apply { addOval(Rect(center, r)) })
    }

    override fun equals(other: Any?): Boolean = other is HudIrisShape && other.edge == edge && other.radius == radius

    override fun hashCode(): Int = 31 * edge.hashCode() + radius.hashCode()
}

private val IRIS_RIM_WIDTH = 1.5.dp

/** Folga do alcance além do notch: com o disco no fim, nenhum canto fica de fora. */
private val IRIS_REACH_SLACK = 12.dp

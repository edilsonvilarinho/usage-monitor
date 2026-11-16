package com.usagemonitor.presentation.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.usagemonitor.HUD_BLACK_HOLE_CELL_SIZE
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.components.AppProviderMark
import com.usagemonitor.presentation.ui.components.HUD_BLACK_HOLE_GLYPH_EXTENT
import com.usagemonitor.presentation.ui.components.HUD_BLACK_HOLE_GLYPH_SIZE
import com.usagemonitor.presentation.ui.components.HUD_BLACK_HOLE_ORBIT_RADIUS
import com.usagemonitor.presentation.ui.components.HUD_BLACK_HOLE_TRAIL_DEG
import com.usagemonitor.presentation.ui.components.HudBlackHoleOrbitEntry
import com.usagemonitor.presentation.ui.components.hudBlackHoleFilament
import com.usagemonitor.presentation.ui.components.color
import com.usagemonitor.presentation.ui.components.hudBlackHoleOrbit
import com.usagemonitor.presentation.ui.theme.AppGargantuaTokens
import com.usagemonitor.presentation.ui.theme.LocalAppMotionPolicy
import kotlin.math.PI

/** Tag de cada glifo em órbita do buraco (#602, A06). */
internal const val HUD_BLACK_HOLE_ORBIT_GLYPH_TEST_TAG = "hudBlackHoleOrbitGlyph"

/** Tag de um nó vazio por conta em execução, só na árvore não mesclada: prova que o filamento foi pedido (#602, A07). */
internal const val HUD_BLACK_HOLE_FILAMENT_TEST_TAG = "hudBlackHoleFilament"

/** Tag da marca real do provedor dentro de cada glifo em órbita (#611, P1). */
internal const val HUD_BLACK_HOLE_MARK_TEST_TAG = "hudBlackHoleOrbitMark"

private const val HUD_BLACK_HOLE_TRAIL_SEGMENTS = 10
private const val HUD_BLACK_HOLE_TRAIL_ALPHA_RUNNING = 0.85f
private const val HUD_BLACK_HOLE_TRAIL_ALPHA_IDLE = 0.2f
private val HUD_BLACK_HOLE_TRAIL_WIDTH_RUNNING = 1.6.dp
private val HUD_BLACK_HOLE_TRAIL_WIDTH_IDLE = 0.8.dp
private val HUD_BLACK_HOLE_HALO_RADIUS = 18.dp
private val HUD_BLACK_HOLE_HALO_RING_RADIUS = 14.dp
private val HUD_BLACK_HOLE_HALO_RING_WIDTH = 1.dp
private const val HUD_BLACK_HOLE_HALO_ALPHA = 0.35f
/** Marca real do provedor no centro do disco (#611, P1). */
private val HUD_BLACK_HOLE_MARK_SIZE = 20.dp
/** Contorno do disco: raio 15,4 dp e traço de 1,2 dp (#611, P1). */
private val HUD_BLACK_HOLE_CONTOUR_RADIUS = 15.4.dp
private val HUD_BLACK_HOLE_CONTOUR_WIDTH = 1.2.dp
/** Traço e vão do tracejado do contorno ocioso: 2 dp e 2 dp (#611, P1). */
private val HUD_BLACK_HOLE_DASH = 2.dp
/** Primeiro arco de cota: 16 dp do disco + 1,5 dp de folga + 1,25 dp de meio traço (#611, P1). */
private val HUD_BLACK_HOLE_ARC_FIRST_RADIUS = 18.75.dp
/** Passo radial entre arcos de cota: traço 2,5 dp + folga 1,5 dp (#611, P1). */
private val HUD_BLACK_HOLE_ARC_STEP = 4.dp
private val HUD_BLACK_HOLE_ARC_WIDTH = 2.5.dp
/** Trilho de fundo e tracejado do contorno, na cor de texto com alfa do prótipo (#611, P1). */
private const val HUD_BLACK_HOLE_TRACK_ALPHA_DARK = 0.06f
private const val HUD_BLACK_HOLE_TRACK_ALPHA_LIGHT = 0.10f
private const val HUD_BLACK_HOLE_DASH_ALPHA_DARK = 0.14f
private const val HUD_BLACK_HOLE_DASH_ALPHA_LIGHT = 0.25f

/**
 * Glifos em órbita da célula do buraco (#602, A06): um glifo por conta do comboio, no raio de
 * [HUD_BLACK_HOLE_ORBIT_RADIUS]. Trilha e halo saem do mesmo quadro (Canvas, fase de desenho); a posição
 * de cada glifo é lida na fase de layout, então o tempo não recompõe a célula. A conta em execução
 * (`sessionActive`) ganha lente, halo e semântica própria. Tempo só corre com movimento contínuo.
 */
@Composable
internal fun HudBlackHoleOrbitGlyphs(
    members: List<HudAccount>,
    language: AppLanguage,
    modifier: Modifier = Modifier
) {
    val policy = LocalAppMotionPolicy.current
    val live = policy.continuous && !policy.reduced
    val claro = hudIsLight(MaterialTheme.colorScheme.surface)
    val lens = if (claro) AppGargantuaTokens.gold else AppGargantuaTokens.lens
    // Filamento: no escuro vai de quente a dourado; no claro, de dourado a poeira (#602, A07).
    val filamentoInicio = if (claro) AppGargantuaTokens.gold else AppGargantuaTokens.hot
    val filamentoFim = if (claro) AppGargantuaTokens.dust else AppGargantuaTokens.gold
    val idleInk = MaterialTheme.colorScheme.onSurface
    val runningLabel = localized(language, "em execução", "running")
    val timeMs = rememberHudBlackHoleClock(live)
    Box(modifier = modifier.size(HUD_BLACK_HOLE_CELL_SIZE), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(HUD_BLACK_HOLE_CELL_SIZE)) {
            drawHudBlackHoleOrbit(members, timeMs.value, lens, idleInk, live, filamentoInicio, filamentoFim)
        }
        if (live) {
            // Um nó vazio por conta em execução: o filamento é desenhado no Canvas acima, este nó só expõe a contagem.
            repeat(members.count { conta -> conta.sessionActive }) {
                Box(
                    modifier = Modifier
                        .size(HUD_BLACK_HOLE_CELL_SIZE)
                        .clearAndSetSemantics { this[SemanticsProperties.TestTag] = HUD_BLACK_HOLE_FILAMENT_TEST_TAG }
                )
            }
        }
        members.indices.forEach { index ->
            HudBlackHoleGlyph(
                account = members[index],
                index = index,
                members = members,
                timeMs = timeMs,
                runningLabel = runningLabel,
                lens = lens
            )
        }
    }
}

/** Relógio de quadros do comboio: só corre com movimento contínuo; sem ele, fica em 0 e a órbita para. */
@Composable
private fun rememberHudBlackHoleClock(live: Boolean): State<Long> {
    val relogio = remember { mutableLongStateOf(0L) }
    LaunchedEffect(live) {
        if (!live) {
            relogio.longValue = 0L
        } else {
            while (true) {
                withFrameMillis { quadro -> relogio.longValue = quadro }
            }
        }
    }
    return relogio
}

/** Arco de cota de um glifo: fração preenchida e cor do tom da cota (#611, P1). */
private class HudBlackHoleArc(val fraction: Float, val color: Color)

/**
 * Glifo de uma conta em órbita (#611, P1). A caixa externa, de [HUD_BLACK_HOLE_GLYPH_EXTENT] × 2, carrega o
 * deslocamento da órbita e os arcos de cota por fora do disco; a interna, de [HUD_BLACK_HOLE_GLYPH_SIZE], é o
 * disco com a tag de glifo e a semântica, e a marca real de [HUD_BLACK_HOLE_MARK_SIZE] no centro.
 */
@Composable
private fun HudBlackHoleGlyph(
    account: HudAccount,
    index: Int,
    members: List<HudAccount>,
    timeMs: State<Long>,
    runningLabel: String,
    lens: Color
) {
    val claro = hudIsLight(MaterialTheme.colorScheme.surface)
    val trilho = MaterialTheme.colorScheme.onSurface.copy(
        alpha = if (claro) HUD_BLACK_HOLE_TRACK_ALPHA_LIGHT else HUD_BLACK_HOLE_TRACK_ALPHA_DARK
    )
    val tracejado = MaterialTheme.colorScheme.onSurface.copy(
        alpha = if (claro) HUD_BLACK_HOLE_DASH_ALPHA_LIGHT else HUD_BLACK_HOLE_DASH_ALPHA_DARK
    )
    val arcos = account.rings.map { quota -> HudBlackHoleArc(quota.fraction, quota.tone.color()) }
    Box(
        modifier = Modifier
            .size(HUD_BLACK_HOLE_GLYPH_EXTENT * 2)
            .offset {
                val entrada = hudBlackHoleOrbit(members, { conta -> conta.sessionActive }, timeMs.value)[index]
                IntOffset(entrada.x.roundToPx(), entrada.y.roundToPx())
            }
            .drawBehind { drawHudBlackHoleArcos(arcos, trilho) },
        contentAlignment = Alignment.Center
    ) {
        // Disco com a semântica do glifo; a marca fica irmã, no mesmo centro, fora da subárvore que o clearAndSetSemantics limpa.
        Box(
            modifier = Modifier
                .size(HUD_BLACK_HOLE_GLYPH_SIZE)
                .drawBehind { drawHudBlackHoleDisco(account.sessionActive, lens, tracejado) }
                .clearAndSetSemantics {
                    this[SemanticsProperties.TestTag] = HUD_BLACK_HOLE_ORBIT_GLYPH_TEST_TAG
                    if (account.sessionActive) contentDescription = "${account.label}, $runningLabel"
                }
        )
        AppProviderMark(
            source = account.source,
            tint = AppGargantuaTokens.hot,
            modifier = Modifier.clearAndSetSemantics { this[SemanticsProperties.TestTag] = HUD_BLACK_HOLE_MARK_TEST_TAG },
            size = HUD_BLACK_HOLE_MARK_SIZE
        )
    }
}

/**
 * Arcos de cota por fora do disco (#611, P1): um anel por cota, do raio [HUD_BLACK_HOLE_ARC_FIRST_RADIUS] com passo
 * [HUD_BLACK_HOLE_ARC_STEP]. Trilho de fundo em volta inteira e arco preenchido a partir de -90 graus.
 */
private fun DrawScope.drawHudBlackHoleArcos(arcos: List<HudBlackHoleArc>, trilho: Color) {
    val centro = center
    arcos.forEachIndexed { indice, arco ->
        val raio = (HUD_BLACK_HOLE_ARC_FIRST_RADIUS + HUD_BLACK_HOLE_ARC_STEP * indice).toPx()
        val topLeft = Offset(centro.x - raio, centro.y - raio)
        val tamanho = Size(raio * 2f, raio * 2f)
        val largura = HUD_BLACK_HOLE_ARC_WIDTH.toPx()
        drawArc(
            color = trilho,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = tamanho,
            style = Stroke(width = largura, cap = StrokeCap.Butt)
        )
        val fracao = arco.fraction.coerceIn(0f, 1f)
        if (fracao > 0f) {
            drawArc(
                color = arco.color,
                startAngle = -90f,
                sweepAngle = 360f * fracao,
                useCenter = false,
                topLeft = topLeft,
                size = tamanho,
                style = Stroke(width = largura, cap = StrokeCap.Round)
            )
        }
    }
}

/**
 * Disco do glifo (#611, P1): 32 dp em `core` e contorno de 1,2 dp no raio 15,4 dp. Em execução, contorno
 * sólido na cor da lente; ociosa, tracejado de 2/2 dp na cor de texto com alfa.
 */
private fun DrawScope.drawHudBlackHoleDisco(running: Boolean, lens: Color, tracejado: Color) {
    val centro = center
    drawCircle(color = AppGargantuaTokens.core, radius = HUD_BLACK_HOLE_GLYPH_SIZE.toPx() / 2f, center = centro)
    val efeito = if (running) {
        null
    } else {
        PathEffect.dashPathEffect(floatArrayOf(HUD_BLACK_HOLE_DASH.toPx(), HUD_BLACK_HOLE_DASH.toPx()))
    }
    drawCircle(
        color = if (running) lens else tracejado,
        radius = HUD_BLACK_HOLE_CONTOUR_RADIUS.toPx(),
        center = centro,
        style = Stroke(width = HUD_BLACK_HOLE_CONTOUR_WIDTH.toPx(), pathEffect = efeito)
    )
}

private fun DrawScope.drawHudBlackHoleOrbit(
    members: List<HudAccount>,
    timeMs: Long,
    lens: Color,
    idleInk: Color,
    live: Boolean,
    filamentoInicio: Color,
    filamentoFim: Color
) {
    val entradas = hudBlackHoleOrbit(members, { conta -> conta.sessionActive }, timeMs)
    val raio = HUD_BLACK_HOLE_ORBIT_RADIUS.toPx()
    val centro = center
    entradas.forEach { entrada ->
        val glifo = centro + Offset(entrada.x.toPx(), entrada.y.toPx())
        if (entrada.running) drawHudBlackHoleHalo(glifo, lens)
        drawHudBlackHoleTrail(entrada, centro, raio, if (entrada.running) lens else idleInk)
    }
    if (live) {
        entradas.filter { entrada -> entrada.running }.forEach { entrada ->
            drawHudBlackHoleFilament(entrada.angleRad, timeMs, filamentoInicio, filamentoFim)
        }
    }
}

/**
 * Fita de maré da conta em execução (#602, A07): segmentos de [hudBlackHoleFilament] saindo do centro do
 * buraco, com cabo redondo. Cor vai de [inicio] a [fim] pelo fator de cada segmento; alfa e espessura vêm do segmento.
 * Chamada só com o relógio vivo; sem movimento contínuo a órbita fica parada e nenhuma fita é desenhada.
 */
private fun DrawScope.drawHudBlackHoleFilament(angleRad: Double, timeMs: Long, inicio: Color, fim: Color) {
    val centro = center
    hudBlackHoleFilament(orbitAngleRad = angleRad, timeMs = timeMs).forEach { segmento ->
        drawLine(
            color = lerp(inicio, fim, segmento.colorMix).copy(alpha = segmento.alpha),
            start = centro + Offset(segmento.startX.dp.toPx(), segmento.startY.dp.toPx()),
            end = centro + Offset(segmento.endX.dp.toPx(), segmento.endY.dp.toPx()),
            strokeWidth = segmento.widthDp.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

/** Rastro de [HUD_BLACK_HOLE_TRAIL_DEG] atrás da cabeça, em dez segmentos que apagam para trás. */
private fun DrawScope.drawHudBlackHoleTrail(entrada: HudBlackHoleOrbitEntry, centro: Offset, raio: Float, cor: Color) {
    val cabecaGraus = entrada.angleRad * 180.0 / PI
    val passoGraus = HUD_BLACK_HOLE_TRAIL_DEG / HUD_BLACK_HOLE_TRAIL_SEGMENTS
    val alfaBase = if (entrada.running) HUD_BLACK_HOLE_TRAIL_ALPHA_RUNNING else HUD_BLACK_HOLE_TRAIL_ALPHA_IDLE
    val espessura = if (entrada.running) HUD_BLACK_HOLE_TRAIL_WIDTH_RUNNING else HUD_BLACK_HOLE_TRAIL_WIDTH_IDLE
    for (segmento in 0 until HUD_BLACK_HOLE_TRAIL_SEGMENTS) {
        val fracao = 1f - segmento.toFloat() / HUD_BLACK_HOLE_TRAIL_SEGMENTS
        val inicioGraus = cabecaGraus - HUD_BLACK_HOLE_TRAIL_DEG * (segmento + 1) / HUD_BLACK_HOLE_TRAIL_SEGMENTS
        drawArc(
            color = cor.copy(alpha = alfaBase * fracao),
            startAngle = inicioGraus.toFloat(),
            sweepAngle = passoGraus.toFloat(),
            useCenter = false,
            topLeft = Offset(centro.x - raio, centro.y - raio),
            size = Size(raio * 2f, raio * 2f),
            style = Stroke(width = espessura.toPx(), cap = StrokeCap.Round)
        )
    }
}

/** Halo da conta em execução: gradiente da lente até 18 dp, com anel de 1 dp em 14 dp. */
private fun DrawScope.drawHudBlackHoleHalo(glifo: Offset, lens: Color) {
    val raioHalo = HUD_BLACK_HOLE_HALO_RADIUS.toPx()
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(lens.copy(alpha = HUD_BLACK_HOLE_HALO_ALPHA), lens.copy(alpha = 0f)),
            center = glifo,
            radius = raioHalo
        ),
        radius = raioHalo,
        center = glifo
    )
    drawCircle(
        color = lens,
        radius = HUD_BLACK_HOLE_HALO_RING_RADIUS.toPx(),
        center = glifo,
        style = Stroke(width = HUD_BLACK_HOLE_HALO_RING_WIDTH.toPx())
    )
}

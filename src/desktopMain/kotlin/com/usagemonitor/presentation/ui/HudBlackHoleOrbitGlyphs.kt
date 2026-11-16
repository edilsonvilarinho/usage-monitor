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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.usagemonitor.HUD_RING_SIZE
import com.usagemonitor.HUD_RING_STROKE
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.components.AppGargantuaRing
import com.usagemonitor.presentation.ui.components.AppProviderMark
import com.usagemonitor.presentation.ui.components.AppRingArc
import com.usagemonitor.presentation.ui.components.HUD_BLACK_HOLE_GLYPH_SIZE
import com.usagemonitor.presentation.ui.components.HUD_BLACK_HOLE_ORBIT_RADIUS
import com.usagemonitor.presentation.ui.components.HUD_BLACK_HOLE_TRAIL_DEG
import com.usagemonitor.presentation.ui.components.HudBlackHoleOrbitEntry
import com.usagemonitor.presentation.ui.components.hudBlackHoleFilament
import com.usagemonitor.presentation.ui.components.hudBlackHoleOrbit
import com.usagemonitor.presentation.ui.theme.AppGargantuaTokens
import com.usagemonitor.presentation.ui.theme.LocalAppMotionPolicy
import kotlin.math.PI

/** Tag de cada glifo em órbita do buraco (#602, A06). */
internal const val HUD_BLACK_HOLE_ORBIT_GLYPH_TEST_TAG = "hudBlackHoleOrbitGlyph"

/** Tag de um nó vazio por conta em execução, só na árvore não mesclada: prova que o filamento foi pedido (#602, A07). */
internal const val HUD_BLACK_HOLE_FILAMENT_TEST_TAG = "hudBlackHoleFilament"

private const val HUD_BLACK_HOLE_TRAIL_SEGMENTS = 10
private const val HUD_BLACK_HOLE_TRAIL_ALPHA_RUNNING = 0.85f
private const val HUD_BLACK_HOLE_TRAIL_ALPHA_IDLE = 0.2f
private val HUD_BLACK_HOLE_TRAIL_WIDTH_RUNNING = 1.6.dp
private val HUD_BLACK_HOLE_TRAIL_WIDTH_IDLE = 0.8.dp
private val HUD_BLACK_HOLE_HALO_RADIUS = 18.dp
private val HUD_BLACK_HOLE_HALO_RING_RADIUS = 14.dp
private val HUD_BLACK_HOLE_HALO_RING_WIDTH = 1.dp
private const val HUD_BLACK_HOLE_HALO_ALPHA = 0.35f

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
                runningLabel = runningLabel
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

@Composable
private fun HudBlackHoleGlyph(
    account: HudAccount,
    index: Int,
    members: List<HudAccount>,
    timeMs: State<Long>,
    runningLabel: String
) {
    Box(
        modifier = Modifier
            .size(HUD_BLACK_HOLE_GLYPH_SIZE)
            .offset {
                val entrada = hudBlackHoleOrbit(members, { conta -> conta.sessionActive }, timeMs.value)[index]
                IntOffset(entrada.x.roundToPx(), entrada.y.roundToPx())
            }
            .clearAndSetSemantics {
                this[SemanticsProperties.TestTag] = HUD_BLACK_HOLE_ORBIT_GLYPH_TEST_TAG
                if (account.sessionActive) contentDescription = "${account.label}, $runningLabel"
            }
    ) {
        AppGargantuaRing(
            arcs = account.rings.map { quota -> AppRingArc(quota.fraction, quota.tone, quota.hasForecast) },
            description = account.label,
            size = HUD_BLACK_HOLE_GLYPH_SIZE,
            stroke = HUD_RING_STROKE * (HUD_BLACK_HOLE_GLYPH_SIZE / HUD_RING_SIZE),
            centerContent = { AppProviderMark(source = account.source, tint = AppGargantuaTokens.mark) }
        )
    }
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

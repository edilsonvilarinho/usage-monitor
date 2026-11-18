package com.usagemonitor.presentation.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.usagemonitor.HUD_HANDLE_GAP
import com.usagemonitor.HUD_HANDLE_SIZE
import com.usagemonitor.HUD_SHADOW_MARGIN
import com.usagemonitor.HudEdge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * A medida do notch: o corpo rente à borda, as dicas e as alças nas pontas e o
 * balão centrado no anel em foco, preso ao contêiner com a margem da sombra.
 *
 * Criada a cada composição, como o lambda que ela substitui: o balão lê a
 * posição animada de [balloonAlong] e os centros de [ringCenters], e é a nova
 * política que faz a medida seguinte enxergar o anel novo.
 */
internal fun hudNotchMeasurePolicy(
    edge: HudEdge,
    notchCenter: Dp?,
    ringCenters: Map<Int, Float>,
    shownIndex: Int?,
    placement: HudBalloonPlacement,
    balloonAlong: Animatable<Float, AnimationVector1D>,
    alongSpec: AnimationSpec<Float>,
    scope: CoroutineScope,
    detachedAlongShift: Dp = 0.dp,
    openProgress: () -> Float = { 1f }
): MeasurePolicy
 = MeasurePolicy { measurables, constraints ->
    val notch = measurables.first { measurable -> measurable.layoutId == HudNotchPart.NOTCH }
        .measure(Constraints())
    val balloon = measurables.firstOrNull { measurable -> measurable.layoutId == HudNotchPart.BALLOON }
        ?.measure(Constraints())
    val extras = listOf<Any>(HudNotchPart.HINT_START, HudNotchPart.HINT_END, HudNotchPart.MOVE, HudNotchPart.GEAR, HudRetractPart.HANDLE, HudRetractPart.STRIP, HudFloatPart.HANDLE)
        .associateWith { part ->
            measurables.firstOrNull { measurable -> measurable.layoutId == part }?.measure(Constraints())
        }
    val width = if (constraints.hasBoundedWidth) constraints.maxWidth else notch.width
    val height = if (constraints.hasBoundedHeight) constraints.maxHeight else notch.height
    val alongLength = if (edge.isHorizontal) width else height
    val notchAlong = if (edge.isHorizontal) notch.width else notch.height
    val center = notchCenter?.roundToPx() ?: (alongLength / 2)
    // Detached (A10b): a coluna nasce no buraco e escorrega para dentro da área junto com a abertura.
    val shiftPx = detachedAlongShift.roundToPx() * openProgress()
    val notchStart = (center - notchAlong / 2 + shiftPx.roundToInt()).coerceIn(0, (alongLength - notchAlong).coerceAtLeast(0))

    // O balão centrado no anel e preso ao contêiner, com a margem da sombra.
    var balloonStart = 0
    if (balloon != null && balloon.width > 0) {
        val balloonAlongSize = if (edge.isHorizontal) balloon.width else balloon.height
        val ring = ringCenters[shownIndex ?: 0] ?: (notchStart + notchAlong / 2f)
        val margin = HUD_SHADOW_MARGIN.roundToPx()
        val maxStart = (alongLength - margin - balloonAlongSize).coerceAtLeast(0).toFloat()
        val target = (ring - balloonAlongSize / 2f).coerceIn(margin.toFloat().coerceAtMost(maxStart), maxStart)
        // Trocar de anel repete o jato a partir do anel novo (B3): o balão salta
        // para lá em vez de deslizar, senão desdobraria no meio do caminho.
        if (!placement.placed || placement.index != shownIndex) {
            placement.placed = true
            placement.index = shownIndex
            placement.target = target
            scope.launch { balloonAlong.snapTo(target) }
            balloonStart = target.roundToInt()
        } else {
            if (placement.target != target) {
                placement.target = target
                scope.launch { balloonAlong.animateTo(target, alongSpec) }
            }
            balloonStart = balloonAlong.value.roundToInt()
        }
    }

    val notchAcross = if (edge.isHorizontal) notch.height else notch.width
    val handleGap = HUD_HANDLE_GAP.roundToPx()

    layout(width, height) {
        // (ao longo, a partir da borda da tela) → posição na caixa de cada borda.
        fun Placeable.placeAt(along: Int, across: Int, zIndex: Float = 0f) {
            val acrossSize = if (edge.isHorizontal) this.height else this.width
            when (edge) {
                HudEdge.TOP -> place(along, across, zIndex)
                HudEdge.BOTTOM -> place(along, height - across - acrossSize, zIndex)
                HudEdge.LEFT -> place(across, along, zIndex)
                HudEdge.RIGHT -> place(width - across - acrossSize, along, zIndex)
            }
        }
        // A faixa do modo recolher (#400) por baixo do notch: aberto, ele a cobre.
        extras[HudRetractPart.STRIP]?.placeAt(notchStart, 0, STRIP_Z_INDEX)
        notch.placeAt(notchStart, 0)
        val notchEnd = notchStart + notchAlong
        extras[HudNotchPart.HINT_START]?.let { hint -> hint.placeAt(notchStart - hint.alongSize(edge), 0) }
        extras[HudNotchPart.HINT_END]?.let { hint -> hint.placeAt(notchEnd, 0) }
        // As alças centradas na espessura do notch, uma além de cada ponta, e
        // por baixo dele: entrando, elas saem de trás do notch.
        extras[HudNotchPart.MOVE]?.let { handle ->
            handle.placeAt(
                notchStart - handleGap - handle.alongSize(edge),
                (notchAcross - handle.acrossSize(edge)) / 2,
                HANDLE_Z_INDEX
            )
        }
        // O alfinete do modo recolher, além da mão (#400); a geometria reserva
        // duas alças em cada ponta.
        extras[HudRetractPart.HANDLE]?.let { handle ->
            handle.placeAt(
                notchStart - (handleGap + HUD_HANDLE_SIZE.roundToPx()) - handleGap - handle.alongSize(edge),
                (notchAcross - handle.acrossSize(edge)) / 2,
                HANDLE_Z_INDEX
            )
        }
        extras[HudNotchPart.GEAR]?.let { handle ->
            handle.placeAt(notchEnd + handleGap, (notchAcross - handle.acrossSize(edge)) / 2, HANDLE_Z_INDEX)
        }
        // O botão de soltar/encaixar (#619), por fora da engrenagem: não move nem o notch nem a engrenagem.
        extras[HudFloatPart.HANDLE]?.let { handle ->
            handle.placeAt(
                notchEnd + handleGap + HUD_HANDLE_SIZE.roundToPx() + handleGap,
                (notchAcross - handle.acrossSize(edge)) / 2,
                HANDLE_Z_INDEX
            )
        }
        if (balloon != null) {
            when (edge) {
                HudEdge.TOP -> balloon.place(balloonStart, notch.height)
                HudEdge.BOTTOM -> balloon.place(balloonStart, height - notch.height - balloon.height)
                HudEdge.LEFT -> balloon.place(notch.width, balloonStart)
                HudEdge.RIGHT -> balloon.place(width - notch.width - balloon.width, balloonStart)
            }
        }
    }
}

package com.usagemonitor.presentation.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.usagemonitor.HUD_RING_STROKE
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.hudSinkFrame
import com.usagemonitor.presentation.ui.components.AppGargantuaRing

/** Conta em trânsito até o buraco negro (#600): posições ao longo da borda, em px da caixa da faixa. */
internal data class HudFlight(val label: String, val progress: Float, val fromAlong: Float, val toAlong: Float)

/**
 * Monta os voos de [flying] a partir do centro medido de cada slot e do centro do buraco ([holeIndex]).
 * Índice sem medição ainda não voa: some do quadro seguinte em vez de desenhar no chute.
 */
internal fun hudFlights(
    accounts: List<HudAccount>,
    flying: List<Int>,
    minimizeProgress: Map<UsageTargetKey, Float>,
    centers: Map<Int, Float>,
    holeIndex: Int,
): List<HudFlight> {
    val toAlong = centers[holeIndex] ?: return emptyList()
    return flying.mapNotNull { index ->
        val fromAlong = centers[index] ?: return@mapNotNull null
        val account = accounts[index]
        HudFlight(
            label = account.label,
            progress = minimizeProgress[account.targetKey] ?: 0f,
            fromAlong = fromAlong,
            toAlong = toAlong,
        )
    }
}

/**
 * Centro ao longo da borda de cada índice medido, na caixa [box]: Y na borda vertical, X na horizontal
 * ([horizontal], #612). Índice sem coordenada fica de fora.
 */
internal fun hudFlightCenters(
    box: LayoutCoordinates,
    coordinates: Map<Int, LayoutCoordinates>,
    indices: List<Int>,
    horizontal: Boolean = false,
): Map<Int, Float> {
    if (!box.isAttached) return emptyMap()
    return indices.mapNotNull { index ->
        val coords = coordinates[index]?.takeIf { it.isAttached } ?: return@mapNotNull null
        val center = Offset(coords.size.width / 2f, coords.size.height / 2f)
        val local = box.localPositionOf(coords, center)
        index to (if (horizontal) local.x else local.y)
    }.toMap()
}

/**
 * Desenha cada voo como anel sobreposto; sem interação, sem animação própria (o progresso vem de fora).
 * [horizontal] leva o trajeto ao eixo X, ao longo da faixa de cima ou de baixo (#612).
 */
@Composable
internal fun HudBlackHoleFlights(flights: List<HudFlight>, horizontal: Boolean = false) {
    val density = LocalDensity.current
    Box(modifier = Modifier.fillMaxSize()) {
        flights.forEach { flight ->
            val frame = hudSinkFrame(flight.progress, flight.fromAlong, flight.toAlong)
            val shift = with(density) { (frame.along - frame.ringSize.toPx() / 2f).toDp() }
            Box(
                modifier = Modifier
                    .align(if (horizontal) Alignment.CenterStart else Alignment.TopCenter)
                    .offset(x = if (horizontal) shift else 0.dp, y = if (horizontal) 0.dp else shift)
                    .size(frame.ringSize)
                    .testTag("hudBlackHoleFlying"),
            ) {
                AppGargantuaRing(
                    arcs = emptyList(),
                    description = flight.label,
                    size = frame.ringSize,
                    stroke = HUD_RING_STROKE,
                )
            }
        }
    }
}

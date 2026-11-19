package com.usagemonitor.presentation.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onPlaced
import com.usagemonitor.HudEdge
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.components.gargantuaHorizonBody
import com.usagemonitor.presentation.ui.theme.AppShapes
import com.usagemonitor.verticalCollapsed

/**
 * Coluna vertical de anéis do modo flutuante expandido (A11a). É a mesma coluna da barra HUD encaixada:
 * um anel embaixo do outro, com o corpo de horizonte de eventos e o campo estelar recortados pela
 * mesma forma. Sem campo circular; o anel sob o ponteiro chama [onRingHovered] com o índice dele.
 */
@Composable
internal fun HudFloatingColumn(
    accounts: List<HudAccount>,
    fallbackLabel: String,
    fallbackTone: AppTone,
    language: AppLanguage,
    revealed: Boolean,
    onRingHovered: (Int) -> Unit,
    onRingRefresh: (Int) -> Unit,
    /** Clique no glifo em órbita do buraco recoleta a conta dele (A06). */
    onHoleGlyphRefresh: (UsageTargetKey) -> Unit = {},
    /** Clique no corpo da coluna sobre um anel recoleta a conta dele, sem arrastar nem fechar o balão (A06). */
    onRingClicked: (UsageTargetKey) -> Unit = {},
    onHoleClicked: () -> Unit = {},
    onHoleHovered: () -> Unit = {},
    hitBoxes: HudRingHitBoxes = remember { HudRingHitBoxes() },
    minimize: HudMinimizeProgress = HudMinimizeProgress(emptyMap(), moving = false),
    modifier: Modifier = Modifier
) {
    val columnSize = verticalCollapsed(accounts, fallbackLabel, compact = false)
    val ringItemBounds = hitBoxes
    val iris = rememberHudIrisFrame(revealed)
    val shape = AppShapes.large
    // O buraco ocupa o índice após a última conta, como na barra encaixada.
    val holeIndex = accounts.size
    // Voo da conta pelo buraco: entrada e saída seguem o progresso de minimizar, como na barra (A11i).
    val departures = rememberHudDepartures(accounts)
    val settle = departures.values.minOrNull()
    Box(
        modifier = modifier
            .size(columnSize)
            .hudEclipseIris(HudEdge.LEFT, iris)
            .gargantuaHorizonBody(shape)
            .onPlaced { coordinates -> ringItemBounds.body = coordinates }
            .hudPressGesture(
                draggable = false,
                onDragStart = {},
                onDragMove = {},
                onDragEnd = {},
                onClick = { position -> hudRingKeyAt(ringItemBounds, accounts, position)?.let(onRingClicked) },
            )
    ) {
        HudStarfieldCanvas(
            rings = { ringItemBounds.rings() },
            running = { hudStarRunningRings(accounts) },
            forbidden = { hudStarfieldForbidden(ringItemBounds.boxes, ringItemBounds.rings()) },
            holeIndex = holeIndex,
            modifier = Modifier.fillMaxSize().clip(shape)
        )
        HudRingStrip(
            accounts = accounts,
            edge = HudEdge.LEFT,
            fallbackLabel = fallbackLabel,
            fallbackTone = fallbackTone,
            size = columnSize,
            compact = false,
            language = language,
            onRingHovered = onRingHovered,
            onHoleClicked = onHoleClicked,
            onHoleHovered = onHoleHovered,
            onRingRefresh = onRingRefresh,
            onHoleGlyphRefresh = onHoleGlyphRefresh,
            departures = departures,
            settle = settle,
            minimizeProgress = minimize.values,
            onItemPlaced = { index, coordinates ->
                val body = ringItemBounds.body
                if (body != null && body.isAttached && coordinates.isAttached) {
                    ringItemBounds.boxes[index] = body.localBoundingBoxOf(coordinates)
                }
            },
            onRingPlaced = { index, coordinates -> ringItemBounds.measureRing(index, coordinates) }
        )
    }
}

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
    onHoleClicked: () -> Unit = {},
    onHoleHovered: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val columnSize = verticalCollapsed(accounts, fallbackLabel, compact = false)
    val ringItemBounds = remember { HudRingHitBoxes() }
    val iris = rememberHudIrisFrame(revealed)
    val shape = AppShapes.large
    // O buraco ocupa o índice após a última conta, como na barra encaixada.
    val holeIndex = accounts.size
    Box(
        modifier = modifier
            .size(columnSize)
            .hudEclipseIris(HudEdge.LEFT, iris)
            .gargantuaHorizonBody(shape)
            .onPlaced { coordinates -> ringItemBounds.body = coordinates }
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

package com.usagemonitor.presentation.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.usagemonitor.HUD_BLACK_HOLE_CELL_SIZE
import com.usagemonitor.HUD_RING_STROKE
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.components.AppGargantuaRing

/** Anel central do buraco (#612, C1): 40 dp; também é o poço do campo estelar da célula (raio 20 dp). */
internal val HUD_BLACK_HOLE_RING_SIZE: Dp = 40.dp

/**
 * Buraco negro (#600): as contas minimizadas da coluna vertical viram um anel sem arcos de cota
 * sem texto visível: a contagem vai só na semântica (#612, C1). Anel central de 40 dp; célula de 106 dp, a da geometria.
 * Clique (ou Enter com foco) chama [onClick], que alterna a lista. Entrar com o ponteiro chama [onHovered] (#612):
 * quem decide se abre é o dono do balão, que só abre se a lista ainda não é a do buraco.
 * Órbita (#602, A06): um glifo por conta de [members] gira no comboio em volta do anel.
 */
@Composable
internal fun HudBlackHoleCell(
    count: Int,
    members: List<HudAccount>,
    language: AppLanguage,
    onClick: () -> Unit,
    onHovered: () -> Unit,
    onItemPlaced: (LayoutCoordinates) -> Unit,
    onRingPlaced: (LayoutCoordinates) -> Unit,
    modifier: Modifier = Modifier
) {
    val label = hudBlackHoleCountLabel(count, language)
    val interaction = remember { MutableInteractionSource() }
    val isHovered by interaction.collectIsHoveredAsState()
    val currentOnHovered by rememberUpdatedState(onHovered)
    LaunchedEffect(isHovered) { if (isHovered) currentOnHovered() }
    Column(
        modifier = modifier
            .testTag("hudBlackHole")
            .semantics { contentDescription = label }
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .onPlaced { coordinates -> onItemPlaced(coordinates) },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.requiredSize(HUD_BLACK_HOLE_CELL_SIZE).onPlaced { coordinates -> onRingPlaced(coordinates) },
            contentAlignment = Alignment.Center
        ) {
            AppGargantuaRing(
                arcs = emptyList(),
                description = label,
                size = HUD_BLACK_HOLE_RING_SIZE,
                stroke = HUD_RING_STROKE
            )
            HudBlackHoleOrbitGlyphs(members = members, language = language)
        }
    }
}

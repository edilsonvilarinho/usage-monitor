package com.usagemonitor.presentation.ui

import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.usagemonitor.HUD_RING_SIZE
import com.usagemonitor.HUD_RING_STROKE
import com.usagemonitor.HUD_WORD_LINE
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.components.AppGargantuaRing

/**
 * Buraco negro (#600): as contas minimizadas da coluna vertical viram um anel sem arcos de cota
 * e a contagem numa linha só, com altura de [HUD_WORD_LINE]. Altura total = anel + linha, a da geometria.
 * O anel e a célula medidos entram no campo estelar pelo índice [index] (fora da faixa das contas).
 */
@Composable
internal fun HudBlackHoleCell(
    count: Int,
    language: AppLanguage,
    onHovered: (Boolean) -> Unit,
    onItemPlaced: (LayoutCoordinates) -> Unit,
    onRingPlaced: (LayoutCoordinates) -> Unit,
    modifier: Modifier = Modifier
) {
    val label = hudBlackHoleCountLabel(count, language)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    LaunchedEffect(hovered) {
        if (hovered) onHovered(true)
    }
    Column(
        modifier = modifier
            .testTag("hudBlackHole")
            .semantics { contentDescription = label }
            .hoverable(interaction)
            .onPlaced { coordinates -> onItemPlaced(coordinates) },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(HUD_RING_SIZE).onPlaced { coordinates -> onRingPlaced(coordinates) },
            contentAlignment = Alignment.Center
        ) {
            AppGargantuaRing(
                arcs = emptyList(),
                description = label,
                size = HUD_RING_SIZE,
                stroke = HUD_RING_STROKE
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            modifier = Modifier.height(HUD_WORD_LINE)
        )
    }
}

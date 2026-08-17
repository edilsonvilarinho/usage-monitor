package com.usagemonitor.presentation.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.usagemonitor.presentation.ui.theme.AppElevation
import com.usagemonitor.presentation.ui.theme.AppShapes

private const val ACCENT_GLOW_ALPHA = 0.22f
private const val ACCENT_GLOW_HEIGHT_FRACTION = 0.7f
private const val BORDER_ALPHA = 0.6f

/**
 * Superfície com profundidade: elevação, borda sutil e um brilho de acento
 * esmaecendo do topo.
 *
 * É o mesmo tratamento que `ApiUsageCard` aplica inline há tempos e que dá ao
 * dashboard a sensação de relevo. Extraído aqui para que telas novas não
 * recaiam em retângulos chapados de `surfaceVariant` com alpha.
 */
@Composable
fun DepthSurface(
    accent: Color,
    modifier: Modifier = Modifier,
    shape: Shape = AppShapes.medium,
    elevation: Dp = AppElevation.card,
    contentPadding: Dp = 14.dp,
    glowAlpha: Float = ACCENT_GLOW_ALPHA,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = BORDER_ALPHA))
    ) {
        Column(
            modifier = Modifier
                // A largura cheia não chega sozinha até aqui: o `Surface` do `Card`
                // propaga a constraint mínima, mas a `Column` que o `Card` põe entre
                // ele e o conteúdo não. Sem isto esta `Column` mede pelo texto mais
                // largo e o brilho — que é desenhado sobre o `size` dela — para no
                // meio do card.
                .fillMaxWidth()
                .clip(shape)
                .drawWithCache {
                    val glow = Brush.verticalGradient(
                        colors = listOf(accent.copy(alpha = glowAlpha), Color.Transparent),
                        startY = 0f,
                        endY = size.height * ACCENT_GLOW_HEIGHT_FRACTION
                    )
                    onDrawBehind {
                        drawRect(brush = glow)
                    }
                }
                .padding(contentPadding),
            content = content
        )
    }
}

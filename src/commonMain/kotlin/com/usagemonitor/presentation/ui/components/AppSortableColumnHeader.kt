package com.usagemonitor.presentation.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.usagemonitor.presentation.ui.theme.AppMotion
import com.usagemonitor.presentation.ui.theme.AppSpacing
import com.usagemonitor.presentation.ui.theme.appTween

/** Tags dos indicadores de direção, para o teste de componente (`AppSortableColumnHeaderTest`). */
internal const val SORTABLE_HEADER_ARROW_DESC_TAG = "sortable-header-arrow-desc"
internal const val SORTABLE_HEADER_ARROW_ASC_TAG = "sortable-header-arrow-asc"
internal const val SORTABLE_HEADER_ARROW_HINT_TAG = "sortable-header-arrow-hint"

/** Opacidade da seta fraca que a coluna inativa mostra ao passar o ponteiro. */
private const val HINT_ALPHA = 0.5f

/**
 * Legenda de coluna ordenável (#537). Mesma tipografia de [AppColumnHeaderLabel].
 *
 * - [sortDescending] `null` é coluna inativa: rótulo em `onSurfaceVariant`, sem seta; ao passar o
 *   ponteiro aparece uma seta fraca.
 * - Coluna ativa: rótulo em `onSurface` e seta à direita (descendente ou ascendente).
 * - A seta está fora do fluxo do `Row`: fica no vão à direita do rótulo, deslocada por `AppSpacing.xs`,
 *   e não entra na largura da célula. Ativar ou desativar a coluna não muda a largura da célula.
 * - Semântica: `Role.Button`, e `stateDescription` com a direção, porque cor e seta não informam sozinhas.
 *   Os textos vêm de quem chama, por `localized`.
 * - A cor muda por `appTween`, que respeita `LocalAppMotionPolicy`; a reordenação da lista não tem animação de item.
 */
@Composable
fun AppSortableColumnHeaderLabel(
    label: String,
    sortDescending: Boolean?,
    onClick: () -> Unit,
    descendingStateDescription: String,
    ascendingStateDescription: String,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val colors = MaterialTheme.colorScheme
    val active = sortDescending != null
    val labelColor by animateColorAsState(
        targetValue = if (active) colors.onSurface else colors.onSurfaceVariant,
        animationSpec = appTween(durationMillis = AppMotion.fast)
    )
    val arrowColor by animateColorAsState(
        targetValue = if (active) colors.onSurface else colors.onSurfaceVariant.copy(alpha = HINT_ALPHA),
        animationSpec = appTween(durationMillis = AppMotion.fast)
    )
    val arrowTag = when (sortDescending) {
        true -> SORTABLE_HEADER_ARROW_DESC_TAG
        false -> SORTABLE_HEADER_ARROW_ASC_TAG
        null -> SORTABLE_HEADER_ARROW_HINT_TAG
    }
    val arrowVisible = active || hovered
    val stateText = when (sortDescending) {
        true -> descendingStateDescription
        false -> ascendingStateDescription
        null -> null
    }

    Row(
        modifier = modifier
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { if (stateText != null) stateDescription = stateText },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        // Largura zero no fluxo, alinhada à esquerda e sem limite: a seta começa logo depois do rótulo
        // e ultrapassa a largura zero sem empurrar a célula.
        Box(modifier = Modifier.width(0.dp).wrapContentWidth(Alignment.Start, unbounded = true)) {
            if (arrowVisible) {
                Icon(
                    imageVector = if (sortDescending == false) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                    contentDescription = null,
                    tint = arrowColor,
                    modifier = Modifier
                        .padding(start = AppSpacing.xs)
                        .size(AppSpacing.md)
                        .testTag(arrowTag)
                )
            }
        }
    }
}

package com.usagemonitor.presentation.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.usagemonitor.presentation.ui.theme.AppMotion
import com.usagemonitor.presentation.ui.theme.AppShapes
import com.usagemonitor.presentation.ui.theme.AppSpacing
import com.usagemonitor.presentation.ui.theme.appTween

/**
 * Chip de alternância: uma restrição ligada ou desligada.
 *
 * Não é [AppSegmentedControl] com duas opções — segmentado diz "ou isto, ou
 * aquilo", e aqui existe um estado só, que está ativo ou não. Carrega
 * `selectable` pela mesma razão do segmentado: é o que `assertIsSelected`
 * observa.
 */
@Composable
fun AppToggleChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val container by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.surface
        },
        animationSpec = appTween(AppMotion.normal),
        label = "appToggleChipContainer"
    )
    val border by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
        animationSpec = appTween(AppMotion.normal),
        label = "appToggleChipBorder"
    )
    val content by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = appTween(AppMotion.normal),
        label = "appToggleChipContent"
    )
    val alpha = if (enabled) 1f else DISABLED_ALPHA

    Box(
        modifier = modifier
            .clip(AppShapes.small)
            .background(container.copy(alpha = container.alpha * alpha))
            .border(AppBorderWidth, border.copy(alpha = alpha), AppShapes.small)
            .selectable(selected = selected, enabled = enabled, onClick = onClick)
            .defaultMinSize(minHeight = CONTROL_HEIGHT)
            .padding(horizontal = AppSpacing.sm),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = content.copy(alpha = alpha),
            maxLines = 1
        )
    }
}

/**
 * Opção de cor: amostra redonda e rótulo, **uma** escolha entre várias (issue
 * #275, a cor de cada conta Claude).
 *
 * Não é [AppToggleChip]: aquele liga ou desliga uma restrição, este escolhe uma
 * entre N — é `selectable` com `Role.RadioButton`, que é o que `assertIsSelected`
 * observa. A opção escolhida carrega **marca além do realce**, com o espaço da
 * marca reservado em todas: cor nunca informa sozinha, e sem a reserva o rótulo
 * andaria para o lado a cada troca. [swatch] nulo é a opção "Padrão", desenhada
 * como anel vazio — não há cor própria a mostrar.
 */
@Composable
fun AppSwatchChip(
    label: String,
    swatch: Color?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val container by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        animationSpec = appTween(AppMotion.normal),
        label = "appSwatchChipContainer"
    )
    val border by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = appTween(AppMotion.normal),
        label = "appSwatchChipBorder"
    )
    val content = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    val ring = MaterialTheme.colorScheme.outline
    Row(
        modifier = modifier
            .clip(AppShapes.small)
            .background(container)
            .border(AppBorderWidth, border, AppShapes.small)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .defaultMinSize(minHeight = CONTROL_HEIGHT)
            .padding(horizontal = AppSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
    ) {
        Box(
            modifier = Modifier
                .size(SWATCH_SIZE)
                .clip(CircleShape)
                .then(
                    if (swatch != null) {
                        Modifier.background(swatch)
                    } else {
                        Modifier.border(AppBorderWidth, ring, CircleShape)
                    }
                )
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = content,
            maxLines = 1
        )
        Box(modifier = Modifier.width(SWATCH_MARK_WIDTH), contentAlignment = Alignment.Center) {
            if (selected) {
                Text(
                    text = "✓",
                    style = MaterialTheme.typography.labelLarge,
                    color = content,
                    maxLines = 1
                )
            }
        }
    }
}

private val SWATCH_SIZE = 10.dp

/**
 * Opção de glifo: um emoji (ou uma palavra curta) em vez de amostra e rótulo,
 * **uma** escolha entre várias (issue #287, o emoji de cada conta Claude).
 *
 * O contrato é o de [AppSwatchChip] — `selectable` com `Role.RadioButton`, marca
 * além do realce e o espaço da marca reservado em todas —, sem o rótulo: o glifo
 * é o conteúdo, e dezesseis opções com nome escrito tomariam três linhas da aba.
 * O nome vai em [description], que é a semântica da opção: é por ele que leitor
 * de tela e testes chegam a ela.
 */
@Composable
fun AppGlyphChip(
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyph: @Composable () -> Unit
) {
    val container by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        animationSpec = appTween(AppMotion.normal),
        label = "appGlyphChipContainer"
    )
    val border by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = appTween(AppMotion.normal),
        label = "appGlyphChipBorder"
    )
    val content = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier
            .clip(AppShapes.small)
            .background(container)
            .border(AppBorderWidth, border, AppShapes.small)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = description }
            .defaultMinSize(minHeight = CONTROL_HEIGHT)
            .padding(horizontal = AppSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        glyph()
        Box(modifier = Modifier.width(SWATCH_MARK_WIDTH), contentAlignment = Alignment.Center) {
            if (selected) {
                Text(
                    text = "✓",
                    style = MaterialTheme.typography.labelLarge,
                    color = content,
                    maxLines = 1
                )
            }
        }
    }
}

private val SWATCH_MARK_WIDTH = 10.dp

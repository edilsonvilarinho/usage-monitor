package com.usagemonitor.presentation.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.usagemonitor.HUD_BLACK_HOLE_LIST_HEADER
import com.usagemonitor.HUD_BLACK_HOLE_LIST_ROW
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.components.AccountEmojiGlyph
import com.usagemonitor.presentation.ui.components.AppProviderMark
import com.usagemonitor.presentation.ui.components.AppStatusPill
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.components.accentColorFor
import com.usagemonitor.presentation.ui.theme.AppAccents
import com.usagemonitor.presentation.ui.theme.AppSpacing
import kotlinx.coroutines.delay

/** Permanência do cursor numa linha antes de ela abrir o balão da conta (#600, A05). */
internal const val HUD_BLACK_HOLE_HOVER_DELAY_MS = 160L

/** Tamanho do glifo da marca e do emoji de cada linha da lista do buraco (#600, A05). */
private val BLACK_HOLE_ROW_MARK_SIZE = 16.dp

/**
 * Lista do buraco negro da HUD (#600, A05): uma linha por membro, na ordem recebida. [onSelect] recebe a posição
 * da conta em [members]. Hover espera [HUD_BLACK_HOLE_HOVER_DELAY_MS] antes de selecionar, para o cursor poder
 * passar por cima sem trocar; clique e Enter selecionam na hora.
 */
@Composable
internal fun HudBlackHoleList(
    members: List<HudAccount>,
    language: AppLanguage,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = hudBlackHoleCountLabel(members.size, language),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.height(HUD_BLACK_HOLE_LIST_HEADER).padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs)
        )
        members.forEachIndexed { index, account ->
            HudBlackHoleListRow(
                account = account,
                language = language,
                onSelect = { onSelect(index) }
            )
        }
    }
}

@Composable
private fun HudBlackHoleListRow(
    account: HudAccount,
    language: AppLanguage,
    onSelect: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    LaunchedEffect(hovered) {
        if (hovered) {
            delay(HUD_BLACK_HOLE_HOVER_DELAY_MS)
            onSelect()
        }
    }
    val emoji = account.accountEmoji
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(HUD_BLACK_HOLE_LIST_ROW)
            .hoverable(interactionSource = interaction)
            .clickable(interactionSource = interaction, indication = null) { onSelect() }
            .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppProviderMark(
            source = account.source,
            tint = account.accountAccent?.current ?: accentColorFor(source = account.source, accents = AppAccents.current),
            size = BLACK_HOLE_ROW_MARK_SIZE
        )
        if (emoji != null) {
            Spacer(Modifier.width(AppSpacing.xs))
            AccountEmojiGlyph(emoji = emoji, size = BLACK_HOLE_ROW_MARK_SIZE)
        }
        Spacer(Modifier.width(AppSpacing.sm))
        Text(
            text = account.label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(AppSpacing.sm))
        Text(
            text = account.focusLine.text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (account.autoMinimized) {
            Spacer(Modifier.width(AppSpacing.sm))
            AppStatusPill(label = hudBlackHoleAutoBadge(language), tone = AppTone.NEUTRAL)
        }
    }
}

package com.usagemonitor.presentation.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.usagemonitor.HUD_BLACK_HOLE_AUTO_NOTE
import com.usagemonitor.HUD_BLACK_HOLE_BACK_STRIP
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.theme.AppSpacing

/**
 * Conteúdo do balão do buraco negro (#600, A05). Sem [selected], mostra a lista das contas no buraco;
 * com ele, o balão da conta escolhida, com a volta "← N no buraco" no topo. [members] são os índices de
 * [accounts] que estão no buraco, na ordem publicada. [onSelect] recebe o índice da conta em [accounts].
 */
@Composable
internal fun HudBlackHoleBalloonContent(
    accounts: List<HudAccount>,
    members: List<Int>,
    selected: Int?,
    language: AppLanguage,
    accountActions: (@Composable (HudAccount) -> Unit)?,
    bodyHeight: Dp,
    onSelect: (Int) -> Unit,
    onBack: () -> Unit
) {
    val account = selected?.let { index -> accounts.getOrNull(index) }
    if (account == null) {
        HudBlackHoleList(
            members = members.mapNotNull { index -> accounts.getOrNull(index) },
            language = language,
            onSelect = { position -> members.getOrNull(position)?.let(onSelect) }
        )
    } else {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = hudBlackHoleBackLabel(members.size, language),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HUD_BLACK_HOLE_BACK_STRIP)
                    .clickable { onBack() }
                    .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs)
            )
            // A nota de auto-minimizada tem altura fixa e sai do corpo: a geometria soma as duas (#600, A05).
            val noteHeight = if (account.autoMinimized) HUD_BLACK_HOLE_AUTO_NOTE else 0.dp
            HudAccountBalloonContent(
                account,
                language,
                accountActions,
                bodyHeight - HUD_BLACK_HOLE_BACK_STRIP - noteHeight
            )
            if (account.autoMinimized) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(HUD_BLACK_HOLE_AUTO_NOTE)
                        .padding(horizontal = AppSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = hudBlackHoleAutoNote(language),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

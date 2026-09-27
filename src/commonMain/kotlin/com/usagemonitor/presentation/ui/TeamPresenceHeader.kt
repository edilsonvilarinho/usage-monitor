package com.usagemonitor.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.components.AppColumnHeaderLabel
import com.usagemonitor.presentation.ui.components.AppColumnHeaderRow
import com.usagemonitor.presentation.ui.components.AppDataSurface
import com.usagemonitor.presentation.ui.components.AppStatusIndicator
import com.usagemonitor.presentation.ui.components.AppTextField
import com.usagemonitor.presentation.ui.components.AppToggleChip
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.components.color
import com.usagemonitor.presentation.ui.theme.AppSpacing
import com.usagemonitor.presentation.viewmodel.TeamPresenceUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TeamPresenceHeader(
    state: TeamPresenceUiState.Success,
    language: AppLanguage,
    onSetOnlyOnline: (Boolean) -> Unit,
    onQueryChange: (String) -> Unit
) {
    // Superfície de dados como as outras, com o patamar `AppDepth.CARD` que ela
    // já traz: a sombra de diálogo que existia aqui punha 8dp sob um bloco que
    // não flutua sobre nada.
    //
    // `Arrangement.Top` porque este bloco separa os filhos com o `Spacer` que ele
    // já traz; o `spacedBy` default somaria 8dp a cada um deles.
    AppDataSurface(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = AppSpacing.md,
        verticalArrangement = Arrangement.Top
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // O e-mail é dado, e dado fica na cor do texto — a mesma decisão do
            // cabeçalho do modal de consumo.
            if (state.accountLabel != null) {
                Text(
                    text = state.accountLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (state.isAdminOverview) {
                Text(
                    text = TeamUsageLabels.allEmailGroups(
                        emailCount = state.emailGroups.size,
                        accountCount = state.presenceGroups.size,
                        language = language
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
            }
            LiveBadge(language = language)
            Text(
                text = TeamUsageLabels.lastChange(
                    instantLabel = state.lastChangedAt?.let { instant -> formatInstant(instant) },
                    language = language
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(6.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth().testTag(PRESENCE_SUMMARY_TAG),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Indicador de estado, não bloco de métrica: aqui o texto já traz o
            // número ("2 conectados"), e o protótipo desenha esta tela com ponto
            // e palavra. Eram três números soltos e coloridos — verde, azul e
            // branco — sem nada dizendo o que a cor significava. Os tons são os
            // mesmos das linhas abaixo, ou a mesma ideia teria duas cores na
            // mesma tela.
            AppStatusIndicator(
                label = TeamPresenceLabels.workingSummary(state.workingCount, language),
                tone = AppTone.OK
            )
            AppStatusIndicator(
                label = TeamPresenceLabels.onlineSummary(state.onlineCount, language),
                tone = AppTone.INFO
            )
            AppStatusIndicator(
                label = TeamPresenceLabels.knownSummary(state.totalCount, language),
                tone = AppTone.NEUTRAL
            )

            // Filtro binário: um segmentado de dois estados diria "ou isto, ou
            // aquilo", e o que existe aqui é uma restrição ligada ou desligada.
            // O `selectable` mantém a semântica que o teste observa.
            AppToggleChip(
                label = TeamPresenceLabels.onlyOnline(language),
                selected = state.onlyOnline,
                onClick = { onSetOnlyOnline(!state.onlyOnline) },
                modifier = Modifier.testTag(PRESENCE_ONLY_ONLINE_TAG)
            )

            // Campo de texto ao lado do chip, como no protótipo: o chip liga uma
            // restrição, o campo estreita por nome. Num time de vinte máquinas o
            // chip sozinho não acha ninguém.
            AppTextField(
                value = state.query,
                onValueChange = onQueryChange,
                placeholder = TeamPresenceLabels.filterPlaceholder(language),
                modifier = Modifier.width(PRESENCE_FILTER_FIELD_WIDTH).testTag(PRESENCE_FILTER_TAG)
            )
        }
    }
}

/**
 * Faixa de legendas das colunas, uma vez para a lista inteira.
 *
 * Antes cada linha reimprimia "Máquina", "Estado", "Trabalhando agora" e "Status"
 * ao lado do próprio valor. Numa lista de time isso dobra o texto da tela e o
 * ruído cresce com o número de pessoas — a legenda pertence à coluna, não à
 * célula. As larguras são as mesmas `PRESENCE_COLUMN_*` da linha, senão o
 * cabeçalho prometeria um alinhamento que o conteúdo não cumpre.
 *
 * Não é `stickyHeader`: fica fora da `LazyColumn` de propósito, porque na visão
 * global a lista já tem as faixas de conta rolando dentro dela e dois níveis de
 * cabeçalho grudado empilhariam.
 */
@Composable
internal fun TeamPresenceColumnHeader(
    language: AppLanguage,
    hasHealthColumn: Boolean,
    hasActionColumn: Boolean
) {
    AppColumnHeaderRow(
        modifier = Modifier
            .padding(end = SCROLLBAR_GUTTER)
            .testTag(PRESENCE_COLUMN_HEADER_TAG),
        horizontalPadding = PRESENCE_ROW_CONTENT_PADDING,
        spacing = PRESENCE_COLUMN_SPACING
    ) {
        AppColumnHeaderLabel(
            label = TeamPresenceLabels.columnState(language),
            modifier = Modifier.width(PRESENCE_COLUMN_STATE)
        )
        AppColumnHeaderLabel(
            label = TeamPresenceLabels.columnMember(language),
            modifier = Modifier.width(PRESENCE_COLUMN_IDENTITY)
        )
        AppColumnHeaderLabel(
            label = CliSessionsLabels.machine(language),
            modifier = Modifier.width(PRESENCE_COLUMN_MACHINE)
        )
        AppColumnHeaderLabel(
            label = TeamPresenceLabels.columnActiveSessions(language),
            modifier = Modifier.width(PRESENCE_COLUMN_ACTIVE_SESSIONS)
        )
        AppColumnHeaderLabel(
            label = TeamPresenceLabels.columnLastSeen(language),
            modifier = Modifier.width(PRESENCE_COLUMN_LAST_SEEN)
        )
        AppColumnHeaderLabel(
            label = TeamPresenceLabels.columnLastTurn(language),
            modifier = Modifier.width(PRESENCE_COLUMN_LAST_TURN)
        )
        if (hasHealthColumn) {
            AppColumnHeaderLabel(
                label = TeamUsageLabels.columnStatus(language),
                modifier = Modifier.width(PRESENCE_COLUMN_STATUS)
            )
        }
        if (hasActionColumn) {
            // A ação é coluna fixa à direita nas linhas e na faixa da conta; o vão
            // elástico é o que leva a legenda até o mesmo x.
            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(PRESENCE_ACTION_SLOT))
        }
    }
}

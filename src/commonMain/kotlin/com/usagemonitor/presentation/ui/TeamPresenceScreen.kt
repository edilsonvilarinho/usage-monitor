package com.usagemonitor.presentation.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.TeamMemberPresence
import com.usagemonitor.presentation.ui.components.DepthSurface
import com.usagemonitor.presentation.ui.theme.AppElevation
import com.usagemonitor.presentation.ui.theme.AppShapes
import com.usagemonitor.presentation.viewmodel.TeamPresenceAccountGroup
import com.usagemonitor.presentation.viewmodel.TeamPresenceUiState
import com.usagemonitor.presentation.viewmodel.TeamPresenceViewModel

internal const val PRESENCE_LIST_SCROLLBAR_TAG = "teamPresenceListScrollbar"
internal const val PRESENCE_ROW_TAG_PREFIX = "teamPresenceRow:"
internal const val PRESENCE_STATE_TAG_PREFIX = "teamPresenceState:"
internal const val PRESENCE_WORKING_TAG_PREFIX = "teamPresenceWorking:"
internal const val PRESENCE_LOCAL_BADGE_TAG_PREFIX = "teamPresenceLocal:"
internal const val PRESENCE_ACCOUNT_GROUP_TAG_PREFIX = "teamPresenceAccountGroup:"
internal const val PRESENCE_ONLY_ONLINE_TAG = "teamPresenceOnlyOnline"
internal const val PRESENCE_CLOCK_SKEW_TAG = "teamPresenceClockSkewNotice"
internal const val PRESENCE_SUMMARY_TAG = "teamPresenceSummary"

// Larguras das colunas num lugar só, pelo mesmo motivo da tela de consumo: a
// faixa da conta e a linha do integrante têm de cair no mesmo x.
private val PRESENCE_COLUMN_IDENTITY = 220.dp
private val PRESENCE_COLUMN_MACHINE = 150.dp
private val PRESENCE_COLUMN_STATE = 160.dp
private val PRESENCE_COLUMN_WORKING = 180.dp
private val PRESENCE_COLUMN_STATUS = 112.dp

private val PRESENCE_ROW_CONTENT_PADDING = 14.dp

/** Único componente stateful: lê o estado do ViewModel e delega para filhos puros. */
@Composable
fun TeamPresenceScreen(
    viewModel: TeamPresenceViewModel,
    language: AppLanguage,
    /** `deviceId` desta instalação; a linha correspondente ganha o selo. */
    localDeviceId: String?,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    TeamPresenceContent(
        state = state,
        language = language,
        localDeviceId = localDeviceId,
        onToggleAccount = { groupKey -> viewModel.toggleAccount(groupKey) },
        onSetOnlyOnline = { value -> viewModel.setOnlyOnline(value) },
        modifier = modifier
    )
}

@Composable
internal fun TeamPresenceContent(
    state: TeamPresenceUiState,
    language: AppLanguage,
    modifier: Modifier = Modifier,
    localDeviceId: String? = null,
    onToggleAccount: (String) -> Unit = {},
    onSetOnlyOnline: (Boolean) -> Unit = {}
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (state) {
            is TeamPresenceUiState.Loading -> CenteredMessage(CliSessionsLabels.loading(language))

            is TeamPresenceUiState.Error -> CenteredMessage(
                TeamPresenceLabels.error(state.message, language)
            )

            is TeamPresenceUiState.Success -> TeamPresenceList(
                state = state,
                language = language,
                localDeviceId = localDeviceId,
                onToggleAccount = onToggleAccount,
                onSetOnlyOnline = onSetOnlyOnline
            )
        }
    }
}

@Composable
private fun TeamPresenceList(
    state: TeamPresenceUiState.Success,
    language: AppLanguage,
    localDeviceId: String?,
    onToggleAccount: (String) -> Unit,
    onSetOnlyOnline: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TeamPresenceHeader(state = state, language = language, onSetOnlyOnline = onSetOnlyOnline)

        // Sem este aviso a coluna Estado passaria um número errado com cara de
        // certo: o carimbo vem do relógio do servidor, e sem NTP nos dois lados
        // ele deixa de significar o que promete.
        if (state.clockSkewSuspected) {
            Text(
                text = TeamPresenceLabels.clockSkewNotice(state.clockSkewMinutes, language),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth().testTag(PRESENCE_CLOCK_SKEW_TAG)
            )
        }

        if (state.isEmpty) {
            CenteredMessage(TeamPresenceLabels.empty(language))
            return@Column
        }

        if (state.isFilteredEmpty) {
            CenteredMessage(TeamPresenceLabels.emptyFiltered(language))
            return@Column
        }

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            val listState = rememberLazyListState()

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(end = SCROLLBAR_GUTTER),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (group in state.presenceGroups) {
                    // Faixa só na visão global: no modal de uma conta só ela já é
                    // a da janela e repeti-la aqui seria ruído.
                    if (state.isAdminOverview) {
                        item(key = "account:${group.accountKey}") {
                            TeamPresenceAccountHeader(
                                group = group,
                                expanded = state.isAccountExpanded(group),
                                language = language,
                                onToggle = { onToggleAccount(group.groupKey) }
                            )
                        }
                    }

                    if (!state.isAccountExpanded(group)) {
                        continue
                    }

                    items(count = group.entries.size, key = { index -> group.entries[index].memberKey }) { index ->
                        TeamPresenceRow(
                            entry = group.entries[index],
                            language = language,
                            isLocalMachine = localDeviceId != null &&
                                group.entries[index].deviceId == localDeviceId
                        )
                    }
                }
            }

            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(listState),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .testTag(PRESENCE_LIST_SCROLLBAR_TAG)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TeamPresenceHeader(
    state: TeamPresenceUiState.Success,
    language: AppLanguage,
    onSetOnlyOnline: (Boolean) -> Unit
) {
    DepthSurface(
        accent = CACHE_READ_COLOR,
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.large,
        elevation = AppElevation.dialog,
        contentPadding = 16.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state.accountLabel != null) {
                Text(
                    text = state.accountLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = CACHE_READ_COLOR,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (state.isAdminOverview) {
                Text(
                    text = TeamUsageLabels.allAccounts(state.presenceGroups.size, language),
                    style = MaterialTheme.typography.labelMedium,
                    color = CACHE_READ_COLOR,
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
            SummaryNumber(
                value = state.workingCount.toString(),
                caption = TeamPresenceLabels.workingSummary(state.workingCount, language),
                valueColor = CACHE_READ_COLOR
            )
            SummaryNumber(
                value = state.onlineCount.toString(),
                caption = TeamPresenceLabels.onlineSummary(state.onlineCount, language),
                valueColor = INPUT_COLOR
            )
            SummaryNumber(
                value = state.totalCount.toString(),
                caption = TeamPresenceLabels.knownSummary(state.totalCount, language),
                valueColor = MaterialTheme.colorScheme.onSurface
            )

            FilterChip(
                selected = state.onlyOnline,
                onClick = { onSetOnlyOnline(!state.onlyOnline) },
                label = { Text(TeamPresenceLabels.onlyOnline(language)) },
                modifier = Modifier.testTag(PRESENCE_ONLY_ONLINE_TAG)
            )
        }
    }
}

@Composable
private fun SummaryNumber(
    value: String,
    caption: String,
    valueColor: androidx.compose.ui.graphics.Color
) {
    Column {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = valueColor
        )
        Text(
            text = caption,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Faixa que separa uma conta da seguinte na visão global.
 *
 * Mostra o rótulo **e** o `accountUuid` pela mesma razão da tela de consumo: o
 * rótulo é texto que o administrador digitou e o servidor não o verifica, então
 * ele orienta mas não prova.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TeamPresenceAccountHeader(
    group: TeamPresenceAccountGroup,
    expanded: Boolean,
    language: AppLanguage,
    onToggle: () -> Unit
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clickable(onClick = onToggle)
            .padding(horizontal = PRESENCE_ROW_CONTENT_PADDING)
            .testTag("$PRESENCE_ACCOUNT_GROUP_TAG_PREFIX${group.accountKey.orEmpty()}"),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Ícone dentro da coluna de identidade, como na linha do integrante: é o
        // que mantém as colunas seguintes no mesmo x nas duas.
        Row(
            modifier = Modifier.width(PRESENCE_COLUMN_IDENTITY),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = if (expanded) {
                    TeamUsageLabels.collapseAccount(language)
                } else {
                    TeamUsageLabels.expandAccount(language)
                },
                tint = CACHE_READ_COLOR
            )
            Column {
                Text(
                    text = group.accountLabel ?: TeamUsageLabels.unlabeledAccount(language),
                    style = MaterialTheme.typography.titleSmall,
                    color = CACHE_READ_COLOR,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = group.accountKey.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        MetricText(
            label = TeamPresenceLabels.columnState(language),
            value = TeamPresenceLabels.groupSummary(group.onlineCount, group.totalCount, language),
            modifier = Modifier.width(PRESENCE_COLUMN_STATE)
        )

        MetricText(
            label = TeamPresenceLabels.columnWorking(language),
            value = group.workingCount.toString(),
            valueColor = CACHE_READ_COLOR,
            modifier = Modifier.width(PRESENCE_COLUMN_WORKING)
        )
    }
}

/**
 * Uma pessoa na lista.
 *
 * O ponto de estado é **estático**, não pulsa. Uma animação infinita numa linha
 * de lista travaria o `waitForIdle` dos testes de componente — a mesma razão pela
 * qual `rememberSessionPulseFrame` devolve `null` antes de criar a transição
 * quando não há nada a piscar.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TeamPresenceRow(
    entry: TeamMemberPresence,
    language: AppLanguage,
    isLocalMachine: Boolean
) {
    // Neutro para offline, na mesma gramática de "sem atividade" da tela de
    // consumo; quem está trabalhando ganha o acento mais forte.
    val accent = when {
        entry.isWorkingNow -> CACHE_READ_COLOR
        entry.isOnline -> INPUT_COLOR
        else -> MaterialTheme.colorScheme.outline
    }

    DepthSurface(
        accent = accent,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("$PRESENCE_ROW_TAG_PREFIX${entry.memberKey}"),
        shape = AppShapes.medium,
        contentPadding = PRESENCE_ROW_CONTENT_PADDING
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.width(PRESENCE_COLUMN_IDENTITY)) {
                Text(
                    text = entry.alias,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isLocalMachine) {
                    Text(
                        text = TeamPresenceLabels.thisMachine(language),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag(
                            "$PRESENCE_LOCAL_BADGE_TAG_PREFIX${entry.memberKey}"
                        )
                    )
                }
            }

            MetricText(
                label = CliSessionsLabels.machine(language),
                value = entry.machineLabel,
                modifier = Modifier.width(PRESENCE_COLUMN_MACHINE)
            )

            // Semântica mesclada: "Conectado" e o horário do último sinal são uma
            // unidade de leitura, e separá-los faria o leitor de tela anunciar
            // dois fragmentos soltos.
            Row(
                modifier = Modifier
                    .width(PRESENCE_COLUMN_STATE)
                    .semantics(mergeDescendants = true) { }
                    .testTag("$PRESENCE_STATE_TAG_PREFIX${entry.memberKey}"),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(AppShapes.small)
                        .background(if (entry.isOnline) INPUT_COLOR else MaterialTheme.colorScheme.outline)
                )
                Column {
                    Text(
                        text = if (entry.isOnline) {
                            TeamPresenceLabels.online(language)
                        } else {
                            TeamPresenceLabels.offline(language)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (entry.isOnline) INPUT_COLOR else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Hora absoluta, e não tempo decorrido: o decorrido mudaria a
                    // cada tique e recomporia a lista inteira de 5 em 5 segundos.
                    Text(
                        text = TeamPresenceLabels.lastSignal(
                            instantLabel = entry.lastSeenAt?.let { instant -> formatInstant(instant) },
                            language = language
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(
                modifier = Modifier
                    .width(PRESENCE_COLUMN_WORKING)
                    .semantics(mergeDescendants = true) { }
                    .testTag("$PRESENCE_WORKING_TAG_PREFIX${entry.memberKey}")
            ) {
                Text(
                    text = if (entry.isWorkingNow) {
                        TeamPresenceLabels.activeSessions(entry.activeSessionCount, language)
                    } else {
                        TeamPresenceLabels.idle(language)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (entry.isWorkingNow) {
                        CACHE_READ_COLOR
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Text(
                    text = TeamPresenceLabels.lastTurn(
                        instantLabel = entry.lastActivityAt?.let { instant -> formatInstant(instant) },
                        language = language
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val worstHealth = entry.worstHealth
            if (worstHealth != null) {
                TeamHealthCell(
                    health = worstHealth,
                    language = language,
                    modifier = Modifier.width(PRESENCE_COLUMN_STATUS)
                )
            }
        }
    }
}

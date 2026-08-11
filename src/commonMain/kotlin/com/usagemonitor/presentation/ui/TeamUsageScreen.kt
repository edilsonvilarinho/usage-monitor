package com.usagemonitor.presentation.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CliSessionRange
import com.usagemonitor.domain.entity.TeamMemberUsage
import com.usagemonitor.presentation.ui.components.DepthSurface
import com.usagemonitor.presentation.ui.theme.AppElevation
import com.usagemonitor.presentation.ui.theme.AppShapes
import com.usagemonitor.presentation.viewmodel.TeamUsageUiState
import com.usagemonitor.presentation.viewmodel.TeamUsageViewModel

internal const val TEAM_LIST_SCROLLBAR_TAG = "teamUsageListScrollbar"
internal const val TEAM_MEMBER_ROW_TAG_PREFIX = "teamMemberRow:"
internal const val TEAM_MEMBER_SESSIONS_TAG_PREFIX = "teamMemberSessions:"

/** Único componente stateful: lê o estado do ViewModel e delega para filhos puros. */
@Composable
fun TeamUsageScreen(
    viewModel: TeamUsageViewModel,
    language: AppLanguage,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    TeamUsageContent(
        state = state,
        language = language,
        onSelectRange = { range -> viewModel.setRange(range) },
        onToggleMember = { deviceId -> viewModel.toggleMember(deviceId) },
        modifier = modifier
    )
}

@Composable
internal fun TeamUsageContent(
    state: TeamUsageUiState,
    language: AppLanguage,
    onSelectRange: (CliSessionRange) -> Unit,
    onToggleMember: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (state) {
            is TeamUsageUiState.Loading -> CenteredMessage(
                if (language == AppLanguage.PT) "Consultando o servidor do time…" else "Querying the team server…"
            )

            is TeamUsageUiState.Error -> CenteredMessage(
                TeamUsageLabels.serverError(state.message, language)
            )

            is TeamUsageUiState.Success -> TeamUsageList(
                state = state,
                language = language,
                onSelectRange = onSelectRange,
                onToggleMember = onToggleMember
            )
        }
    }
}

@Composable
private fun TeamUsageList(
    state: TeamUsageUiState.Success,
    language: AppLanguage,
    onSelectRange: (CliSessionRange) -> Unit,
    onToggleMember: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TeamUsageHeader(state = state, language = language, onSelectRange = onSelectRange)

        if (state.isEmpty) {
            CenteredMessage(
                TeamUsageLabels.emptyInRange(state.range, state.rangeAnchored, language)
            )
            return@Column
        }

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            val listState = rememberLazyListState()

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(end = SCROLLBAR_GUTTER),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (member in state.members) {
                    item(key = member.deviceId) {
                        TeamMemberRow(
                            member = member,
                            share = state.tokenShareOf(member),
                            expanded = member.deviceId in state.expandedDeviceIds,
                            language = language,
                            onToggle = { onToggleMember(member.deviceId) }
                        )
                    }

                    if (member.deviceId in state.expandedDeviceIds) {
                        // As sessões entram como itens irmãos, e não dentro da
                        // linha: aninhar uma lista rolável em outra quebra a
                        // rolagem e desliga o reaproveitamento de itens.
                        items(
                            count = member.sessions.size,
                            key = { index -> "${member.deviceId}:${member.sessions[index].sessionId}" }
                        ) { index ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 24.dp)
                                    .testTag("$TEAM_MEMBER_SESSIONS_TAG_PREFIX${member.deviceId}")
                            ) {
                                CliSessionRow(
                                    session = member.sessions[index],
                                    language = language,
                                    // A sessão é de outra máquina: o transcript
                                    // não está aqui, então não há detalhe a abrir.
                                    onOpen = {}
                                )
                            }
                        }
                    }
                }
            }

            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(listState),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .testTag(TEAM_LIST_SCROLLBAR_TAG)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TeamUsageHeader(
    state: TeamUsageUiState.Success,
    language: AppLanguage,
    onSelectRange: (CliSessionRange) -> Unit
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
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column {
                Text(
                    text = TeamUsageLabels.memberCount(state.activeMemberCount, language),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = TeamUsageLabels.sessionCount(state.sessionCount, language),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column {
                Text(
                    text = formatQuantity(state.totalTokens),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = CACHE_READ_COLOR
                )
                Text(
                    text = TeamUsageLabels.columnTokens(language),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column {
                Text(
                    text = if (state.isTotalCostComplete) {
                        formatMicrosUsdShort(state.totalCostMicros)
                    } else {
                        "${formatMicrosUsdShort(state.totalCostMicros)}+"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = INPUT_COLOR
                )
                Text(
                    text = TeamUsageLabels.estimatedTotalInRange(state.range, state.rangeEndsAt, language),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (entry in CliSessionRange.entries) {
                FilterChip(
                    selected = state.range == entry,
                    onClick = { onSelectRange(entry) },
                    label = { Text(TeamUsageLabels.rangeLabel(entry, language)) }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TeamMemberRow(
    member: TeamMemberUsage,
    share: Double,
    expanded: Boolean,
    language: AppLanguage,
    onToggle: () -> Unit
) {
    // Integrante sem uso no período fica neutro: destacá-lo com a mesma cor de
    // quem consumiu daria a impressão de atividade que não houve.
    val accent = if (member.hasActivity) CACHE_READ_COLOR else MaterialTheme.colorScheme.outline

    DepthSurface(
        accent = accent,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = member.hasActivity, onClick = onToggle)
            .testTag("$TEAM_MEMBER_ROW_TAG_PREFIX${member.deviceId}"),
        glowAlpha = 0.16f,
        contentPadding = 14.dp
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.width(210.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (member.hasActivity) {
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (expanded) {
                            TeamUsageLabels.collapse(language)
                        } else {
                            TeamUsageLabels.expand(language)
                        },
                        tint = accent
                    )
                }
                Column {
                    Text(
                        text = member.alias,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = TeamUsageLabels.lastSeen(
                            instantLabel = member.lastSeenAt?.let { instant -> formatInstant(instant) },
                            language = language
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Larguras fixas, como na lista de sessões: sem elas cada linha
            // dimensiona pelo próprio número e as colunas param de alinhar.
            MetricText(
                label = TeamUsageLabels.columnMachine(language),
                value = member.machineLabel,
                modifier = Modifier.width(140.dp)
            )

            MetricText(
                label = TeamUsageLabels.sessionCount(member.sessionCount, language),
                value = if (member.hasActivity) {
                    formatQuantity(member.totalTokens)
                } else {
                    TeamUsageLabels.noActivityInRange(language)
                },
                valueColor = if (member.hasActivity) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.width(140.dp)
            )

            MetricText(
                label = TeamUsageLabels.columnCost(language),
                value = if (member.isCostComplete) {
                    formatMicrosUsd(member.totalCostMicros)
                } else {
                    "${formatMicrosUsd(member.totalCostMicros)}+"
                },
                valueColor = INPUT_COLOR,
                modifier = Modifier.width(96.dp)
            )

            Column(modifier = Modifier.width(96.dp)) {
                MetricText(TeamUsageLabels.columnShare(language), formatPercent(share))
                Spacer(modifier = Modifier.height(4.dp))
                MeterBar(fraction = share, color = accent, height = 4.dp)
            }
        }
    }
}

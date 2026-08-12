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
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
internal const val TEAM_MEMBER_REMOVE_TAG_PREFIX = "teamMemberRemove:"
internal const val TEAM_REMOVE_CONFIRM_TAG = "teamMemberRemoveConfirm"

/** Único componente stateful: lê o estado do ViewModel e delega para filhos puros. */
@Composable
fun TeamUsageScreen(
    viewModel: TeamUsageViewModel,
    language: AppLanguage,
    /** `deviceId` desta instalação; a linha correspondente não ganha o botão. */
    localDeviceId: String?,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val removalError by viewModel.removalError.collectAsState()

    TeamUsageContent(
        state = state,
        language = language,
        localDeviceId = localDeviceId,
        removalError = removalError,
        onSelectRange = { range -> viewModel.setRange(range) },
        onToggleMember = { deviceId -> viewModel.toggleMember(deviceId) },
        onRemoveMember = { deviceId -> viewModel.removeMember(deviceId) },
        onDismissRemovalError = { viewModel.clearRemovalError() },
        modifier = modifier
    )
}

@Composable
internal fun TeamUsageContent(
    state: TeamUsageUiState,
    language: AppLanguage,
    onSelectRange: (CliSessionRange) -> Unit,
    onToggleMember: (String) -> Unit,
    modifier: Modifier = Modifier,
    localDeviceId: String? = null,
    removalError: String? = null,
    onRemoveMember: (String) -> Unit = {},
    onDismissRemovalError: () -> Unit = {}
) {
    // Qual integrante está aguardando confirmação. Estado de tela, não do
    // servidor: o laço ao vivo recarrega a lista a cada 5s e não pode fechar o
    // diálogo debaixo do usuário.
    var pendingRemoval by remember { mutableStateOf<TeamMemberUsage?>(null) }

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
                localDeviceId = localDeviceId,
                removalError = removalError,
                onSelectRange = onSelectRange,
                onToggleMember = onToggleMember,
                onRequestRemoveMember = { member -> pendingRemoval = member },
                onDismissRemovalError = onDismissRemovalError
            )
        }
    }

    val memberToRemove = pendingRemoval
    if (memberToRemove != null) {
        RemoveMemberConfirmation(
            member = memberToRemove,
            language = language,
            onConfirm = {
                pendingRemoval = null
                onRemoveMember(memberToRemove.deviceId)
            },
            onDismiss = { pendingRemoval = null }
        )
    }
}

/** Confirmação obrigatória: a remoção apaga dados e não tem desfazer. */
@Composable
private fun RemoveMemberConfirmation(
    member: TeamMemberUsage,
    language: AppLanguage,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(TeamUsageLabels.removeMemberTitle(language)) },
        text = { Text(TeamUsageLabels.removeMemberWarning(member.alias, language)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag(TEAM_REMOVE_CONFIRM_TAG)
            ) {
                Text(
                    text = TeamUsageLabels.confirmRemoval(language),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(TeamUsageLabels.cancel(language))
            }
        }
    )
}

@Composable
private fun TeamUsageList(
    state: TeamUsageUiState.Success,
    language: AppLanguage,
    localDeviceId: String?,
    removalError: String?,
    onSelectRange: (CliSessionRange) -> Unit,
    onToggleMember: (String) -> Unit,
    onRequestRemoveMember: (TeamMemberUsage) -> Unit,
    onDismissRemovalError: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TeamUsageHeader(state = state, language = language, onSelectRange = onSelectRange)

        if (removalError != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = TeamUsageLabels.removalError(removalError, language),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onDismissRemovalError) {
                    Text(TeamUsageLabels.cancel(language))
                }
            }
        }

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
                            // Esta máquina volta no próximo envio, então oferecer
                            // o botão só entregaria uma remoção que se desfaz
                            // sozinha — e apagaria o histórico dela no caminho.
                            removable = localDeviceId != null && member.deviceId != localDeviceId,
                            onToggle = { onToggleMember(member.deviceId) },
                            onRemove = { onRequestRemoveMember(member) }
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
                    text = TeamUsageLabels.estimatedTotalInRange(
                        range = state.range,
                        endsAt = state.rangeEndsAt,
                        isAnchored = state.rangeAnchored,
                        language = language
                    ),
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
    removable: Boolean,
    onToggle: () -> Unit,
    onRemove: () -> Unit
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

            if (removable) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.testTag("$TEAM_MEMBER_REMOVE_TAG_PREFIX${member.deviceId}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = TeamUsageLabels.removeMember(language),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
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
import com.usagemonitor.presentation.viewmodel.TeamAccountGroup
import com.usagemonitor.presentation.viewmodel.TeamSessionDetailUiState
import com.usagemonitor.presentation.viewmodel.TeamUsageUiState
import com.usagemonitor.presentation.viewmodel.TeamUsageViewModel

internal const val TEAM_LIST_SCROLLBAR_TAG = "teamUsageListScrollbar"
internal const val TEAM_DETAIL_SCROLLBAR_TAG = "teamUsageDetailScrollbar"
internal const val TEAM_MEMBER_ROW_TAG_PREFIX = "teamMemberRow:"
internal const val TEAM_MEMBER_SESSIONS_TAG_PREFIX = "teamMemberSessions:"
internal const val TEAM_MEMBER_REMOVE_TAG_PREFIX = "teamMemberRemove:"
internal const val TEAM_MEMBER_HEALTH_TAG_PREFIX = "teamMemberHealth:"
internal const val TEAM_REMOVE_CONFIRM_TAG = "teamMemberRemoveConfirm"
internal const val TEAM_ADMIN_OVERVIEW_TAG = "teamAdminOverviewBadge"
internal const val TEAM_ACCOUNT_GROUP_TAG_PREFIX = "teamAccountGroup:"
internal const val TEAM_SLIDING_WINDOW_NOTICE_TAG = "teamSlidingWindowNotice"

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
        onToggleMember = { memberKey -> viewModel.toggleMember(memberKey) },
        onRemoveMember = { memberKey -> viewModel.removeMember(memberKey) },
        onDismissRemovalError = { viewModel.clearRemovalError() },
        onOpenSession = { memberKey, sessionId -> viewModel.openSession(memberKey, sessionId) },
        onCloseDetail = { viewModel.closeDetail() },
        onToggleAdvanced = { viewModel.toggleAdvanced() },
        onToggleGlossary = { viewModel.toggleGlossary() },
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
    onDismissRemovalError: () -> Unit = {},
    // Com default para não arrastar as chamadas que não exercitam o detalhe.
    onOpenSession: (String, String) -> Unit = { _, _ -> },
    onCloseDetail: () -> Unit = {},
    onToggleAdvanced: () -> Unit = {},
    onToggleGlossary: () -> Unit = {}
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

            is TeamUsageUiState.Success -> {
                val detail = state.detail
                if (detail == null) {
                    TeamUsageList(
                        state = state,
                        language = language,
                        localDeviceId = localDeviceId,
                        removalError = removalError,
                        onSelectRange = onSelectRange,
                        onToggleMember = onToggleMember,
                        onOpenSession = onOpenSession,
                        onRequestRemoveMember = { member -> pendingRemoval = member },
                        onDismissRemovalError = onDismissRemovalError
                    )
                } else {
                    TeamSessionDetailPane(
                        detail = detail,
                        language = language,
                        advancedExpanded = state.advancedExpanded,
                        glossaryExpanded = state.glossaryExpanded,
                        onCloseDetail = onCloseDetail,
                        onToggleAdvanced = onToggleAdvanced,
                        onToggleGlossary = onToggleGlossary
                    )
                }
            }
        }
    }

    val memberToRemove = pendingRemoval
    if (memberToRemove != null) {
        RemoveMemberConfirmation(
            member = memberToRemove,
            language = language,
            onConfirm = {
                pendingRemoval = null
                onRemoveMember(memberToRemove.memberKey)
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
    onOpenSession: (String, String) -> Unit,
    onRequestRemoveMember: (TeamMemberUsage) -> Unit,
    onDismissRemovalError: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TeamUsageHeader(state = state, language = language, onSelectRange = onSelectRange)

        // Sem este aviso a diferença para o modal de uma conta parece defeito:
        // lá a janela de 5h começa no reset da quota daquela conta, e aqui não
        // pode começar no reset de nenhuma, porque são várias.
        if (state.isAdminOverview && state.range == CliSessionRange.LAST_5H) {
            Text(
                text = TeamUsageLabels.slidingWindowNotice(language),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().testTag(TEAM_SLIDING_WINDOW_NOTICE_TAG)
            )
        }

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
                for (group in state.memberGroups) {
                    // Cabeçalho só na visão global: no modal de uma conta só, a
                    // conta já é a da janela e repeti-la aqui seria ruído.
                    if (state.isAdminOverview) {
                        item(key = "account:${group.accountKey}") {
                            TeamAccountGroupHeader(group = group, language = language)
                        }
                    }

                    for (member in group.members) {
                        item(key = member.memberKey) {
                            TeamMemberRow(
                                member = member,
                                share = state.tokenShareOf(member),
                                expanded = member.memberKey in state.expandedMemberKeys,
                                language = language,
                                // Esta máquina volta no próximo envio, então oferecer
                                // o botão só entregaria uma remoção que se desfaz
                                // sozinha — e apagaria o histórico dela no caminho.
                                removable = localDeviceId != null && member.deviceId != localDeviceId,
                                onToggle = { onToggleMember(member.memberKey) },
                                onRemove = { onRequestRemoveMember(member) }
                            )
                        }

                        if (member.memberKey in state.expandedMemberKeys) {
                            // As sessões entram como itens irmãos, e não dentro da
                            // linha: aninhar uma lista rolável em outra quebra a
                            // rolagem e desliga o reaproveitamento de itens.
                            items(
                                count = member.sessions.size,
                                key = { index ->
                                    "${member.memberKey}:${member.sessions[index].sessionId}"
                                }
                            ) { index ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 24.dp)
                                        .testTag("$TEAM_MEMBER_SESSIONS_TAG_PREFIX${member.deviceId}")
                                ) {
                                    val session = member.sessions[index]
                                    CliSessionRow(
                                        session = session,
                                        language = language,
                                        // O transcript é de outra máquina, mas os
                                        // turnos estão no servidor desde o primeiro
                                        // envio: o detalhe vem de lá.
                                        onOpen = {
                                            onOpenSession(member.memberKey, session.sessionId)
                                        }
                                    )
                                }
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

/**
 * Faixa que separa uma conta da seguinte na visão global.
 *
 * Mostra o rótulo **e** o `accountUuid`: o rótulo é texto que o administrador
 * digitou ao emitir a chave e o servidor não o verifica, então ele orienta mas
 * não prova. Conta sem chave emitida aparece só pelo uuid.
 */
@Composable
private fun TeamAccountGroupHeader(
    group: TeamAccountGroup,
    language: AppLanguage
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .testTag("$TEAM_ACCOUNT_GROUP_TAG_PREFIX${group.accountKey.orEmpty()}"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = group.accountLabel ?: TeamUsageLabels.unlabeledAccount(language),
            style = MaterialTheme.typography.titleSmall,
            color = CACHE_READ_COLOR,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = group.accountKey.orEmpty(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = TeamUsageLabels.memberCount(group.activeMemberCount, language),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
            if (state.isAdminOverview) {
                Text(
                    text = TeamUsageLabels.allAccounts(state.memberGroups.size, language),
                    style = MaterialTheme.typography.labelMedium,
                    color = CACHE_READ_COLOR,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag(TEAM_ADMIN_OVERVIEW_TAG)
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
                // O veredito por sessão vive dois níveis abaixo, dentro de um
                // integrante recolhido. Aqui ele aparece sem nenhum clique.
                HealthTallyText(tally = state.healthTally, language = language)
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

            // Pior status entre as sessões deste integrante. Sem ele, a única
            // sessão saturada de um time fica escondida atrás de um clique que
            // ninguém dá — nada na linha recolhida indicaria que vale a pena.
            val worstHealth = member.worstHealth
            if (worstHealth != null) {
                val healthAccent = healthColor(worstHealth)
                Row(
                    modifier = Modifier
                        .width(112.dp)
                        .testTag("$TEAM_MEMBER_HEALTH_TAG_PREFIX${member.deviceId}"),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(AppShapes.small)
                            .background(healthAccent)
                    )
                    MetricText(
                        label = TeamUsageLabels.columnStatus(language),
                        value = TeamUsageLabels.healthShort(worstHealth, language),
                        valueColor = healthAccent
                    )
                }
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

// ----------------------------------------------------------------------------
// Detalhe da sessão
// ----------------------------------------------------------------------------

/**
 * O mesmo painel do modal de Sessões CLI, para uma sessão de outra máquina.
 *
 * As seções vêm de `CliSessionsScreen` — a sessão de um colega tem de ser lida
 * exatamente como a da própria máquina. Aqui só se orquestra a rolagem e o
 * estado de carga; nenhuma métrica é recalculada.
 */
@Composable
private fun TeamSessionDetailPane(
    detail: TeamSessionDetailUiState,
    language: AppLanguage,
    advancedExpanded: Boolean,
    glossaryExpanded: Boolean,
    onCloseDetail: () -> Unit,
    onToggleAdvanced: () -> Unit,
    onToggleGlossary: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onCloseDetail) {
                Text(TeamUsageLabels.back(language))
            }
            Text(
                text = shortSessionId(detail.sessionId),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        when (detail) {
            is TeamSessionDetailUiState.Loading -> CenteredMessage(
                TeamUsageLabels.detailLoading(language)
            )

            is TeamSessionDetailUiState.Error -> CenteredMessage(
                TeamUsageLabels.serverError(detail.message, language)
            )

            is TeamSessionDetailUiState.Ready -> TeamSessionDetailBody(
                detail = detail,
                language = language,
                advancedExpanded = advancedExpanded,
                glossaryExpanded = glossaryExpanded,
                onToggleAdvanced = onToggleAdvanced,
                onToggleGlossary = onToggleGlossary
            )
        }
    }
}

@Composable
private fun TeamSessionDetailBody(
    detail: TeamSessionDetailUiState.Ready,
    language: AppLanguage,
    advancedExpanded: Boolean,
    glossaryExpanded: Boolean,
    onToggleAdvanced: () -> Unit,
    onToggleGlossary: () -> Unit
) {
    val scrollState = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                // Mesma razão da lista: a barra flutua sobre o conteúdo.
                .padding(end = SCROLLBAR_GUTTER),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CliSessionDetailSections(
                detail = detail.result.detail,
                analytics = detail.result.analytics,
                language = language,
                advancedExpanded = advancedExpanded,
                glossaryExpanded = glossaryExpanded,
                onToggleAdvanced = onToggleAdvanced,
                onToggleGlossary = onToggleGlossary,
                missingTurnsNotice = if (detail.turnsUnavailable) {
                    TeamUsageLabels.missingTurnsNotice(language)
                } else {
                    null
                }
            )
        }

        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(scrollState),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .testTag(TEAM_DETAIL_SCROLLBAR_TAG)
        )
    }
}

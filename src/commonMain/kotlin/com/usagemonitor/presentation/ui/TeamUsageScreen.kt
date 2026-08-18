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
import androidx.compose.foundation.shape.CircleShape
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
import com.usagemonitor.domain.entity.CliSessionHealth
import com.usagemonitor.domain.entity.CliSessionRange
import com.usagemonitor.domain.entity.TeamMemberUsage
import com.usagemonitor.domain.entity.TeamUsageTrend
import com.usagemonitor.presentation.ui.components.CopySessionCommandButton
import com.usagemonitor.presentation.ui.components.DepthSurface
import com.usagemonitor.presentation.ui.components.TeamTrendChart
import com.usagemonitor.presentation.ui.theme.AppAccents
import com.usagemonitor.presentation.ui.theme.AppElevation
import com.usagemonitor.presentation.ui.theme.AppGlow
import com.usagemonitor.presentation.ui.theme.AppShapes
import com.usagemonitor.presentation.viewmodel.CliExportOutcome
import com.usagemonitor.presentation.viewmodel.TeamAccountGroup
import com.usagemonitor.presentation.viewmodel.TeamSessionDetailUiState
import com.usagemonitor.presentation.viewmodel.TeamUsageUiState
import com.usagemonitor.presentation.viewmodel.TeamUsageView
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
const val TEAM_TAB_MEMBERS_TAG = "teamUsageTabMembers"
const val TEAM_TAB_BREAKDOWN_TAG = "teamUsageTabBreakdown"
const val TEAM_TAB_TREND_TAG = "teamUsageTabTrend"
internal const val TEAM_TREND_PANE_TAG = "teamUsageTrendPane"
internal const val TEAM_TREND_SCROLLBAR_TAG = "teamUsageTrendScrollbar"
const val TEAM_EXPORT_PDF_TAG = "teamUsageExportPdf"

/** Compartilhada com o modal da máquina: as duas telas dão o mesmo aviso. */
const val REFRESHING_NOTICE_TAG = "usageRefreshingNotice"

// Larguras das colunas da lista, num lugar só: a faixa da conta e a linha do
// integrante têm de cair no mesmo x, e dois conjuntos de literais seriam dois
// números que precisam concordar em arquivos diferentes.
private val TEAM_COLUMN_IDENTITY = 210.dp
private val TEAM_COLUMN_MACHINE = 140.dp
private val TEAM_COLUMN_TOKENS = 140.dp
private val TEAM_COLUMN_COST = 96.dp
private val TEAM_COLUMN_ACTIVE_TIME = 84.dp
private val TEAM_COLUMN_SHARE = 96.dp
private val TEAM_COLUMN_STATUS = 112.dp

// A linha do integrante mora dentro de um `DepthSurface`, e a faixa da conta
// não. Sem repetir aqui o `contentPadding` dele, as colunas da faixa nasceriam
// deslocadas das colunas de baixo.
private val TEAM_ROW_CONTENT_PADDING = 14.dp

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
        onToggleAccount = { groupKey -> viewModel.toggleAccount(groupKey) },
        onRemoveMember = { memberKey -> viewModel.removeMember(memberKey) },
        onDismissRemovalError = { viewModel.clearRemovalError() },
        onOpenSession = { memberKey, sessionId -> viewModel.openSession(memberKey, sessionId) },
        onCloseDetail = { viewModel.closeDetail() },
        onToggleAdvanced = { viewModel.toggleAdvanced() },
        onToggleGlossary = { viewModel.toggleGlossary() },
        onSelectView = { view -> viewModel.setView(view) },
        onExportReport = { viewModel.exportReport(language) },
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
    // Com default para não arrastar as chamadas que só exercitam o modal de uma
    // conta, onde não existe faixa a recolher.
    onToggleAccount: (String) -> Unit = {},
    localDeviceId: String? = null,
    removalError: String? = null,
    onRemoveMember: (String) -> Unit = {},
    onDismissRemovalError: () -> Unit = {},
    // Com default para não arrastar as chamadas que não exercitam o detalhe.
    onOpenSession: (String, String) -> Unit = { _, _ -> },
    onCloseDetail: () -> Unit = {},
    onToggleAdvanced: () -> Unit = {},
    onToggleGlossary: () -> Unit = {},
    onSelectView: (TeamUsageView) -> Unit = {},
    onExportReport: () -> Unit = {}
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
                        onToggleAccount = onToggleAccount,
                        onOpenSession = onOpenSession,
                        onRequestRemoveMember = { member -> pendingRemoval = member },
                        onDismissRemovalError = onDismissRemovalError,
                        onSelectView = onSelectView,
                        onExportReport = onExportReport
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
    onToggleAccount: (String) -> Unit,
    onOpenSession: (String, String) -> Unit,
    onRequestRemoveMember: (TeamMemberUsage) -> Unit,
    onDismissRemovalError: () -> Unit,
    onSelectView: (TeamUsageView) -> Unit,
    onExportReport: () -> Unit
) {
    val view = state.effectiveView

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TeamUsageHeader(
            state = state,
            language = language,
            onSelectRange = onSelectRange,
            onSelectView = onSelectView,
            onExportReport = onExportReport
        )

        // Sem este aviso a diferença para o modal de uma conta parece defeito:
        // lá a janela de 5h começa no reset da quota daquela conta, e aqui não
        // pode começar no reset de nenhuma, porque são várias. Só nas abas que
        // obedecem ao filtro de janela: a tendência é de dias e o ignora.
        if (state.isAdminOverview &&
            state.range == CliSessionRange.LAST_5H &&
            view != TeamUsageView.TREND
        ) {
            Text(
                text = TeamUsageLabels.slidingWindowNotice(language),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().testTag(TEAM_SLIDING_WINDOW_NOTICE_TAG)
            )
        }

        // Acima do despacho de aba: vale para as três, porque a troca de janela
        // deixa desatualizado tudo o que elas mostram.
        if (state.isRefreshing) {
            Text(
                text = BreakdownLabels.refreshing(language),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().testTag(REFRESHING_NOTICE_TAG)
            )
        }

        // Acima do despacho de aba, e não dentro da lista: é o retorno de uma ação
        // que o usuário tomou, e trocar de aba não pode escondê-lo antes de ele
        // ser lido — o mesmo motivo pelo qual ele não mora no `uiState`.
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

        if (view == TeamUsageView.TREND) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                TeamTrendPane(trend = state.trend, language = language)
            }
            return@Column
        }

        if (view == TeamUsageView.BREAKDOWN) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                CliUsageBreakdownPane(
                    breakdown = state.breakdown,
                    errorMessage = null,
                    language = language,
                    // Sem orçamento nem créditos: os dois são da máquina e da conta
                    // desta instalação, não do time que a janela mostra.
                    hint = TeamUsageLabels.breakdownHint(language)
                )
            }
            return@Column
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
                            TeamAccountGroupHeader(
                                group = group,
                                share = state.tokenShareOf(group),
                                expanded = state.isAccountExpanded(group),
                                language = language,
                                onToggle = { onToggleAccount(group.groupKey) }
                            )
                        }
                    }

                    if (!state.isAccountExpanded(group)) {
                        continue
                    }

                    for (member in group.members) {
                        item(key = member.memberKey) {
                            TeamMemberRow(
                                member = member,
                                share = state.tokenShareOf(member),
                                expanded = member.memberKey in state.expandedMemberKeys,
                                language = language,
                                // No modal de uma conta, esta máquina volta no próximo
                                // envio e a remoção se desfaz sozinha. Na visão global,
                                // porém, quem administra pode apagar qualquer histórico,
                                // inclusive o enviado pela própria máquina.
                                removable = state.isAdminOverview ||
                                    (localDeviceId != null && member.deviceId != localDeviceId),
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
                                        },
                                        isLocalSession = false
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
 * Aba da tendência: o gráfico com a altura inteira do modal.
 *
 * Era um painel fixo acima da lista, e nessa posição comia cerca de metade da
 * janela para mostrar um recorte — dias — que nem sequer obedece ao filtro de
 * janela escolhido logo acima dele. Como aba, ele só aparece para quem o pediu, e
 * aí tem espaço para ser lido.
 *
 * Rola porque a altura cresce com o número de integrantes: com dez pessoas a
 * última faixa cairia fora da janela.
 */
@Composable
private fun TeamTrendPane(trend: TeamUsageTrend?, language: AppLanguage) {
    if (trend == null) {
        CenteredMessage(TeamUsageLabels.trendUnavailable(language))
        return
    }
    if (trend.isEmpty) {
        CenteredMessage(TeamUsageLabels.trendEmpty(language))
        return
    }

    val scrollState = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize().testTag(TEAM_TREND_PANE_TAG)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(end = SCROLLBAR_GUTTER),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DepthSurface(
                accent = OUTPUT_COLOR,
                modifier = Modifier.fillMaxWidth(),
                shape = AppShapes.large,
                elevation = AppElevation.dialog,
                contentPadding = 16.dp
            ) {
                Text(
                    text = TeamUsageLabels.trendTitle(trend.days.size, language),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = OUTPUT_COLOR
                )
                // O que a tendência é, antes de como lê-la: sem esta frase o
                // painel entrega barras sem dizer que grandeza elas medem.
                Text(
                    text = TeamUsageLabels.trendHint(trend.days.size, language),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = TeamUsageLabels.trendNotice(language),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            TeamTrendChart(trend = trend, accent = OUTPUT_COLOR, language = language)
        }

        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(scrollState),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .testTag(TEAM_TREND_SCROLLBAR_TAG)
        )
    }
}

/**
 * Faixa que separa uma conta da seguinte na visão global.
 *
 * Mostra o rótulo **e** o `accountUuid`: o rótulo é texto que o administrador
 * digitou ao emitir a chave e o servidor não o verifica, então ele orienta mas
 * não prova. Conta sem chave emitida aparece só pelo uuid.
 *
 * Os totais repetem as colunas da linha de integrante, nas mesmas larguras: sem
 * eles, comparar duas contas exige somar as linhas de cada uma na mão — o único
 * total da tela é o do cabeçalho, que já mistura todas as contas.
 *
 * É por ela que a conta abre e fecha. A visão global nasce recolhida, então esta
 * faixa é a lista inteira até alguém pedir o detalhe de uma conta.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TeamAccountGroupHeader(
    group: TeamAccountGroup,
    share: Double,
    expanded: Boolean,
    language: AppLanguage,
    onToggle: () -> Unit
) {
    val accents = AppAccents.current

    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clickable(onClick = onToggle)
            .padding(horizontal = TEAM_ROW_CONTENT_PADDING)
            .testTag("$TEAM_ACCOUNT_GROUP_TAG_PREFIX${group.accountKey.orEmpty()}"),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Ícone dentro da coluna de identidade, como na linha do integrante: é o
        // que mantém as colunas seguintes no mesmo x nas duas.
        Row(
            modifier = Modifier.width(TEAM_COLUMN_IDENTITY),
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
                tint = accents.cacheRead
            )
            Column {
                Text(
                    text = group.accountLabel ?: TeamUsageLabels.unlabeledAccount(language),
                    style = MaterialTheme.typography.titleSmall,
                    color = accents.cacheRead,
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

        // Só o número: o rótulo da coluna já diz o que ele conta, e repetir
        // "integrantes" no valor deixaria a célula diferente de todas as outras.
        MetricText(
            label = TeamUsageLabels.columnMembers(language),
            value = group.activeMemberCount.toString(),
            modifier = Modifier.width(TEAM_COLUMN_MACHINE)
        )

        MetricText(
            label = TeamUsageLabels.sessionCount(group.sessionCount, language),
            value = formatQuantity(group.totalTokens),
            modifier = Modifier.width(TEAM_COLUMN_TOKENS)
        )

        MetricText(
            label = TeamUsageLabels.columnCost(language),
            value = if (group.isCostComplete) {
                formatMicrosUsd(group.totalCostMicros)
            } else {
                "${formatMicrosUsd(group.totalCostMicros)}+"
            },
            valueColor = accents.input,
            modifier = Modifier.width(TEAM_COLUMN_COST)
        )

        Column(modifier = Modifier.width(TEAM_COLUMN_SHARE)) {
            MetricText(TeamUsageLabels.columnShare(language), formatPercent(share))
            Spacer(modifier = Modifier.height(4.dp))
            MeterBar(fraction = share, color = accents.cacheRead, height = 4.dp)
        }

        val worstHealth = group.worstHealth
        if (worstHealth != null) {
            TeamHealthCell(
                health = worstHealth,
                language = language,
                modifier = Modifier.width(TEAM_COLUMN_STATUS)
            )
        }
    }
}

/**
 * Ponto colorido + veredito, igual na faixa da conta e na linha do integrante.
 *
 * `internal` porque a tela de presença mostra o mesmo veredito na mesma coluna:
 * duplicar a célula faria as duas divergirem no primeiro ajuste de cor.
 */
@Composable
internal fun TeamHealthCell(
    health: CliSessionHealth,
    language: AppLanguage,
    modifier: Modifier = Modifier,
    /** `false` onde a lista já carrega a legenda numa faixa de cabeçalho. */
    showLabel: Boolean = true
) {
    val healthAccent = healthColor(health, AppAccents.current)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(healthAccent)
        )
        if (showLabel) {
            MetricText(
                label = TeamUsageLabels.columnStatus(language),
                value = TeamUsageLabels.healthShort(health, language),
                valueColor = healthAccent
            )
        } else {
            MetricValue(
                value = TeamUsageLabels.healthShort(health, language),
                valueColor = healthAccent
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TeamUsageHeader(
    state: TeamUsageUiState.Success,
    language: AppLanguage,
    onSelectRange: (CliSessionRange) -> Unit,
    onSelectView: (TeamUsageView) -> Unit,
    onExportReport: () -> Unit
) {
    val accents = AppAccents.current

    DepthSurface(
        accent = accents.cacheRead,
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
                    color = accents.cacheRead,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (state.isAdminOverview) {
                Text(
                    text = TeamUsageLabels.allAccounts(state.memberGroups.size, language),
                    style = MaterialTheme.typography.labelMedium,
                    color = accents.cacheRead,
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
                    color = accents.cacheRead
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
                    color = accents.input
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

        Spacer(modifier = Modifier.height(8.dp))

        // Abas depois dos chips de janela, como no modal da máquina: a janela vale
        // para as leituras de dentro, então trocá-la é a escolha de fora.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val view = state.effectiveView

            FilterChip(
                selected = view == TeamUsageView.MEMBERS,
                onClick = { onSelectView(TeamUsageView.MEMBERS) },
                label = { Text(TeamUsageLabels.tabMembers(language)) },
                modifier = Modifier.testTag(TEAM_TAB_MEMBERS_TAG)
            )
            FilterChip(
                selected = view == TeamUsageView.BREAKDOWN,
                onClick = { onSelectView(TeamUsageView.BREAKDOWN) },
                label = { Text(BreakdownLabels.tabBreakdown(language)) },
                modifier = Modifier.testTag(TEAM_TAB_BREAKDOWN_TAG)
            )
            // Na visão global a série nunca é carregada — uma linha por conta não
            // caberia num gráfico só — e um chip que nunca mostra nada é pior que
            // chip nenhum.
            if (state.isTrendAvailable) {
                FilterChip(
                    selected = view == TeamUsageView.TREND,
                    onClick = { onSelectView(TeamUsageView.TREND) },
                    label = { Text(TeamUsageLabels.tabTrend(language)) },
                    modifier = Modifier.testTag(TEAM_TAB_TREND_TAG)
                )
            }

            // O relatorio nao segue a aba: ele e o recorte inteiro da janela, com
            // integrantes, resumo e sessoes juntos.
            TextButton(
                onClick = onExportReport,
                modifier = Modifier.testTag(TEAM_EXPORT_PDF_TAG)
            ) {
                Text(ExportLabels.exportPdf(language))
            }
        }

        val exportOutcome = state.exportOutcome
        if (exportOutcome != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = exportOutcomeMessage(exportOutcome, language),
                style = MaterialTheme.typography.labelSmall,
                color = if (exportOutcome is CliExportOutcome.Failed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
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
    val accents = AppAccents.current

    // Integrante sem uso no período fica neutro: destacá-lo com a mesma cor de
    // quem consumiu daria a impressão de atividade que não houve.
    val accent = if (member.hasActivity) accents.cacheRead else MaterialTheme.colorScheme.outline

    DepthSurface(
        accent = accent,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = member.hasActivity, onClick = onToggle)
            .testTag("$TEAM_MEMBER_ROW_TAG_PREFIX${member.deviceId}"),
        glowAlpha = AppGlow.row,
        contentPadding = 14.dp
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.width(TEAM_COLUMN_IDENTITY),
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
                modifier = Modifier.width(TEAM_COLUMN_MACHINE)
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
                modifier = Modifier.width(TEAM_COLUMN_TOKENS)
            )

            MetricText(
                label = TeamUsageLabels.columnCost(language),
                value = if (member.isCostComplete) {
                    formatMicrosUsd(member.totalCostMicros)
                } else {
                    "${formatMicrosUsd(member.totalCostMicros)}+"
                },
                valueColor = accents.input,
                modifier = Modifier.width(TEAM_COLUMN_COST)
            )

            // Servidor anterior à 0.7.0 não mede tempo e a coluna sai como "—".
            // Zero mediria e diria "não trabalhou", que é outra afirmação.
            MetricText(
                label = TeamUsageLabels.columnActiveTime(language),
                value = member.totalActiveMillis
                    ?.takeIf { millis -> millis > 0L }
                    ?.let { millis -> formatActiveTime(millis) }
                    ?: "—",
                modifier = Modifier.width(TEAM_COLUMN_ACTIVE_TIME)
            )

            Column(modifier = Modifier.width(TEAM_COLUMN_SHARE)) {
                MetricText(TeamUsageLabels.columnShare(language), formatPercent(share))
                Spacer(modifier = Modifier.height(4.dp))
                MeterBar(fraction = share, color = accent, height = 4.dp)
            }

            // Pior status entre as sessões deste integrante. Sem ele, a única
            // sessão saturada de um time fica escondida atrás de um clique que
            // ninguém dá — nada na linha recolhida indicaria que vale a pena.
            val worstHealth = member.worstHealth
            if (worstHealth != null) {
                TeamHealthCell(
                    health = worstHealth,
                    language = language,
                    modifier = Modifier
                        .width(TEAM_COLUMN_STATUS)
                        .testTag("$TEAM_MEMBER_HEALTH_TAG_PREFIX${member.deviceId}")
                )
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
            CopySessionCommandButton(
                sessionId = detail.sessionId,
                language = language,
                isLocalSession = false,
                showLabel = true
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

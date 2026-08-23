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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.usagemonitor.domain.entity.CliSessionSummary
import com.usagemonitor.domain.entity.TeamMemberUsage
import com.usagemonitor.domain.entity.TeamUsageTrend
import com.usagemonitor.presentation.ui.components.AppButton
import com.usagemonitor.presentation.ui.components.AppButtonTone
import com.usagemonitor.presentation.ui.components.AppCellValue
import com.usagemonitor.presentation.ui.components.AppColumnHeaderLabel
import com.usagemonitor.presentation.ui.components.AppColumnHeaderRow
import com.usagemonitor.presentation.ui.components.AppDataRow
import com.usagemonitor.presentation.ui.components.AppDivider
import com.usagemonitor.presentation.ui.components.AppIconButton
import com.usagemonitor.presentation.ui.components.AppMetricBlock
import com.usagemonitor.presentation.ui.components.AppProgressTrack
import com.usagemonitor.presentation.ui.components.AppSegment
import com.usagemonitor.presentation.ui.components.AppSegmentedControl
import com.usagemonitor.presentation.ui.components.AppSourceMarker
import com.usagemonitor.presentation.ui.components.AppTab
import com.usagemonitor.presentation.ui.components.AppTabs
import com.usagemonitor.presentation.ui.components.AppToolbar
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.components.AppWindowScaffold
import com.usagemonitor.presentation.ui.components.CopySessionCommandButton
import com.usagemonitor.presentation.ui.components.DepthSurface
import com.usagemonitor.presentation.ui.components.TeamTrendChart
import com.usagemonitor.presentation.ui.theme.AppAccents
import com.usagemonitor.presentation.ui.theme.AppSpacing
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
internal const val TEAM_SESSION_REMOVE_TAG_PREFIX = "teamSessionRemove:"
internal const val TEAM_SESSION_REMOVE_CONFIRM_TAG = "teamSessionRemoveConfirm"
internal const val TEAM_ADMIN_OVERVIEW_TAG = "teamAdminOverviewBadge"
internal const val TEAM_ACCOUNT_GROUP_TAG_PREFIX = "teamAccountGroup:"
internal const val TEAM_SLIDING_WINDOW_NOTICE_TAG = "teamSlidingWindowNotice"
internal const val TEAM_COLUMN_HEADER_TAG = "teamUsageColumnHeader"
const val TEAM_TAB_MEMBERS_TAG = "teamUsageTabMembers"
const val TEAM_TAB_BREAKDOWN_TAG = "teamUsageTabBreakdown"
const val TEAM_TAB_TREND_TAG = "teamUsageTabTrend"
internal const val TEAM_TREND_PANE_TAG = "teamUsageTrendPane"
internal const val TEAM_TREND_SCROLLBAR_TAG = "teamUsageTrendScrollbar"
const val TEAM_EXPORT_PDF_TAG = "teamUsageExportPdf"

/**
 * Bloco de total de integrantes do cabeçalho.
 *
 * O número deixou de vir emendado à palavra ("3 integrantes") e virou valor de
 * um bloco com rótulo próprio, então não há mais um texto único que prove a
 * contagem: a âncora é o bloco.
 */
const val TEAM_TOTAL_MEMBERS_BLOCK_TAG = "teamUsageTotalMembers"

/** Compartilhada com o modal da máquina: as duas telas dão o mesmo aviso. */
const val REFRESHING_NOTICE_TAG = "usageRefreshingNotice"

// Larguras das colunas da lista, num lugar só: a faixa de legendas, a faixa da
// conta e a linha do integrante têm de cair no mesmo x, e três conjuntos de
// literais seriam três números que precisam concordar.
//
// O somatório não é livre e é ele que sustenta a faixa de cabeçalho. Com a janela
// no piso de 960dp sobram 836dp para a linha, descontados os 32 do corpo da
// janela, os 12 da barra de rolagem, os 28 do padding da linha, os 14 do marcador
// e os 26 do botão de remover. Com o vão de 16dp entre sete colunas, sobram
// **740dp** para as larguras somadas — e é essa a conta abaixo. Passar disso faz
// a linha quebrar, e faixa de legendas sobre linha quebrada promete um
// alinhamento que o conteúdo não cumpre.
//
// A máquina saiu da coluna própria e desceu para a segunda linha da coluna de
// identidade: ela qualifica o integrante — como "esta máquina" na tela de
// presença — e é isso que libera a largura que a contagem de sessões passou a
// ocupar como coluna, no lugar de ser o rótulo dinâmico da célula de tokens.
private val TEAM_COLUMN_IDENTITY = 180.dp
private val TEAM_COLUMN_SESSIONS = 60.dp
private val TEAM_COLUMN_TOKENS = 136.dp
private val TEAM_COLUMN_COST = 96.dp
private val TEAM_COLUMN_ACTIVE_TIME = 76.dp
private val TEAM_COLUMN_SHARE = 96.dp
private val TEAM_COLUMN_STATUS = 96.dp

/** Vão entre colunas, igual ao da tela de presença. */
private val TEAM_COLUMN_SPACING = 16.dp

/** Mesma pegada do `AppIconButton`, para o cabeçalho reservar a casa certa. */
private val TEAM_ACTION_SLOT = 26.dp

// A linha do integrante mora dentro de um `DepthSurface`, e a faixa da conta
// não. O padding horizontal compartilhado mantém as colunas no mesmo x; os
// paddings verticais são diferentes de propósito para destacar a hierarquia.
private val TEAM_ROW_HORIZONTAL_PADDING = 14.dp
private val TEAM_ACCOUNT_VERTICAL_PADDING = 10.dp
private val TEAM_MEMBER_VERTICAL_PADDING = 10.dp
private val TEAM_MEMBER_WRAPPED_ROW_GAP = 4.dp

// Recuo do bloco de sessões de um integrante.
//
// O recuo sozinho não estava sendo lido: numa lista onde conta, integrante e
// sessão flutuam sobre o mesmo fundo, 24dp à esquerda passam por alinhamento
// diferente, não por nível abaixo. Quem dá o nível é a superfície, como no
// protótipo: lá o bloco aninhado tem recuo **e** fundo próprio.
private val TEAM_SESSION_INDENT = AppSpacing.xl

/** Único componente stateful: lê o estado do ViewModel e delega para filhos puros. */
@Composable
fun TeamUsageScreen(
    viewModel: TeamUsageViewModel,
    language: AppLanguage,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val removalError by viewModel.removalError.collectAsState()
    val sessionRemovalError by viewModel.sessionRemovalError.collectAsState()

    TeamUsageContent(
        state = state,
        language = language,
        removalError = removalError,
        sessionRemovalError = sessionRemovalError,
        onSelectRange = { range -> viewModel.setRange(range) },
        onToggleMember = { memberKey -> viewModel.toggleMember(memberKey) },
        onToggleAccount = { groupKey -> viewModel.toggleAccount(groupKey) },
        onRemoveMember = { memberKey -> viewModel.removeMember(memberKey) },
        onDismissRemovalError = { viewModel.clearRemovalError() },
        onRemoveSession = { memberKey, sessionId ->
            viewModel.removeSession(memberKey, sessionId)
        },
        onDismissSessionRemovalError = { viewModel.clearSessionRemovalError() },
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
    removalError: String? = null,
    sessionRemovalError: String? = null,
    onRemoveMember: (String) -> Unit = {},
    onDismissRemovalError: () -> Unit = {},
    onRemoveSession: (String, String) -> Unit = { _, _ -> },
    onDismissSessionRemovalError: () -> Unit = {},
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
    var pendingSessionRemoval by remember { mutableStateOf<PendingSessionRemoval?>(null) }

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
                        removalError = removalError,
                        sessionRemovalError = sessionRemovalError,
                        onSelectRange = onSelectRange,
                        onToggleMember = onToggleMember,
                        onToggleAccount = onToggleAccount,
                        onOpenSession = onOpenSession,
                        onRequestRemoveMember = { member -> pendingRemoval = member },
                        onDismissRemovalError = onDismissRemovalError,
                        onRequestRemoveSession = { member, session ->
                            pendingSessionRemoval = PendingSessionRemoval(member, session)
                        },
                        onDismissSessionRemovalError = onDismissSessionRemovalError,
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

    val sessionToRemove = pendingSessionRemoval
    if (sessionToRemove != null) {
        RemoveSessionConfirmation(
            target = sessionToRemove,
            language = language,
            onConfirm = {
                pendingSessionRemoval = null
                onRemoveSession(
                    sessionToRemove.member.memberKey,
                    sessionToRemove.session.sessionId
                )
            },
            onDismiss = { pendingSessionRemoval = null }
        )
    }
}

private data class PendingSessionRemoval(
    val member: TeamMemberUsage,
    val session: CliSessionSummary
)

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
            AppButton(
                label = TeamUsageLabels.confirmRemoval(language),
                onClick = onConfirm,
                tone = AppButtonTone.DANGER,
                modifier = Modifier.testTag(TEAM_REMOVE_CONFIRM_TAG)
            )
        },
        dismissButton = {
            AppButton(
                label = TeamUsageLabels.cancel(language),
                onClick = onDismiss,
                tone = AppButtonTone.GHOST
            )
        }
    )
}

@Composable
private fun RemoveSessionConfirmation(
    target: PendingSessionRemoval,
    language: AppLanguage,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(TeamUsageLabels.removeSessionTitle(language)) },
        text = {
            Text(
                TeamUsageLabels.removeSessionWarning(
                    sessionId = target.session.sessionId,
                    projectName = target.session.projectName,
                    language = language
                )
            )
        },
        confirmButton = {
            AppButton(
                label = TeamUsageLabels.confirmSessionRemoval(language),
                onClick = onConfirm,
                tone = AppButtonTone.DANGER,
                modifier = Modifier.testTag(TEAM_SESSION_REMOVE_CONFIRM_TAG)
            )
        },
        dismissButton = {
            AppButton(
                label = TeamUsageLabels.cancel(language),
                onClick = onDismiss,
                tone = AppButtonTone.GHOST
            )
        }
    )
}

@Composable
private fun TeamUsageList(
    state: TeamUsageUiState.Success,
    language: AppLanguage,
    removalError: String?,
    sessionRemovalError: String?,
    onSelectRange: (CliSessionRange) -> Unit,
    onToggleMember: (String) -> Unit,
    onToggleAccount: (String) -> Unit,
    onOpenSession: (String, String) -> Unit,
    onRequestRemoveMember: (TeamMemberUsage) -> Unit,
    onDismissRemovalError: () -> Unit,
    onRequestRemoveSession: (TeamMemberUsage, CliSessionSummary) -> Unit,
    onDismissSessionRemovalError: () -> Unit,
    onSelectView: (TeamUsageView) -> Unit,
    onExportReport: () -> Unit
) {
    val view = state.effectiveView

    // Aviso de recarga a esquerda e carimbo da ultima alteracao a direita, fora
    // da area que rola. Os dois eram linhas no topo: o aviso aparece e some a
    // cada troca de janela e deslocava a lista inteira, e o carimbo muda a cada
    // tique do laco de 5s ao lado do rotulo da conta.
    AppWindowScaffold(
        modifier = Modifier.fillMaxSize(),
        contentPadding = AppSpacing.lg,
        spacing = AppSpacing.md,
        statusBar = {
            if (state.isRefreshing) {
                Text(
                    text = BreakdownLabels.refreshing(language),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.testTag(REFRESHING_NOTICE_TAG)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = TeamUsageLabels.lastChange(
                    instantLabel = state.lastChangedAt?.let { instant -> formatInstant(instant) },
                    language = language
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
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
                AppButton(
                    label = TeamUsageLabels.cancel(language),
                    onClick = onDismissRemovalError,
                    tone = AppButtonTone.GHOST
                )
            }
        }

        if (sessionRemovalError != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = TeamUsageLabels.sessionRemovalError(sessionRemovalError, language),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
                AppButton(
                    label = TeamUsageLabels.cancel(language),
                    onClick = onDismissSessionRemovalError,
                    tone = AppButtonTone.GHOST
                )
            }
        }

        if (view == TeamUsageView.TREND) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                TeamTrendPane(trend = state.trend, language = language)
            }
            return@AppWindowScaffold
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
            return@AppWindowScaffold
        }

        if (state.isEmpty) {
            CenteredMessage(
                TeamUsageLabels.emptyInRange(state.range, state.rangeAnchored, language)
            )
            return@AppWindowScaffold
        }

        // Decidido uma vez para a lista inteira, e não por linha: as colunas só
        // alinham se todas as linhas reservarem as mesmas casas. Uma coluna que
        // aparece em algumas linhas e some em outras desloca tudo o que vem depois.
        val hasStatusColumn = state.memberGroups.any { group ->
            group.worstHealth != null || group.members.any { member -> member.worstHealth != null }
        }

        TeamColumnHeader(
            language = language,
            hasStatusColumn = hasStatusColumn,
            hasActionColumn = state.isAdminOverview
        )

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            val listState = rememberLazyListState()

            // Sem espaço entre itens, como na lista da máquina: cada linha traz a
            // própria divisória, e o vão de 8dp entre elas era justamente o que
            // desfazia a leitura de tabela — conta, integrante e sessão viravam
            // três blocos soltos do mesmo peso.
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(end = SCROLLBAR_GUTTER)
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
                                hasStatusColumn = hasStatusColumn,
                                hasActionColumn = state.isAdminOverview,
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
                                removable = state.isAdminOverview,
                                hasStatusColumn = hasStatusColumn,
                                hasActionColumn = state.isAdminOverview,
                                onToggle = { onToggleMember(member.memberKey) },
                                onRemove = { onRequestRemoveMember(member) }
                            )
                        }

                        if (member.memberKey in state.expandedMemberKeys) {
                            // A faixa de legendas do bloco aninhado: são as colunas
                            // da lista de sessões, não as da lista de integrantes, e
                            // sem ela o bloco entrega sete números sem dizer o que
                            // cada um é.
                            item(key = "${member.memberKey}:sessionHeader") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surface)
                                        .padding(start = TEAM_SESSION_INDENT, top = AppSpacing.sm)
                                ) {
                                    CliSessionColumnHeader(
                                        language = language,
                                        hasActionColumn = state.isAdminOverview
                                    )
                                }
                            }

                            // As sessões entram como itens irmãos, e não dentro da
                            // linha: aninhar uma lista rolável em outra quebra a
                            // rolagem e desliga o reaproveitamento de itens.
                            items(
                                count = member.sessions.size,
                                key = { index ->
                                    "${member.memberKey}:${member.sessions[index].sessionId}"
                                }
                            ) { index ->
                                // Terceiro degrau da escada de superfícies: a faixa
                                // da conta em `surfaceVariant`, a linha do
                                // integrante transparente sobre o fundo da janela e
                                // o bloco de sessões em `surface`. É `surface` e não
                                // `surfaceVariant` porque `surfaceVariant` é o realce
                                // de hover do `AppDataRow`: com ele aqui, passar o
                                // mouse numa sessão deixaria de dar retorno nenhum.
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surface)
                                        .padding(start = TEAM_SESSION_INDENT)
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
                                        isLocalSession = false,
                                        onRemove = if (state.isAdminOverview) {
                                            { onRequestRemoveSession(member, session) }
                                        } else {
                                            null
                                        },
                                        removeButtonTag = if (state.isAdminOverview) {
                                            "$TEAM_SESSION_REMOVE_TAG_PREFIX${member.memberKey}:${session.sessionId}"
                                        } else {
                                            null
                                        },
                                        hasActionColumn = state.isAdminOverview
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
            // O gráfico primeiro e a explicação depois, como no protótipo: um
            // painel de três linhas acima dele empurrava as barras para baixo da
            // dobra numa janela baixa, e a frase que diz o que ele mede já vive
            // na linha de cabeçalho do próprio gráfico.
            TeamTrendChart(trend = trend, language = language)

            Text(
                text = TeamUsageLabels.trendHint(trend.days.size, language),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // O fuso e a independência do filtro de janela: sem esta frase, um
            // gráfico de dias ao lado de números de 5h é lido como o mesmo
            // recorte.
            Text(
                text = TeamUsageLabels.trendNotice(language),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
@Composable
private fun TeamAccountGroupHeader(
    group: TeamAccountGroup,
    share: Double,
    expanded: Boolean,
    language: AppLanguage,
    hasStatusColumn: Boolean,
    hasActionColumn: Boolean,
    onToggle: () -> Unit
) {
    val accents = AppAccents.current

    // Escada de três superfícies neutras, todas já na paleta: a faixa da conta em
    // `surfaceVariant`, a linha do integrante transparente sobre o fundo da janela
    // e o bloco de sessões em `surface`. É ela que responde ao "não está claro
    // identificar os times": até aqui os três níveis eram retângulos de mesmo peso
    // empilhados, separados só por um vão de 8dp.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(
                    horizontal = TEAM_ROW_HORIZONTAL_PADDING,
                    vertical = TEAM_ACCOUNT_VERTICAL_PADDING
                )
                .testTag("$TEAM_ACCOUNT_GROUP_TAG_PREFIX${group.accountKey.orEmpty()}"),
            // Marcador e vão iguais aos do `AppDataRow` da linha do integrante: é o que
            // mantém os totais da conta no mesmo x das colunas dela. Sem ele a faixa
            // começava 14dp à esquerda da linha e as duas colunas de custo não
            // alinhavam, que é justamente a comparação que a faixa existe para permitir.
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppSourceMarker(color = accents.cacheRead)
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(TEAM_COLUMN_SPACING),
                verticalAlignment = Alignment.CenterVertically
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
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        // A palavra vem antes do e-mail: sem ela a faixa entregava um
                        // endereço e um uuid sem dizer que aquilo é a conta, e ao lado de
                        // uma linha de integrante — que também tem nome e identificador —
                        // as duas liam igual.
                        //
                        // A contagem de integrantes vem emendada nela: a coluna
                        // que ela ocupava virou "Sessões", e um número sob a
                        // legenda "Tempo ativo" diria uma coisa e valeria outra.
                        Text(
                            text = TeamUsageLabels.accountBandWithMembers(
                                memberCount = group.activeMemberCount,
                                language = language
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                        // O e-mail é o dado, e dado fica na cor do texto. Quem
                        // identifica a faixa como conta é o marcador de 2dp à
                        // esquerda e a palavra logo acima.
                        Text(
                            text = group.accountLabel ?: TeamUsageLabels.unlabeledAccount(language),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
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

                AppCellValue(
                    value = group.sessionCount.toString(),
                    modifier = Modifier.width(TEAM_COLUMN_SESSIONS)
                )

                AppCellValue(
                    value = formatQuantity(group.totalTokens),
                    modifier = Modifier.width(TEAM_COLUMN_TOKENS)
                )

                AppCellValue(
                    // Custo na cor do texto, como na lista da máquina: azul só no custo
                    // sugeria uma categoria que as outras colunas não têm.
                    value = if (group.isCostComplete) {
                        formatMicrosUsd(group.totalCostMicros)
                    } else {
                        "${formatMicrosUsd(group.totalCostMicros)}+"
                    },
                    modifier = Modifier.width(TEAM_COLUMN_COST)
                )

                // A conta não agrega tempo de trabalho: somar o tempo ativo de
                // duas máquinas que trabalharam ao mesmo tempo daria uma hora que
                // ninguém passou. A coluna fica vazia — e continua reservada, ou
                // as colunas seguintes sairiam de x.
                Spacer(modifier = Modifier.width(TEAM_COLUMN_ACTIVE_TIME))

                Column(modifier = Modifier.width(TEAM_COLUMN_SHARE)) {
                    AppCellValue(value = formatPercent(share))
                    Spacer(modifier = Modifier.height(4.dp))
                    AppProgressTrack(fraction = share.toFloat(), tone = AppTone.INFO)
                }

                val worstHealth = group.worstHealth
                if (worstHealth != null) {
                    TeamHealthCell(
                        health = worstHealth,
                        language = language,
                        showLabel = false,
                        modifier = Modifier.width(TEAM_COLUMN_STATUS)
                    )
                } else if (hasStatusColumn) {
                    Spacer(modifier = Modifier.width(TEAM_COLUMN_STATUS))
                }
            }

            if (hasActionColumn) {
                Spacer(modifier = Modifier.size(TEAM_ACTION_SLOT))
            }
        }
        AppDivider()
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
    // Sem painel em volta: o corpo da janela já é a superfície, e um retângulo com
    // borda envolvendo barra de controles, métricas e abas transformava o
    // cabeçalho inteiro num bloco só — que é o que o protótipo desenha solto.
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
    ) {
        // Abas **antes** das métricas, como no protótipo: a aba escolhe o que a
        // janela mostra, e os totais são conteúdo dela.
        //
        // `Row` e não `FlowRow`: as abas levam `weight` para empurrar o resto para
        // a direita, e peso dentro de um `FlowRow` fica sem referência de largura.
        AppToolbar(spacing = AppSpacing.sm) {
            val view = state.effectiveView
            // Na visão global a série nunca é carregada — uma linha por conta não
            // caberia num gráfico só — e uma aba que nunca mostra nada é pior que
            // aba nenhuma.
            val tabs = buildList {
                add(AppTab(label = TeamUsageLabels.tabMembers(language), testTag = TEAM_TAB_MEMBERS_TAG))
                add(AppTab(label = BreakdownLabels.tabBreakdown(language), testTag = TEAM_TAB_BREAKDOWN_TAG))
                if (state.isTrendAvailable) {
                    add(AppTab(label = TeamUsageLabels.tabTrend(language), testTag = TEAM_TAB_TREND_TAG))
                }
            }
            val views = buildList {
                add(TeamUsageView.MEMBERS)
                add(TeamUsageView.BREAKDOWN)
                if (state.isTrendAvailable) {
                    add(TeamUsageView.TREND)
                }
            }

            AppTabs(
                tabs = tabs,
                selectedIndex = views.indexOf(view).coerceAtLeast(0),
                onSelect = { index -> onSelectView(views[index]) },
                modifier = Modifier.weight(1f)
            )

            if (state.isAdminOverview) {
                Text(
                    text = TeamUsageLabels.allAccounts(state.memberGroups.size, language),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.testTag(TEAM_ADMIN_OVERVIEW_TAG)
                )
            }
            // O carimbo da última alteração desceu para a barra de estado; aqui
            // fica só o selo de leitura ao vivo, que é estado do laço e não dado.
            LiveBadge(language = language)

            // A janela vale para as três abas, então trocá-la é a escolha de fora
            // e a aba é a de dentro — as duas na mesma faixa.
            AppSegmentedControl(
                options = CliSessionRange.entries.map { entry ->
                    AppSegment(label = TeamUsageLabels.rangeLabel(entry, language))
                },
                selectedIndex = CliSessionRange.entries.indexOf(state.range),
                onSelect = { index -> onSelectRange(CliSessionRange.entries[index]) }
            )

            // O relatorio nao segue a aba: ele e o recorte inteiro da janela, com
            // integrantes, resumo e sessoes juntos.
            AppButton(
                label = ExportLabels.exportPdf(language),
                onClick = onExportReport,
                modifier = Modifier.testTag(TEAM_EXPORT_PDF_TAG)
            )
        }

        // O rótulo da conta é dado e não controle: fica na linha de texto abaixo
        // da barra, junto das outras qualificações da janela.
        if (state.accountLabel != null) {
            Text(
                text = state.accountLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Blocos de métrica de largura igual, como no modal da máquina: eram
        // três pares valor/rótulo flutuando sobre o mesmo painel.
        //
        // Os totais são o mesmo tipo de coisa e ficam na mesma cor. O acento é
        // identidade de fonte, não de valor: tokens em verde ao lado de custo em
        // azul sugere duas categorias onde há duas medidas da mesma janela.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            AppMetricBlock(
                label = TeamUsageLabels.columnMembers(language),
                value = state.activeMemberCount.toString(),
                // O veredito por sessão vive dois níveis abaixo, dentro de um
                // integrante recolhido. Aqui ele aparece sem nenhum clique.
                footer = CliSessionsLabels.healthTally(state.healthTally, language),
                footerColor = healthTallyColor(state.healthTally),
                modifier = Modifier.width(METRIC_BLOCK_WIDTH).testTag(TEAM_TOTAL_MEMBERS_BLOCK_TAG)
            )

            AppMetricBlock(
                label = TeamUsageLabels.columnTokens(language),
                value = formatQuantity(state.totalTokens),
                modifier = Modifier.width(METRIC_BLOCK_WIDTH)
            )

            AppMetricBlock(
                label = TeamUsageLabels.columnCost(language),
                value = if (state.isTotalCostComplete) {
                    formatMicrosUsdShort(state.totalCostMicros)
                } else {
                    "${formatMicrosUsdShort(state.totalCostMicros)}+"
                },
                modifier = Modifier.width(METRIC_BLOCK_WIDTH)
            )
        }

        // Fora dos blocos, pela mesma razão do modal da máquina: a janela do
        // custo é uma frase, e dentro do bloco ela media três vezes a largura
        // dele.
        Text(
            text = TeamUsageLabels.sessionCount(state.sessionCount, language),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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

        val exportOutcome = state.exportOutcome
        if (exportOutcome != null) {
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

/**
 * Faixa de legendas das colunas, uma vez para a lista inteira.
 *
 * Cada célula reimprimia "Máquina", "Custo", "Tempo ativo" e "do time" ao lado do
 * próprio valor: numa lista de time isso dobra o texto da tela e o ruído cresce
 * com o número de pessoas. A legenda pertence à coluna.
 *
 * Fora da `LazyColumn` de propósito, como na tela de presença: na visão global a
 * lista já tem as faixas de conta rolando dentro dela, e dois níveis de cabeçalho
 * grudado empilhariam.
 */
@Composable
private fun TeamColumnHeader(
    language: AppLanguage,
    hasStatusColumn: Boolean,
    hasActionColumn: Boolean
) {
    AppColumnHeaderRow(
        modifier = Modifier.padding(end = SCROLLBAR_GUTTER).testTag(TEAM_COLUMN_HEADER_TAG),
        horizontalPadding = TEAM_ROW_HORIZONTAL_PADDING,
        spacing = TEAM_COLUMN_SPACING
    ) {
        AppColumnHeaderLabel(
            label = TeamUsageLabels.columnMember(language),
            modifier = Modifier.width(TEAM_COLUMN_IDENTITY)
        )
        AppColumnHeaderLabel(
            label = CliSessionsLabels.columnSessions(language),
            modifier = Modifier.width(TEAM_COLUMN_SESSIONS)
        )
        AppColumnHeaderLabel(
            label = TeamUsageLabels.columnTokens(language),
            modifier = Modifier.width(TEAM_COLUMN_TOKENS)
        )
        AppColumnHeaderLabel(
            label = TeamUsageLabels.columnCost(language),
            modifier = Modifier.width(TEAM_COLUMN_COST)
        )
        AppColumnHeaderLabel(
            label = TeamUsageLabels.columnActiveTime(language),
            modifier = Modifier.width(TEAM_COLUMN_ACTIVE_TIME)
        )
        AppColumnHeaderLabel(
            label = CliSessionsLabels.columnShare(language),
            modifier = Modifier.width(TEAM_COLUMN_SHARE)
        )
        if (hasStatusColumn) {
            AppColumnHeaderLabel(
                label = TeamUsageLabels.columnStatus(language),
                modifier = Modifier.width(TEAM_COLUMN_STATUS)
            )
        }
        if (hasActionColumn) {
            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(TEAM_ACTION_SLOT))
        }
    }
}

@Composable
private fun TeamMemberRow(
    member: TeamMemberUsage,
    share: Double,
    expanded: Boolean,
    language: AppLanguage,
    removable: Boolean,
    /** A lista tem coluna de status; esta linha reserva a casa mesmo sem veredito. */
    hasStatusColumn: Boolean,
    /** A lista tem coluna de ação; esta linha reserva a casa mesmo sem botão. */
    hasActionColumn: Boolean,
    onToggle: () -> Unit,
    onRemove: () -> Unit
) {
    val accents = AppAccents.current

    // Integrante sem uso no período fica neutro: destacá-lo com a mesma cor de
    // quem consumiu daria a impressão de atividade que não houve.
    val accent = if (member.hasActivity) accents.cacheRead else MaterialTheme.colorScheme.outline

    // Linha de tabela, não card: eram até vinte cards empilhados numa janela de
    // time grande. O marcador de 2dp mantém a leitura de "esta pessoa produziu"
    // que o acento do card dava, sem pintar a linha inteira.
    //
    // `Row` e não `FlowRow`: quebrar é o que a faixa de legendas não admite. É o
    // orçamento de `TEAM_COLUMN_*` mais o piso da janela que garantem que ela não
    // quebre.
    AppDataRow(
        modifier = Modifier.testTag("$TEAM_MEMBER_ROW_TAG_PREFIX${member.deviceId}"),
        onClick = if (member.hasActivity) onToggle else null,
        horizontalPadding = TEAM_ROW_HORIZONTAL_PADDING,
        verticalPadding = TEAM_MEMBER_VERTICAL_PADDING
    ) {
        AppSourceMarker(color = accent)
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(TEAM_COLUMN_SPACING),
            verticalAlignment = Alignment.CenterVertically
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
                // Apelido, máquina e último envio: identidade e os dois carimbos
                // que a qualificam. A máquina deixou de ser coluna própria — ela
                // não é uma medida ao lado de custo e tokens, é quem é a pessoa.
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = member.alias,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = member.machineLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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

            AppCellValue(
                value = member.sessionCount.toString(),
                modifier = Modifier.width(TEAM_COLUMN_SESSIONS)
            )

            AppCellValue(
                value = if (member.hasActivity) {
                    formatQuantity(member.totalTokens)
                } else {
                    TeamUsageLabels.noActivityInRange(language)
                },
                color = if (member.hasActivity) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.width(TEAM_COLUMN_TOKENS)
            )

            AppCellValue(
                value = if (member.isCostComplete) {
                    formatMicrosUsd(member.totalCostMicros)
                } else {
                    "${formatMicrosUsd(member.totalCostMicros)}+"
                },
                modifier = Modifier.width(TEAM_COLUMN_COST)
            )

            // Servidor anterior à 0.7.0 não mede tempo e a coluna sai como "—".
            // Zero mediria e diria "não trabalhou", que é outra afirmação.
            AppCellValue(
                value = member.totalActiveMillis
                    ?.takeIf { millis -> millis > 0L }
                    ?.let { millis -> formatActiveTime(millis) }
                    ?: "—",
                modifier = Modifier.width(TEAM_COLUMN_ACTIVE_TIME)
            )

            Column(modifier = Modifier.width(TEAM_COLUMN_SHARE)) {
                AppCellValue(value = formatPercent(share))
                Spacer(modifier = Modifier.height(4.dp))
                AppProgressTrack(fraction = share.toFloat(), tone = AppTone.INFO)
            }

            // Pior status entre as sessões deste integrante. Sem ele, a única
            // sessão saturada de um time fica escondida atrás de um clique que
            // ninguém dá — nada na linha recolhida indicaria que vale a pena.
            val worstHealth = member.worstHealth
            if (worstHealth != null) {
                TeamHealthCell(
                    health = worstHealth,
                    language = language,
                    showLabel = false,
                    modifier = Modifier
                        .width(TEAM_COLUMN_STATUS)
                        .testTag("$TEAM_MEMBER_HEALTH_TAG_PREFIX${member.deviceId}")
                )
            } else if (hasStatusColumn) {
                Spacer(modifier = Modifier.width(TEAM_COLUMN_STATUS))
            }
        }

        // Fora do fluxo de colunas, como na tela de presença: lá dentro a ação é o
        // último item e o primeiro a quebrar numa janela estreita.
        if (removable) {
            AppIconButton(
                contentDescription = TeamUsageLabels.removeMember(language),
                onClick = onRemove,
                tone = AppButtonTone.DANGER,
                modifier = Modifier.testTag("$TEAM_MEMBER_REMOVE_TAG_PREFIX${member.deviceId}")
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        } else if (hasActionColumn) {
            Spacer(modifier = Modifier.size(TEAM_ACTION_SLOT))
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
            AppButton(
                label = TeamUsageLabels.back(language),
                onClick = onCloseDetail,
                tone = AppButtonTone.GHOST
            )
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

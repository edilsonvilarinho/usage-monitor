package com.usagemonitor.presentation.ui

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.unit.Dp
import com.usagemonitor.presentation.ui.components.appItemMotion
import com.usagemonitor.presentation.ui.components.AppStateCrossfade
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CliSessionRange
import com.usagemonitor.domain.entity.CliSessionSummary
import com.usagemonitor.domain.entity.TeamMemberUsage
import com.usagemonitor.presentation.ui.components.AppLoadingState
import com.usagemonitor.presentation.ui.components.AppErrorState
import com.usagemonitor.presentation.ui.components.AppEmptyState
import com.usagemonitor.presentation.ui.components.AppButton
import com.usagemonitor.presentation.ui.components.AppDialog
import com.usagemonitor.presentation.ui.components.AppButtonTone
import com.usagemonitor.presentation.ui.components.color
import com.usagemonitor.presentation.ui.components.AppWindowScaffold
import com.usagemonitor.presentation.ui.components.ModalDialogText
import com.usagemonitor.presentation.ui.components.appNestedGroupItem
import com.usagemonitor.presentation.ui.theme.AppSpacing
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
internal val TEAM_COLUMN_IDENTITY = 180.dp
internal val TEAM_COLUMN_SESSIONS = 60.dp
internal val TEAM_COLUMN_TOKENS = 136.dp
internal val TEAM_COLUMN_COST = 96.dp
internal val TEAM_COLUMN_ACTIVE_TIME = 76.dp
internal val TEAM_COLUMN_SHARE = 96.dp
internal val TEAM_COLUMN_STATUS = 96.dp

/** Vão entre colunas, igual ao da tela de presença. */
internal val TEAM_COLUMN_SPACING = 16.dp

/** Mesma pegada do `AppIconButton`, para o cabeçalho reservar a casa certa. */
internal val TEAM_ACTION_SLOT = 26.dp

/** Pegada do `Icon` do Material, para a linha sem ícone reservar a mesma casa. */
internal val TEAM_EXPAND_ICON_SIZE = 24.dp

// O padding horizontal é compartilhado entre a faixa da conta e a linha do
// integrante: é ele que mantém as colunas das duas no mesmo x, que é a
// comparação que a faixa existe para permitir.
//
// O padding vertical **não** é compartilhado, e a diferença deixou de ser
// simbólica: a faixa da conta media 62dp e a linha do integrante 63dp, ou seja,
// a capa era mais baixa que o item que ela cobre (issue #104).
internal val TEAM_ROW_HORIZONTAL_PADDING = 14.dp
internal val TEAM_ACCOUNT_VERTICAL_PADDING = AppSpacing.md
internal val TEAM_MEMBER_VERTICAL_PADDING = 10.dp
private val TEAM_MEMBER_WRAPPED_ROW_GAP = 4.dp

// Degrau de aninhamento da lista, aplicado **dentro da coluna de identidade** e
// não no início da linha.
//
// Recuar a linha inteira moveria as colunas numéricas do integrante para fora do
// x das da faixa da conta, e é justamente a comparação entre os dois números que
// a faixa existe para dar. Recuar só o texto entrega o degrau visual e deixa a
// faixa de legendas — uma só, acima da lista inteira — continuar descrevendo
// todas as linhas. A coluna de identidade cede a mesma largura que o degrau
// ocupa, então nada além do texto se move.
private val TEAM_NEST_INDENT = AppSpacing.md

// Recuo do bloco de sessões de um integrante, somado ao degrau do integrante.
//
// O recuo sozinho não estava sendo lido: numa lista onde conta, integrante e
// sessão flutuam sobre o mesmo fundo, 24dp à esquerda passam por alinhamento
// diferente, não por nível abaixo. Quem dá o nível é a superfície, como no
// protótipo, mais a guia vertical que liga o bloco à linha de quem o abriu.
private val TEAM_SESSION_INDENT = AppSpacing.xl

/** Único componente stateful: lê o estado do ViewModel e delega para filhos puros. */
@Composable
fun TeamUsageScreen(
    viewModel: TeamUsageViewModel,
    language: AppLanguage,
    /**
     * Máquina desta instalação, para separar a sessão própria da de um colega.
     *
     * Nulo quando a integração não está configurada ou a instalação é só de
     * administração — e ali nenhuma sessão da lista é desta máquina, que é o
     * resultado seguro.
     */
    localDeviceId: String? = null,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val removalError by viewModel.removalError.collectAsState()
    val sessionRemovalError by viewModel.sessionRemovalError.collectAsState()

    TeamUsageContent(
        state = state,
        language = language,
        localDeviceId = localDeviceId,
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
    /** Ver `TeamUsageScreen`; default nulo para não arrastar quem não o exercita. */
    localDeviceId: String? = null,
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
        AppStateCrossfade(state, key = { current -> if (current is TeamUsageUiState.Success) "success:${current.detail != null}" else current::class }) { state ->
    when (state) {
                is TeamUsageUiState.Loading -> AppLoadingState(
                    if (language == AppLanguage.PT) "Consultando o servidor do time…" else "Querying the team server…"
                )

                is TeamUsageUiState.Error -> AppErrorState(
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
                            isLocalSession = localDeviceId != null && detail.deviceId == localDeviceId,
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
    AppDialog(
        onDismissRequest = onDismiss,
        title = { Text(TeamUsageLabels.removeMemberTitle(language)) },
        text = { ModalDialogText(TeamUsageLabels.removeMemberWarning(member.alias, language)) },
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
    AppDialog(
        onDismissRequest = onDismiss,
        title = { Text(TeamUsageLabels.removeSessionTitle(language)) },
        text = {
            ModalDialogText(
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
    localDeviceId: String?,
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
            TeamActionErrorRow(
                text = TeamUsageLabels.removalError(removalError, language),
                language = language,
                onDismiss = onDismissRemovalError
            )
        }

        if (sessionRemovalError != null) {
            TeamActionErrorRow(
                text = TeamUsageLabels.sessionRemovalError(sessionRemovalError, language),
                language = language,
                onDismiss = onDismissSessionRemovalError
            )
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
            AppEmptyState(
                TeamUsageLabels.emptyInRange(state.range, state.rangeAnchored, language)
            )
            return@AppWindowScaffold
        }

        // Decidido uma vez para a lista inteira, e não por linha: as colunas só
        // alinham se todas as linhas reservarem as mesmas casas. Uma coluna que
        // aparece em algumas linhas e some em outras desloca tudo o que vem depois.
        val hasStatusColumn = state.emailGroups.any { group ->
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
                for (emailGroup in state.emailGroups) {
                    // Cabeçalho só na visão global: no modal de uma conta só, a
                    // conta já é a da janela e repeti-la aqui seria ruído.
                    if (state.isAdminOverview) {
                        item(key = "email:${emailGroup.groupKey}") {
                            Box(modifier = appItemMotion()) {
                                TeamAccountGroupHeader(
                                    group = emailGroup,
                                    share = state.tokenShareOf(emailGroup),
                                    expanded = state.isEmailExpanded(emailGroup),
                                    language = language,
                                    hasStatusColumn = hasStatusColumn,
                                    hasActionColumn = state.isAdminOverview,
                                    onToggle = { onToggleAccount(emailGroup.groupKey) }
                                )
                            }
                        }
                    }

                    if (!state.isEmailExpanded(emailGroup)) {
                        continue
                    }

                    for (account in emailGroup.accounts) {
                        // Um degrau por nível que existe acima do integrante: a
                        // faixa da conta só é desenhada na visão global, e a
                        // sub-faixa de uuid só quando o mesmo e-mail tem mais de
                        // uma conta. No modal de uma conta os dois somem e o
                        // recuo é zero, que é a geometria de sempre.
                        val hasUuidHeader = emailGroup.accounts.size > 1
                        val memberIndent = when {
                            !state.isAdminOverview -> 0.dp
                            hasUuidHeader -> TEAM_NEST_INDENT * 2
                            else -> TEAM_NEST_INDENT
                        }
                        val sessionIndent = memberIndent + TEAM_SESSION_INDENT

                        if (hasUuidHeader) {
                            item(key = "uuid:${account.accountKey}") {
                                Box(modifier = appItemMotion()) {
                                    TeamAccountUuidHeader(
                                        account = account,
                                        language = language,
                                        indent = TEAM_NEST_INDENT
                                    )
                                }
                            }
                        }

                    for (member in account.members) {
                        teamMemberItems(
                            member = member,
                            state = state,
                            language = language,
                            memberIndent = memberIndent,
                            sessionIndent = sessionIndent,
                            hasStatusColumn = hasStatusColumn,
                            localDeviceId = localDeviceId,
                            onToggleMember = onToggleMember,
                            onRequestRemoveMember = onRequestRemoveMember,
                            onOpenSession = onOpenSession,
                            onRequestRemoveSession = onRequestRemoveSession
                        )
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

@Composable
private fun TeamActionErrorRow(text: String, language: AppLanguage, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f)
        )
        AppButton(
            label = TeamUsageLabels.cancel(language),
            onClick = onDismiss,
            tone = AppButtonTone.GHOST
        )
    }
}

/**
 * A linha de um integrante e, expandido, a faixa de legendas e as sessões dele
 * como itens irmãos da lista.
 */
private fun LazyListScope.teamMemberItems(
    member: TeamMemberUsage,
    state: TeamUsageUiState.Success,
    language: AppLanguage,
    memberIndent: Dp,
    sessionIndent: Dp,
    hasStatusColumn: Boolean,
    localDeviceId: String?,
    onToggleMember: (String) -> Unit,
    onRequestRemoveMember: (TeamMemberUsage) -> Unit,
    onOpenSession: (String, String) -> Unit,
    onRequestRemoveSession: (TeamMemberUsage, CliSessionSummary) -> Unit
) {
    item(key = member.memberKey) {
        Box(modifier = appItemMotion()) {
            TeamMemberRow(
                member = member,
                share = state.tokenShareOf(member),
                expanded = member.memberKey in state.expandedMemberKeys,
                language = language,
                indent = memberIndent,
                removable = state.isAdminOverview,
                hasStatusColumn = hasStatusColumn,
                hasActionColumn = state.isAdminOverview,
                onToggle = { onToggleMember(member.memberKey) },
                onRemove = { onRequestRemoveMember(member) }
            )
        }
    }

    if (member.memberKey in state.expandedMemberKeys) {
        // A faixa de legendas do bloco aninhado: são as colunas
        // da lista de sessões, não as da lista de integrantes, e
        // sem ela o bloco entrega sete números sem dizer o que
        // cada um é.
        item(key = "${member.memberKey}:sessionHeader") {
            Box(modifier = appItemMotion()) {
                Box(
                    modifier = Modifier
                        .appNestedGroupItem(indent = sessionIndent)
                        .padding(top = AppSpacing.sm)
                ) {
                    CliSessionColumnHeader(
                        language = language,
                        hasActionColumn = state.isAdminOverview
                    )
                }
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
            //
            // A guia atravessa o cabeçalho e todas as sessões:
            // é ela que diz que estes itens irmãos pertencem à
            // linha do integrante logo acima.
            Box(
                modifier = Modifier
                    .appNestedGroupItem(indent = sessionIndent)
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
                    // Só a máquina desta instalação tem o
                    // transcript no disco, e é só nela que o
                    // botão de copiar aparece: a sessão de um
                    // colega não é copiável (issue #102).
                    isLocalSession = localDeviceId != null &&
                        member.deviceId == localDeviceId,
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

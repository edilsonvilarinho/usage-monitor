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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.TeamMemberPresence
import com.usagemonitor.presentation.ui.components.AppButton
import com.usagemonitor.presentation.ui.components.AppColumnHeaderLabel
import com.usagemonitor.presentation.ui.components.AppColumnHeaderRow
import com.usagemonitor.presentation.ui.components.AppCellValue
import com.usagemonitor.presentation.ui.components.AppSourceMarker
import com.usagemonitor.presentation.ui.components.AppTextField
import com.usagemonitor.presentation.ui.components.AppStatusIndicator
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.components.color
import com.usagemonitor.presentation.ui.components.AppDataRow
import com.usagemonitor.presentation.ui.components.AppDivider
import com.usagemonitor.presentation.ui.components.AppGroupBand
import com.usagemonitor.presentation.ui.components.AppToggleChip
import com.usagemonitor.presentation.ui.components.AppDataSurface
import com.usagemonitor.presentation.ui.components.AppIconButton
import com.usagemonitor.presentation.ui.components.AppButtonTone
import com.usagemonitor.presentation.ui.components.ModalDialogText
import com.usagemonitor.presentation.ui.theme.AppAccents
import com.usagemonitor.presentation.ui.theme.AppSpacing
import com.usagemonitor.presentation.viewmodel.TeamPresenceAccountGroup
import com.usagemonitor.presentation.viewmodel.TeamPresenceEmailGroup
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
internal const val PRESENCE_MEMBER_REMOVE_TAG_PREFIX = "teamPresenceMemberRemove:"
internal const val PRESENCE_ACCOUNT_DELETE_TAG_PREFIX = "teamPresenceAccountDelete:"
internal const val PRESENCE_REMOVE_CONFIRM_TAG = "teamPresenceRemoveConfirm"
internal const val PRESENCE_DELETE_ACCOUNT_CONFIRM_TAG = "teamPresenceDeleteAccountConfirm"
internal const val PRESENCE_ACTION_ERROR_TAG = "teamPresenceActionError"
internal const val PRESENCE_COLUMN_HEADER_TAG = "teamPresenceColumnHeader"
internal const val PRESENCE_FILTER_TAG = "teamPresenceFilter"
internal const val PRESENCE_LAST_SEEN_TAG_PREFIX = "teamPresenceLastSeen:"
internal const val PRESENCE_LAST_TURN_TAG_PREFIX = "teamPresenceLastTurn:"

// Larguras das colunas num lugar só, pelo mesmo motivo da tela de consumo: a
// faixa do cabeçalho, a da conta e a linha do integrante têm de cair no mesmo x.
//
// O somatório não é livre. Quando ele passa da largura útil da janela a linha
// quebra e as colunas deixam de alinhar entre as linhas — que é exatamente o que
// as larguras fixas existem para impedir. A conta:
//
//     Σ colunas + 16dp × (n − 1) + 14dp (marcador + vão) + 12dp + 26dp (ação)
//         ≤ largura_janela − 72dp
//
// onde os 72dp são 32 de padding da Column + 12 do `SCROLLBAR_GUTTER` + 28 do
// `contentPadding` da linha. O que faltava era impedir o arrasto da borda para
// baixo do orçamento — foi assim que a faixa da conta apareceu em produção com o
// botão de apagar numa linha própria.
//
// A ação fica **fora** do `Row`, como coluna fixa à direita: dentro dele ela é o
// último item e portanto o primeiro a quebrar, e o que sobra na tela é um ícone
// vermelho solto, sem coluna, sem legenda e sem dizer a que linha pertence.
//
// A ordem é a do protótipo: o estado primeiro, porque é a pergunta que a tela
// responde. E os dois carimbos ganharam colunas próprias — moravam dentro das
// células de Estado e de Trabalhando, dois dados por célula, que é exatamente o
// que uma faixa de legendas existe para desfazer.
//
// São **sete** colunas onde antes eram cinco, e é por isso que o piso da janela
// subiu de 940 para 1030dp: cada carimbo é "12/08 10:58 BRT" por extenso, e
// truncá-lo em "12/08 10:58 B…" apaga justamente o fuso que a frase existe para
// dizer. O somatório abaixo é 802dp.
private val PRESENCE_COLUMN_STATE = 132.dp
private val PRESENCE_COLUMN_IDENTITY = 150.dp
private val PRESENCE_COLUMN_MACHINE = 104.dp
private val PRESENCE_COLUMN_ACTIVE_SESSIONS = 96.dp
private val PRESENCE_COLUMN_LAST_SEEN = 116.dp
private val PRESENCE_COLUMN_LAST_TURN = 116.dp
private val PRESENCE_COLUMN_STATUS = 88.dp

private val PRESENCE_COLUMN_SPACING = 16.dp
private val PRESENCE_ROW_CONTENT_PADDING = 14.dp

// A faixa da conta é a capa, e capa mais baixa que o item é o defeito que a
// issue #104 abriu na lista de consumo. Aqui ela nasce mais alta pelo padding e
// pelo degrau tipográfico do e-mail.
private val PRESENCE_ACCOUNT_VERTICAL_PADDING = AppSpacing.md

// Degrau de aninhamento, aplicado **dentro da coluna de identidade**, como na
// lista de consumo.
//
// A faixa da conta e a linha do integrante têm a identidade no mesmo x — as duas
// começam depois da coluna de estado —, e é ali que o olho compara os dois
// níveis. Recuar a linha inteira desalinharia a faixa de legendas, que é uma só
// para a lista e descreve todas as linhas (issue #81).
private val PRESENCE_NEST_INDENT = AppSpacing.md

// A faixa da conta abre com um ícone de recolher e a linha do integrante não
// tem ícone nenhum, ao contrário da lista de consumo. Sem compensar essa casa, o
// apelido do integrante começava **à esquerda** do e-mail da conta que o cobre —
// um degrau invertido, medido na captura do README —, e o recuo trabalhava
// contra a hierarquia em vez de a favor.
private val PRESENCE_BAND_ICON_GUTTER = 24.dp + 8.dp

/** Largura fixa: num `FlowRow` um campo elástico empurraria os indicadores. */
private val PRESENCE_FILTER_FIELD_WIDTH = 200.dp

/** Mesma pegada do `AppIconButton` (26dp), para o cabeçalho reservar a casa certa. */
private val PRESENCE_ACTION_SLOT = 26.dp

/**
 * As três palavras que os dois booleanos produzem.
 *
 * Continuam **duas camadas** e não uma escala: *conectado* é heartbeat dentro de
 * 90s e *trabalhando agora* é turno dentro de 5min. O que muda é o desenho — as
 * três combinações que existem (trabalhar implica estar online) cabem numa coluna
 * só, e colapsá-las em duas é que esconderia o caso que a tela existe para
 * mostrar: quem está com o app aberto e parado.
 */
private fun presenceStateLabel(entry: TeamMemberPresence, language: AppLanguage): String {
    return when {
        entry.isWorkingNow -> TeamPresenceLabels.workingNow(language)
        entry.isOnline -> TeamPresenceLabels.online(language)
        else -> TeamPresenceLabels.offline(language)
    }
}

private fun presenceStateTone(entry: TeamMemberPresence): AppTone {
    return when {
        entry.isWorkingNow -> AppTone.OK
        entry.isOnline -> AppTone.INFO
        else -> AppTone.NEUTRAL
    }
}

/** Único componente stateful: lê o estado do ViewModel e delega para filhos puros. */
@Composable
fun TeamPresenceScreen(
    viewModel: TeamPresenceViewModel,
    language: AppLanguage,
    /** `deviceId` desta instalação; a linha correspondente ganha o selo. */
    localDeviceId: String?,
    /** Modo administrador: é o que libera os botões destrutivos. */
    canManage: Boolean = false,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val actionError by viewModel.actionError.collectAsState()

    TeamPresenceContent(
        state = state,
        language = language,
        localDeviceId = localDeviceId,
        canManage = canManage,
        actionError = actionError,
        onToggleAccount = { groupKey -> viewModel.toggleAccount(groupKey) },
        onSetOnlyOnline = { value -> viewModel.setOnlyOnline(value) },
        onQueryChange = { value -> viewModel.setQuery(value) },
        onRemoveMember = { memberKey -> viewModel.removeMember(memberKey) },
        onDeleteAccount = { accountKey -> viewModel.deleteAccount(accountKey) },
        onDismissActionError = { viewModel.clearActionError() },
        modifier = modifier
    )
}

@Composable
internal fun TeamPresenceContent(
    state: TeamPresenceUiState,
    language: AppLanguage,
    modifier: Modifier = Modifier,
    localDeviceId: String? = null,
    canManage: Boolean = false,
    actionError: String? = null,
    onToggleAccount: (String) -> Unit = {},
    onSetOnlyOnline: (Boolean) -> Unit = {},
    onQueryChange: (String) -> Unit = {},
    onRemoveMember: (String) -> Unit = {},
    onDeleteAccount: (String) -> Unit = {},
    onDismissActionError: () -> Unit = {}
) {
    // O alvo pendente mora aqui, e não no ViewModel: é estado de diálogo, e o
    // laço ao vivo republica o estado a cada 5s sem saber que há um modal aberto.
    var pendingMember by remember { mutableStateOf<TeamMemberPresence?>(null) }
    var pendingAccount by remember { mutableStateOf<TeamPresenceAccountGroup?>(null) }

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
                canManage = canManage && state.isAdminOverview,
                actionError = actionError,
                onToggleAccount = onToggleAccount,
                onSetOnlyOnline = onSetOnlyOnline,
                onQueryChange = onQueryChange,
                onRequestRemoveMember = { entry -> pendingMember = entry },
                onRequestDeleteAccount = { group -> pendingAccount = group },
                onDismissActionError = onDismissActionError
            )
        }
    }

    val memberToRemove = pendingMember
    if (memberToRemove != null) {
        ConfirmationDialog(
            title = TeamUsageLabels.removeMemberTitle(language),
            message = TeamUsageLabels.removeMemberWarning(memberToRemove.alias, language),
            confirmLabel = TeamUsageLabels.confirmRemoval(language),
            confirmTag = PRESENCE_REMOVE_CONFIRM_TAG,
            language = language,
            onConfirm = {
                pendingMember = null
                onRemoveMember(memberToRemove.memberKey)
            },
            onDismiss = { pendingMember = null }
        )
    }

    val accountToDelete = pendingAccount
    if (accountToDelete != null) {
        ConfirmationDialog(
            title = TeamPresenceLabels.deleteAccountTitle(language),
            message = TeamPresenceLabels.deleteAccountWarning(
                accountKey = accountToDelete.groupKey,
                memberCount = accountToDelete.totalCount,
                language = language
            ),
            confirmLabel = TeamPresenceLabels.confirmAccountDeletion(language),
            confirmTag = PRESENCE_DELETE_ACCOUNT_CONFIRM_TAG,
            language = language,
            onConfirm = {
                pendingAccount = null
                onDeleteAccount(accountToDelete.groupKey)
            },
            onDismiss = { pendingAccount = null }
        )
    }
}

/** Confirmação obrigatória: as duas ações apagam dados e não têm desfazer. */
@Composable
private fun ConfirmationDialog(
    title: String,
    message: String,
    confirmLabel: String,
    confirmTag: String,
    language: AppLanguage,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { ModalDialogText(message) },
        confirmButton = {
            AppButton(
                label = confirmLabel,
                onClick = onConfirm,
                tone = AppButtonTone.DANGER,
                modifier = Modifier.testTag(confirmTag)
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
private fun TeamPresenceList(
    state: TeamPresenceUiState.Success,
    language: AppLanguage,
    localDeviceId: String?,
    canManage: Boolean,
    actionError: String?,
    onToggleAccount: (String) -> Unit,
    onSetOnlyOnline: (Boolean) -> Unit,
    onQueryChange: (String) -> Unit,
    onRequestRemoveMember: (TeamMemberPresence) -> Unit,
    onRequestDeleteAccount: (TeamPresenceAccountGroup) -> Unit,
    onDismissActionError: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TeamPresenceHeader(
            state = state,
            language = language,
            onSetOnlyOnline = onSetOnlyOnline,
            onQueryChange = onQueryChange
        )

        if (actionError != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = TeamPresenceLabels.actionError(actionError, language),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f).testTag(PRESENCE_ACTION_ERROR_TAG)
                )
                AppButton(
                    label = TeamUsageLabels.cancel(language),
                    onClick = onDismissActionError,
                    tone = AppButtonTone.GHOST
                )
            }
        }

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
            // Dois filtros, duas mensagens: "desligue o filtro para ver o time
            // inteiro" não ajuda quem digitou um nome que não existe.
            val query = state.query.trim()
            CenteredMessage(
                if (query.isEmpty()) {
                    TeamPresenceLabels.emptyFiltered(language)
                } else {
                    TeamPresenceLabels.emptyQuery(query, language)
                }
            )
            return@Column
        }

        // Decidido uma vez para a lista inteira, e não por linha: as colunas só
        // alinham se todas as linhas reservarem as mesmas casas. Uma coluna que
        // aparece em algumas linhas e some em outras desloca tudo o que vem depois.
        val hasHealthColumn = state.emailGroups.any { group ->
            group.entries.any { entry -> entry.worstHealth != null }
        }

        TeamPresenceColumnHeader(
            language = language,
            hasHealthColumn = hasHealthColumn,
            hasActionColumn = canManage
        )

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            val listState = rememberLazyListState()

            // Sem espaço entre itens, como na lista de consumo do time: cada
            // linha traz a própria divisória e a faixa da conta também, e o vão de
            // 8dp era justamente o que desfazia a leitura de tabela — conta e
            // integrante viravam dois blocos soltos do mesmo peso.
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(end = SCROLLBAR_GUTTER)
            ) {
                for (emailGroup in state.emailGroups) {
                    // Faixa só na visão global: no modal de uma conta só ela já é
                    // a da janela e repeti-la aqui seria ruído.
                    if (state.isAdminOverview) {
                        item(key = "email:${emailGroup.groupKey}") {
                            TeamPresenceEmailHeader(
                                group = emailGroup,
                                expanded = state.isEmailExpanded(emailGroup),
                                language = language,
                                hasActionColumn = canManage,
                                onToggle = { onToggleAccount(emailGroup.groupKey) }
                            )
                        }
                    }

                    if (!state.isEmailExpanded(emailGroup)) {
                        continue
                    }

                    for (account in emailGroup.accounts) {
                        val isLocalAccount = localDeviceId != null &&
                            account.entries.any { entry -> entry.deviceId == localDeviceId }
                        // Um degrau por nível que existe acima do integrante. No
                        // modal de uma conta não há faixa nenhuma e o recuo é
                        // zero, que é a geometria de sempre.
                        val entryIndent = when {
                            !state.isAdminOverview -> 0.dp
                            emailGroup.accounts.size > 1 ->
                                PRESENCE_BAND_ICON_GUTTER + PRESENCE_NEST_INDENT * 2
                            else -> PRESENCE_BAND_ICON_GUTTER + PRESENCE_NEST_INDENT
                        }
                        if (state.isAdminOverview) {
                            item(key = "uuid:${account.accountKey}") {
                                TeamPresenceAccountSubgroupHeader(
                                    group = account,
                                    language = language,
                                    deletable = canManage && !isLocalAccount,
                                    hasActionColumn = canManage,
                                    onDelete = { onRequestDeleteAccount(account) }
                                )
                            }
                        }

                    items(count = account.entries.size, key = { index -> account.entries[index].memberKey }) { index ->
                        val entry = account.entries[index]
                        val isLocalMachine = localDeviceId != null && entry.deviceId == localDeviceId
                        TeamPresenceRow(
                            entry = entry,
                            language = language,
                            isLocalMachine = isLocalMachine,
                            indent = entryIndent,
                            // Mesma regra do modal de consumo: esta máquina volta
                            // no próximo envio, então o botão entregaria uma
                            // remoção que se desfaz sozinha.
                            removable = canManage && !isLocalMachine,
                            hasHealthColumn = hasHealthColumn,
                            hasActionColumn = canManage,
                            onRemove = { onRequestRemoveMember(entry) }
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
    onSetOnlyOnline: (Boolean) -> Unit,
    onQueryChange: (String) -> Unit
) {
    // Superfície de dados como as outras: `AppElevation.dialog` num painel dentro
    // da janela punha 8dp de sombra sob um bloco que não flutua sobre nada.
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
private fun TeamPresenceColumnHeader(
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TeamPresenceEmailHeader(
    group: TeamPresenceEmailGroup,
    expanded: Boolean,
    language: AppLanguage,
    /** A lista tem coluna de ação; a faixa reserva a casa mesmo sem botão. */
    hasActionColumn: Boolean,
    onToggle: () -> Unit
) {
    val accents = AppAccents.current

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
                    horizontal = PRESENCE_ROW_CONTENT_PADDING,
                    vertical = PRESENCE_ACCOUNT_VERTICAL_PADDING
                )
                .testTag(
                    "$PRESENCE_ACCOUNT_GROUP_TAG_PREFIX${group.accounts.singleOrNull()?.accountKey ?: group.groupKey}"
                ),
            // Marcador e vão iguais aos do `AppDataRow` da linha do integrante: é o
            // que mantém os agregados da conta no mesmo x das colunas dela.
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppSourceMarker(color = accents.cacheRead)
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(PRESENCE_COLUMN_SPACING),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // A primeira coluna da linha é o estado, e a faixa não tem estado
                // agregado: o vão mantém a identidade da conta no mesmo x do
                // apelido do integrante.
                Spacer(modifier = Modifier.width(PRESENCE_COLUMN_STATE))

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
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        // A palavra vem antes do e-mail: sem ela a faixa entregava um
                        // endereço e um uuid sem dizer que aquilo é a conta, e ao lado de
                        // uma linha de integrante — que também tem nome e identificador —
                        // as duas liam igual.
                        Text(
                            text = TeamUsageLabels.accountBand(language),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        // Continua em `titleSmall`, como na lista de consumo:
                        // com 16sp o e-mail não cabe nos 118dp úteis da coluna e
                        // a captura saiu com "ana@example…". A altura da faixa
                        // vem do padding vertical, que não custa largura.
                        Text(
                            text = group.accountEmail ?: TeamUsageLabels.unlabeledAccount(language),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = group.accounts.singleOrNull()?.accountKey.orEmpty().takeIf { it.isNotEmpty() }
                                ?.let { accountKey -> accountKey }
                                ?: TeamUsageLabels.technicalAccounts(
                                    count = group.accounts.size,
                                    language = language
                                ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // A faixa da conta agrega, e a coluna Máquina não tem agregado: o vão
                // mantém as colunas seguintes no mesmo x da linha do integrante.
                Spacer(modifier = Modifier.width(PRESENCE_COLUMN_MACHINE))

                // Uma célula só, atravessando as três colunas que a conta não
                // tem — sessões ativas, último sinal e último turno. O texto se
                // descreve ("2 de 2 conectados · 1 trabalhando"), então ele não
                // depende da legenda de coluna nenhuma; o que não pode acontecer
                // é um número cru sob a legenda errada.
                AppCellValue(
                    value = TeamPresenceLabels.accountBandSummary(
                        online = group.onlineCount,
                        total = group.totalCount,
                        working = group.workingCount,
                        language = language
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(
                        PRESENCE_COLUMN_ACTIVE_SESSIONS +
                            PRESENCE_COLUMN_SPACING + PRESENCE_COLUMN_LAST_SEEN +
                            PRESENCE_COLUMN_SPACING + PRESENCE_COLUMN_LAST_TURN
                    )
                )
            }

            // Fora do `Row` de propósito: dentro dele a ação é o último item e
            // portanto o primeiro a quebrar, e numa janela estreita o botão de
            // apagar aparecia sozinho numa linha abaixo do e-mail.
            if (hasActionColumn) {
                Spacer(modifier = Modifier.size(PRESENCE_ACTION_SLOT))
            }
        }
        AppDivider()
    }
}

@Composable
private fun TeamPresenceAccountSubgroupHeader(
    group: TeamPresenceAccountGroup,
    language: AppLanguage,
    deletable: Boolean,
    hasActionColumn: Boolean,
    onDelete: () -> Unit
) {
    AppGroupBand(
        label = "${TeamUsageLabels.accountBand(language)} · ${group.accountKey.orEmpty()}",
        detail = TeamPresenceLabels.accountBandSummary(
            online = group.onlineCount,
            total = group.totalCount,
            working = group.workingCount,
            language = language
        ),
        indent = AppSpacing.xl,
        horizontalPadding = PRESENCE_ROW_CONTENT_PADDING
    ) {
        if (deletable) {
            AppIconButton(
                contentDescription = TeamPresenceLabels.deleteAccount(language),
                onClick = onDelete,
                tone = AppButtonTone.DANGER,
                modifier = Modifier.testTag("$PRESENCE_ACCOUNT_DELETE_TAG_PREFIX${group.groupKey}")
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteForever,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        } else if (hasActionColumn) {
            Spacer(modifier = Modifier.size(PRESENCE_ACTION_SLOT))
        }
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
    isLocalMachine: Boolean,
    /** Degrau de aninhamento, aplicado **dentro** da coluna de identidade. */
    indent: Dp,
    removable: Boolean,
    /** A lista tem coluna de status; esta linha reserva a casa mesmo sem veredito. */
    hasHealthColumn: Boolean,
    /** A lista tem coluna de ação; esta linha reserva a casa mesmo sem botão. */
    hasActionColumn: Boolean,
    onRemove: () -> Unit
) {
    val accents = AppAccents.current

    // Acento da fonte para quem está conectado e neutro para offline, na mesma
    // gramática de "sem atividade" da lista de consumo.
    //
    // Ele carregava a escala de três estados — verde trabalhando, azul online,
    // neutro offline — e isso tinha dois problemas ao mesmo tempo (issue #104):
    // o verde é o mesmo marcador da faixa da conta logo acima, então capa e item
    // ficavam idênticos justamente na linha de quem está trabalhando; e o estado
    // já tem coluna própria, a primeira da linha, com ponto **e** palavra. Duas
    // codificações da mesma coisa, uma delas só por cor.
    val accent = if (entry.isOnline) accents.anthropic else MaterialTheme.colorScheme.outline

    // Linha de tabela, como as outras listas do app: eram painéis empilhados com
    // vão entre eles, e uma lista de vinte máquinas virava vinte blocos.
    AppDataRow(
        modifier = Modifier.testTag("$PRESENCE_ROW_TAG_PREFIX${entry.memberKey}"),
        horizontalPadding = PRESENCE_ROW_CONTENT_PADDING,
        verticalPadding = PRESENCE_ROW_CONTENT_PADDING
    ) {
        AppSourceMarker(color = accent)
        // `Row` e não `FlowRow`: quebrar é o que a faixa de legendas não admite.
        // Quem garante que ela não quebre é o orçamento de `PRESENCE_COLUMN_*`
        // mais o piso da janela.
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(PRESENCE_COLUMN_SPACING),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Um estado e três palavras, não dois booleanos soltos: trabalhar
            // implica estar online, então as três combinações que existem cabem
            // numa coluna só — e ela continua sendo ponto **e** palavra, porque
            // cor sozinha não informa.
            AppStatusIndicator(
                label = presenceStateLabel(entry, language),
                tone = presenceStateTone(entry),
                modifier = Modifier
                    .width(PRESENCE_COLUMN_STATE)
                    .semantics(mergeDescendants = true) { }
                    .testTag("$PRESENCE_STATE_TAG_PREFIX${entry.memberKey}")
            )

            Row(
                modifier = Modifier.width(PRESENCE_COLUMN_IDENTITY),
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // O degrau vive dentro da coluna, que é de largura fixa: o que
                // ele consome sai do texto e nenhuma coluna à direita se move.
                if (indent > 0.dp) {
                    Spacer(modifier = Modifier.width(indent))
                }
                // Sem a palavra do nível, ao contrário da lista de consumo.
                //
                // Lá ela vem emendada na máquina, que já ocupava a linha abaixo
                // do apelido e deixou de ser coluna. Aqui a máquina **é** coluna
                // própria, então o rótulo não carregaria nada além de si mesmo —
                // e a legenda desta coluna já diz "Integrante", uma vez para a
                // lista inteira. Repeti-la em cada linha é exatamente o rótulo
                // por célula que a issue #81 desfez.
                //
                // Quem separa capa de item aqui são os outros três degraus: o
                // recuo, o marcador e o peso do e-mail da faixa.
                Column(modifier = Modifier.weight(1f)) {
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
            }

            AppCellValue(
                value = entry.machineLabel,
                modifier = Modifier.width(PRESENCE_COLUMN_MACHINE)
            )

            // Só o número: a legenda da coluna já diz o que ele conta. Quem não
            // está trabalhando tem zero, e zero aqui é medida — o "Parado" que
            // existia é o que a coluna Estado passou a dizer.
            AppCellValue(
                value = entry.activeSessionCount.toString(),
                color = if (entry.isWorkingNow) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .width(PRESENCE_COLUMN_ACTIVE_SESSIONS)
                    .testTag("$PRESENCE_WORKING_TAG_PREFIX${entry.memberKey}")
            )

            // Hora absoluta, e não tempo decorrido: o decorrido mudaria a cada
            // tique e recomporia a lista inteira de 5 em 5 segundos.
            AppCellValue(
                value = entry.lastSeenAt?.let { instant -> formatInstant(instant) }
                    ?: TeamPresenceLabels.neverSeen(language),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .width(PRESENCE_COLUMN_LAST_SEEN)
                    .testTag("$PRESENCE_LAST_SEEN_TAG_PREFIX${entry.memberKey}")
            )

            AppCellValue(
                value = entry.lastActivityAt?.let { instant -> formatInstant(instant) }
                    ?: TeamPresenceLabels.neverSeen(language),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .width(PRESENCE_COLUMN_LAST_TURN)
                    .testTag("$PRESENCE_LAST_TURN_TAG_PREFIX${entry.memberKey}")
            )

            // Quem não tem sessão não tem veredito, mas a coluna continua ocupada:
            // sem o vão, a linha sem status encolhe e o botão de ação sobe uma
            // coluna, aparecendo em x diferente do das linhas vizinhas.
            val worstHealth = entry.worstHealth
            if (worstHealth != null) {
                TeamHealthCell(
                    health = worstHealth,
                    language = language,
                    modifier = Modifier.width(PRESENCE_COLUMN_STATUS)
                )
            } else if (hasHealthColumn) {
                Spacer(modifier = Modifier.width(PRESENCE_COLUMN_STATUS))
            }
        }

        // Fora do `FlowRow`, como na faixa da conta: lá dentro a ação é o último
        // item e o primeiro a quebrar numa janela estreita, e o botão destrutivo
        // acabava numa linha própria, sem coluna e sem dizer a quem pertence.
        if (removable) {
            AppIconButton(
                contentDescription = TeamUsageLabels.removeMember(language),
                onClick = onRemove,
                tone = AppButtonTone.DANGER,
                modifier = Modifier.testTag(
                    "$PRESENCE_MEMBER_REMOVE_TAG_PREFIX${entry.memberKey}"
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        } else if (hasActionColumn) {
            // Quadrado, não só largura: a máquina local não tem botão, e sem
            // reservar também a altura a linha dela sairia mais baixa que as
            // vizinhas — a lista ganharia um degrau sem motivo.
            Spacer(modifier = Modifier.size(PRESENCE_ACTION_SLOT))
        }
    }
}

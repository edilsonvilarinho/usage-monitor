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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.TeamMemberPresence
import com.usagemonitor.presentation.ui.components.AppButton
import com.usagemonitor.presentation.ui.components.AppToggleChip
import com.usagemonitor.presentation.ui.components.AppIconButton
import com.usagemonitor.presentation.ui.components.AppButtonTone
import com.usagemonitor.presentation.ui.components.DepthSurface
import com.usagemonitor.presentation.ui.theme.AppAccents
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
internal const val PRESENCE_MEMBER_REMOVE_TAG_PREFIX = "teamPresenceMemberRemove:"
internal const val PRESENCE_ACCOUNT_DELETE_TAG_PREFIX = "teamPresenceAccountDelete:"
internal const val PRESENCE_REMOVE_CONFIRM_TAG = "teamPresenceRemoveConfirm"
internal const val PRESENCE_DELETE_ACCOUNT_CONFIRM_TAG = "teamPresenceDeleteAccountConfirm"
internal const val PRESENCE_ACTION_ERROR_TAG = "teamPresenceActionError"
internal const val PRESENCE_COLUMN_HEADER_TAG = "teamPresenceColumnHeader"

// Larguras das colunas num lugar só, pelo mesmo motivo da tela de consumo: a
// faixa do cabeçalho, a da conta e a linha do integrante têm de cair no mesmo x.
//
// O somatório não é livre. Quando ele passa da largura útil da janela o `FlowRow`
// quebra e as colunas deixam de alinhar entre as linhas — que é exatamente o que
// as larguras fixas existem para impedir. A conta que faltava:
//
//     Σ colunas + 16dp × (n − 1) + 64dp (botão de remover) ≤ largura_janela − 72dp
//
// onde os 72dp são 32 de padding da Column + 12 do `SCROLLBAR_GUTTER` + 28 do
// `contentPadding` da linha. Com a janela em 960dp (`TeamPresenceWindowPreferences`)
// o teto é 888dp; o pior caso abaixo dá 872dp.
// As colunas Estado e Trabalhando carregam um carimbo do tipo
// "último sinal 12/08 10:58 BRT": abaixo de ~150dp de texto ele quebra em duas
// linhas e a lista fica com alturas irregulares. Estado ainda desconta o ponto
// de status e o espaçamento (14dp), daí ser a mais larga das duas.
private val PRESENCE_COLUMN_IDENTITY = 190.dp
private val PRESENCE_COLUMN_MACHINE = 125.dp
private val PRESENCE_COLUMN_STATE = 170.dp
private val PRESENCE_COLUMN_WORKING = 155.dp
private val PRESENCE_COLUMN_STATUS = 104.dp

private val PRESENCE_COLUMN_SPACING = 16.dp
private val PRESENCE_ROW_CONTENT_PADDING = 14.dp

/** Reserva a mesma pegada do `IconButton` de ação, para o cabeçalho alinhar. */
private val PRESENCE_ACTION_SLOT = 48.dp

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
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag(confirmTag)) {
                Text(text = confirmLabel, color = MaterialTheme.colorScheme.error)
            }
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
    onRequestRemoveMember: (TeamMemberPresence) -> Unit,
    onRequestDeleteAccount: (TeamPresenceAccountGroup) -> Unit,
    onDismissActionError: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TeamPresenceHeader(state = state, language = language, onSetOnlyOnline = onSetOnlyOnline)

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
            CenteredMessage(TeamPresenceLabels.emptyFiltered(language))
            return@Column
        }

        // Decidido uma vez para a lista inteira, e não por linha: as colunas só
        // alinham se todas as linhas reservarem as mesmas casas. Uma coluna que
        // aparece em algumas linhas e some em outras desloca tudo o que vem depois.
        val hasHealthColumn = state.presenceGroups.any { group ->
            group.entries.any { entry -> entry.worstHealth != null }
        }

        TeamPresenceColumnHeader(
            language = language,
            hasHealthColumn = hasHealthColumn,
            hasActionColumn = canManage
        )

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            val listState = rememberLazyListState()

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(end = SCROLLBAR_GUTTER),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (group in state.presenceGroups) {
                    // Esta máquina participa da conta: apagá-la levaria junto o
                    // histórico local, e a conta voltaria no envio seguinte.
                    val isLocalAccount = localDeviceId != null &&
                        group.entries.any { entry -> entry.deviceId == localDeviceId }

                    // Faixa só na visão global: no modal de uma conta só ela já é
                    // a da janela e repeti-la aqui seria ruído.
                    if (state.isAdminOverview) {
                        item(key = "account:${group.accountKey}") {
                            TeamPresenceAccountHeader(
                                group = group,
                                expanded = state.isAccountExpanded(group),
                                language = language,
                                deletable = canManage && !isLocalAccount,
                                onToggle = { onToggleAccount(group.groupKey) },
                                onDelete = { onRequestDeleteAccount(group) }
                            )
                        }
                    }

                    if (!state.isAccountExpanded(group)) {
                        continue
                    }

                    items(count = group.entries.size, key = { index -> group.entries[index].memberKey }) { index ->
                        val entry = group.entries[index]
                        val isLocalMachine = localDeviceId != null && entry.deviceId == localDeviceId
                        TeamPresenceRow(
                            entry = entry,
                            language = language,
                            isLocalMachine = isLocalMachine,
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
    val accents = AppAccents.current

    DepthSurface(
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
                    text = TeamUsageLabels.allAccounts(state.presenceGroups.size, language),
                    style = MaterialTheme.typography.labelMedium,
                    color = accents.cacheRead,
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
                valueColor = accents.cacheRead
            )
            SummaryNumber(
                value = state.onlineCount.toString(),
                caption = TeamPresenceLabels.onlineSummary(state.onlineCount, language),
                valueColor = accents.input
            )
            SummaryNumber(
                value = state.totalCount.toString(),
                caption = TeamPresenceLabels.knownSummary(state.totalCount, language),
                valueColor = MaterialTheme.colorScheme.onSurface
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = SCROLLBAR_GUTTER)
            .padding(horizontal = PRESENCE_ROW_CONTENT_PADDING)
            .testTag(PRESENCE_COLUMN_HEADER_TAG),
        horizontalArrangement = Arrangement.spacedBy(PRESENCE_COLUMN_SPACING)
    ) {
        ColumnHeaderLabel(
            label = TeamPresenceLabels.columnMember(language),
            modifier = Modifier.width(PRESENCE_COLUMN_IDENTITY)
        )
        ColumnHeaderLabel(
            label = CliSessionsLabels.machine(language),
            modifier = Modifier.width(PRESENCE_COLUMN_MACHINE)
        )
        ColumnHeaderLabel(
            label = TeamPresenceLabels.columnState(language),
            modifier = Modifier.width(PRESENCE_COLUMN_STATE)
        )
        ColumnHeaderLabel(
            label = TeamPresenceLabels.columnWorking(language),
            modifier = Modifier.width(PRESENCE_COLUMN_WORKING)
        )
        if (hasHealthColumn) {
            ColumnHeaderLabel(
                label = TeamUsageLabels.columnStatus(language),
                modifier = Modifier.width(PRESENCE_COLUMN_STATUS)
            )
        }
        if (hasActionColumn) {
            Spacer(modifier = Modifier.width(PRESENCE_ACTION_SLOT))
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
    deletable: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val accents = AppAccents.current

    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clickable(onClick = onToggle)
            .padding(horizontal = PRESENCE_ROW_CONTENT_PADDING)
            .testTag("$PRESENCE_ACCOUNT_GROUP_TAG_PREFIX${group.accountKey.orEmpty()}"),
        horizontalArrangement = Arrangement.spacedBy(PRESENCE_COLUMN_SPACING),
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

        // A faixa da conta agrega, e a coluna Máquina não tem agregado: o vão
        // mantém as colunas seguintes no mesmo x da linha do integrante.
        Spacer(modifier = Modifier.width(PRESENCE_COLUMN_MACHINE))

        MetricValue(
            value = TeamPresenceLabels.groupSummary(group.onlineCount, group.totalCount, language),
            modifier = Modifier.width(PRESENCE_COLUMN_STATE)
        )

        MetricValue(
            value = group.workingCount.toString(),
            valueColor = accents.cacheRead,
            modifier = Modifier.width(PRESENCE_COLUMN_WORKING)
        )

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
    removable: Boolean,
    /** A lista tem coluna de status; esta linha reserva a casa mesmo sem veredito. */
    hasHealthColumn: Boolean,
    /** A lista tem coluna de ação; esta linha reserva a casa mesmo sem botão. */
    hasActionColumn: Boolean,
    onRemove: () -> Unit
) {
    val accents = AppAccents.current

    // Neutro para offline, na mesma gramática de "sem atividade" da tela de
    // consumo; quem está trabalhando ganha o acento mais forte.
    val accent = when {
        entry.isWorkingNow -> accents.cacheRead
        entry.isOnline -> accents.input
        else -> MaterialTheme.colorScheme.outline
    }

    DepthSurface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("$PRESENCE_ROW_TAG_PREFIX${entry.memberKey}"),
        shape = AppShapes.medium,
        contentPadding = PRESENCE_ROW_CONTENT_PADDING
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PRESENCE_COLUMN_SPACING),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // O botão de remover tem 48dp de alvo e é o item mais alto da linha.
            // Sem centrar, as células de texto ficam grudadas no topo com um vão
            // morto embaixo. O `FlowRow` desta versão do Compose não tem
            // alinhamento de item, então cada célula carrega o seu.
            val cellAlignment = Modifier.align(Alignment.CenterVertically)

            Column(modifier = cellAlignment.width(PRESENCE_COLUMN_IDENTITY)) {
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

            MetricValue(
                value = entry.machineLabel,
                modifier = cellAlignment.width(PRESENCE_COLUMN_MACHINE)
            )

            // Semântica mesclada: "Conectado" e o horário do último sinal são uma
            // unidade de leitura, e separá-los faria o leitor de tela anunciar
            // dois fragmentos soltos.
            Row(
                modifier = cellAlignment
                    .width(PRESENCE_COLUMN_STATE)
                    .semantics(mergeDescendants = true) { }
                    .testTag("$PRESENCE_STATE_TAG_PREFIX${entry.memberKey}"),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Alinhado ao topo, e não centrado: a célula tem duas linhas, e
                // centrar deixaria o ponto flutuando entre elas em vez de ao lado
                // do estado que ele codifica. Os 5dp o descem à altura da x-height
                // da primeira linha.
                Box(
                    modifier = Modifier
                        .padding(top = 5.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (entry.isOnline) accents.input else MaterialTheme.colorScheme.outline)
                )
                Column {
                    Text(
                        text = if (entry.isOnline) {
                            TeamPresenceLabels.online(language)
                        } else {
                            TeamPresenceLabels.offline(language)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (entry.isOnline) accents.input else MaterialTheme.colorScheme.onSurfaceVariant
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
                modifier = cellAlignment
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
                        accents.cacheRead
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

            // Quem não tem sessão não tem veredito, mas a coluna continua ocupada:
            // sem o vão, a linha sem status encolhe e o botão de ação sobe uma
            // coluna, aparecendo em x diferente do das linhas vizinhas.
            val worstHealth = entry.worstHealth
            if (worstHealth != null) {
                TeamHealthCell(
                    health = worstHealth,
                    language = language,
                    modifier = cellAlignment.width(PRESENCE_COLUMN_STATUS),
                    showLabel = false
                )
            } else if (hasHealthColumn) {
                Spacer(modifier = Modifier.width(PRESENCE_COLUMN_STATUS))
            }

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
}

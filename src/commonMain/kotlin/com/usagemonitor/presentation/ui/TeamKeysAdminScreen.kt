package com.usagemonitor.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.TeamBlockedAccount
import com.usagemonitor.domain.entity.TeamKeyAccount
import com.usagemonitor.domain.entity.TeamKeyEntry
import com.usagemonitor.presentation.ui.components.AppLoadingState
import com.usagemonitor.presentation.ui.components.AppEmptyState
import com.usagemonitor.presentation.ui.components.AppButton
import com.usagemonitor.presentation.ui.components.AppConfirmationDialog
import com.usagemonitor.presentation.ui.components.AppDataSurface
import com.usagemonitor.presentation.ui.components.AppSectionHeader
import com.usagemonitor.presentation.ui.components.AppStatusIndicator
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.components.AppWindowScaffold
import com.usagemonitor.presentation.ui.components.AppTextField
import com.usagemonitor.presentation.ui.components.AppButtonTone
import com.usagemonitor.presentation.ui.theme.AppShapes
import com.usagemonitor.presentation.viewmodel.TeamKeysAdminViewModel
import com.usagemonitor.presentation.viewmodel.TeamKeysUiState

internal const val TEAM_KEYS_LIST_TAG = "teamKeysList"
internal const val TEAM_KEYS_CREATE_FIELD_TAG = "teamKeysCreateField"
internal const val TEAM_KEYS_CREATE_BUTTON_TAG = "teamKeysCreateButton"
internal const val TEAM_KEYS_ROW_TAG_PREFIX = "teamKeyRow:"
internal const val TEAM_KEYS_ERROR_TAG = "teamKeysError"
internal const val TEAM_KEYS_REMOVE_ACCOUNT_TAG_PREFIX = "teamKeyRemoveAccount:"
internal const val TEAM_KEYS_REMOVE_CONFIRM_TAG = "teamKeyRemoveAccountConfirm"
internal const val TEAM_KEYS_UNAUTHORIZED_TAG_PREFIX = "teamKeyUnauthorized:"
internal const val TEAM_KEYS_BLOCKED_SECTION_TAG = "teamKeysBlockedSection"
internal const val TEAM_KEYS_UNBLOCK_TAG_PREFIX = "teamKeyUnblock:"

/** Único componente stateful: lê o estado do ViewModel e delega para filhos puros. */
@Composable
fun TeamKeysAdminScreen(
    viewModel: TeamKeysAdminViewModel,
    language: AppLanguage,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    TeamKeysAdminContent(
        state = state,
        language = language,
        onCreate = { label, maxAccounts -> viewModel.create(label, maxAccounts) },
        onRename = { id, label -> viewModel.rename(id, label) },
        onSetMaxAccounts = { id, value -> viewModel.setMaxAccounts(id, value) },
        onRegenerate = { id -> viewModel.regenerate(id) },
        onRevoke = { id -> viewModel.revoke(id) },
        onUnclaim = { id, accountKey -> viewModel.unclaim(id, accountKey) },
        onRemoveAccount = { accountKey -> viewModel.removeAccountFromTeam(accountKey) },
        onUnblockAccount = { accountKey -> viewModel.unblock(accountKey) },
        onDismissError = { viewModel.clearActionError() },
        onRetry = { viewModel.refresh() },
        modifier = modifier
    )
}

@Composable
internal fun TeamKeysAdminContent(
    state: TeamKeysUiState,
    language: AppLanguage,
    onCreate: (String, Int) -> Unit,
    modifier: Modifier = Modifier,
    onRename: (String, String) -> Unit = { _, _ -> },
    onSetMaxAccounts: (String, Int) -> Unit = { _, _ -> },
    onRegenerate: (String) -> Unit = {},
    onRevoke: (String) -> Unit = {},
    onUnclaim: (String, String) -> Unit = { _, _ -> },
    onRemoveAccount: (String) -> Unit = {},
    onUnblockAccount: (String) -> Unit = {},
    onDismissError: () -> Unit = {},
    onRetry: () -> Unit = {}
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (state) {
            is TeamKeysUiState.Loading -> AppLoadingState(TeamKeysLabels.loading(language))

            is TeamKeysUiState.Error -> Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag(TEAM_KEYS_ERROR_TAG)
                )
                AppButton(
                    label = TeamKeysLabels.retry(language),
                    onClick = onRetry,
                    tone = AppButtonTone.PRIMARY
                )
            }

            is TeamKeysUiState.Success -> TeamKeysList(
                state = state,
                language = language,
                onCreate = onCreate,
                onRename = onRename,
                onSetMaxAccounts = onSetMaxAccounts,
                onRegenerate = onRegenerate,
                onRevoke = onRevoke,
                onUnclaim = onUnclaim,
                onRemoveAccount = onRemoveAccount,
                onUnblockAccount = onUnblockAccount,
                onDismissError = onDismissError
            )
        }
    }
}

@Composable
private fun TeamKeysList(
    state: TeamKeysUiState.Success,
    language: AppLanguage,
    onCreate: (String, Int) -> Unit,
    onRename: (String, String) -> Unit,
    onSetMaxAccounts: (String, Int) -> Unit,
    onRegenerate: (String) -> Unit,
    onRevoke: (String) -> Unit,
    onUnclaim: (String, String) -> Unit,
    onRemoveAccount: (String) -> Unit,
    onUnblockAccount: (String) -> Unit,
    onDismissError: () -> Unit
) {
    // A conta pendente de confirmação: a ação apaga dados e não tem desfazer.
    // Mora aqui, e não no ViewModel, porque é estado de diálogo — nada que o
    // servidor conheça.
    var pendingAccount by remember { mutableStateOf<TeamKeyAccount?>(null) }

    // O corpo desta janela era `Column(fillMaxSize).padding(16.dp)` com
    // `spacedBy(12.dp)` — exatamente o que `AppWindowScaffold` faz, com os dois
    // valores escritos como literal em vez de sair de `AppSpacing`.
    AppWindowScaffold(modifier = Modifier.fillMaxSize()) {
        Text(
            text = TeamKeysLabels.explanation(language),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        CreateKeyRow(language = language, enabled = !state.busy, onCreate = onCreate)

        val actionError = state.actionError
        if (actionError != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = actionError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f).testTag(TEAM_KEYS_ERROR_TAG)
                )
                AppButton(
                    label = TeamKeysLabels.dismiss(language),
                    onClick = onDismissError,
                    tone = AppButtonTone.GHOST
                )
            }
        }

        if (state.keys.isEmpty() && state.blockedAccounts.isEmpty()) {
            AppEmptyState(TeamKeysLabels.empty(language))
            return@AppWindowScaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f).testTag(TEAM_KEYS_LIST_TAG),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.keys.size, key = { index -> state.keys[index].id }) { index ->
                TeamKeyCard(
                    entry = state.keys[index],
                    language = language,
                    enabled = !state.busy,
                    onRename = onRename,
                    onSetMaxAccounts = onSetMaxAccounts,
                    onRegenerate = onRegenerate,
                    onRevoke = onRevoke,
                    onUnclaim = onUnclaim,
                    onRequestRemoveAccount = { account -> pendingAccount = account }
                )
            }

            // Dentro da lista, e não abaixo dela: a seção rola junto das chaves,
            // que é o que ela descreve — a conta removida saiu de uma delas.
            if (state.blockedAccounts.isNotEmpty()) {
                item(key = TEAM_KEYS_BLOCKED_SECTION_TAG) {
                    BlockedAccountsCard(
                        accounts = state.blockedAccounts,
                        language = language,
                        enabled = !state.busy,
                        onUnblock = onUnblockAccount
                    )
                }
            }
        }
    }

    val accountToRemove = pendingAccount
    if (accountToRemove != null) {
        AppConfirmationDialog(
            title = TeamKeysLabels.removeAccountTitle(language),
            message = TeamKeysLabels.removeAccountWarning(
                account = accountToRemove.accountEmail ?: accountToRemove.accountKey,
                language = language
            ),
            confirmLabel = TeamKeysLabels.confirmRemoveAccount(language),
            cancelLabel = TeamKeysLabels.cancel(language),
            confirmTag = TEAM_KEYS_REMOVE_CONFIRM_TAG,
            onConfirm = {
                pendingAccount = null
                onRemoveAccount(accountToRemove.accountKey)
            },
            onDismiss = { pendingAccount = null }
        )
    }
}

/**
 * As contas que o administrador tirou do time.
 *
 * O e-mail é o retrato guardado no bloqueio: os dados da conta foram apagados
 * junto, então não há de onde relê-lo — e o UUID sozinho não identifica ninguém
 * para quem vai decidir se devolve a conta.
 */
@Composable
private fun BlockedAccountsCard(
    accounts: List<TeamBlockedAccount>,
    language: AppLanguage,
    enabled: Boolean,
    onUnblock: (String) -> Unit
) {
    AppDataSurface(
        modifier = Modifier.fillMaxWidth().testTag(TEAM_KEYS_BLOCKED_SECTION_TAG),
        shape = AppShapes.large,
        contentPadding = 12.dp,
        verticalArrangement = Arrangement.Top
    ) {
        AppSectionHeader(title = TeamKeysLabels.blockedSection(language))

        for (account in accounts) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = account.accountEmail ?: TeamKeysLabels.noEmail(language),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = account.blockedAt
                            ?.let { at -> TeamKeysLabels.blockedAt(formatInstant(at), language) }
                            ?: account.accountKey,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                AppButton(
                    label = TeamKeysLabels.unblock(language),
                    onClick = { onUnblock(account.accountKey) },
                    enabled = enabled,
                    tone = AppButtonTone.GHOST,
                    modifier = Modifier.testTag(
                        "$TEAM_KEYS_UNBLOCK_TAG_PREFIX${account.accountKey}"
                    )
                )
            }
        }
    }
}

@Composable
private fun CreateKeyRow(
    language: AppLanguage,
    enabled: Boolean,
    onCreate: (String, Int) -> Unit
) {
    // Estado de formulário, sem significado no servidor: mora aqui em vez de no
    // ViewModel, que só guarda o que o servidor confirmou.
    var label by remember { mutableStateOf("") }
    var maxAccounts by remember { mutableStateOf(1) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // O rótulo do campo vira texto ao lado, e não `label` flutuante: um
        // campo de 28dp não tem altura para acomodar o rótulo subindo por cima
        // da borda, que é como o Material o desenha.
        Text(
            text = TeamKeysLabels.labelField(language),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        AppTextField(
            value = label,
            onValueChange = { text -> label = text },
            placeholder = TeamKeysLabels.labelPlaceholder(language),
            enabled = enabled,
            modifier = Modifier.weight(1f).testTag(TEAM_KEYS_CREATE_FIELD_TAG)
        )
        MaxAccountsStepper(
            value = maxAccounts,
            language = language,
            enabled = enabled,
            onChange = { value -> maxAccounts = value }
        )
        AppButton(
            label = TeamKeysLabels.create(language),
            onClick = {
                onCreate(label, maxAccounts)
                label = ""
                maxAccounts = 1
            },
            enabled = enabled && label.isNotBlank(),
            modifier = Modifier.testTag(TEAM_KEYS_CREATE_BUTTON_TAG),
            tone = AppButtonTone.PRIMARY
        )
    }
}

@Composable
private fun MaxAccountsStepper(
    value: Int,
    language: AppLanguage,
    enabled: Boolean,
    onChange: (Int) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppButton(
            label = "−",
            onClick = { onChange(value - 1) },
            enabled = enabled && value > 1,
            tone = AppButtonTone.GHOST
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "$value", style = MaterialTheme.typography.titleSmall)
            Text(
                text = TeamKeysLabels.maxAccounts(language),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        AppButton(
            label = "+",
            onClick = { onChange(value + 1) },
            enabled = enabled,
            tone = AppButtonTone.GHOST
        )
    }
}

@Composable
private fun TeamKeyCard(
    entry: TeamKeyEntry,
    language: AppLanguage,
    enabled: Boolean,
    onRename: (String, String) -> Unit,
    onSetMaxAccounts: (String, Int) -> Unit,
    onRegenerate: (String) -> Unit,
    onRevoke: (String) -> Unit,
    onUnclaim: (String, String) -> Unit,
    onRequestRemoveAccount: (TeamKeyAccount) -> Unit
) {
    // Só visualização: mostrar a chave é decisão de quem está olhando a tela, e
    // não estado que o servidor conheça.
    var revealed by remember(entry.id) { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    AppDataSurface(
        modifier = Modifier.fillMaxWidth().testTag("$TEAM_KEYS_ROW_TAG_PREFIX${entry.id}"),
        shape = AppShapes.large,
        contentPadding = 12.dp,
        verticalArrangement = Arrangement.Top
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = entry.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (entry.isRevoked) {
                Text(
                    text = TeamKeysLabels.revoked(language),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (revealed) entry.key else "${entry.keyPrefix}${"•".repeat(12)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            AppButton(
                label = if (revealed) TeamKeysLabels.hide(language) else TeamKeysLabels.show(language),
                onClick = { revealed = !revealed },
                tone = AppButtonTone.GHOST
            )
            AppButton(
                label = TeamKeysLabels.copy(language),
                onClick = { clipboard.setText(AnnotatedString(entry.key)) },
                tone = AppButtonTone.GHOST
            )
        }

        // O vínculo é a prova de a quem a chave pertence — o rótulo acima é só
        // texto que alguém digitou. Por isso os dois aparecem juntos.
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (entry.isUnclaimed) {
                Text(
                    text = TeamKeysLabels.unclaimed(language),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                for (account in entry.accountEntries) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            // O e-mail em cima e o uuid embaixo: era o uuid
                            // sozinho, e a conta pessoal que entrou no time ficava
                            // indistinguível das legítimas.
                            Text(
                                text = account.accountEmail
                                    ?: TeamKeysLabels.noEmail(language),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = account.accountKey,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (!account.authorized) {
                            // Ponto e palavra, nunca cor sozinha.
                            AppStatusIndicator(
                                label = TeamKeysLabels.unauthorizedAccount(language),
                                tone = AppTone.WARNING,
                                modifier = Modifier.testTag(
                                    "$TEAM_KEYS_UNAUTHORIZED_TAG_PREFIX${account.accountKey}"
                                )
                            )
                        }
                        AppButton(
                            label = TeamKeysLabels.unlink(language),
                            onClick = { onUnclaim(entry.id, account.accountKey) },
                            enabled = enabled,
                            tone = AppButtonTone.GHOST
                        )
                        // Destrutiva ao lado da branda: desvincular solta o
                        // vínculo e deixa os dados, e o envio seguinte daquela
                        // máquina refaz tudo. Remover é o que encerra.
                        AppButton(
                            label = TeamKeysLabels.removeAccount(language),
                            onClick = { onRequestRemoveAccount(account) },
                            enabled = enabled,
                            tone = AppButtonTone.DANGER,
                            modifier = Modifier.testTag(
                                "$TEAM_KEYS_REMOVE_ACCOUNT_TAG_PREFIX${account.accountKey}"
                            )
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MaxAccountsStepper(
                value = entry.maxAccounts,
                language = language,
                enabled = enabled,
                onChange = { value -> onSetMaxAccounts(entry.id, value) }
            )
            Box(modifier = Modifier.weight(1f))
            AppButton(
                label = TeamKeysLabels.regenerate(language),
                onClick = { onRegenerate(entry.id) },
                enabled = enabled,
                tone = AppButtonTone.GHOST
            )
            AppButton(
                label = TeamKeysLabels.revoke(language),
                onClick = { onRevoke(entry.id) },
                enabled = enabled && !entry.isRevoked,
                tone = AppButtonTone.GHOST
            )
        }

        RenameRow(entry = entry, language = language, enabled = enabled, onRename = onRename)
    }
}

@Composable
private fun RenameRow(
    entry: TeamKeyEntry,
    language: AppLanguage,
    enabled: Boolean,
    onRename: (String, String) -> Unit
) {
    var editing by remember(entry.id) { mutableStateOf(false) }
    var draft by remember(entry.id) { mutableStateOf(entry.label) }

    if (!editing) {
        AppButton(
            label = TeamKeysLabels.rename(language),
            onClick = { editing = true },
            enabled = enabled,
            tone = AppButtonTone.GHOST
        )
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Campo do sistema: 28dp e sem rotulo flutuante. O `OutlinedTextField`
        // tem 56dp de altura minima, e nesta linha ele ficava o dobro dos dois
        // botoes ao lado.
        AppTextField(
            value = draft,
            onValueChange = { text -> draft = text },
            placeholder = TeamKeysLabels.labelField(language),
            enabled = enabled,
            modifier = Modifier.weight(1f)
        )
        AppButton(
            label = TeamKeysLabels.save(language),
            onClick = {
                onRename(entry.id, draft)
                editing = false
            },
            enabled = enabled && draft.isNotBlank(),
            tone = AppButtonTone.PRIMARY
        )
        AppButton(
            label = TeamKeysLabels.cancel(language),
            onClick = {
            draft = entry.label
            editing = false
        },
            tone = AppButtonTone.GHOST
        )
    }
}

internal object TeamKeysLabels {

    fun loading(language: AppLanguage): String = if (language == AppLanguage.PT) {
        "Consultando as chaves no servidor…"
    } else {
        "Querying keys on the server…"
    }

    fun explanation(language: AppLanguage): String = if (language == AppLanguage.PT) {
        "O rótulo é a relação do time daquela chave: escreva ali o e-mail da pessoa, e só a " +
            "conta com aquele e-mail poderá usá-la. Separe por vírgula para incluir mais de uma. " +
            "Rótulo sem e-mail aceita qualquer conta."
    } else {
        "The label is that key's team roster: type the person's e-mail there and only the " +
            "account with that e-mail may use it. Separate with commas to list more than one. " +
            "A label without an e-mail accepts any account."
    }

    fun empty(language: AppLanguage): String = if (language == AppLanguage.PT) {
        "Nenhuma chave emitida ainda."
    } else {
        "No keys issued yet."
    }

    fun labelField(language: AppLanguage): String =
        if (language == AppLanguage.PT) "Rótulo" else "Label"

    fun labelPlaceholder(language: AppLanguage): String =
        if (language == AppLanguage.PT) "fulano@empresa.com" else "person@company.com"

    fun maxAccounts(language: AppLanguage): String =
        if (language == AppLanguage.PT) "contas" else "accounts"

    fun create(language: AppLanguage): String =
        if (language == AppLanguage.PT) "Emitir" else "Issue"

    fun show(language: AppLanguage): String =
        if (language == AppLanguage.PT) "Mostrar" else "Show"

    fun hide(language: AppLanguage): String =
        if (language == AppLanguage.PT) "Ocultar" else "Hide"

    fun copy(language: AppLanguage): String =
        if (language == AppLanguage.PT) "Copiar" else "Copy"

    fun unclaimed(language: AppLanguage): String = if (language == AppLanguage.PT) {
        "Sem conta vinculada — o vínculo nasce no primeiro envio."
    } else {
        "No account linked yet — the link is created on the first upload."
    }

    fun unlink(language: AppLanguage): String =
        if (language == AppLanguage.PT) "Desvincular" else "Unlink"

    fun regenerate(language: AppLanguage): String =
        if (language == AppLanguage.PT) "Regerar" else "Regenerate"

    fun revoke(language: AppLanguage): String =
        if (language == AppLanguage.PT) "Revogar" else "Revoke"

    fun revoked(language: AppLanguage): String =
        if (language == AppLanguage.PT) "revogada" else "revoked"

    fun rename(language: AppLanguage): String =
        if (language == AppLanguage.PT) "Renomear" else "Rename"

    fun save(language: AppLanguage): String =
        if (language == AppLanguage.PT) "Salvar" else "Save"

    fun cancel(language: AppLanguage): String =
        if (language == AppLanguage.PT) "Cancelar" else "Cancel"

    fun dismiss(language: AppLanguage): String =
        if (language == AppLanguage.PT) "Dispensar" else "Dismiss"

    fun retry(language: AppLanguage): String =
        if (language == AppLanguage.PT) "Tentar de novo" else "Retry"

    fun noEmail(language: AppLanguage): String =
        if (language == AppLanguage.PT) "sem e-mail reportado" else "no reported e-mail"

    fun unauthorizedAccount(language: AppLanguage): String =
        if (language == AppLanguage.PT) "Fora do rótulo" else "Outside the label"

    fun removeAccount(language: AppLanguage): String =
        if (language == AppLanguage.PT) "Remover do time" else "Remove from team"

    fun removeAccountTitle(language: AppLanguage): String = if (language == AppLanguage.PT) {
        "Remover esta conta do time?"
    } else {
        "Remove this account from the team?"
    }

    /**
     * Diz as duas coisas que a ação faz, porque a segunda é irreversível e a
     * primeira, sozinha, seria desfeita pelo envio seguinte daquela máquina.
     */
    fun removeAccountWarning(account: String, language: AppLanguage): String =
        if (language == AppLanguage.PT) {
            "Apaga tudo o que $account enviou — integrantes, sessões e turnos — e passa a " +
                "recusá-la, mesmo que a máquina dela continue enviando. Os dados não voltam nem " +
                "depois de devolvê-la ao time."
        } else {
            "Deletes everything $account sent — members, sessions and turns — and starts " +
                "refusing it, even if that machine keeps uploading. The data does not come back, " +
                "not even after returning the account to the team."
        }

    fun confirmRemoveAccount(language: AppLanguage): String =
        if (language == AppLanguage.PT) "Remover do time" else "Remove from team"

    fun blockedSection(language: AppLanguage): String =
        if (language == AppLanguage.PT) "Contas fora do time" else "Accounts outside the team"

    fun blockedEmpty(language: AppLanguage): String = if (language == AppLanguage.PT) {
        "Nenhuma conta removida."
    } else {
        "No account removed."
    }

    fun unblock(language: AppLanguage): String =
        if (language == AppLanguage.PT) "Devolver ao time" else "Return to team"

    fun blockedAt(timestamp: String, language: AppLanguage): String =
        if (language == AppLanguage.PT) "removida em $timestamp" else "removed on $timestamp"
}

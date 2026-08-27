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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.TeamKeyEntry
import com.usagemonitor.presentation.ui.components.AppButton
import com.usagemonitor.presentation.ui.components.AppTextField
import com.usagemonitor.presentation.ui.components.AppButtonTone
import com.usagemonitor.presentation.ui.components.DepthSurface
import com.usagemonitor.presentation.ui.theme.AppElevation
import com.usagemonitor.presentation.ui.theme.AppShapes
import com.usagemonitor.presentation.viewmodel.TeamKeysAdminViewModel
import com.usagemonitor.presentation.viewmodel.TeamKeysUiState

internal const val TEAM_KEYS_LIST_TAG = "teamKeysList"
internal const val TEAM_KEYS_CREATE_FIELD_TAG = "teamKeysCreateField"
internal const val TEAM_KEYS_CREATE_BUTTON_TAG = "teamKeysCreateButton"
internal const val TEAM_KEYS_ROW_TAG_PREFIX = "teamKeyRow:"
internal const val TEAM_KEYS_ERROR_TAG = "teamKeysError"

/** Único componente stateful: lê o estado do ViewModel e delega para filhos puros. */
@Composable
fun TeamKeysAdminScreen(
    viewModel: TeamKeysAdminViewModel,
    language: AppLanguage,
    onRequiredHeightChanged: (Dp) -> Unit = {},
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
        onDismissError = { viewModel.clearActionError() },
        onRetry = { viewModel.refresh() },
        onRequiredHeightChanged = onRequiredHeightChanged,
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
    onDismissError: () -> Unit = {},
    onRetry: () -> Unit = {},
    onRequiredHeightChanged: (Dp) -> Unit = {}
) {
    Surface(
        modifier = modifier
            .fillMaxSize()
            .then(rememberModalContentHeightReporter(onRequiredHeightChanged)),
        color = MaterialTheme.colorScheme.background
    ) {
        when (state) {
            is TeamKeysUiState.Loading -> CenteredMessage(TeamKeysLabels.loading(language))

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
    onDismissError: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
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

        if (state.keys.isEmpty()) {
            CenteredMessage(TeamKeysLabels.empty(language))
            return@Column
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
                    onUnclaim = onUnclaim
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
    onUnclaim: (String, String) -> Unit
) {
    // Só visualização: mostrar a chave é decisão de quem está olhando a tela, e
    // não estado que o servidor conheça.
    var revealed by remember(entry.id) { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    DepthSurface(
        modifier = Modifier.fillMaxWidth().testTag("$TEAM_KEYS_ROW_TAG_PREFIX${entry.id}"),
        shape = AppShapes.large,
        elevation = AppElevation.card,
        contentPadding = 12.dp
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
                for (accountKey in entry.accounts) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = accountKey,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        AppButton(
                            label = TeamKeysLabels.unlink(language),
                            onClick = { onUnclaim(entry.id, accountKey) },
                            enabled = enabled,
                            tone = AppButtonTone.GHOST
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
        "Cada chave vale para as contas que ela já usou. O rótulo é livre — use o e-mail da " +
            "pessoa — e não é verificado pelo servidor: quem prova o vínculo é a conta listada, " +
            "gravada no primeiro envio daquela chave."
    } else {
        "Each key covers the accounts it has already used. The label is free text — use the " +
            "person's e-mail — and is not verified by the server: the listed account is what " +
            "proves the link, recorded on that key's first upload."
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
}

package com.usagemonitor.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.theme.AppAccents
import com.usagemonitor.presentation.ui.theme.AppSpacing

const val CODEX_ACCOUNTS_ADD_TEST_TAG = "codexAccountsAdd"

fun codexAccountSwitchTestTag(profileId: String): String = "codexAccountSwitch:$profileId"

/**
 * O que a seção de contas Codex recebe, num objeto só: somados um a um, os cinco
 * parâmetros passavam `SettingsDialogContent` do limite de 300 linhas por função.
 */
data class CodexAccountsSettings(
    val profiles: List<CodexProfileUiModel> = emptyList(),
    /** Por que o último diretório não entrou; `null` sem aviso. */
    val error: String? = null,
    val onAdd: () -> Unit = {},
    val onToggle: (String, Boolean) -> Unit = { _, _ -> },
    val onRemove: (String) -> Unit = {}
)

/** Uma conta Codex extra na aba Contas (issue #329). */
data class CodexProfileUiModel(
    val id: String,
    val label: String,
    val path: String,
    val enabled: Boolean
)

/**
 * "Contas Codex extras", abaixo das contas Anthropic (issue #329).
 *
 * Menor que a seção da Anthropic, pelo escopo aprovado: um card por conta, sem
 * apelido editável, cor, emoji nem filtro de sessões. O texto de apoio diz de
 * onde vem a conta padrão e o que o diretório precisa ser, porque é isso que o
 * usuário não adivinha: o app só lê — quem mantém o login renovado é o Codex CLI
 * rodando com `CODEX_HOME` apontando para o diretório.
 */
@Composable
internal fun CodexAccountsSection(
    language: AppLanguage,
    settings: CodexAccountsSettings
) {
    val isPt = language == AppLanguage.PT
    val error = settings.error
    AppDataSurfaceFlush(
        header = {
            AppSectionHeader(
                title = if (isPt) "Contas Codex extras" else "Extra Codex accounts",
                trailing = {
                    AppButton(
                        label = if (isPt) "Adicionar" else "Add",
                        onClick = settings.onAdd,
                        tone = AppButtonTone.PRIMARY,
                        modifier = Modifier.testTag(CODEX_ACCOUNTS_ADD_TEST_TAG)
                    )
                }
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(AppSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            Text(
                text = if (isPt) {
                    "A conta padrão (~/.codex) já tem card. Cada conta extra é um diretório que o Codex CLI " +
                        "usa como CODEX_HOME, com auth.json e cap_sid; o app só lê, e é o CLI que mantém o login renovado."
                } else {
                    "The default account (~/.codex) already has a card. Each extra account is a directory the Codex CLI " +
                        "uses as CODEX_HOME, with auth.json and cap_sid; the app only reads it, and the CLI keeps the login fresh."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (error != null) {
                AppBanner(title = if (isPt) "Conta não adicionada" else "Account not added", description = error, tone = AppTone.CRITICAL)
            }
            settings.profiles.forEach { profile ->
                key(profile.id) {
                    CodexProfileRow(profile = profile, language = language, onToggle = settings.onToggle, onRemove = settings.onRemove)
                }
            }
        }
    }
}

@Composable
private fun CodexProfileRow(
    profile: CodexProfileUiModel,
    language: AppLanguage,
    onToggle: (String, Boolean) -> Unit,
    onRemove: (String) -> Unit
) {
    AppDataRow(showDivider = false, horizontalPadding = 0.dp) {
        AppSourceMarker(color = accentColorFor(ApiSource.CODEX, AppAccents.current), height = 28.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = profile.label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = profile.path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        AppSwitch(
            checked = profile.enabled,
            onCheckedChange = { checked -> onToggle(profile.id, checked) },
            modifier = Modifier.testTag(codexAccountSwitchTestTag(profile.id))
        )
        AppIconButton(
            contentDescription = if (language == AppLanguage.PT) "Remover ${profile.label}" else "Remove ${profile.label}",
            onClick = { onRemove(profile.id) }
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

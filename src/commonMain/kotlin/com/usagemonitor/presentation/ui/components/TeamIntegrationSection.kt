package com.usagemonitor.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.TeamIntegrationSettings

/** Resultado do botão "Testar conexão". */
enum class TeamConnectionUiStatus { IDLE, CHECKING, OK, FAILED }

data class TeamConnectionUiState(
    val status: TeamConnectionUiStatus = TeamConnectionUiStatus.IDLE,
    val message: String? = null
)

const val TEAM_SECTION_TEST_TAG = "teamIntegrationSection"
const val TEAM_ENABLE_SWITCH_TEST_TAG = "teamIntegrationEnableSwitch"
const val TEAM_TEST_CONNECTION_TEST_TAG = "teamIntegrationTestConnection"
const val TEAM_ALIAS_FIELD_TEST_TAG = "teamIntegrationAliasField"
const val TEAM_SYNC_STATUS_TEST_TAG = "teamIntegrationSyncStatus"
const val TEAM_ADMIN_SWITCH_TEST_TAG = "teamIntegrationAdminSwitch"
const val TEAM_ADMIN_VALIDATE_TEST_TAG = "teamIntegrationAdminValidate"
const val TEAM_ADMIN_KEYS_TEST_TAG = "teamIntegrationAdminKeys"
const val TEAM_ADMIN_EXIT_TEST_TAG = "teamIntegrationAdminExit"

/**
 * Seção "Integração com time" das Configurações.
 *
 * Stateless como o resto do diálogo: os valores chegam por [settings] e os
 * eventos saem pelas lambdas. Os campos usam [DebouncedTextField], então o texto
 * sobe numa pausa da digitação e não a cada tecla — ver o KDoc de lá para o
 * porquê do estado local do cursor.
 *
 * A lista de contas é a mesma dos perfis Anthropic: a máquina costuma estar
 * logada em várias ao mesmo tempo e só as marcadas aqui empurram dados ao
 * servidor e ganham o botão de time no card.
 */
@Composable
fun TeamIntegrationSection(
    settings: TeamIntegrationSettings,
    language: AppLanguage,
    profiles: List<AnthropicProfileUiModel>,
    connection: TeamConnectionUiState,
    onEnabledChange: (Boolean) -> Unit,
    onServerUrlChange: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onAliasChange: (String) -> Unit,
    onProfileParticipationChange: (String, Boolean) -> Unit,
    onTestConnection: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Aviso do envio em background, em texto já pronto.
     *
     * `null` quando não há o que dizer. A seção não conhece o `TeamSyncService` —
     * quem traduz o estado é o `Main`.
     */
    syncFailureMessage: String? = null,
    /** Resultado da última validação do token; separado do teste da chave de time. */
    adminConnection: TeamConnectionUiState = TeamConnectionUiState(),
    onAdminTokenChange: (String) -> Unit = {},
    onValidateAdminToken: () -> Unit = {},
    onOpenKeysManager: () -> Unit = {},
    onExitAdminMode: () -> Unit = {}
) {
    val isPt = language == AppLanguage.PT

    Column(
        modifier = modifier.fillMaxWidth().testTag(TEAM_SECTION_TEST_TAG),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isPt) "Integração com time" else "Team integration",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = settings.enabled,
                onCheckedChange = onEnabledChange,
                modifier = Modifier.testTag(TEAM_ENABLE_SWITCH_TEST_TAG)
            )
        }

        Text(
            text = if (isPt) {
                "Envia ao servidor da empresa apenas os totais de tokens das sessões — " +
                    "nunca o conteúdo das conversas."
            } else {
                "Sends only session token totals to your company server — never conversation content."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (!settings.enabled) {
            return@Column
        }

        DebouncedTextField(
            value = settings.serverUrl,
            label = if (isPt) "Servidor" else "Server",
            placeholder = "https://usage.empresa.com",
            onCommit = onServerUrlChange
        )

        DebouncedSecretField(
            value = settings.apiKey,
            label = if (isPt) "Chave do time" else "Team key",
            revealLabel = if (isPt) "Mostrar chave" else "Show key",
            hideLabel = if (isPt) "Ocultar chave" else "Hide key",
            onCommit = onApiKeyChange
        )

        DebouncedTextField(
            value = settings.alias,
            label = if (isPt) "Seu apelido" else "Your alias",
            placeholder = if (isPt) "como o time vai te ver" else "how the team sees you",
            onCommit = onAliasChange,
            // Apagar o apelido derrubaria `isConfigured` e pararia o laço de
            // envio sem nenhum aviso, e o servidor recusaria o ingest com 400.
            // Um apelido já gravado nunca pode virar vazio por edição.
            validate = { text ->
                if (text.isBlank() && settings.alias.isNotBlank()) {
                    if (isPt) "O apelido não pode ficar vazio." else "The alias cannot be empty."
                } else {
                    null
                }
            },
            modifier = Modifier.testTag(TEAM_ALIAS_FIELD_TEST_TAG)
        )

        Text(
            text = if (isPt) "Contas que fazem parte do time" else "Accounts in the team",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 4.dp)
        )

        if (profiles.isEmpty()) {
            Text(
                text = if (isPt) {
                    "Nenhuma conta Anthropic detectada."
                } else {
                    "No Anthropic account detected."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            profiles.forEach { profile ->
                key(profile.id) {
                    TeamProfileCheckboxRow(
                        profile = profile,
                        checked = profile.id in settings.participatingProfileIds,
                        onCheckedChange = { checked ->
                            onProfileParticipationChange(profile.id, checked)
                        }
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onTestConnection,
                // Sem servidor, chave e apelido não há o que testar.
                enabled = settings.isConfigured && connection.status != TeamConnectionUiStatus.CHECKING,
                modifier = Modifier.testTag(TEAM_TEST_CONNECTION_TEST_TAG)
            ) {
                Text(if (isPt) "Testar conexão" else "Test connection")
            }

            if (connection.status == TeamConnectionUiStatus.CHECKING) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            }

            val statusMessage = connection.message
            if (statusMessage != null) {
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (connection.status == TeamConnectionUiStatus.FAILED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // O envio roda em background e falhava calado: quem estava com a chave
        // errada só percebia pela ausência dos próprios dados na tela dos
        // colegas, sem nada que apontasse a causa.
        if (syncFailureMessage != null) {
            Text(
                text = if (isPt) {
                    "Último envio ao time falhou: $syncFailureMessage"
                } else {
                    "Last team upload failed: $syncFailureMessage"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth().testTag(TEAM_SYNC_STATUS_TEST_TAG)
            )
        }

        TeamAdminBlock(
            settings = settings,
            isPt = isPt,
            connection = adminConnection,
            onAdminTokenChange = onAdminTokenChange,
            onValidateAdminToken = onValidateAdminToken,
            onOpenKeysManager = onOpenKeysManager,
            onExitAdminMode = onExitAdminMode
        )
    }
}

/**
 * Bloco "Eu sou admin".
 *
 * Fica abaixo dos campos de participação de propósito, e não depende deles:
 * quem administra o servidor não é necessariamente integrante de nenhuma conta,
 * então basta servidor e token para o bloco funcionar — sem chave de time, sem
 * apelido e sem conta marcada.
 *
 * A marca de "validado" não é persistida: o botão de gerenciar chaves aparece
 * assim que há token gravado, e uma credencial que deixou de valer se denuncia
 * na primeira chamada, com a mensagem do servidor.
 */
@Composable
private fun TeamAdminBlock(
    settings: TeamIntegrationSettings,
    isPt: Boolean,
    connection: TeamConnectionUiState,
    onAdminTokenChange: (String) -> Unit,
    onValidateAdminToken: () -> Unit,
    onOpenKeysManager: () -> Unit,
    onExitAdminMode: () -> Unit
) {
    var adminExpanded by remember(settings.adminToken.isNotBlank()) {
        mutableStateOf(settings.adminToken.isNotBlank())
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (isPt) "Eu sou admin do servidor" else "I administer the server",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = adminExpanded,
            onCheckedChange = { checked ->
                adminExpanded = checked
                if (!checked) {
                    onExitAdminMode()
                }
            },
            modifier = Modifier.testTag(TEAM_ADMIN_SWITCH_TEST_TAG)
        )
    }

    if (!adminExpanded) {
        return
    }

    Text(
        text = if (isPt) {
            "O token de administração dá acesso de leitura a todas as contas do servidor e " +
                "permite emitir chaves. Não é preciso participar de nenhum time."
        } else {
            "The admin token grants read access to every account on the server and allows " +
                "issuing keys. You do not need to belong to any team."
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    DebouncedSecretField(
        value = settings.adminToken,
        label = if (isPt) "Token de administração" else "Admin token",
        revealLabel = if (isPt) "Mostrar token" else "Show token",
        hideLabel = if (isPt) "Ocultar token" else "Hide token",
        onCommit = onAdminTokenChange
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = onValidateAdminToken,
            enabled = settings.isAdminMode && connection.status != TeamConnectionUiStatus.CHECKING,
            modifier = Modifier.testTag(TEAM_ADMIN_VALIDATE_TEST_TAG)
        ) {
            Text(if (isPt) "Validar" else "Validate")
        }

        Button(
            onClick = onOpenKeysManager,
            enabled = settings.isAdminMode,
            modifier = Modifier.testTag(TEAM_ADMIN_KEYS_TEST_TAG)
        ) {
            Text(if (isPt) "Configurar chaves das contas" else "Manage account keys")
        }

        if (connection.status == TeamConnectionUiStatus.CHECKING) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        }
    }

    val adminMessage = connection.message
    if (adminMessage != null) {
        Text(
            text = adminMessage,
            style = MaterialTheme.typography.bodySmall,
            color = if (connection.status == TeamConnectionUiStatus.FAILED) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            }
        )
    }

    if (settings.adminToken.isNotBlank()) {
        TextButton(
            onClick = onExitAdminMode,
            modifier = Modifier.testTag(TEAM_ADMIN_EXIT_TEST_TAG)
        ) {
            Text(if (isPt) "Sair do modo admin" else "Leave admin mode")
        }
    }
}

@Composable
private fun TeamProfileCheckboxRow(
    profile: AnthropicProfileUiModel,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = profile.label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            val identity = profile.identityLabel
            if (identity != null) {
                Text(
                    text = identity,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}


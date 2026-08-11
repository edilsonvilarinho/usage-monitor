package com.usagemonitor.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
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

/**
 * Seção "Integração com time" das Configurações.
 *
 * Stateless como o resto do diálogo: os valores chegam por [settings] e os
 * eventos saem pelas lambdas. Os campos de texto guardam o cursor localmente
 * (mesmo motivo do apelido do perfil, issue #19) e propagam o texto a cada tecla.
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
    modifier: Modifier = Modifier
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

        TeamTextField(
            initialValue = settings.serverUrl,
            label = if (isPt) "Servidor" else "Server",
            placeholder = "https://usage.empresa.com",
            onValueChange = onServerUrlChange
        )

        TeamSecretField(
            initialValue = settings.apiKey,
            label = if (isPt) "Chave do time" else "Team key",
            revealLabel = if (isPt) "Mostrar chave" else "Show key",
            hideLabel = if (isPt) "Ocultar chave" else "Hide key",
            onValueChange = onApiKeyChange
        )

        TeamTextField(
            initialValue = settings.alias,
            label = if (isPt) "Seu apelido" else "Your alias",
            placeholder = if (isPt) "como o time vai te ver" else "how the team sees you",
            onValueChange = onAliasChange
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

/**
 * Campo de texto com o cursor guardado localmente.
 *
 * [initialValue] só semeia o estado na primeira composição; depois disso a
 * verdade do campo é local e o texto sobe por [onValueChange]. Controlar o campo
 * pelo valor que volta de fora atrasa o cursor em um caractere (issue #19).
 */
@Composable
private fun TeamTextField(
    initialValue: String,
    label: String,
    placeholder: String,
    onValueChange: (String) -> Unit
) {
    var fieldValue by remember {
        mutableStateOf(TextFieldValue(initialValue, TextRange(initialValue.length)))
    }
    OutlinedTextField(
        value = fieldValue,
        onValueChange = { newValue ->
            fieldValue = newValue
            onValueChange(newValue.text)
        },
        singleLine = true,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        modifier = Modifier.fillMaxWidth()
    )
}

/** Campo mascarado com alternância de visibilidade, para conferir o que foi colado. */
@Composable
private fun TeamSecretField(
    initialValue: String,
    label: String,
    revealLabel: String,
    hideLabel: String,
    onValueChange: (String) -> Unit
) {
    var fieldValue by remember {
        mutableStateOf(TextFieldValue(initialValue, TextRange(initialValue.length)))
    }
    var revealed by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = fieldValue,
        onValueChange = { newValue ->
            fieldValue = newValue
            onValueChange(newValue.text)
        },
        singleLine = true,
        label = { Text(label) },
        visualTransformation = if (revealed) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        trailingIcon = {
            IconButton(onClick = { revealed = !revealed }) {
                Icon(
                    imageVector = if (revealed) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = if (revealed) hideLabel else revealLabel
                )
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

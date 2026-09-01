package com.usagemonitor.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.theme.AppSpacing
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
const val TEAM_PROFILE_REJECTION_TAG_PREFIX = "teamIntegrationProfileRejection:"

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
    /**
     * Contas que o servidor recusou, por `profileId`, com o motivo dele.
     *
     * O aviso mora **na linha da conta**, e não junto do erro geral lá embaixo:
     * o conserto é desligar aquele interruptor, que está ali do lado. Uma
     * mensagem no rodapé dizia que algo foi recusado sem dizer qual das contas
     * marcadas era a errada.
     */
    rejectedProfiles: Map<String, String> = emptyMap(),
    /** Resultado da última validação do token; separado do teste da chave de time. */
    adminConnection: TeamConnectionUiState = TeamConnectionUiState(),
    onAdminTokenChange: (String) -> Unit = {},
    onValidateAdminToken: () -> Unit = {},
    onOpenKeysManager: () -> Unit = {},
    onExitAdminMode: () -> Unit = {}
) {
    val isPt = language == AppLanguage.PT

    // Painel com cabeçalho e divisória, como as duas seções da aba Geral: o
    // interruptor da integração inteira mora no cabeçalho, que é onde ele
    // pertence — ele liga e desliga tudo o que está abaixo dele.
    AppDataSurfaceFlush(
        modifier = modifier.fillMaxWidth().testTag(TEAM_SECTION_TEST_TAG),
        header = {
            AppSectionHeader(
                title = if (isPt) "Integração com time" else "Team integration",
                trailing = {
                    AppSwitch(
                        checked = settings.enabled,
                        onCheckedChange = onEnabledChange,
                        modifier = Modifier.testTag(TEAM_ENABLE_SWITCH_TEST_TAG)
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
                        },
                        rejection = rejectedProfiles[profile.id],
                        language = language,
                        showDivider = profile !== profiles.last()
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Sem servidor, chave e apelido não há o que testar.
            AppButton(
                label = if (isPt) "Testar conexão" else "Test connection",
                onClick = onTestConnection,
                enabled = settings.isConfigured && connection.status != TeamConnectionUiStatus.CHECKING,
                modifier = Modifier.testTag(TEAM_TEST_CONNECTION_TEST_TAG)
            )

            if (connection.status == TeamConnectionUiStatus.CHECKING) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            }

            // Ponto e palavra: a mensagem dizia o resultado só pela cor do
            // texto — vermelho para falha, azul para sucesso —, e azul neste
            // sistema é informação, não confirmação.
            val statusMessage = connection.message
            if (statusMessage != null) {
                AppStatusIndicator(
                    label = statusMessage,
                    tone = connectionTone(connection.status),
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
        AppSwitch(
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
        AppButton(
            label = if (isPt) "Validar" else "Validate",
            onClick = onValidateAdminToken,
            enabled = settings.isAdminMode && connection.status != TeamConnectionUiStatus.CHECKING,
            modifier = Modifier.testTag(TEAM_ADMIN_VALIDATE_TEST_TAG)
        )

        AppButton(
            label = if (isPt) "Configurar chaves das contas" else "Manage account keys",
            onClick = onOpenKeysManager,
            enabled = settings.isAdminMode,
            modifier = Modifier.testTag(TEAM_ADMIN_KEYS_TEST_TAG)
        )

        if (connection.status == TeamConnectionUiStatus.CHECKING) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        }
    }

    val adminMessage = connection.message
    if (adminMessage != null) {
        AppStatusIndicator(label = adminMessage, tone = connectionTone(connection.status))
    }

    if (settings.adminToken.isNotBlank()) {
        AppButton(
            label = if (isPt) "Sair do modo admin" else "Leave admin mode",
            onClick = onExitAdminMode,
            modifier = Modifier.testTag(TEAM_ADMIN_EXIT_TEST_TAG),
            tone = AppButtonTone.GHOST
        )
    }
}

@Composable
private fun TeamProfileCheckboxRow(
    profile: AnthropicProfileUiModel,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    language: AppLanguage,
    rejection: String? = null,
    showDivider: Boolean = true
) {
    // Linha de dados como as opções da aba Geral: rótulo em mono à esquerda,
    // controle à direita. O interruptor abria a linha, e a coluna de identidade
    // começava depois dele — desalinhada de tudo o que vem acima.
    AppDataRow(showDivider = showDivider, horizontalPadding = 0.dp) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = profile.label,
                style = MaterialTheme.typography.labelLarge,
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
            // Ponto e palavra, e o motivo que o próprio servidor mandou: ele
            // nomeia a chave e a conta, que é o que separa "desmarque esta
            // conta" de "peça outra chave ao administrador".
            if (rejection != null) {
                AppStatusIndicator(
                    label = if (language == AppLanguage.PT) {
                        "Recusada pelo servidor — $rejection"
                    } else {
                        "Refused by the server — $rejection"
                    },
                    tone = AppTone.CRITICAL,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .testTag("$TEAM_PROFILE_REJECTION_TAG_PREFIX${profile.id}")
                )
            }
        }
        // Interruptor e não caixa de seleção: a linha diz se a conta participa do
        // time, que é um estado ligado ou desligado — a mesma coisa que os outros
        // controles desta tela dizem, e agora com o mesmo desenho.
        AppSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}


/**
 * Tom do resultado de uma verificação de conexão.
 *
 * Enquanto checa não há veredito: neutro. Sem tentativa, também não — mas ali
 * não há mensagem, e o indicador nem chega a ser composto.
 */
private fun connectionTone(status: TeamConnectionUiStatus): AppTone {
    return when (status) {
        TeamConnectionUiStatus.OK -> AppTone.OK
        TeamConnectionUiStatus.FAILED -> AppTone.CRITICAL
        TeamConnectionUiStatus.CHECKING, TeamConnectionUiStatus.IDLE -> AppTone.NEUTRAL
    }
}

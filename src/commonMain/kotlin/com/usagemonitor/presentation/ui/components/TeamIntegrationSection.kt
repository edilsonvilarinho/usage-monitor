package com.usagemonitor.presentation.ui.components

import com.usagemonitor.domain.entity.TeamConnectionFailure
import com.usagemonitor.presentation.ui.TeamConnectionPath
import com.usagemonitor.presentation.ui.TeamCredentialKind
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
    val message: String? = null,
    /** Onde a conexão parou (#494); com ela a seção mostra o caminho em vez de [message]. */
    val failure: TeamConnectionFailure? = null,
    /** Qual credencial a última etapa testa: a chave (teste de conexão) ou o token admin. */
    val credentialKind: TeamCredentialKind = TeamCredentialKind.ACCOUNT_KEY
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
const val TEAM_LIST_ROW_TAG_PREFIX = "teamIntegrationRow:"
const val TEAM_MOVE_HINT_TAG_PREFIX = "teamIntegrationMoveHint:"
const val TEAM_ADD_TEST_TAG = "teamIntegrationAdd"
const val TEAM_EMPTY_STATE_TEST_TAG = "teamIntegrationEmpty"
const val TEAM_NAME_FIELD_TEST_TAG = "teamIntegrationNameField"
const val TEAM_REMOVE_TEST_TAG = "teamIntegrationRemove"
const val TEAM_REMOVE_CONFIRM_TEST_TAG = "teamIntegrationRemoveConfirm"
const val TEAM_ACCOUNT_KEY_TAG_PREFIX = "teamIntegrationAccountKey:"

/**
 * Seção "Integração com time" das Configurações, em mestre-detalhe (A08, direção B).
 *
 * Esquerda: a lista de times, com o status escrito por linha. Abaixo: o formulário
 * do time em [settings], que é o time aberto. Stateless como o resto do diálogo:
 * os valores chegam por parâmetro e os eventos saem pelas lambdas. Os campos usam
 * [DebouncedTextField], então o texto sobe numa pausa da digitação e não a cada tecla.
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
     * o conserto é desligar aquele interruptor, que está ali do lado.
     */
    rejectedProfiles: Map<String, String> = emptyMap(),
    /** Resultado da última validação do token; separado do teste da chave de time. */
    adminConnection: TeamConnectionUiState = TeamConnectionUiState(),
    onAdminTokenChange: (String) -> Unit = {},
    onValidateAdminToken: () -> Unit = {},
    onOpenKeysManager: () -> Unit = {},
    onExitAdminMode: () -> Unit = {},
    /** Todos os times configurados; a lista lê daqui. Por padrão, só o time de [settings]. */
    teams: List<TeamIntegrationSettings> = listOf(settings),
    /** Time com o formulário aberto (destacado na lista). */
    selectedTeamId: String? = settings.id,
    /** Resultado do último teste por time, pelo id. Sem entrada, o time mostra "Não testado". */
    teamConnections: Map<String, TeamConnectionUiState> = emptyMap(),
    onSelectTeam: (String) -> Unit = {},
    onAddTeam: () -> Unit = {},
    onRemoveTeam: (String) -> Unit = {},
    onRenameTeam: (String) -> Unit = {},
    /** Chave própria de uma conta do time; recebe o `profileId` e o valor digitado. */
    onAccountApiKeyChange: (String, String) -> Unit = { _, _ -> }
) {
    val labels = TeamIntegrationLabels(language)

    // Painel com cabeçalho e divisória, como os painéis da aba Sistema: o
    // interruptor da integração mora no cabeçalho e liga e desliga o time aberto.
    AppDataSurfaceFlush(
        modifier = modifier.fillMaxWidth().testTag(TEAM_SECTION_TEST_TAG),
        header = {
            AppSectionHeader(
                title = labels.title,
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
            text = labels.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (teams.isEmpty()) {
            TeamEmptyState(labels = labels, onAddTeam = onAddTeam)
            return@Column
        }

        TeamListPane(
            teams = teams,
            selectedTeamId = selectedTeamId,
            teamConnections = teamConnections,
            labels = labels,
            onSelectTeam = onSelectTeam,
            onAddTeam = onAddTeam
        )

        DebouncedTextField(
            value = settings.name,
            label = labels.teamNameLabel,
            placeholder = labels.teamNamePlaceholder,
            onCommit = onRenameTeam,
            validate = { text ->
                if (text.isBlank()) labels.teamNameEmpty else null
            },
            modifier = Modifier.testTag(TEAM_NAME_FIELD_TEST_TAG)
        )

        if (!settings.enabled) {
            TeamRemoveAction(settings = settings, labels = labels, onRemoveTeam = onRemoveTeam)
            return@Column
        }

        DebouncedTextField(
            value = settings.serverUrl,
            label = labels.serverLabel,
            placeholder = "https://usage.empresa.com",
            onCommit = onServerUrlChange
        )

        DebouncedSecretField(
            value = settings.apiKey,
            label = labels.teamKeyLabel,
            revealLabel = labels.showKey,
            hideLabel = labels.hideKey,
            onCommit = onApiKeyChange
        )

        DebouncedTextField(
            value = settings.alias,
            label = labels.aliasLabel,
            placeholder = labels.aliasPlaceholder,
            onCommit = onAliasChange,
            // Apagar o apelido derrubaria `isConfigured` e pararia o laço de
            // envio sem nenhum aviso, e o servidor recusaria o ingest com 400.
            // Um apelido já gravado nunca pode virar vazio por edição.
            validate = { text ->
                if (text.isBlank() && settings.alias.isNotBlank()) labels.aliasEmpty else null
            },
            modifier = Modifier.testTag(TEAM_ALIAS_FIELD_TEST_TAG)
        )

        Text(
            text = labels.accountsTitle,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 4.dp)
        )

        if (profiles.isEmpty()) {
            Text(
                text = labels.noAccounts,
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
                        labels = labels,
                        showDivider = profile !== profiles.last(),
                        movedFromTeam = otherTeamNameFor(teams, settings.id, profile.id),
                        accountKey = settings.accountApiKeys[profile.id].orEmpty(),
                        onAccountKeyChange = { value -> onAccountApiKeyChange(profile.id, value) }
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
                label = labels.testConnection,
                onClick = onTestConnection,
                enabled = settings.isConfigured && connection.status != TeamConnectionUiStatus.CHECKING,
                modifier = Modifier.testTag(TEAM_TEST_CONNECTION_TEST_TAG)
            )

            if (connection.status == TeamConnectionUiStatus.CHECKING) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            }

            // Ponto e palavra: a mensagem dizia o resultado só pela cor do texto.
            val statusMessage = connection.message
            if (statusMessage != null && connection.failure == null) {
                AppStatusIndicator(
                    label = statusMessage,
                    tone = connectionTone(connection.status),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // O caminho mora fora da linha do botão: `AppStatusIndicator` é uma linha só, e o
        // texto da exceção cortava em "failed:" (#494).
        val connectionFailure = connection.failure
        if (connectionFailure != null) {
            TeamConnectionPath(failure = connectionFailure, kind = connection.credentialKind, language = language)
        }

        // O envio roda em background e falhava calado: quem estava com a chave
        // errada só percebia pela ausência dos próprios dados na tela dos
        // colegas, sem nada que apontasse a causa.
        if (syncFailureMessage != null) {
            Text(
                text = labels.lastUploadFailed(syncFailureMessage),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth().testTag(TEAM_SYNC_STATUS_TEST_TAG)
            )
        }

        TeamAdminBlock(
            settings = settings,
            labels = labels,
            language = language,
            connection = adminConnection,
            onAdminTokenChange = onAdminTokenChange,
            onValidateAdminToken = onValidateAdminToken,
            onOpenKeysManager = onOpenKeysManager,
            onExitAdminMode = onExitAdminMode
        )

        TeamRemoveAction(settings = settings, labels = labels, onRemoveTeam = onRemoveTeam)
      }
    }
}

/**
 * Lista de times do mestre. Cada linha mostra o nome, o servidor e o status escrito;
 * a linha aberta fica destacada. O botão de adicionar mora embaixo da lista.
 */
@Composable
private fun TeamListPane(
    teams: List<TeamIntegrationSettings>,
    selectedTeamId: String?,
    teamConnections: Map<String, TeamConnectionUiState>,
    labels: TeamIntegrationLabels,
    onSelectTeam: (String) -> Unit,
    onAddTeam: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        teams.forEach { team ->
            key(team.id) {
                val status = teamStatusOf(team, teamConnections[team.id], labels)
                AppDataRow(
                    onClick = { onSelectTeam(team.id) },
                    showDivider = team !== teams.last(),
                    highlighted = team.id == selectedTeamId,
                    horizontalPadding = 0.dp,
                    modifier = Modifier.testTag("$TEAM_LIST_ROW_TAG_PREFIX${team.id}")
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = team.name,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = team.serverUrl,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    AppStatusIndicator(label = status.first, tone = status.second)
                }
            }
        }
        AppButton(
            label = labels.addTeam,
            onClick = onAddTeam,
            tone = AppButtonTone.GHOST,
            modifier = Modifier.padding(top = 4.dp).testTag(TEAM_ADD_TEST_TAG)
        )
    }
}

/** Estado sem time: diz o recorte e oferece o caminho para criar o primeiro. */
@Composable
private fun TeamEmptyState(labels: TeamIntegrationLabels, onAddTeam: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag(TEAM_EMPTY_STATE_TEST_TAG),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
    ) {
        Text(
            text = labels.emptyTitle,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = labels.emptyBody,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        AppButton(
            label = labels.addTeam,
            onClick = onAddTeam,
            modifier = Modifier.testTag(TEAM_ADD_TEST_TAG)
        )
    }
}

/**
 * Remover o time aberto. A ação só apaga a configuração local: os dados que já
 * chegaram ao servidor continuam lá, e o diálogo diz isso antes de confirmar.
 */
@Composable
private fun TeamRemoveAction(
    settings: TeamIntegrationSettings,
    labels: TeamIntegrationLabels,
    onRemoveTeam: (String) -> Unit
) {
    var confirming by remember(settings.id) { mutableStateOf(false) }

    AppButton(
        label = labels.removeTeam,
        onClick = { confirming = true },
        tone = AppButtonTone.DANGER,
        modifier = Modifier.padding(top = 4.dp).testTag(TEAM_REMOVE_TEST_TAG)
    )

    if (confirming) {
        AppConfirmationDialog(
            title = labels.removeTitle(settings.name),
            message = labels.removeMessage,
            confirmLabel = labels.removeConfirm,
            cancelLabel = labels.cancel,
            onConfirm = {
                confirming = false
                onRemoveTeam(settings.id)
            },
            onDismiss = { confirming = false },
            confirmTag = TEAM_REMOVE_CONFIRM_TEST_TAG
        )
    }
}

/**
 * Status escrito de um time na lista. A palavra é o que diz o estado; a cor só reforça.
 *
 * Sem resultado de teste para o time, o status é "Não testado" — nunca "Conectado" por
 * deduzir da configuração: conexão só existe depois de um teste que a confirme.
 */
private fun teamStatusOf(
    team: TeamIntegrationSettings,
    connection: TeamConnectionUiState?,
    labels: TeamIntegrationLabels
): Pair<String, AppTone> {
    return when {
        connection?.status == TeamConnectionUiStatus.OK -> labels.statusConnected to AppTone.OK
        connection?.status == TeamConnectionUiStatus.FAILED -> labels.statusFailed to AppTone.CRITICAL
        connection?.status == TeamConnectionUiStatus.CHECKING -> labels.statusChecking to AppTone.NEUTRAL
        !team.isConfigured -> labels.statusIncomplete to AppTone.WARNING
        else -> labels.statusNotTested to AppTone.NEUTRAL
    }
}

/** Nome do outro time que já tem a conta marcada; `null` quando nenhum tem. */
private fun otherTeamNameFor(
    teams: List<TeamIntegrationSettings>,
    selectedTeamId: String,
    profileId: String
): String? {
    return teams.firstOrNull { it.id != selectedTeamId && profileId in it.participatingProfileIds }?.name
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
    labels: TeamIntegrationLabels,
    language: AppLanguage,
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
            text = labels.adminSwitch,
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
        text = labels.adminDescription,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    DebouncedSecretField(
        value = settings.adminToken,
        label = labels.adminTokenLabel,
        revealLabel = labels.showToken,
        hideLabel = labels.hideToken,
        onCommit = onAdminTokenChange
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AppButton(
            label = labels.validate,
            onClick = onValidateAdminToken,
            enabled = settings.isAdminMode && connection.status != TeamConnectionUiStatus.CHECKING,
            modifier = Modifier.testTag(TEAM_ADMIN_VALIDATE_TEST_TAG)
        )

        AppButton(
            label = labels.manageKeys,
            onClick = onOpenKeysManager,
            enabled = settings.isAdminMode,
            modifier = Modifier.testTag(TEAM_ADMIN_KEYS_TEST_TAG)
        )

        if (connection.status == TeamConnectionUiStatus.CHECKING) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        }
    }

    val adminFailure = connection.failure
    val adminMessage = connection.message
    if (adminFailure != null) {
        TeamConnectionPath(failure = adminFailure, kind = connection.credentialKind, language = language)
    } else if (adminMessage != null) {
        AppStatusIndicator(label = adminMessage, tone = connectionTone(connection.status))
    }

    if (settings.adminToken.isNotBlank()) {
        AppButton(
            label = labels.leaveAdmin,
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
    labels: TeamIntegrationLabels,
    rejection: String? = null,
    showDivider: Boolean = true,
    /** Outro time que já tem esta conta; ligar aqui move a conta para o time aberto. */
    movedFromTeam: String? = null,
    /** Chave própria da conta no time; vazia, o servidor usa a chave do time. */
    accountKey: String = "",
    onAccountKeyChange: (String) -> Unit = {}
) {
    // Linha de dados como as opções da aba Sistema: rótulo em mono à esquerda,
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
            // Conta de outro time: o texto diz o que o interruptor faz antes de ser ligado.
            if (movedFromTeam != null && !checked) {
                Text(
                    text = labels.moveHint(movedFromTeam),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp).testTag("$TEAM_MOVE_HINT_TAG_PREFIX${profile.id}")
                )
            }
            // Ponto e palavra, e o motivo que o próprio servidor mandou: ele
            // nomeia a chave e a conta, que é o que separa "desmarque esta
            // conta" de "peça outra chave ao administrador".
            if (rejection != null) {
                AppStatusIndicator(
                    label = labels.rejected(rejection),
                    tone = AppTone.CRITICAL,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .testTag("$TEAM_PROFILE_REJECTION_TAG_PREFIX${profile.id}")
                )
            }
            // Campo só com a conta marcada: desligada, a chave própria não tem para onde ir.
            if (checked) {
                Column(modifier = Modifier.padding(top = 4.dp)) {
                    DebouncedSecretField(
                        value = accountKey,
                        label = labels.accountKeyLabel,
                        revealLabel = labels.showKey,
                        hideLabel = labels.hideKey,
                        onCommit = onAccountKeyChange,
                        placeholder = labels.accountKeyPlaceholder,
                        modifier = Modifier.testTag("$TEAM_ACCOUNT_KEY_TAG_PREFIX${profile.id}")
                    )
                }
            }
        }
        // Interruptor e não caixa de seleção: a linha diz se a conta participa do time.
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

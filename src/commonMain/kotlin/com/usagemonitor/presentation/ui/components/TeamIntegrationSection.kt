package com.usagemonitor.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.TeamConnectionFailure
import com.usagemonitor.domain.entity.TeamIntegrationSettings
import com.usagemonitor.presentation.ui.TeamCredentialKind
import com.usagemonitor.presentation.ui.theme.AppSpacing

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
 * Esquerda: a lista de times, com o status escrito por linha. Abaixo: o detalhe do time em
 * [settings], em três blocos numerados (Conexão, Contas, Administração; ver [TeamStepsPane]).
 * Stateless como o resto do diálogo: os valores chegam por parâmetro e os eventos saem pelas
 * lambdas. Os campos usam [DebouncedTextField], então o texto sobe numa pausa da digitação.
 */
@Composable
fun TeamIntegrationSection(
    settings: TeamIntegrationSettings,
    language: AppLanguage,
    profiles: List<AnthropicProfileUiModel>,
    connection: TeamConnectionUiState,
    onEnabledChange: (Boolean) -> Unit,
    onServerUrlChange: (String) -> Unit,
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

        TeamStepsPane(
            settings = settings,
            teams = teams,
            teamConnections = teamConnections,
            onRenameTeam = onRenameTeam,
            language = language,
            labels = labels,
            profiles = profiles,
            connection = connection,
            adminConnection = adminConnection,
            syncFailureMessage = syncFailureMessage,
            rejectedProfiles = rejectedProfiles,
            onServerUrlChange = onServerUrlChange,
            onAliasChange = onAliasChange,
            onProfileParticipationChange = onProfileParticipationChange,
            onAccountApiKeyChange = onAccountApiKeyChange,
            onTestConnection = onTestConnection,
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
internal fun teamStatusOf(
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

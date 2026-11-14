package com.usagemonitor.presentation.ui.components

import androidx.compose.runtime.Composable
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.TeamIntegrationSettings

/**
 * O que a seção Time lê, num objeto só (mesmo motivo de [CodexAccountsSettings]): somados
 * um a um, os parâmetros de time passavam `SettingsDialogContent` do limite de 300 linhas.
 */
data class TeamSectionState(
    val settings: TeamIntegrationSettings = TeamIntegrationSettings(),
    val connection: TeamConnectionFormState = TeamConnectionFormState(),
    val adminConnection: TeamConnectionFormState = TeamConnectionFormState(),
    /** Falha do último envio ao servidor; `null` sem falha. */
    val syncFailureMessage: String? = null,
    /** Contas recusadas pelo servidor, por `profileId`. Aponta a linha errada. */
    val rejectedProfiles: Map<String, String> = emptyMap(),
    /** Todos os times configurados; a seção lista e abre um deles. */
    val teams: List<TeamIntegrationSettings> = listOf(settings),
    val selectedTeamId: String? = settings.id,
    val teamConnections: Map<String, TeamConnectionFormState> = emptyMap()
)

/**
 * Os callbacks da seção Time. Classe comum, e não `data class`: o nome
 * `onAdminTokenChange` casa o padrão de segredo de `ArchitectureRulesTest`, que cobra
 * `toString` redigido em `data class` com esses campos.
 */
class TeamSectionActions(
    val onEnabledChange: (Boolean) -> Unit = {},
    val onServerUrlChange: (String) -> Unit = {},
    val onAliasChange: (String) -> Unit = {},
    val onProfileParticipationChange: (String, Boolean) -> Unit = { _, _ -> },
    val onTestConnection: () -> Unit = {},
    val onAdminTokenChange: (String) -> Unit = {},
    val onValidateAdminToken: () -> Unit = {},
    val onOpenKeysManager: () -> Unit = {},
    val onExitAdminMode: () -> Unit = {},
    val onSelectTeam: (String) -> Unit = {},
    val onAddTeam: () -> Unit = {},
    val onRemoveTeam: (String) -> Unit = {},
    val onRenameTeam: (String) -> Unit = {},
    /** Chave própria de uma conta do time; recebe o `profileId` e o valor digitado. */
    val onAccountApiKeyChange: (String, String) -> Unit = { _, _ -> }
)

/** Liga o estado e as ações à seção Time; `SettingsDialogContent` só repassa os dois objetos. */
@Composable
internal fun TeamSettingsSection(
    state: TeamSectionState,
    actions: TeamSectionActions,
    language: AppLanguage,
    profiles: List<AnthropicProfileUiModel>
) {
    TeamIntegrationSection(
        settings = state.settings,
        language = language,
        profiles = profiles,
        connection = state.connection,
        onEnabledChange = actions.onEnabledChange,
        onServerUrlChange = actions.onServerUrlChange,
        onAliasChange = actions.onAliasChange,
        onProfileParticipationChange = actions.onProfileParticipationChange,
        onTestConnection = actions.onTestConnection,
        syncFailureMessage = state.syncFailureMessage,
        rejectedProfiles = state.rejectedProfiles,
        adminConnection = state.adminConnection,
        onAdminTokenChange = actions.onAdminTokenChange,
        onValidateAdminToken = actions.onValidateAdminToken,
        onOpenKeysManager = actions.onOpenKeysManager,
        onExitAdminMode = actions.onExitAdminMode,
        teams = state.teams,
        selectedTeamId = state.selectedTeamId,
        teamConnections = state.teamConnections,
        onSelectTeam = actions.onSelectTeam,
        onAddTeam = actions.onAddTeam,
        onRemoveTeam = actions.onRemoveTeam,
        onRenameTeam = actions.onRenameTeam,
        onAccountApiKeyChange = actions.onAccountApiKeyChange
    )
}

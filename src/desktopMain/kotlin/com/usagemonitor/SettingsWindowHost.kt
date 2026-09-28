package com.usagemonitor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.window.DialogState
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.AppDialogWindow
import com.usagemonitor.presentation.ui.ModalWindowEnvironment
import com.usagemonitor.presentation.ui.components.CodexAccountsSettings
import com.usagemonitor.presentation.ui.components.AnthropicProfileUiModel
import com.usagemonitor.presentation.ui.components.SettingsDialogContent
import com.usagemonitor.update.AutoUpdateController
import com.usagemonitor.update.isEnabled

/**
 * A janela de Configurações. O estado vem dos donos dele — [AppShellState],
 * [AppGraph], [AppSettingsFeedback] — e cada controle chama um método de
 * [SettingsActions].
 */
@Composable
internal fun SettingsWindowHost(
    graph: AppGraph,
    viewModels: AppViewModels,
    shell: AppShellState,
    modal: AppModalState,
    feedback: AppSettingsFeedback,
    actions: SettingsActions,
    autoUpdate: AutoUpdateController,
    profileUiModels: List<AnthropicProfileUiModel>,
    state: DialogState,
    environment: ModalWindowEnvironment
) {
    val enabledApis by graph.enabledApis.collectAsState()
    val apiKeySettings by graph.apiKeySettings.collectAsState()
    val alertSettings by graph.alertSettingsFlow.collectAsState()
    val teamSettings by graph.teamSettingsFlow.collectAsState()
    val codexRecords by graph.codexProfileRegistry.profiles.collectAsState()
    val proxySettings by graph.proxySettingsFlow.collectAsState()
    val teamSyncStatus by viewModels.teamSync.syncStatus.collectAsState()
    val language = shell.language

    AppDialogWindow(
        visible = modal.isSettingsOpen,
        title = if (language == AppLanguage.PT) "Configurações" else "Settings",
        state = state,
        environment = environment,
        diagnosticName = "Configurações",
        minWidthDp = 320,
        minHeightDp = DEFAULT_MODAL_MIN_HEIGHT.value.toInt(),
        onCloseRequest = { modal.isSettingsOpen = false },
        openGeneration = modal.settingsOpenGeneration
    ) {
        SettingsDialogContent(
            currentTheme = shell.themePreset,
            currentLanguage = language,
            enabledApis = enabledApis,
            configuredApiKeys = apiKeySettings.configuredSources(),
            autoStartEnabled = shell.autoStartEnabled,
            alwaysOnTopEnabled = shell.alwaysOnTopEnabled,
            cardsOnlyMode = shell.cardsOnlyMode,
            hudMode = shell.hudMode,
            windowOpacityPercent = shell.windowOpacityPercent,
            windowOpacityEnabled = shell.windowOpacitySupported,
            uiScalePercent = shell.uiScalePercent,
            onUiScaleChange = actions::changeUiScale,
            reducedMotion = shell.reducedMotion,
            onReducedMotionChange = actions::changeReducedMotion,
            trayUsageRing = shell.trayUsageRing,
            onTrayUsageRingChange = actions::changeTrayUsageRing,
            onReportBug = actions::reportBug,
            onThemeChange = actions::changeTheme,
            onLanguageChange = actions::changeLanguage,
            onAutoStartChange = actions::changeAutoStart,
            onAlwaysOnTopChange = actions::changeAlwaysOnTop,
            onCardsOnlyModeChange = actions::changeCardsOnlyMode,
            onHudModeChange = actions::changeHudMode,
            autoUpdateEnabled = autoUpdate.isEnabled(),
            autoUpdateSupport = autoUpdate.support,
            autoUpdatePlatform = autoUpdate.platform,
            lastUpdateReceipt = autoUpdate.lastReceipt,
            autoUpdateFeedOverride = autoUpdate.feedUrlOverride,
            onAutoUpdateChange = { enabled -> autoUpdate.setEnabled(enabled) },
            onWindowOpacityChange = actions::changeWindowOpacity,
            alertSettings = alertSettings,
            onAlertSettingsChange = actions::changeAlertSettings,
            monthlyBudgetText = formatBudgetUsd(shell.monthlyBudgetMicros),
            onMonthlyBudgetCommit = actions::commitMonthlyBudget,
            onApiToggle = actions::toggleApi,
            onApiKeySave = actions::saveApiKey,
            onApiKeyRemove = actions::removeApiKey,
            apiKeyCheck = feedback.apiKeyCheck,
            onApiKeyTest = feedback::checkApiKey,
            onApiKeyCheckReset = feedback::resetApiKeyCheck,
            anthropicProfiles = profileUiModels,
            onAnthropicProfileToggle = actions::toggleAnthropicProfile,
            onAnthropicProfileRename = actions::renameAnthropicProfile,
            onAnthropicProfileColorChange = actions::changeAnthropicProfileColor,
            onAnthropicProfileEmojiChange = actions::changeAnthropicProfileEmoji,
            onAddAnthropicProfile = actions::addAnthropicProfile,
            onRemoveAnthropicProfile = actions::removeAnthropicProfile,
            onRescanAnthropicProfiles = actions::rescanAnthropicProfiles,
            expandedProfileId = modal.expandedAnthropicProfileId,
            onToggleProfileExpanded = actions::toggleProfileExpanded,
            codexAccounts = CodexAccountsSettings(
                profiles = buildCodexProfileUiModels(codexRecords),
                error = modal.codexProfileError,
                onAdd = actions::addCodexProfile,
                onToggle = actions::toggleCodexProfile,
                onRemove = actions::removeCodexProfile
            ),
            teamSettings = teamSettings,
            teamConnection = feedback.teamConnection,
            onTeamEnabledChange = actions::changeTeamEnabled,
            onTeamServerUrlChange = actions::changeTeamServerUrl,
            onTeamApiKeyChange = actions::changeTeamApiKey,
            onTeamAliasChange = actions::changeTeamAlias,
            onTeamProfileParticipationChange = actions::changeTeamProfileParticipation,
            onTeamTestConnection = feedback::checkTeamConnection,
            teamSyncFailureMessage = teamSyncStatus.takeIf { status -> status.isFailing }?.lastFailureMessage,
            teamRejectedProfiles = teamSyncStatus.rejectedProfiles,
            teamAdminConnection = feedback.teamAdminConnection,
            onTeamAdminTokenChange = actions::changeTeamAdminToken,
            onTeamValidateAdminToken = feedback::validateAdminToken,
            onTeamOpenKeysManager = actions::openTeamKeysManager,
            onTeamExitAdminMode = actions::exitTeamAdminMode,
            proxySettings = proxySettings,
            proxyConnection = feedback.proxyConnection,
            onProxyUseEnvironmentChange = actions::changeProxyUseEnvironment,
            onProxyHostChange = actions::changeProxyHost,
            onProxyPortChange = actions::changeProxyPort,
            onProxyUsernameChange = actions::changeProxyUsername,
            onProxyPasswordChange = actions::changeProxyPassword,
            onProxyTestConnection = feedback::checkProxyConnection,
            toastEvent = feedback.toastEvent
        )
    }
}

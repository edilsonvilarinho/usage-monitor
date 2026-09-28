package com.usagemonitor

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import com.usagemonitor.domain.entity.ApiSource

/**
 * Quais janelas modais estão abertas, e sobre o quê.
 *
 * A geração de cada uma sobe a cada abertura: é ela que faz `AppDialogWindow`
 * reativar uma janela já visível quando o usuário pede de novo. O assunto da
 * janela (conta, rótulo, visão global) é gravado por quem a abre, porque só quem
 * abriu sabe — o rótulo nulo de uma conta não prova que a janela é a visão global.
 */
@Stable
internal class AppModalState(bugReportOpenAtStart: Boolean) {
    var isSettingsOpen by mutableStateOf(false)
    var settingsOpenGeneration by mutableStateOf(0)
        private set
    var isHelpOpen by mutableStateOf(false)
    var isBugReportOpen by mutableStateOf(bugReportOpenAtStart)
    var isTeamKeysOpen by mutableStateOf(false)

    var historySource by mutableStateOf<ApiSource?>(null)
    var historyOpenGeneration by mutableStateOf(0)
        private set

    var isCliSessionsOpen by mutableStateOf(false)
    var cliSessionsOpenGeneration by mutableStateOf(0)
        private set
    var cliSessionsProfileLabel by mutableStateOf<String?>(null)
        private set
    var cliSessionsProfileId by mutableStateOf<String?>(null)
        private set

    var isCodexCliSessionsOpen by mutableStateOf(false)
    var codexCliSessionsOpenGeneration by mutableStateOf(0)
        private set

    var isTeamUsageOpen by mutableStateOf(false)
    var teamUsageOpenGeneration by mutableStateOf(0)
        private set
    var teamUsageAccountLabel by mutableStateOf<String?>(null)
        private set
    var teamUsageProfileId by mutableStateOf<String?>(null)
        private set
    var teamUsageIsAdminOverview by mutableStateOf(false)
        private set

    var isTeamPresenceOpen by mutableStateOf(false)
    var teamPresenceOpenGeneration by mutableStateOf(0)
        private set
    var teamPresenceAccountLabel by mutableStateOf<String?>(null)
        private set
    var teamPresenceIsAdminOverview by mutableStateOf(false)
        private set

    /** Estado efêmero do editor de conta nas Configurações; reabrir o diálogo pode colapsá-lo. */
    var expandedAnthropicProfileId by mutableStateOf<String?>(null)

    /** Por que o último diretório Codex não entrou (issue #329); some na próxima tentativa. */
    var codexProfileError by mutableStateOf<String?>(null)

    /** Alguma janela modal na tela; a troca automática para a HUD espera todas fecharem. */
    val anyOpen: Boolean
        get() = isSettingsOpen || isHelpOpen || historySource != null || isCliSessionsOpen ||
            isCodexCliSessionsOpen || isTeamUsageOpen || isTeamPresenceOpen || isTeamKeysOpen

    fun openSettings() {
        isSettingsOpen = true
        settingsOpenGeneration++
    }

    fun openHistory(source: ApiSource) {
        historySource = source
        historyOpenGeneration++
    }

    fun openCliSessions(profileId: String, profileLabel: String?) {
        cliSessionsProfileLabel = profileLabel
        cliSessionsProfileId = profileId
        isCliSessionsOpen = true
        cliSessionsOpenGeneration++
    }

    fun openCodexCliSessions() {
        isCodexCliSessionsOpen = true
        codexCliSessionsOpenGeneration++
    }

    /** [profileId] nulo com [isAdminOverview] é a visão global do administrador. */
    fun openTeamUsage(accountLabel: String?, profileId: String?, isAdminOverview: Boolean) {
        teamUsageAccountLabel = accountLabel
        teamUsageProfileId = profileId
        teamUsageIsAdminOverview = isAdminOverview
        isTeamUsageOpen = true
        teamUsageOpenGeneration++
    }

    fun openTeamPresence(accountLabel: String?, isAdminOverview: Boolean) {
        teamPresenceAccountLabel = accountLabel
        teamPresenceIsAdminOverview = isAdminOverview
        isTeamPresenceOpen = true
        teamPresenceOpenGeneration++
    }
}

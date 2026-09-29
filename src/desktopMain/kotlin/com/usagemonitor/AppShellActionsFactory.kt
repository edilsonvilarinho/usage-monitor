package com.usagemonitor

import com.usagemonitor.data.export.DefaultUsageExportEncoder
import com.usagemonitor.domain.entity.DEFAULT_ANTHROPIC_PROFILE_ID
import com.usagemonitor.domain.entity.TeamIntegrationSettings
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.presentation.ui.exportRequestForDashboard
import com.usagemonitor.presentation.viewmodel.UiState
import com.usagemonitor.presentation.viewmodel.recordFailure
import kotlin.time.Clock

/**
 * As ações do rodapé e dos cards, montadas uma vez para as duas portas — o
 * rodapé do modo padrão e a engrenagem da barra HUD.
 *
 * Cada abertura grava na trilha **o nome da tela**, nunca a conta nem o apelido:
 * o apelido é digitado pelo usuário, costuma ser o e-mail, e a trilha vira issue
 * pública.
 */
internal fun buildShellActions(
    graph: AppGraph,
    viewModels: AppViewModels,
    shell: AppShellState,
    modal: AppModalState,
    profileRecords: List<AnthropicProfileRecord>,
    profileResolution: AnthropicProfileResolution,
    dashboardState: UiState,
    teamSettings: TeamIntegrationSettings
): AppShellActions {
    val breadcrumbs = graph.breadcrumbs
    return AppShellActions(
        refreshAll = { viewModels.dashboard.refresh() },
        openSettings = {
            breadcrumbs.recordScreenOpened("Configurações")
            modal.openSettings()
        },
        openHelp = {
            breadcrumbs.recordScreenOpened("Ajuda")
            modal.isHelpOpen = true
        },
        // Retrato do Dashboard (issue #215): o mesmo writer das Sessões CLI e do
        // Time, um diálogo de arquivo só.
        exportSnapshot = { stats ->
            graph.usageExportWriter.write(exportRequestForDashboard(DefaultUsageExportEncoder, stats, Clock.System.now()))
        },
        onExportFailure = { error -> breadcrumbs.recordFailure("exportar retrato do dashboard", error) },
        // Só quem administra: a conta não entra na condição de propósito —
        // administrar o servidor não exige participar de nenhum time.
        openAdminOverview = if (teamSettings.isAdminMode) {
            {
                breadcrumbs.recordScreenOpened("visão global do time (admin)")
                modal.openTeamUsage(accountLabel = null, profileId = null, isAdminOverview = true)
                viewModels.teamUsage.openForAllAccounts()
            }
        } else {
            null
        },
        openHistory = { source, accountKey ->
            breadcrumbs.recordScreenOpened("histórico de ${source.name}")
            modal.openHistory(source)
            viewModels.history.openForSource(source, accountKey)
        },
        openCliSessions = { target ->
            breadcrumbs.recordScreenOpened("sessões CLI da máquina")
            val profileId = target.profileIdOrDefault()
            val label = profileRecords.firstOrNull { record -> record.id == profileId }?.label
            modal.openCliSessions(profileId = profileId, profileLabel = label)
            viewModels.cliSessions.openForProfile(
                profileId = profileId,
                profileLabel = label,
                quotaWindows = quotaWindowsForProfile(dashboardState, profileId)
            )
        },
        openCodexCliSessions = { _ ->
            breadcrumbs.recordScreenOpened("sessões Codex CLI")
            modal.openCodexCliSessions()
            viewModels.codexCliSessions.openWindow()
        },
        openTeamUsage = { target ->
            val profileId = target.profileIdOrDefault()
            val accountContext = profileResolution.inspections[profileId]?.accountContext
            // Sem `accountUuid` não há como agrupar as máquinas — e o botão nem
            // deveria ter aparecido. Abortar é melhor que consultar o servidor com
            // uma chave inventada.
            val accountKey = accountContext?.key?.providerAccountId
            if (accountKey != null) {
                breadcrumbs.recordScreenOpened("uso do time")
                modal.openTeamUsage(accountLabel = accountContext.displayLabel, profileId = profileId, isAdminOverview = false)
                viewModels.teamUsage.openForAccount(
                    accountKey = accountKey,
                    accountLabel = accountContext.displayLabel,
                    quotaWindows = quotaWindowsForProfile(dashboardState, profileId)
                )
                // Antecipa o envio desta máquina: sem isso a janela abriria sem o
                // que foi feito aqui desde o último tique de 30s.
                viewModels.teamSync.requestImmediateSync()
            }
        },
        openTeamPresence = { target ->
            val profileId = target.profileIdOrDefault()
            val accountContext = profileResolution.inspections[profileId]?.accountContext
            val accountKey = accountContext?.key?.providerAccountId
            if (accountKey != null) {
                breadcrumbs.recordScreenOpened("presença do time")
                modal.openTeamPresence(accountLabel = accountContext.displayLabel, isAdminOverview = false)
                viewModels.teamPresence.openForAccount(accountKey = accountKey, accountLabel = accountContext.displayLabel)
                // Antecipa a batida desta máquina: sem isso a janela abriria com o
                // próprio usuário aparecendo offline por até 30 segundos.
                viewModels.teamSync.requestImmediateSync()
            }
        },
        openTeamPresenceOverview = if (teamSettings.isAdminMode) {
            {
                breadcrumbs.recordScreenOpened("presença global do time (admin)")
                modal.openTeamPresence(accountLabel = null, isAdminOverview = true)
                viewModels.teamPresence.openForAllAccounts()
            }
        } else {
            null
        }
    )
}

private fun UsageTargetKey.profileIdOrDefault(): String = profileId ?: DEFAULT_ANTHROPIC_PROFILE_ID

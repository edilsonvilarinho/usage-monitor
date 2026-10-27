package com.usagemonitor

import com.usagemonitor.data.export.DefaultUsageExportEncoder
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.CliQuotaWindows
import com.usagemonitor.domain.entity.DEFAULT_ANTHROPIC_PROFILE_ID
import com.usagemonitor.domain.entity.TeamIntegrationSettings
import com.usagemonitor.domain.entity.UsageAccountKey
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.domain.entity.displayName
import com.usagemonitor.domain.entity.snapshotLabel
import com.usagemonitor.domain.repository.BreadcrumbRecorder
import com.usagemonitor.presentation.ui.exportRequestForDashboard
import com.usagemonitor.presentation.viewmodel.UiState
import com.usagemonitor.presentation.viewmodel.UsageExportWriter
import com.usagemonitor.presentation.viewmodel.recordFailure
import kotlin.time.Clock

/**
 * O que as ações do app pedem aos view models. Porta e não os view models
 * direto: a decisão de cada ação (o nome na trilha, a conta sem `accountUuid`
 * que não abre nada, o que é só de admin) fica testável sem montar o
 * `AppViewModels`, que sobe a coleta inteira. Quem implementa é o próprio
 * `AppViewModels`.
 */
internal interface ShellActionTargets {
    fun refreshAll()
    fun openComparison()
    fun openHistory(source: ApiSource, accountKey: UsageAccountKey?)
    fun openCliSessions(profileId: String, profileLabel: String?, quotaWindows: CliQuotaWindows)
    fun openCodexCliSessions()
    fun openTeamUsageForAccount(accountKey: String, accountLabel: String, quotaWindows: CliQuotaWindows)
    fun openTeamUsageForAllAccounts()
    fun openTeamPresenceForAccount(accountKey: String, accountLabel: String)
    fun openTeamPresenceForAllAccounts()
    fun openAnalysis(targetKey: String, accountLabel: String)
    fun requestTeamSync()
}

/**
 * As ações do rodapé e dos cards, montadas uma vez para as duas portas — o
 * rodapé do modo padrão e a engrenagem da barra HUD.
 *
 * Cada abertura grava na trilha **o nome da tela**, nunca a conta nem o apelido:
 * o apelido é digitado pelo usuário, costuma ser o e-mail, e a trilha vira issue
 * pública.
 */
internal fun buildShellActions(
    breadcrumbs: BreadcrumbRecorder,
    targets: ShellActionTargets,
    exportWriter: UsageExportWriter,
    modal: AppModalState,
    profileRecords: List<AnthropicProfileRecord>,
    profileResolution: AnthropicProfileResolution,
    dashboardState: UiState,
    teamSettings: TeamIntegrationSettings,
    clock: Clock = Clock.System
): AppShellActions {
    return AppShellActions(
        refreshAll = { targets.refreshAll() },
        openSettings = {
            breadcrumbs.recordScreenOpened("Configurações")
            modal.openSettings()
        },
        openHelp = {
            breadcrumbs.recordScreenOpened("Ajuda")
            modal.openHelp()
        },
        openComparison = {
            breadcrumbs.recordScreenOpened("Comparar modelos e APIs")
            modal.openComparison()
            targets.openComparison()
        },
        // Retrato do Dashboard (issue #215): o mesmo writer das Sessões CLI e do
        // Time, um diálogo de arquivo só.
        exportSnapshot = { stats ->
            exportWriter.write(exportRequestForDashboard(DefaultUsageExportEncoder, stats, clock.now()))
        },
        onExportFailure = { error -> breadcrumbs.recordFailure("exportar retrato do dashboard", error) },
        // Só quem administra: a conta não entra na condição de propósito —
        // administrar o servidor não exige participar de nenhum time.
        openAdminOverview = if (teamSettings.isAdminMode) {
            {
                breadcrumbs.recordScreenOpened("visão global do time (admin)")
                modal.openTeamUsage(accountLabel = null, profileId = null, isAdminOverview = true)
                targets.openTeamUsageForAllAccounts()
            }
        } else {
            null
        },
        openHistory = { source, accountKey ->
            breadcrumbs.recordScreenOpened("histórico de ${source.name}")
            modal.openHistory(source)
            targets.openHistory(source, accountKey)
        },
        openCliSessions = { target ->
            breadcrumbs.recordScreenOpened("sessões CLI da máquina")
            val profileId = target.profileIdOrDefault()
            val label = profileRecords.firstOrNull { record -> record.id == profileId }?.label
            modal.openCliSessions(profileId = profileId, profileLabel = label)
            targets.openCliSessions(
                profileId = profileId,
                profileLabel = label,
                quotaWindows = quotaWindowsForProfile(dashboardState, profileId)
            )
        },
        openCodexCliSessions = { _ ->
            breadcrumbs.recordScreenOpened("sessões Codex CLI")
            modal.openCodexCliSessions()
            targets.openCodexCliSessions()
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
                targets.openTeamUsageForAccount(
                    accountKey = accountKey,
                    accountLabel = accountContext.displayLabel,
                    quotaWindows = quotaWindowsForProfile(dashboardState, profileId)
                )
                // Antecipa o envio desta máquina: sem isso a janela abriria sem o
                // que foi feito aqui desde o último tique de 30s.
                targets.requestTeamSync()
            }
        },
        openTeamPresence = { target ->
            val profileId = target.profileIdOrDefault()
            val accountContext = profileResolution.inspections[profileId]?.accountContext
            val accountKey = accountContext?.key?.providerAccountId
            if (accountKey != null) {
                breadcrumbs.recordScreenOpened("presença do time")
                modal.openTeamPresence(accountLabel = accountContext.displayLabel, isAdminOverview = false)
                targets.openTeamPresenceForAccount(accountKey = accountKey, accountLabel = accountContext.displayLabel)
                // Antecipa a batida desta máquina: sem isso a janela abriria com o
                // próprio usuário aparecendo offline por até 30 segundos.
                targets.requestTeamSync()
            }
        },
        openAnalysis = { target ->
            breadcrumbs.recordScreenOpened("análise da sessão de ${target.source.name}")
            // O rótulo é o do retrato (fonte e apelido), o mesmo do bot; nunca o e-mail cru.
            val stats = (dashboardState as? UiState.Success)?.data?.firstOrNull { item -> item.targetKey == target }
            modal.openAnalysis()
            targets.openAnalysis(target.storageKey, stats?.snapshotLabel ?: target.source.displayName())
        },
        openTeamPresenceOverview = if (teamSettings.isAdminMode) {
            {
                breadcrumbs.recordScreenOpened("presença global do time (admin)")
                modal.openTeamPresence(accountLabel = null, isAdminOverview = true)
                targets.openTeamPresenceForAllAccounts()
            }
        } else {
            null
        }
    )
}

private fun UsageTargetKey.profileIdOrDefault(): String = profileId ?: DEFAULT_ANTHROPIC_PROFILE_ID

package com.usagemonitor

import com.usagemonitor.domain.entity.TeamIntegrationSettings
import com.usagemonitor.domain.entity.TeamIntegrations

/** Opção do seletor de time da janela de uso do time (A07). */
internal data class TeamSelectorOption(val id: String, val name: String)

/** O que a troca de time faz com a janela de uso. */
internal sealed interface TeamAccountSwitch {
    data class Account(val accountKey: String, val accountLabel: String?) : TeamAccountSwitch
    data object AllAccounts : TeamAccountSwitch
    data object None : TeamAccountSwitch
}

/** Opções do seletor: só times ativos, na ordem do agregado. */
internal fun teamSelectorOptions(integrations: TeamIntegrations): List<TeamSelectorOption> =
    integrations.activeTeams.map { TeamSelectorOption(id = it.id, name = it.name) }

/** O seletor só aparece quando há escolha real (mais de um time ativo). */
internal fun teamSelectorVisible(options: List<TeamSelectorOption>): Boolean = options.size > 1

/** Time que o card abre: o que tem a conta como participante. */
internal fun dashboardTeamIdFor(profileId: String?, integrations: TeamIntegrations): String? =
    integrations.teamFor(profileId)?.id

/**
 * Primeira conta do time, na ordem dos alvos. Sem conta, só o admin abre a visão
 * de todas as contas; quem não administra não tem o que mostrar.
 */
internal fun teamAccountSwitchFor(team: TeamIntegrationSettings, targets: List<TeamSyncTarget>): TeamAccountSwitch {
    val target = targets.firstOrNull { team.participates(it.profileId) }
    if (target != null) {
        return TeamAccountSwitch.Account(target.accountKey, target.accountEmail)
    }
    return if (team.isAdminMode) TeamAccountSwitch.AllAccounts else TeamAccountSwitch.None
}

/**
 * Time selecionado que sobrevive: some ou desligado cai para o primeiro ativo.
 * Diferente de `resolveTeamId`, que só checa existência e deixa passar time desligado.
 */
internal fun resolveDashboardTeamId(selected: String?, integrations: TeamIntegrations): String? {
    if (selected != null && integrations.team(selected)?.isActive == true) {
        return selected
    }
    return integrations.activeTeams.firstOrNull()?.id
}

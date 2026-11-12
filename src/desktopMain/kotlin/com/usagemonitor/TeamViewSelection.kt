package com.usagemonitor

import com.usagemonitor.domain.entity.TeamIntegrations
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Times escolhidos nas duas vistas de time (A06). Um id `null`, ou que saiu do agregado, cai no
 * primeiro time ativo; sem time ativo a resolução é `null` e o roteamento responde "não configurado".
 */
class TeamViewSelection(private val integrations: () -> TeamIntegrations) {

    /** Time do painel: uso, presença e overview admin. */
    val dashboardTeamId = MutableStateFlow<String?>(null)

    /** Time aberto nas Configurações (admin de chaves); escrito por `SettingsActions.selectTeam`. */
    val settingsTeamId = MutableStateFlow<String?>(null)

    fun dashboardTeam(): String? = resolveDashboardTeamId(dashboardTeamId.value, integrations())

    // O admin de chaves não exige time ativo (`isAdminMode` independe da chave): a queda é o primeiro time,
    // o mesmo que as Configurações abrem, e não o primeiro ativo.
    fun settingsTeam(): String? {
        val current = integrations()
        val selected = settingsTeamId.value
        if (selected != null && current.team(selected) != null) {
            return selected
        }
        return current.teams.firstOrNull()?.id
    }
}

/** Função pura: o id escolhido se ainda existe no agregado; senão o primeiro time ativo; senão `null`. */
internal fun resolveTeamId(selected: String?, integrations: TeamIntegrations): String? {
    if (selected != null && integrations.team(selected) != null) {
        return selected
    }
    return integrations.activeTeams.firstOrNull()?.id
}

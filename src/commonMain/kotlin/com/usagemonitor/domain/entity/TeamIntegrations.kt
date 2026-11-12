package com.usagemonitor.domain.entity

/**
 * Agregado de integrações com servidores de time, guardado pelo app.
 *
 * O [deviceId] e o [enabled] são da instalação, não do time: cada time exposto em [teams]
 * carrega os dois valores do agregado. Não há pausa por time.
 *
 * Os times ficam em ordem de inserção, que é a ordem total e estável das listas publicadas.
 * Cada conta participa de no máximo um time (ver [withParticipation]).
 */
class TeamIntegrations(
    val deviceId: String = "",
    val enabled: Boolean = false,
    teams: List<TeamIntegrationSettings> = emptyList()
) {
    /** Times com o `deviceId` e o `enabled` do agregado, na ordem recebida. */
    val teams: List<TeamIntegrationSettings> = teams.map { it.copy(deviceId = deviceId, enabled = enabled) }

    /** Time pelo id, ou `null` quando não existe. */
    fun team(id: String): TeamIntegrationSettings? = teams.firstOrNull { it.id == id }

    /** Time em que a conta participa, ou `null` quando nenhum. */
    fun teamFor(profileId: String?): TeamIntegrationSettings? = teams.firstOrNull { it.participates(profileId) }

    /** Substitui o time de mesmo `id` mantendo a posição; sem `id` correspondente, acrescenta no fim. */
    fun upsert(team: TeamIntegrationSettings): TeamIntegrations {
        val index = teams.indexOfFirst { it.id == team.id }
        val updated = teams.toMutableList()
        if (index < 0) {
            updated.add(team)
        } else {
            updated[index] = team
        }
        return TeamIntegrations(deviceId, enabled, updated)
    }

    /** Remove o time pelo id. */
    fun remove(id: String): TeamIntegrations = TeamIntegrations(deviceId, enabled, teams.filterNot { it.id == id })

    /** Põe ou tira a conta de um time; `true` tira a conta de todos os outros. Time inexistente: agregado inalterado. */
    fun withParticipation(teamId: String, profileId: String, participating: Boolean): TeamIntegrations {
        if (teams.none { it.id == teamId }) {
            return this
        }
        val updated = teams.map { adjustParticipation(it, teamId, profileId, participating) }
        return TeamIntegrations(deviceId, enabled, updated)
    }

    /** Times ligados e configurados, na ordem de [teams]. */
    val activeTeams: List<TeamIntegrationSettings>
        get() = teams.filter { it.isActive }

    /** Times com token de administração, na ordem de [teams]. */
    val adminTeams: List<TeamIntegrationSettings>
        get() = teams.filter { it.isAdminMode }

    /** Segredos saem pelo `toString` dos times, que já os redigem. */
    override fun toString(): String = "TeamIntegrations(deviceId=$deviceId, enabled=$enabled, teams=$teams)"

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is TeamIntegrations) {
            return false
        }
        return deviceId == other.deviceId && enabled == other.enabled && teams == other.teams
    }

    override fun hashCode(): Int = (deviceId.hashCode() * 31 + enabled.hashCode()) * 31 + teams.hashCode()

    /** Só o time alvo recebe ou perde a conta; nos outros, `true` a tira. */
    private fun adjustParticipation(
        team: TeamIntegrationSettings,
        teamId: String,
        profileId: String,
        participating: Boolean
    ): TeamIntegrationSettings {
        val profiles = if (team.id == teamId) {
            if (participating) team.participatingProfileIds + profileId else team.participatingProfileIds - profileId
        } else if (participating) {
            team.participatingProfileIds - profileId
        } else {
            team.participatingProfileIds
        }
        return team.copy(participatingProfileIds = profiles)
    }
    companion object {
        /** Id do time legado: formato plano e primeiro time de instalacao nova. Constante unica. */
        const val LEGACY_TEAM_ID = "team-legacy"
    }
}

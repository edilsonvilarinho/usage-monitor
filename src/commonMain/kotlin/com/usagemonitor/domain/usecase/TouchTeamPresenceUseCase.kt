package com.usagemonitor.domain.usecase

import com.usagemonitor.domain.entity.TeamMemberIdentity
import com.usagemonitor.domain.entity.TeamPresenceReceipt
import com.usagemonitor.domain.repository.TeamUsageRepository

/**
 * Sinaliza que o app está aberto nesta máquina, para uma conta.
 *
 * Sem curto-circuito por lote vazio, ao contrário de [PushTeamUsageUseCase]: o
 * ponto do heartbeat é justamente sair mesmo quando não há nada novo a enviar,
 * que é o estado normal de quem já terminou o backfill.
 */
class TouchTeamPresenceUseCase(
    private val repository: TeamUsageRepository
) {
    /** [profileId] escolhe a chave da conta; `null` usa a do time. */
    suspend operator fun invoke(
        accountKey: String,
        member: TeamMemberIdentity,
        profileId: String? = null
    ): Result<TeamPresenceReceipt> {
        return repository.touchPresence(accountKey = accountKey, member = member, profileId = profileId)
    }

    /** [profileId] escolhe a chave da conta; `null` usa a do time. */
    suspend operator fun invoke(
        accountKey: String,
        accountEmail: String?,
        member: TeamMemberIdentity,
        profileId: String? = null
    ): Result<TeamPresenceReceipt> {
        return repository.touchPresence(
            accountKey = accountKey,
            accountEmail = accountEmail,
            member = member,
            profileId = profileId
        )
    }
}

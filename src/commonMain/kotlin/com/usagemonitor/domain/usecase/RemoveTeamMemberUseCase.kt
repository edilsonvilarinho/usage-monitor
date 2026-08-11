package com.usagemonitor.domain.usecase

import com.usagemonitor.domain.repository.TeamUsageRepository

/**
 * Remove um integrante do time no servidor.
 *
 * Operação destrutiva e irreversível: apaga os turnos e as sessões daquele
 * `deviceId` junto com o membro, e a máquina de origem não os reenvia — o
 * marcador local dela já considera tudo enviado. Serve para desfazer a duplicata
 * que aparece quando uma instalação volta com outro `deviceId`.
 */
class RemoveTeamMemberUseCase(
    private val repository: TeamUsageRepository
) {
    suspend operator fun invoke(accountKey: String, deviceId: String): Result<Unit> {
        return repository.removeMember(accountKey = accountKey, deviceId = deviceId)
    }
}

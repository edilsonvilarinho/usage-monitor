package com.usagemonitor.domain.usecase

import com.usagemonitor.domain.entity.TeamIngestPayload
import com.usagemonitor.domain.entity.TeamIngestReceipt
import com.usagemonitor.domain.repository.TeamUsageRepository

/**
 * Envia um lote de turnos ao servidor de time.
 *
 * Um lote sem turnos não gera requisição: o laço de sincronização roda a cada 30
 * segundos e, na maior parte das passadas, não há nada novo para enviar.
 */
class PushTeamUsageUseCase(
    private val repository: TeamUsageRepository
) {
    suspend operator fun invoke(payload: TeamIngestPayload): Result<TeamIngestReceipt> {
        if (payload.isEmpty) {
            return Result.success(TeamIngestReceipt())
        }
        return repository.push(payload)
    }
}

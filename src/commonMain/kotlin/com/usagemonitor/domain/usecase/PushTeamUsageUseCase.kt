package com.usagemonitor.domain.usecase

import com.usagemonitor.domain.entity.TeamIngestPayload
import com.usagemonitor.domain.entity.TeamIngestReceipt
import com.usagemonitor.domain.repository.TeamUsageRepository

/**
 * Envia um lote de turnos ao servidor de time.
 *
 * Um lote sem turnos não gera requisição: o laço de sincronização roda a cada 30
 * segundos e, na maior parte das passadas, não há nada novo para enviar.
 *
 * [force] existe para o envio de identidade: o servidor grava o apelido no mesmo
 * `POST /v1/ingest`, então trocar o apelido sem atividade nova precisa de uma
 * requisição com `turns` vazio. Sem isso o nome só chegaria ao time de carona no
 * próximo lote de turnos — que pode nunca vir.
 */
class PushTeamUsageUseCase(
    private val repository: TeamUsageRepository
) {
    suspend operator fun invoke(
        payload: TeamIngestPayload,
        force: Boolean = false
    ): Result<TeamIngestReceipt> {
        if (payload.isEmpty && !force) {
            return Result.success(TeamIngestReceipt())
        }
        return repository.push(payload)
    }
}

package com.usagemonitor.domain.repository

import com.usagemonitor.domain.entity.TeamIngestPayload
import com.usagemonitor.domain.entity.TeamIngestReceipt
import com.usagemonitor.domain.entity.TeamUsageSnapshot

/**
 * Contrato de acesso ao servidor de time.
 *
 * O servidor é self-hosted pela empresa: nenhuma chamada aqui fala com um
 * serviço nosso. Toda operação é escopada por `accountKey` — o `accountUuid` da
 * conta Anthropic, que é o que define quem pertence ao mesmo time.
 */
interface TeamUsageRepository {

    /** Envia um lote de turnos. Idempotente: reenviar o mesmo lote é inofensivo. */
    suspend fun push(payload: TeamIngestPayload): Result<TeamIngestReceipt>

    /**
     * Lê o consumo do time de uma conta.
     *
     * [cutoffMillis] recorta os **turnos**, não as sessões: uma sessão antiga com
     * atividade recente aparece com os números da janela. `null` devolve tudo o
     * que sobreviveu à retenção do servidor.
     */
    suspend fun fetch(accountKey: String, cutoffMillis: Long? = null): Result<TeamUsageSnapshot>

    /**
     * Apaga um integrante e tudo o que ele enviou.
     *
     * Existe para desfazer duplicata — uma instalação que perdeu a configuração
     * volta com outro `deviceId` e o antigo fica na lista sem atividade. É
     * **irreversível**: aquela máquina já marcou os turnos como enviados e não
     * os reenvia.
     */
    suspend fun removeMember(accountKey: String, deviceId: String): Result<Unit>

    /** Valida URL e chave sem gravar nada, para o botão "Testar conexão". */
    suspend fun checkConnection(): Result<Unit>
}

package com.usagemonitor.domain.repository

import com.usagemonitor.domain.entity.CliSessionDetail
import com.usagemonitor.domain.entity.TeamIngestPayload
import com.usagemonitor.domain.entity.TeamIngestReceipt
import com.usagemonitor.domain.entity.TeamMemberIdentity
import com.usagemonitor.domain.entity.TeamPresenceReceipt
import com.usagemonitor.domain.entity.TeamTrendRow
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
     * Carimba "esta máquina está com o app aberto" para uma conta.
     *
     * Não envia consumo: só o membro. É o que separa "o app está rodando" de
     * "houve turno novo" — sem esta batida o servidor só sabe da máquina quando
     * ela tem dado a enviar, e quem terminou o backfill fica indistinguível de
     * quem desligou o computador.
     *
     * Contra um servidor anterior à rota de presença o `404` **não** vira falha:
     * a chamada cai num ingest só com o membro, que faz o mesmo upsert e carimba
     * o mesmo campo. Nem todo time atualiza servidor e app juntos. Falha de rede
     * ou credencial continua vindo como `Result.failure`.
     *
     * Recebe `(accountKey, member)` e não um [TeamIngestPayload] de propósito: um
     * payload permitiria passar turnos que o caminho primário descartaria em
     * silêncio.
     */
    suspend fun touchPresence(
        accountKey: String,
        member: TeamMemberIdentity
    ): Result<TeamPresenceReceipt>

    /**
     * Lê o consumo do time de uma conta.
     *
     * [cutoffMillis] recorta os **turnos**, não as sessões: uma sessão antiga com
     * atividade recente aparece com os números da janela. `null` devolve tudo o
     * que sobreviveu à retenção do servidor.
     */
    suspend fun fetch(accountKey: String, cutoffMillis: Long? = null): Result<TeamUsageSnapshot>

    /**
     * Serie diaria dos ultimos [days] dias, para a tendencia do time.
     *
     * Devolve as linhas cruas por `(maquina, dia UTC, modelo)`: quem precifica e
     * quem traduz o dia para o fuso local e o cliente, do mesmo jeito que faz
     * com [fetch].
     *
     * Contra um servidor anterior a rota o `404` **nao** vira falha: a leitura
     * devolve `null`, que a tela mostra como "tendencia indisponivel". Nem todo
     * time atualiza servidor e app juntos. Falha de rede ou credencial continua
     * vindo como `Result.failure`.
     */
    suspend fun fetchTrend(accountKey: String, days: Int): Result<TeamUsageTrendData?>

    /**
     * Detalhe de uma sessão de outra máquina, com os turnos crus.
     *
     * Sem recorte temporal, ao contrário de [fetch]: o detalhe é sempre a sessão
     * inteira, como no modal da própria máquina.
     *
     * `null` significa **sem detalhe disponível** — sessão desconhecida ou
     * servidor anterior à rota. Os dois casos são o mesmo para quem chama: não há
     * turno a mostrar, e a tela cai no agregado que a lista já tem. Falha de rede
     * ou credencial continua vindo como `Result.failure`.
     */
    suspend fun fetchSessionDetail(
        accountKey: String,
        deviceId: String,
        sessionId: String
    ): Result<CliSessionDetail?>

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

/** Linhas cruas da tendencia com os integrantes que as nomeiam. */
data class TeamUsageTrendData(
    val members: List<TeamMemberIdentity> = emptyList(),
    val rows: List<TeamTrendRow> = emptyList()
)

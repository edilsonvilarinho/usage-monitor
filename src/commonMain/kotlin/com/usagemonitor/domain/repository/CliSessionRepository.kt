package com.usagemonitor.domain.repository

import com.usagemonitor.domain.entity.CliSessionDetail
import com.usagemonitor.domain.entity.CliSessionIndexReport
import com.usagemonitor.domain.entity.CliSessionSummary
import com.usagemonitor.domain.entity.CliSessionTail
import com.usagemonitor.domain.entity.CliHourlyUsageRow
import com.usagemonitor.domain.entity.CliToolUsage
import com.usagemonitor.domain.entity.CliUsageBreakdown

/**
 * Contrato de acesso às sessões do Claude Code.
 *
 * A fonte é o transcript local do próprio usuário — não há servidor nem hook.
 * Por isso não existe operação de escrita: apagar ou marcar significaria mexer
 * no histórico do CLI. O índice só é lido e reconstruído a partir do disco.
 */
interface CliSessionRepository {

    /** Sincroniza o índice com os transcripts do disco. */
    suspend fun syncIndex(): Result<CliSessionIndexReport>

    /**
     * Lista as sessões indexadas, da mais recente para a mais antiga.
     * [profileId] nulo devolve todas as contas.
     *
     * [sinceEpochMillis] recorta os **turnos**: cada sessão é reagregada apenas
     * com o que aconteceu a partir desse instante. `null` devolve os agregados
     * históricos completos.
     */
    suspend fun getSessions(
        profileId: String? = null,
        sinceEpochMillis: Long? = null
    ): Result<List<CliSessionSummary>>

    /** Detalhe de uma sessão com os turnos. */
    suspend fun getSessionDetail(sessionId: String): Result<CliSessionDetail?>

    /**
     * Consumo da janela recortado por projeto, branch e modelo.
     *
     * Parte das mesmas linhas de turno que [getSessions] usa na leitura janelada,
     * então os totais dos dois batem. `sinceEpochMillis` zero abrange tudo.
     */
    suspend fun getUsageBreakdown(
        profileId: String? = null,
        sinceEpochMillis: Long = 0L
    ): Result<CliUsageBreakdown>

    /**
     * Turnos somados por hora cheia (UTC) e modelo.
     *
     * Cru de propósito: a hora local depende do fuso da apresentação, e o
     * repositório não conhece esse fuso.
     */
    suspend fun getHourlyUsage(
        profileId: String? = null,
        sinceEpochMillis: Long = 0L
    ): Result<List<CliHourlyUsageRow>>

    /**
     * Ferramentas chamadas na janela.
     *
     * Separado do resumo por eixo porque ferramenta não tem custo próprio: o
     * turno gastou tokens uma vez, mesmo tendo chamado duas.
     */
    suspend fun getToolUsage(
        profileId: String? = null,
        sinceEpochMillis: Long = 0L
    ): Result<List<CliToolUsage>>

    /**
     * O que a cauda do transcript diz sobre o último pedido de cada sessão.
     *
     * Fato bruto, sem veredito: quem decide o que é uma sessão sem resposta é
     * `detectStalledSessions`, que conhece o relógio e o limiar. Sai daqui e não
     * de [getSessions] porque a resposta não está no índice — está no `.jsonl`.
     */
    suspend fun getSessionTails(sessionIds: Collection<String>): Result<List<CliSessionTail>>
}

package com.usagemonitor.data.datasource

import com.usagemonitor.domain.entity.CliSessionActiveTime
import com.usagemonitor.domain.entity.CliSessionDetail
import com.usagemonitor.domain.entity.CliSessionTail
import com.usagemonitor.domain.entity.CliSessionIndexReport
import com.usagemonitor.domain.entity.CliSessionSummary
import com.usagemonitor.domain.entity.CliHourlyUsageRow
import com.usagemonitor.domain.entity.CliToolUsage
import com.usagemonitor.domain.entity.CliUsageGroupRow
import com.usagemonitor.domain.entity.TURN_GAP_CUTOFF_MILLIS

/**
 * Índice local das sessões do Claude Code.
 *
 * A implementação lê os transcripts do disco e mantém agregados num índice
 * SQLite; nenhuma chamada de rede está envolvida.
 */
interface CliSessionDataSource {

    /** Indexa incrementalmente os `.jsonl` novos ou alterados de todas as contas. */
    suspend fun syncIndex(): CliSessionIndexReport

    /**
     * Sessões do índice, da mais recente para a mais antiga.
     * [profileId] nulo devolve todas as contas.
     *
     * [sinceEpochMillis] reagrega cada sessão somando só os turnos a partir
     * desse instante; `null` devolve os agregados gravados na sessão.
     */
    suspend fun readSessions(
        profileId: String? = null,
        sinceEpochMillis: Long? = null
    ): List<CliSessionSummary>

    /** Sessão com os turnos, ou `null` se o identificador não estiver no índice. */
    suspend fun readSession(sessionId: String): CliSessionDetail?

    /**
     * Linhas de turnos agrupadas por `(sessão, projeto, branch, modelo)` a partir
     * de [sinceEpochMillis], para o resumo por eixo.
     *
     * Devolve as linhas cruas, sem precificar: quem aplica a tabela de preços é o
     * domain, no mesmo caminho que a lista de sessões usa.
     */
    suspend fun readUsageGroups(
        profileId: String? = null,
        sinceEpochMillis: Long = 0L
    ): List<CliUsageGroupRow>

    /**
     * Turnos somados por hora cheia (UTC) e modelo, para a grade de atividade.
     *
     * A conversão para hora local fica no domain: o SQLite agrupa em UTC e a
     * grade sairia deslocada.
     */
    suspend fun readHourlyUsage(
        profileId: String? = null,
        sinceEpochMillis: Long = 0L
    ): List<CliHourlyUsageRow>

    /** Ferramentas chamadas nos turnos da janela, da mais chamada para a menos. */
    suspend fun readToolUsage(
        profileId: String? = null,
        sinceEpochMillis: Long = 0L
    ): List<CliToolUsage>

    /**
     * Tempo de trabalho de cada sessão na janela, pela definição de
     * `activeTimeMillisOf`: soma dos intervalos entre turnos consecutivos da
     * thread principal menores que [gapCutoffMillis].
     *
     * O corte chega como parâmetro e **não** é literal no SQL: a constante mora
     * no domain, e a consulta apenas a aplica. Sessão sem intervalo dentro da
     * janela não aparece na lista.
     */
    suspend fun readSessionActiveTimes(
        profileId: String? = null,
        sinceEpochMillis: Long = 0L,
        gapCutoffMillis: Long = TURN_GAP_CUTOFF_MILLIS
    ): List<CliSessionActiveTime>

    /**
     * O que a cauda do transcript de cada sessão diz sobre o último pedido.
     *
     * Não sai do índice: `cli_turns` só guarda turno do assistente, e o que separa
     * "sessão encerrada" de "sessão sem resposta" é o marcador de fim de turno que
     * o CLI escreve no `.jsonl`. Devolve o fato bruto — quem decide se é uma
     * sessão travada é `detectStalledSessions`, que conhece o relógio e o limiar.
     *
     * Sessão desconhecida, arquivo ausente ou cauda sem marcador saem como
     * [com.usagemonitor.domain.entity.CliSessionTailOutcome.NOT_EVALUATED].
     */
    suspend fun readSessionTails(sessionIds: Collection<String>): List<CliSessionTail>
}

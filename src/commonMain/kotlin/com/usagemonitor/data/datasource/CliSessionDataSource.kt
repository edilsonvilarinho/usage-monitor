package com.usagemonitor.data.datasource

import com.usagemonitor.domain.entity.CliSessionDetail
import com.usagemonitor.domain.entity.CliSessionIndexReport
import com.usagemonitor.domain.entity.CliSessionSummary

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
     */
    suspend fun readSessions(
        profileId: String? = null,
        includeHidden: Boolean = false
    ): List<CliSessionSummary>

    /** Sessão com os turnos, ou `null` se o identificador não estiver no índice. */
    suspend fun readSession(sessionId: String): CliSessionDetail?

    /** Marca a sessão como oculta na lista. O transcript no disco não é tocado. */
    suspend fun setHidden(sessionId: String, hidden: Boolean)
}

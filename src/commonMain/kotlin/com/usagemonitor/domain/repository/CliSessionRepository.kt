package com.usagemonitor.domain.repository

import com.usagemonitor.domain.entity.CliSessionDetail
import com.usagemonitor.domain.entity.CliSessionIndexReport
import com.usagemonitor.domain.entity.CliSessionSummary

/**
 * Contrato de acesso às sessões do Claude Code.
 *
 * A fonte é o transcript local do próprio usuário — não há servidor nem hook.
 * Por isso não existe operação de exclusão: apagar significaria destruir o
 * histórico do CLI. O que existe é ocultar da lista.
 */
interface CliSessionRepository {

    /** Sincroniza o índice com os transcripts do disco. */
    suspend fun syncIndex(): Result<CliSessionIndexReport>

    /**
     * Lista as sessões indexadas, da mais recente para a mais antiga.
     * [profileId] nulo devolve todas as contas.
     */
    suspend fun getSessions(
        profileId: String? = null,
        includeHidden: Boolean = false
    ): Result<List<CliSessionSummary>>

    /** Detalhe de uma sessão com os turnos. */
    suspend fun getSessionDetail(sessionId: String): Result<CliSessionDetail?>

    /** Oculta ou reexibe a sessão. O `.jsonl` permanece intacto no disco. */
    suspend fun setSessionHidden(sessionId: String, hidden: Boolean): Result<Unit>
}

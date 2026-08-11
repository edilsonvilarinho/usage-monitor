package com.usagemonitor.domain.repository

import com.usagemonitor.domain.entity.CliSessionDetail
import com.usagemonitor.domain.entity.CliSessionIndexReport
import com.usagemonitor.domain.entity.CliSessionSummary

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
}

package com.usagemonitor.domain.usecase

import com.usagemonitor.domain.entity.CliSessionIndexReport
import com.usagemonitor.domain.entity.CliSessionSummary
import com.usagemonitor.domain.repository.CliSessionRepository

/**
 * Sincroniza o índice e devolve a lista de sessões.
 *
 * A sincronização vem primeiro porque a lista sem ela mostraria dados velhos;
 * se ela falhar, ainda assim tentamos ler o que já está indexado — um erro de
 * leitura de um transcript não pode esvaziar a tela.
 */
class GetCliSessionsUseCase(
    private val repository: CliSessionRepository
) {
    suspend operator fun invoke(
        profileId: String? = null,
        includeHidden: Boolean = false
    ): Result<CliSessionListResult> {
        val indexResult = repository.syncIndex()

        return repository.getSessions(profileId, includeHidden).map { sessions ->
            CliSessionListResult(
                sessions = sessions,
                indexReport = indexResult.getOrNull(),
                indexError = indexResult.exceptionOrNull()
            )
        }
    }
}

data class CliSessionListResult(
    val sessions: List<CliSessionSummary>,
    val indexReport: CliSessionIndexReport? = null,
    /** Falha na indexação que não impediu a leitura do índice existente. */
    val indexError: Throwable? = null
)

package com.usagemonitor.domain.usecase

import com.usagemonitor.domain.entity.CliQuotaWindows
import com.usagemonitor.domain.entity.CliRangeWindow
import com.usagemonitor.domain.entity.CliSessionIndexReport
import com.usagemonitor.domain.entity.CliSessionRange
import com.usagemonitor.domain.entity.CliSessionSummary
import com.usagemonitor.domain.repository.CliSessionRepository
import kotlinx.datetime.Clock

/**
 * Sincroniza o índice e devolve a lista de sessões da janela pedida.
 *
 * A sincronização vem primeiro porque a lista sem ela mostraria dados velhos;
 * se ela falhar, ainda assim tentamos ler o que já está indexado — um erro de
 * leitura de um transcript não pode esvaziar a tela.
 *
 * O [clock] é injetável para que o corte da janela seja testável sem congelar o
 * relógio do sistema.
 */
class GetCliSessionsUseCase(
    private val repository: CliSessionRepository,
    private val clock: Clock = Clock.System
) {
    suspend operator fun invoke(
        profileId: String? = null,
        range: CliSessionRange = CliSessionRange.DEFAULT,
        windows: CliQuotaWindows = CliQuotaWindows()
    ): Result<CliSessionListResult> {
        val indexResult = repository.syncIndex()
        val window = range.resolve(clock.now(), windows)

        return repository.getSessions(profileId, window.cutoffMillis).map { sessions ->
            CliSessionListResult(
                sessions = sessions,
                window = window,
                indexReport = indexResult.getOrNull(),
                indexError = indexResult.exceptionOrNull()
            )
        }
    }
}

data class CliSessionListResult(
    val sessions: List<CliSessionSummary>,
    /** Janela efetivamente aplicada, para a UI rotular sem recalcular. */
    val window: CliRangeWindow = CliRangeWindow(),
    val indexReport: CliSessionIndexReport? = null,
    /** Falha na indexação que não impediu a leitura do índice existente. */
    val indexError: Throwable? = null
)

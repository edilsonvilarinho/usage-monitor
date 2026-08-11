package com.usagemonitor.domain.usecase

import com.usagemonitor.domain.entity.CliQuotaWindows
import com.usagemonitor.domain.entity.CliRangeWindow
import com.usagemonitor.domain.entity.CliSessionRange
import com.usagemonitor.domain.entity.TeamUsageSnapshot
import com.usagemonitor.domain.repository.TeamUsageRepository
import kotlinx.datetime.Clock

/**
 * Consumo do time de uma conta na janela pedida.
 *
 * A resolução da janela é a mesma do modal local ([CliSessionRange.resolve]),
 * inclusive a âncora da janela de 5h no reset de quota da conta: os dois modais
 * têm de concordar sobre o que "últimas 5h" significa, senão os números do time
 * não fecham com os da máquina.
 *
 * O [clock] é injetável para tornar o corte testável sem congelar o relógio.
 */
class GetTeamUsageUseCase(
    private val repository: TeamUsageRepository,
    private val clock: Clock = Clock.System
) {
    suspend operator fun invoke(
        accountKey: String,
        range: CliSessionRange = CliSessionRange.DEFAULT,
        windows: CliQuotaWindows = CliQuotaWindows()
    ): Result<TeamUsageResult> {
        val window = range.resolve(clock.now(), windows)

        return repository.fetch(accountKey, window.cutoffMillis).map { snapshot ->
            TeamUsageResult(snapshot = snapshot, window = window)
        }
    }
}

data class TeamUsageResult(
    val snapshot: TeamUsageSnapshot,
    /** Janela efetivamente aplicada, para a UI rotular sem recalcular. */
    val window: CliRangeWindow = CliRangeWindow()
)

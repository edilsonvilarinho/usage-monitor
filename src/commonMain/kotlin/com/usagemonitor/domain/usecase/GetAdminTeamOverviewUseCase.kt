package com.usagemonitor.domain.usecase

import com.usagemonitor.domain.entity.CliQuotaWindows
import com.usagemonitor.domain.entity.CliRangeWindow
import com.usagemonitor.domain.entity.CliSessionRange
import com.usagemonitor.domain.entity.TeamAccountUsage
import com.usagemonitor.domain.repository.TeamAdminRepository
import kotlinx.datetime.Clock

/**
 * Consumo de **todas** as contas do servidor, para o painel do administrador.
 *
 * A janela é resolvida como em [GetTeamUsageUseCase], com uma diferença que a
 * tela precisa deixar clara: aqui não há âncora de reset. A janela de 5h do
 * modal por conta começa no reset de quota **daquela** conta, e contas
 * diferentes resetam em horas diferentes — ancorar numa delas daria um número
 * que não corresponde a nenhuma. Por isso a resolução usa [CliQuotaWindows]
 * vazio e o recorte de 5h fica deslizante.
 */
class GetAdminTeamOverviewUseCase(
    private val repository: TeamAdminRepository,
    private val clock: Clock = Clock.System
) {
    suspend operator fun invoke(
        range: CliSessionRange = CliSessionRange.DEFAULT
    ): Result<AdminTeamOverviewResult> {
        val window = range.resolve(clock.now(), CliQuotaWindows())

        return repository.fetchOverview(window.cutoffMillis).map { accounts ->
            AdminTeamOverviewResult(accounts = accounts, window = window)
        }
    }
}

data class AdminTeamOverviewResult(
    val accounts: List<TeamAccountUsage> = emptyList(),
    /** Janela efetivamente aplicada, para a UI rotular sem recalcular. */
    val window: CliRangeWindow = CliRangeWindow()
)

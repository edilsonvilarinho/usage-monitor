package com.usagemonitor.domain.usecase

import com.usagemonitor.domain.entity.CliQuotaWindows
import com.usagemonitor.domain.entity.CliSessionRange
import com.usagemonitor.domain.entity.CliUsageBreakdown
import com.usagemonitor.domain.repository.CliSessionRepository
import kotlinx.datetime.Clock

/**
 * Consumo da janela recortado por projeto, branch e modelo.
 *
 * Não sincroniza o índice: quem abre o resumo já veio da lista de sessões, que
 * sincroniza a cada carga e a cada tique do laço ao vivo. Uma segunda varredura
 * aqui só duplicaria trabalho de disco para chegar ao mesmo índice.
 *
 * A janela é resolvida pelo mesmo [CliSessionRange.resolve] da lista — inclusive
 * a âncora no reset de quota — para que os dois números descrevam o mesmo
 * recorte. `null` no corte vira zero: o repositório abrange tudo a partir daí.
 */
class GetCliUsageBreakdownUseCase(
    private val repository: CliSessionRepository,
    private val clock: Clock = Clock.System
) {
    suspend operator fun invoke(
        profileId: String? = null,
        range: CliSessionRange = CliSessionRange.DEFAULT,
        windows: CliQuotaWindows = CliQuotaWindows()
    ): Result<CliUsageBreakdown> {
        val window = range.resolve(clock.now(), windows)
        return repository.getUsageBreakdown(profileId, window.cutoffMillis ?: 0L)
    }
}

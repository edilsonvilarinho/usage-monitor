package com.usagemonitor.domain.usecase

import com.usagemonitor.domain.entity.ACTIVITY_TIME_ZONE_ID
import com.usagemonitor.domain.entity.MonthlyBudgetStatus
import com.usagemonitor.domain.entity.monthlyBudgetStatusOf
import com.usagemonitor.domain.entity.startOfMonthMillis
import com.usagemonitor.domain.repository.CliSessionRepository
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone

/**
 * Gasto do mês corrente contra o teto configurado.
 *
 * O recorte é o mês do **fuso da apresentação**, não UTC: um gasto às 22h do dia
 * 31 cairia no mês seguinte em UTC e sumiria do fechamento.
 *
 * Independe do filtro de janela da tela de propósito — orçamento é mensal, e
 * amarrá-lo ao chip de 5h daria um número sem significado.
 */
class GetMonthlyBudgetStatusUseCase(
    private val repository: CliSessionRepository,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.of(ACTIVITY_TIME_ZONE_ID)
) {
    suspend operator fun invoke(
        profileId: String? = null,
        limitMicros: Long
    ): Result<MonthlyBudgetStatus?> {
        if (limitMicros <= 0L) {
            return Result.success(null)
        }

        val now = clock.now()
        return repository.getUsageBreakdown(profileId, startOfMonthMillis(now, timeZone)).map { breakdown ->
            monthlyBudgetStatusOf(
                limitMicros = limitMicros,
                totals = breakdown.totals,
                now = now,
                timeZone = timeZone
            )
        }
    }
}

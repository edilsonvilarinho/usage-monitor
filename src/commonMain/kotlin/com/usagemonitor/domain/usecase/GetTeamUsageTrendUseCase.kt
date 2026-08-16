package com.usagemonitor.domain.usecase

import com.usagemonitor.domain.entity.ACTIVITY_TIME_ZONE_ID
import com.usagemonitor.domain.entity.TeamUsageTrend
import com.usagemonitor.domain.entity.buildTeamUsageTrend
import com.usagemonitor.domain.repository.TeamUsageRepository
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

/** Janela padrão da tendência. Um mês cobre a leitura típica sem inchar. */
const val DEFAULT_TEAM_TREND_DAYS = 30

/**
 * Tendência diária do time nos últimos [days] dias.
 *
 * Devolve `null` quando o servidor não conhece a rota — a tela mostra isso como
 * recurso indisponível, não como erro. Nem todo time atualiza servidor e app
 * juntos, e essa janela é real.
 *
 * O eixo de dias é montado aqui, e não a partir das linhas: uma série que só
 * tem os dias com atividade desenharia uma linha que pula dias e sugeriria
 * continuidade onde houve silêncio.
 */
class GetTeamUsageTrendUseCase(
    private val repository: TeamUsageRepository,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.of(ACTIVITY_TIME_ZONE_ID)
) {
    suspend operator fun invoke(
        accountKey: String,
        days: Int = DEFAULT_TEAM_TREND_DAYS
    ): Result<TeamUsageTrend?> {
        return repository.fetchTrend(accountKey, days).map { data ->
            if (data == null) {
                return@map null
            }
            buildTeamUsageTrend(
                rows = data.rows,
                members = data.members,
                days = dayAxis(days),
                timeZone = timeZone
            )
        }
    }

    /** Do mais antigo para o mais recente, incluindo hoje. */
    private fun dayAxis(days: Int): List<LocalDate> {
        val today = clock.now().toLocalDateTime(timeZone).date
        return (days - 1 downTo 0).map { offset -> today.minus(offset, DateTimeUnit.DAY) }
    }
}

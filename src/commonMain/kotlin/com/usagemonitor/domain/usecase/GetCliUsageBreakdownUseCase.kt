package com.usagemonitor.domain.usecase

import com.usagemonitor.domain.entity.ACTIVITY_TIME_ZONE_ID
import com.usagemonitor.domain.entity.CliQuotaWindows
import com.usagemonitor.domain.entity.CliSessionRange
import com.usagemonitor.domain.entity.CliUsageBreakdown
import com.usagemonitor.domain.entity.burnRateOf
import com.usagemonitor.domain.entity.toActivityHeatmap
import com.usagemonitor.domain.repository.CliSessionRepository
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone

/**
 * Consumo da janela recortado por projeto, branch e modelo, com a grade de
 * atividade por hora e o ritmo de queima.
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
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.of(ACTIVITY_TIME_ZONE_ID)
) {
    suspend operator fun invoke(
        profileId: String? = null,
        range: CliSessionRange = CliSessionRange.DEFAULT,
        windows: CliQuotaWindows = CliQuotaWindows()
    ): Result<CliUsageBreakdown> {
        val now = clock.now()
        val window = range.resolve(now, windows)
        val cutoffMillis = window.cutoffMillis ?: 0L

        val breakdown = repository.getUsageBreakdown(profileId, cutoffMillis).getOrElse { error ->
            return Result.failure(error)
        }

        // A grade é acessória: uma falha nela não pode derrubar o resumo inteiro,
        // que é a informação principal da aba.
        val heatmap = repository.getHourlyUsage(profileId, cutoffMillis)
            .map { rows -> rows.toActivityHeatmap(timeZone) }
            .getOrNull()

        // Mesmo tratamento da grade: acessória, e uma falha nela não derruba o
        // resumo. Instalação com índice anterior à tabela de ferramentas devolve
        // lista vazia até a reindexação passar.
        val tools = repository.getToolUsage(profileId, cutoffMillis).getOrNull()

        val burnRate = burnRateOf(
            totals = breakdown.totals,
            windowStart = window.cutoffMillis?.let { millis -> Instant.fromEpochMilliseconds(millis) },
            now = now,
            windowEndsAt = window.endsAt
        )

        return Result.success(
            breakdown.copy(
                byTool = tools ?: breakdown.byTool,
                heatmap = heatmap ?: breakdown.heatmap,
                burnRate = burnRate
            )
        )
    }
}

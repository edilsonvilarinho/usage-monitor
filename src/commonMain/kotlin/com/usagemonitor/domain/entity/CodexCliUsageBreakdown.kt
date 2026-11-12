package com.usagemonitor.domain.entity

import kotlinx.datetime.TimeZone

private const val HOUR_MILLIS = 3_600_000L

/**
 * Dobra os turnos do Codex do recorte nos eixos do resumo: projeto, modelo, integrante e heatmap.
 *
 * Sem tarifa: o Codex não tem `ModelPricingTable`, então o custo fica zero e cada turno conta como
 * não precificado. A tela mostra "Respostas" no lugar de custo (decisão do plano #560).
 *
 * Integrante vem de `threadSource`: `user` é "principal", `subagent` é "subagent"; qualquer outro valor
 * ou ausência fica com rótulo `null` ("índice não conhece o valor"), sem chutar o dono.
 *
 * Hora de atividade fica nula: o Codex não entra no ritmo por sessão nesta etapa.
 */
fun Iterable<CodexCliSessionTurn>.toCodexUsageBreakdown(
    timeZone: TimeZone = TimeZone.of(ACTIVITY_TIME_ZONE_ID)
): CliUsageBreakdown {
    val turns = toList()
    return CliUsageBreakdown(
        byProject = turns.bucketsBy { turn -> projectNameFromCwd(turn.cwd) },
        byModel = turns.bucketsBy { turn -> turn.model?.takeIf { model -> model.isNotBlank() } },
        byMember = turns.bucketsBy { turn -> memberLabelOf(turn.threadSource) },
        totals = turns.toCodexBucket(label = null),
        heatmap = turns.toCodexHeatmap(timeZone)
    )
}

private fun memberLabelOf(threadSource: String?): String? {
    return when (threadSource) {
        "user" -> "principal"
        "subagent" -> "subagent"
        else -> null
    }
}

private fun List<CodexCliSessionTurn>.bucketsBy(keyOf: (CodexCliSessionTurn) -> String?): List<CliUsageBucket> {
    return groupBy(keyOf)
        .map { (label, group) -> group.toCodexBucket(label) }
        .rankedByCost()
}

private fun List<CodexCliSessionTurn>.toCodexBucket(label: String?): CliUsageBucket {
    val turnCount = size
    return CliUsageBucket(
        label = label,
        turnCount = turnCount,
        sessionCount = map { turn -> turn.sessionId }.distinct().size,
        inputTokens = sumOf { turn -> turn.usage.billableInputTokens },
        outputTokens = sumOf { turn -> turn.usage.outputTokens },
        cacheReadTokens = sumOf { turn -> turn.usage.cachedInputTokens },
        cacheWrite5mTokens = sumOf { turn -> turn.usage.cacheWriteInputTokens },
        costMicros = 0L,
        unpricedTurnCount = turnCount
    )
}

/** Um turno vira uma linha horária sem modelo: sem modelo não há tarifa, então a célula fica sem custo. */
private fun List<CodexCliSessionTurn>.toCodexHeatmap(timeZone: TimeZone): CliActivityHeatmap {
    return map { turn ->
        CliHourlyUsageRow(
            hourStartMillis = hourStartMillisOf(turn.ts.toEpochMilliseconds()),
            model = null,
            turnCount = 1,
            inputTokens = turn.usage.billableInputTokens,
            outputTokens = turn.usage.outputTokens,
            cacheReadTokens = turn.usage.cachedInputTokens,
            cacheWrite5mTokens = turn.usage.cacheWriteInputTokens,
            cacheWrite1hTokens = 0L,
            longPrompt = null
        )
    }.toActivityHeatmap(timeZone)
}

private fun hourStartMillisOf(epochMillis: Long): Long {
    val remainder = ((epochMillis % HOUR_MILLIS) + HOUR_MILLIS) % HOUR_MILLIS
    return epochMillis - remainder
}

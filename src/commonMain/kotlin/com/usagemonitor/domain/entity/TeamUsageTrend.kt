package com.usagemonitor.domain.entity

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Um dia de consumo de uma máquina, ainda em UTC e por modelo. */
data class TeamTrendRow(
    val deviceId: String,
    val dayStartMillis: Long,
    val model: String? = null,
    val turnCount: Int = 0,
    val inputTokens: Long = 0L,
    val outputTokens: Long = 0L,
    val cacheReadTokens: Long = 0L,
    val cacheWrite5mTokens: Long = 0L,
    val cacheWrite1hTokens: Long = 0L
) {
    val totalTokens: Long
        get() = inputTokens + outputTokens + cacheReadTokens + cacheWrite5mTokens + cacheWrite1hTokens
}

/** Consumo de um dia, já precificado. */
data class TeamTrendPoint(
    val date: LocalDate,
    val costMicros: Long = 0L,
    val totalTokens: Long = 0L,
    val turnCount: Int = 0,
    /** Turnos cujo modelo não está na tabela de preços: o custo do dia é piso. */
    val unpricedTurnCount: Int = 0
) {
    val isCostComplete: Boolean
        get() = unpricedTurnCount == 0
}

/** Série de um integrante do time ao longo dos dias. */
data class TeamMemberTrend(
    val deviceId: String,
    val alias: String,
    val hostName: String? = null,
    val points: List<TeamTrendPoint> = emptyList()
) {
    val totalCostMicros: Long
        get() = points.sumOf { point -> point.costMicros }

    val hasActivity: Boolean
        get() = points.any { point -> point.turnCount > 0 }
}

/**
 * Tendência da conta, por integrante e por dia.
 *
 * Existe separada de `TeamUsageSnapshot` porque descreve outra coisa: o snapshot
 * é uma janela deslizante do agora, esta é a evolução ao longo de dias. Fundir
 * as duas obrigaria o snapshot a carregar histórico que a tela dele não usa.
 */
data class TeamUsageTrend(
    val members: List<TeamMemberTrend> = emptyList(),
    /** Dias cobertos, do mais antigo para o mais recente, sem buracos. */
    val days: List<LocalDate> = emptyList()
) {
    val isEmpty: Boolean
        get() = members.none { member -> member.hasActivity }

    val totalCostMicros: Long
        get() = members.sumOf { member -> member.totalCostMicros }

    /** Maior custo diário de qualquer integrante; escala do gráfico. */
    val peakDailyCostMicros: Long
        get() = members
            .flatMap { member -> member.points }
            .maxOfOrNull { point -> point.costMicros }
            ?: 0L
}

/**
 * Monta a tendência a partir das linhas cruas do servidor.
 *
 * A precificação é do cliente, com [ModelPricingTable] — o servidor devolve
 * tokens e não calcula custo, de propósito, para os dois modais não divergirem.
 *
 * A conversão do dia para o fuso local acontece aqui: o servidor agrupa em UTC
 * porque não conhece o fuso de quem consulta, e agrupar num fuso arbitrário
 * daria um gráfico deslocado para metade do time.
 *
 * Todo integrante ganha um ponto em **todo** dia da janela, mesmo zerado: uma
 * série com buracos desenharia uma linha que pula dias e sugeriria continuidade
 * onde houve silêncio.
 */
fun buildTeamUsageTrend(
    rows: List<TeamTrendRow>,
    members: List<TeamMemberIdentity>,
    days: List<LocalDate>,
    timeZone: TimeZone
): TeamUsageTrend {
    val byDevice = mutableMapOf<String, MutableMap<LocalDate, MutableTrendPoint>>()

    for (row in rows) {
        val date = Instant.fromEpochMilliseconds(row.dayStartMillis).toLocalDateTime(timeZone).date
        val point = byDevice
            .getOrPut(row.deviceId) { mutableMapOf() }
            .getOrPut(date) { MutableTrendPoint() }

        point.turnCount += row.turnCount
        point.totalTokens += row.totalTokens

        val pricing = ModelPricingTable.forModel(row.model)
        if (pricing == null) {
            point.unpricedTurnCount += row.turnCount
            continue
        }
        point.costMicros += pricing.costMicros(
            inputTokens = row.inputTokens,
            outputTokens = row.outputTokens,
            cacheReadTokens = row.cacheReadTokens,
            cacheWrite5mTokens = row.cacheWrite5mTokens,
            cacheWrite1hTokens = row.cacheWrite1hTokens
        )
    }

    // Máquina que aparece nos turnos mas não na lista de integrantes ainda
    // aparece: apagá-la esconderia consumo real por causa de um cadastro
    // incompleto.
    val knownDevices = members.map { member -> member.deviceId }
    val extraDevices = byDevice.keys.filter { deviceId -> deviceId !in knownDevices }

    val memberTrends = (members.map { member -> member.deviceId to member.alias } +
        extraDevices.map { deviceId -> deviceId to deviceId })
        .map { (deviceId, alias) ->
            val points = byDevice[deviceId].orEmpty()
            TeamMemberTrend(
                deviceId = deviceId,
                alias = alias,
                hostName = members.firstOrNull { member -> member.deviceId == deviceId }?.hostName,
                points = days.map { date ->
                    points[date]?.toPoint(date) ?: TeamTrendPoint(date = date)
                }
            )
        }
        // Ordem total e determinística: duas leituras iguais têm de produzir
        // listas iguais, ou o `StateFlow` reemite e a tela recompõe sozinha.
        .sortedWith(
            compareByDescending<TeamMemberTrend> { member -> member.totalCostMicros }
                .thenBy { member -> member.alias }
                .thenBy { member -> member.deviceId }
        )

    return TeamUsageTrend(members = memberTrends, days = days)
}

private class MutableTrendPoint {
    var costMicros = 0L
    var totalTokens = 0L
    var turnCount = 0
    var unpricedTurnCount = 0

    fun toPoint(date: LocalDate): TeamTrendPoint {
        return TeamTrendPoint(
            date = date,
            costMicros = costMicros,
            totalTokens = totalTokens,
            turnCount = turnCount,
            unpricedTurnCount = unpricedTurnCount
        )
    }
}

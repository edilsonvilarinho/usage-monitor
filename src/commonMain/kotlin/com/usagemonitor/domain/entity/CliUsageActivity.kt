package com.usagemonitor.domain.entity

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Uma hora cheia de atividade, ainda em UTC e por modelo. */
data class CliHourlyUsageRow(
    /** Início da hora em epoch millis, sempre múltiplo de uma hora. */
    val hourStartMillis: Long = 0L,
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

/** Uma célula da grade dia da semana × hora local. */
data class CliActivityCell(
    val dayOfWeek: DayOfWeek,
    val hour: Int,
    val turnCount: Int = 0,
    val totalTokens: Long = 0L,
    val costMicros: Long = 0L
)

/**
 * Atividade da janela distribuída em dia da semana × hora local.
 *
 * A grade só existe para revelar padrão — em que horas a janela de 5h costuma
 * apertar. Por isso a intensidade é do custo, não da contagem de turnos: dois
 * turnos de Opus pesam mais que vinte de Haiku.
 */
data class CliActivityHeatmap(
    val cells: List<CliActivityCell> = emptyList(),
    val timeZoneId: String = ACTIVITY_TIME_ZONE_ID
) {
    private val byKey: Map<Pair<DayOfWeek, Int>, CliActivityCell> =
        cells.associateBy { cell -> cell.dayOfWeek to cell.hour }

    val isEmpty: Boolean
        get() = cells.isEmpty()

    /** Maior custo entre as células; é a referência da intensidade da grade. */
    val peakCostMicros: Long
        get() = cells.maxOfOrNull { cell -> cell.costMicros } ?: 0L

    fun cellAt(dayOfWeek: DayOfWeek, hour: Int): CliActivityCell? {
        return byKey[dayOfWeek to hour]
    }

    /**
     * Intensidade de 0 a 1 para pintar a célula.
     *
     * Relativa ao pico da própria janela, e não a um valor absoluto: um dia
     * tranquilo e uma semana inteira produziriam grades igualmente escuras se a
     * escala fosse fixa, e o padrão — que é o que a grade existe para mostrar —
     * sumiria nos dois casos.
     */
    fun intensityAt(dayOfWeek: DayOfWeek, hour: Int): Float {
        val peak = peakCostMicros
        if (peak <= 0L) {
            return 0f
        }
        val cell = cellAt(dayOfWeek, hour) ?: return 0f
        return (cell.costMicros.toDouble() / peak.toDouble()).toFloat().coerceIn(0f, 1f)
    }
}

/** Mesmo fuso que o resto da apresentação usa para reset de quota. */
const val ACTIVITY_TIME_ZONE_ID = "America/Sao_Paulo"

/**
 * Distribui as horas em dia da semana × hora **local**.
 *
 * A conversão acontece aqui, e não em SQL: o SQLite agruparia em UTC e a grade
 * sairia deslocada em três horas, que é o bastante para trocar a madrugada pela
 * noite anterior.
 */
fun Iterable<CliHourlyUsageRow>.toActivityHeatmap(
    timeZone: TimeZone = TimeZone.of(ACTIVITY_TIME_ZONE_ID)
): CliActivityHeatmap {
    val accumulators = linkedMapOf<Pair<DayOfWeek, Int>, MutableActivityCell>()

    for (row in this) {
        val local = Instant.fromEpochMilliseconds(row.hourStartMillis).toLocalDateTime(timeZone)
        val key = local.dayOfWeek to local.hour
        val cell = accumulators.getOrPut(key) { MutableActivityCell(local.dayOfWeek, local.hour) }

        cell.turnCount += row.turnCount
        cell.totalTokens += row.totalTokens
        // Sem tarifa não entra custo; a célula continua contando turnos, que é o
        // que ela ainda sabe com certeza.
        val pricing = ModelPricingTable.forModel(row.model)
        if (pricing != null) {
            cell.costMicros += pricing.costMicros(
                inputTokens = row.inputTokens,
                outputTokens = row.outputTokens,
                cacheReadTokens = row.cacheReadTokens,
                cacheWrite5mTokens = row.cacheWrite5mTokens,
                cacheWrite1hTokens = row.cacheWrite1hTokens
            )
        }
    }

    // Ordem determinística: duas leituras iguais têm de produzir listas iguais,
    // ou o `StateFlow` reemite e a tela recompõe a cada tique do laço ao vivo.
    val cells = accumulators.values
        .map { cell -> cell.toCell() }
        .sortedWith(compareBy({ cell -> cell.dayOfWeek.ordinal }, { cell -> cell.hour }))

    return CliActivityHeatmap(cells = cells, timeZoneId = timeZone.id)
}

private class MutableActivityCell(val dayOfWeek: DayOfWeek, val hour: Int) {
    var turnCount = 0
    var totalTokens = 0L
    var costMicros = 0L

    fun toCell(): CliActivityCell {
        return CliActivityCell(
            dayOfWeek = dayOfWeek,
            hour = hour,
            turnCount = turnCount,
            totalTokens = totalTokens,
            costMicros = costMicros
        )
    }
}

/**
 * Ritmo de consumo dentro da janela corrente.
 *
 * Mede tokens e dinheiro **reais** dos turnos, e não percentual de quota sobre
 * snapshots — que é o que `UsageHistorySeries.averageDisplayConsumptionPerHour`
 * já faz. São grandezas diferentes e a tela precisa rotulá-las como tal.
 */
data class CliBurnRate(
    val elapsedMillis: Long,
    val tokensPerHour: Double,
    val costMicrosPerHour: Long,
    /** Fim da janela de quota, quando conhecido. */
    val windowEndsAt: Instant? = null,
    /**
     * Custo projetado até o fim da janela, mantido o ritmo.
     *
     * `null` sem `resets_at` conhecido: projetar contra um fim inventado daria
     * um número que parece informação e não é.
     */
    val projectedCostMicros: Long? = null
)

private const val MILLIS_PER_HOUR = 3_600_000.0

/**
 * Ritmo a partir dos totais já agregados da janela.
 *
 * O denominador é o **tempo decorrido desde o início da janela**, não a duração
 * nominal dela: usar cinco horas fixas subestimaria o ritmo em toda a primeira
 * hora, justamente quando o aviso ainda serviria para alguma coisa.
 *
 * `null` quando não há janela ([windowStart] nulo, o caso do filtro "Total") ou
 * quando ainda não passou tempo suficiente para uma média significar algo.
 */
fun burnRateOf(
    totals: CliUsageBucket,
    windowStart: Instant?,
    now: Instant,
    windowEndsAt: Instant? = null
): CliBurnRate? {
    if (windowStart == null) {
        return null
    }
    val elapsedMillis = now.toEpochMilliseconds() - windowStart.toEpochMilliseconds()
    if (elapsedMillis < MIN_BURN_RATE_ELAPSED_MILLIS) {
        return null
    }

    val elapsedHours = elapsedMillis / MILLIS_PER_HOUR
    val tokensPerHour = totals.totalTokens / elapsedHours
    val costMicrosPerHour = (totals.costMicros / elapsedHours).toLong()

    val projected = windowEndsAt?.let { endsAt ->
        val remainingMillis = (endsAt.toEpochMilliseconds() - now.toEpochMilliseconds()).coerceAtLeast(0L)
        totals.costMicros + (costMicrosPerHour * (remainingMillis / MILLIS_PER_HOUR)).toLong()
    }

    return CliBurnRate(
        elapsedMillis = elapsedMillis,
        tokensPerHour = tokensPerHour,
        costMicrosPerHour = costMicrosPerHour,
        windowEndsAt = windowEndsAt,
        projectedCostMicros = projected
    )
}

/**
 * Piso do tempo decorrido para calcular ritmo.
 *
 * Um minuto de janela com um turno caro daria "US$ 60/h", um número verdadeiro
 * na aritmética e falso como previsão.
 */
const val MIN_BURN_RATE_ELAPSED_MILLIS = 5L * 60 * 1_000

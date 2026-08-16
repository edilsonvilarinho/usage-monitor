package com.usagemonitor.domain.entity

/**
 * Uma linha do índice já agrupada por `(sessão, projeto, branch, modelo)`.
 *
 * É a mesma forma que `SELECT_SESSIONS_SINCE_SQL` produz para a lista de
 * sessões, de propósito: os dois consumidores partem das mesmas linhas, então os
 * totais do resumo e os do cabeçalho da lista não podem divergir.
 */
data class CliUsageGroupRow(
    /** Sessão de origem; uma sessão gera uma linha por modelo que ela usou. */
    val sessionId: String? = null,
    val cwd: String? = null,
    val gitBranch: String? = null,
    val model: String? = null,
    val turnCount: Int = 0,
    val inputTokens: Long = 0L,
    val outputTokens: Long = 0L,
    val cacheReadTokens: Long = 0L,
    val cacheWrite5mTokens: Long = 0L,
    val cacheWrite1hTokens: Long = 0L
)

/** Consumo somado de um recorte — um projeto, um branch, um modelo ou o total. */
data class CliUsageBucket(
    /** Rótulo do recorte; `null` quando o índice não conhece o valor. */
    val label: String? = null,
    val turnCount: Int = 0,
    val sessionCount: Int = 0,
    val inputTokens: Long = 0L,
    val outputTokens: Long = 0L,
    val cacheReadTokens: Long = 0L,
    val cacheWrite5mTokens: Long = 0L,
    val cacheWrite1hTokens: Long = 0L,
    val costMicros: Long = 0L,
    val cacheSavingsMicros: Long = 0L,
    /** Turnos cujo modelo não está na tabela de preços: o custo é parcial. */
    val unpricedTurnCount: Int = 0
) {
    val cacheWriteTokens: Long
        get() = cacheWrite5mTokens + cacheWrite1hTokens

    val totalTokens: Long
        get() = inputTokens + outputTokens + cacheReadTokens + cacheWriteTokens

    /** `cacheRead / (cacheRead + cacheWrite)`. Zero sem atividade de cache. */
    val cacheHitRate: Double
        get() {
            val denominator = cacheReadTokens + cacheWriteTokens
            if (denominator <= 0L) {
                return 0.0
            }
            return cacheReadTokens.toDouble() / denominator.toDouble()
        }

    val isCostComplete: Boolean
        get() = unpricedTurnCount == 0

    /** Fatia deste balde no custo de [total]; zero quando o total é zero. */
    fun costShareOf(total: CliUsageBucket): Double {
        if (total.costMicros <= 0L) {
            return 0.0
        }
        return costMicros.toDouble() / total.costMicros.toDouble()
    }
}

/**
 * Consumo do índice CLI recortado de três maneiras, mais o total.
 *
 * As três listas descrevem os **mesmos** turnos por eixos diferentes; somar
 * baldes de listas diferentes contaria o mesmo gasto duas vezes.
 */
data class CliUsageBreakdown(
    val byProject: List<CliUsageBucket> = emptyList(),
    val byBranch: List<CliUsageBucket> = emptyList(),
    val byModel: List<CliUsageBucket> = emptyList(),
    val totals: CliUsageBucket = CliUsageBucket(),
    /** Mesma janela vista por hora local; vazio quando não foi lida. */
    val heatmap: CliActivityHeatmap = CliActivityHeatmap(),
    /** Ritmo dentro da janela; `null` sem janela ou sem tempo decorrido bastante. */
    val burnRate: CliBurnRate? = null
) {
    val isEmpty: Boolean
        get() = totals.turnCount == 0

    /** Economia do cache como fração do que teria sido gasto sem ele. */
    val cacheSavingsShare: Double
        get() {
            val withoutCache = totals.costMicros + totals.cacheSavingsMicros
            if (withoutCache <= 0L) {
                return 0.0
            }
            return totals.cacheSavingsMicros.toDouble() / withoutCache.toDouble()
        }
}

/**
 * Dobra as linhas agrupadas nos três eixos.
 *
 * O custo é recalculado a partir dos tokens com [ModelPricingTable] — o índice
 * só guarda custo por sessão, e reaproveitá-lo aqui obrigaria a ratear entre
 * modelos, o que é justamente o que este resumo quer evitar. Somar tokens antes
 * de precificar é exato: `ModelPricing.costMicros` soma os produtos e divide uma
 * única vez.
 *
 * A contagem de sessões é por identificador distinto: uma sessão aparece em
 * várias linhas (uma por modelo) e somá-las inflaria o número na tela.
 */
fun Iterable<CliUsageGroupRow>.toUsageBreakdown(): CliUsageBreakdown {
    val byProject = linkedMapOf<String?, BucketAccumulator>()
    val byBranch = linkedMapOf<String?, BucketAccumulator>()
    val byModel = linkedMapOf<String?, BucketAccumulator>()
    val totals = BucketAccumulator(null)

    for (row in this) {
        val sessionKey = row.sessionId
        val projectLabel = projectNameFromCwd(row.cwd)
        val branchLabel = row.gitBranch?.takeIf { branch -> branch.isNotBlank() }
        val modelLabel = row.model?.takeIf { model -> model.isNotBlank() }

        byProject.getOrPut(projectLabel) { BucketAccumulator(projectLabel) }.add(row, sessionKey)
        byBranch.getOrPut(branchLabel) { BucketAccumulator(branchLabel) }.add(row, sessionKey)
        byModel.getOrPut(modelLabel) { BucketAccumulator(modelLabel) }.add(row, sessionKey)
        totals.add(row, sessionKey)
    }

    return CliUsageBreakdown(
        byProject = byProject.values.toRankedBuckets(),
        byBranch = byBranch.values.toRankedBuckets(),
        byModel = byModel.values.toRankedBuckets(),
        totals = totals.toBucket()
    )
}

/**
 * Ordem total e determinística: custo desc, e o rótulo desbanca o empate.
 *
 * O desempate não é estético. Duas leituras iguais têm de produzir listas
 * iguais, senão o `StateFlow` reemite e a tela recompõe a cada tique do laço ao
 * vivo — o mesmo motivo que ordena a tela de presença do time.
 */
private fun Iterable<BucketAccumulator>.toRankedBuckets(): List<CliUsageBucket> {
    return map { accumulator -> accumulator.toBucket() }
        .sortedWith(
            compareByDescending<CliUsageBucket> { bucket -> bucket.costMicros }
                .thenByDescending { bucket -> bucket.totalTokens }
                .thenBy { bucket -> bucket.label ?: "" }
        )
}

private class BucketAccumulator(private val label: String?) {
    private var turnCount = 0
    private var inputTokens = 0L
    private var outputTokens = 0L
    private var cacheReadTokens = 0L
    private var cacheWrite5mTokens = 0L
    private var cacheWrite1hTokens = 0L
    private var costMicros = 0L
    private var cacheSavingsMicros = 0L
    private var unpricedTurnCount = 0
    private val sessionIds = mutableSetOf<String>()

    fun add(row: CliUsageGroupRow, sessionKey: String?) {
        turnCount += row.turnCount
        inputTokens += row.inputTokens
        outputTokens += row.outputTokens
        cacheReadTokens += row.cacheReadTokens
        cacheWrite5mTokens += row.cacheWrite5mTokens
        cacheWrite1hTokens += row.cacheWrite1hTokens
        if (sessionKey != null) {
            sessionIds += sessionKey
        }

        val pricing = ModelPricingTable.forModel(row.model)
        if (pricing == null) {
            // Sem tarifa não se inventa custo nem economia: o balde declara a
            // lacuna em `unpricedTurnCount` e a tela avisa.
            unpricedTurnCount += row.turnCount
            return
        }
        costMicros += pricing.costMicros(
            inputTokens = row.inputTokens,
            outputTokens = row.outputTokens,
            cacheReadTokens = row.cacheReadTokens,
            cacheWrite5mTokens = row.cacheWrite5mTokens,
            cacheWrite1hTokens = row.cacheWrite1hTokens
        )
        cacheSavingsMicros += pricing.cacheSavingsMicros(row.cacheReadTokens)
    }

    fun toBucket(): CliUsageBucket {
        return CliUsageBucket(
            label = label,
            turnCount = turnCount,
            sessionCount = sessionIds.size,
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            cacheReadTokens = cacheReadTokens,
            cacheWrite5mTokens = cacheWrite5mTokens,
            cacheWrite1hTokens = cacheWrite1hTokens,
            costMicros = costMicros,
            cacheSavingsMicros = cacheSavingsMicros,
            unpricedTurnCount = unpricedTurnCount
        )
    }
}

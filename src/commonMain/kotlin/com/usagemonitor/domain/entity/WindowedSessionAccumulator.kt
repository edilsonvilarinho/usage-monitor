package com.usagemonitor.domain.entity

import kotlinx.datetime.Instant

/**
 * Dobra os grupos de turnos por modelo de uma sessão num único [CliSessionSummary].
 *
 * O agrupamento por modelo é o que torna o custo correto: uma sessão que trocou
 * de modelo no meio precisa ser precificada com a tarifa de cada trecho. O custo
 * é somado por grupo porque `ModelPricing.costMicros` soma os produtos e divide
 * uma única vez — agregar tokens antes de precificar é exato, não uma
 * aproximação.
 *
 * Vive no domain, e não no datasource local, porque a mesma agregação vale para
 * duas fontes: as linhas do índice SQLite da máquina e as linhas que o servidor
 * de time devolve. As duas chegam no mesmo formato `(sessão, modelo) → tokens`.
 */
class WindowedSessionAccumulator(
    private val sessionId: String,
    private val filePath: String = "",
    private val profileId: String? = null,
    private val cwd: String? = null,
    private val gitBranch: String? = null,
    private val hostName: String? = null,
    private val liveContextTokens: Long = 0L,
    private val liveContextModel: String? = null,
    private val stale: Boolean = false
) {
    private var turnCount = 0
    private var firstTs = Long.MAX_VALUE
    private var lastTs = Long.MIN_VALUE
    private var inputTokens = 0L
    private var outputTokens = 0L
    private var cacheReadTokens = 0L
    private var cacheWrite5mTokens = 0L
    private var cacheWrite1hTokens = 0L
    private var costMicros = 0L
    private var unpricedTurnCount = 0
    private var primaryModel: String? = null
    private var primaryModelTurns = 0

    fun addModelGroup(
        model: String?,
        turnCount: Int,
        firstTsMillis: Long,
        lastTsMillis: Long,
        inputTokens: Long,
        outputTokens: Long,
        cacheReadTokens: Long,
        cacheWrite5mTokens: Long,
        cacheWrite1hTokens: Long
    ) {
        this.turnCount += turnCount
        this.inputTokens += inputTokens
        this.outputTokens += outputTokens
        this.cacheReadTokens += cacheReadTokens
        this.cacheWrite5mTokens += cacheWrite5mTokens
        this.cacheWrite1hTokens += cacheWrite1hTokens
        firstTs = minOf(firstTs, firstTsMillis)
        lastTs = maxOf(lastTs, lastTsMillis)

        val pricing = ModelPricingTable.forModel(model)
        if (pricing == null) {
            unpricedTurnCount += turnCount
        } else {
            costMicros += pricing.costMicros(
                inputTokens = inputTokens,
                outputTokens = outputTokens,
                cacheReadTokens = cacheReadTokens,
                cacheWrite5mTokens = cacheWrite5mTokens,
                cacheWrite1hTokens = cacheWrite1hTokens
            )
        }

        if (model != null && turnCount > primaryModelTurns) {
            primaryModel = model
            primaryModelTurns = turnCount
        }
    }

    /** `true` quando nenhum grupo foi adicionado — a sessão não tocou a janela. */
    val isEmpty: Boolean
        get() = turnCount == 0

    fun toSummary(): CliSessionSummary {
        return CliSessionSummary(
            sessionId = sessionId,
            filePath = filePath,
            profileId = profileId,
            cwd = cwd,
            gitBranch = gitBranch,
            hostName = hostName,
            firstTs = Instant.fromEpochMilliseconds(if (isEmpty) 0L else firstTs),
            lastTs = Instant.fromEpochMilliseconds(if (isEmpty) 0L else lastTs),
            primaryModel = primaryModel,
            turnCount = turnCount,
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            cacheReadTokens = cacheReadTokens,
            cacheWrite5mTokens = cacheWrite5mTokens,
            cacheWrite1hTokens = cacheWrite1hTokens,
            costMicros = costMicros,
            unpricedTurnCount = unpricedTurnCount,
            liveContextTokens = liveContextTokens,
            liveContextModel = liveContextModel,
            stale = stale
        )
    }
}

package com.usagemonitor.domain.usecase

import com.usagemonitor.domain.entity.CliSessionAnalytics
import com.usagemonitor.domain.entity.CliSessionCostBreakdown
import com.usagemonitor.domain.entity.CliSessionDetail
import com.usagemonitor.domain.entity.CliSessionSummary
import com.usagemonitor.domain.entity.CliSessionTurn
import com.usagemonitor.domain.entity.MICROS_PER_USD
import com.usagemonitor.domain.entity.ModelPricingTable
import com.usagemonitor.domain.entity.activeTimeMillisOf
import com.usagemonitor.domain.entity.computeContextStatus

/**
 * Deriva as métricas do detalhe de uma sessão. Puro: sem I/O, sem relógio.
 *
 * Equivale ao `analyticsFormatter.ts` do legado, com três correções:
 * o custo usa o preço do modelo de cada turno, o contexto médio é a média dos
 * valores reais por turno (o legado promediava uma série acumulada) e as séries
 * excluem turnos de subagente.
 */
class ComputeCliSessionAnalyticsUseCase {

    operator fun invoke(detail: CliSessionDetail): CliSessionAnalytics {
        val turns = detail.turns.sortedBy { turn -> turn.seq }
        if (turns.isEmpty()) {
            return CliSessionAnalytics(cacheHitRate = detail.summary.cacheHitRate)
        }

        val mainTurns = turns.filter { turn -> !turn.isSidechain }
        val breakdown = computeBreakdown(turns)
        val lastMainTurn = mainTurns.lastOrNull()
        // Mesmo cálculo que a lista faz a partir do índice: um único caminho para
        // que os dois nunca divirjam.
        val contextStatus = computeContextStatus(
            liveContextTokens = lastMainTurn?.cacheReadTokens ?: 0L,
            windowModel = detail.summary.primaryModel,
            lastTurnModel = lastMainTurn?.model
        )

        return CliSessionAnalytics(
            cacheHitRate = detail.summary.cacheHitRate,
            cacheSavingsMicros = turns.sumOf { turn -> turn.cacheSavingsMicros ?: 0L },
            averageContextPerTurn = averageContext(mainTurns),
            liveContextTokens = contextStatus.liveContextTokens,
            nextInteractionCostMicros = contextStatus.nextInteractionCostMicros,
            contextSaturation = contextStatus.contextSaturation,
            costBreakdown = breakdown,
            mainTurnCount = mainTurns.size,
            sidechainTurnCount = turns.size - mainTurns.size,
            unpricedTurnCount = turns.count { turn -> turn.pricing == null },
            contextPerTurn = mainTurns.map { turn -> turn.cacheReadTokens },
            cacheWrite5mPerTurn = mainTurns.map { turn -> turn.cacheWrite5mTokens },
            cacheWrite1hPerTurn = mainTurns.map { turn -> turn.cacheWrite1hTokens },
            cumulativeCostMicros = accumulate(turns) { turn -> turn.costMicros ?: 0L },
            cumulativeSavingsMicros = accumulate(turns) { turn -> turn.cacheSavingsMicros ?: 0L },
            // Só a thread principal: o subagente roda em paralelo, e somar os
            // intervalos dele contaria o mesmo tempo duas vezes.
            activeTimeMillis = activeTimeMillisOf(mainTurns)
        )
    }

    /**
     * O que dá para afirmar sem os turnos.
     *
     * Existe para o detalhe de sessão do time contra um servidor que não expõe a
     * rota de turnos: o veredito de contexto e a taxa de acerto de cache saem do
     * resumo e são exatos. Tudo o que só um turno prova — séries por turno,
     * distribuição do custo por componente, economia do cache — fica em zero e
     * **não é exibido**. Estimar esses números a partir do modelo predominante
     * seria inventá-los.
     */
    fun fromSummary(summary: CliSessionSummary): CliSessionAnalytics {
        val status = summary.contextStatus

        return CliSessionAnalytics(
            cacheHitRate = summary.cacheHitRate,
            liveContextTokens = status.liveContextTokens,
            nextInteractionCostMicros = status.nextInteractionCostMicros,
            contextSaturation = status.contextSaturation,
            unpricedTurnCount = summary.unpricedTurnCount
        )
    }

    private fun computeBreakdown(turns: List<CliSessionTurn>): CliSessionCostBreakdown {
        var input = 0L
        var output = 0L
        var cacheRead = 0L
        var cacheWrite = 0L

        for (turn in turns) {
            val pricing = ModelPricingTable.forModel(turn.model) ?: continue
            input += turn.inputTokens * pricing.inputMicrosPerMillion / MICROS_PER_USD
            output += turn.outputTokens * pricing.outputMicrosPerMillion / MICROS_PER_USD
            cacheRead += turn.cacheReadTokens * pricing.cacheReadMicrosPerMillion / MICROS_PER_USD
            cacheWrite += turn.cacheWrite5mTokens * pricing.cacheWrite5mMicrosPerMillion / MICROS_PER_USD
            cacheWrite += turn.cacheWrite1hTokens * pricing.cacheWrite1hMicrosPerMillion / MICROS_PER_USD
        }

        return CliSessionCostBreakdown(
            inputMicros = input,
            outputMicros = output,
            cacheReadMicros = cacheRead,
            cacheWriteMicros = cacheWrite
        )
    }

    /** Média aritmética do `cache_read` real por turno — o tamanho típico do contexto. */
    private fun averageContext(mainTurns: List<CliSessionTurn>): Long {
        if (mainTurns.isEmpty()) {
            return 0L
        }
        return mainTurns.sumOf { turn -> turn.cacheReadTokens } / mainTurns.size
    }

    private fun accumulate(turns: List<CliSessionTurn>, selector: (CliSessionTurn) -> Long): List<Long> {
        var running = 0L
        return turns.map { turn ->
            running += selector(turn)
            running
        }
    }
}

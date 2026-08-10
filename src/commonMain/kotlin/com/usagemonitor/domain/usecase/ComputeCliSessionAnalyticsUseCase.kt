package com.usagemonitor.domain.usecase

import com.usagemonitor.domain.entity.CliSessionAnalytics
import com.usagemonitor.domain.entity.CliSessionCostBreakdown
import com.usagemonitor.domain.entity.CliSessionDetail
import com.usagemonitor.domain.entity.CliSessionTurn
import com.usagemonitor.domain.entity.MICROS_PER_USD
import com.usagemonitor.domain.entity.ModelContextWindowTable
import com.usagemonitor.domain.entity.ModelPricingTable

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
        val liveContext = mainTurns.lastOrNull()?.cacheReadTokens ?: 0L
        val contextWindow = ModelContextWindowTable.forModel(detail.summary.primaryModel)

        return CliSessionAnalytics(
            cacheHitRate = detail.summary.cacheHitRate,
            cacheSavingsMicros = turns.sumOf { turn -> turn.cacheSavingsMicros ?: 0L },
            averageContextPerTurn = averageContext(mainTurns),
            liveContextTokens = liveContext,
            nextInteractionCostMicros = nextInteractionCost(mainTurns.lastOrNull()),
            contextSaturation = contextWindow
                ?.takeIf { window -> window > 0L }
                ?.let { window -> liveContext.toDouble() / window.toDouble() },
            costBreakdown = breakdown,
            mainTurnCount = mainTurns.size,
            sidechainTurnCount = turns.size - mainTurns.size,
            unpricedTurnCount = turns.count { turn -> turn.pricing == null },
            contextPerTurn = mainTurns.map { turn -> turn.cacheReadTokens },
            cacheWrite5mPerTurn = mainTurns.map { turn -> turn.cacheWrite5mTokens },
            cacheWrite1hPerTurn = mainTurns.map { turn -> turn.cacheWrite1hTokens },
            cumulativeCostMicros = accumulate(turns) { turn -> turn.costMicros ?: 0L },
            cumulativeSavingsMicros = accumulate(turns) { turn -> turn.cacheSavingsMicros ?: 0L }
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

    /**
     * O `cache_read` do último turno é o tamanho do contexto vivo: reenviá-lo
     * na próxima mensagem custa esse volume à tarifa de cache read do modelo.
     */
    private fun nextInteractionCost(lastMainTurn: CliSessionTurn?): Long {
        val turn = lastMainTurn ?: return 0L
        val pricing = turn.pricing ?: return 0L
        return turn.cacheReadTokens * pricing.cacheReadMicrosPerMillion / MICROS_PER_USD
    }

    private fun accumulate(turns: List<CliSessionTurn>, selector: (CliSessionTurn) -> Long): List<Long> {
        var running = 0L
        return turns.map { turn ->
            running += selector(turn)
            running
        }
    }
}

package com.usagemonitor.domain

import com.usagemonitor.domain.entity.CliSessionDetail
import com.usagemonitor.domain.entity.CliSessionSummary
import com.usagemonitor.domain.entity.CliSessionTurn
import com.usagemonitor.domain.entity.ModelPricingTable
import com.usagemonitor.domain.entity.computeContextStatus
import com.usagemonitor.domain.usecase.ComputeCliSessionAnalyticsUseCase
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

private const val HAIKU_5_5 = "claude-haiku-5-5"
private const val OPUS_5_5 = "claude-opus-5-5"

/** Custo e economia de um turno escolhem a faixa de prompt pelo prompt do próprio turno. */
class ModelPricingTurnTest {

    private val haiku = assertNotNull(ModelPricingTable.forModel(HAIKU_5_5))
    private val opus = assertNotNull(ModelPricingTable.forModel(OPUS_5_5))

    @Test
    fun `a prompt at the threshold is priced in the base tier even with a large output`() {
        // Prompt de 100.000 = 40.000 input + 50.000 cache read + 10.000 cache write 5m. Output de 500.000 não entra no prompt.
        val cost = haiku.turnCostMicros(
            inputTokens = 40_000L,
            outputTokens = 500_000L,
            cacheReadTokens = 50_000L,
            cacheWrite5mTokens = 10_000L
        )

        // Faixa baixa: 40.000 x 0,10 + 50.000 x 0,01 + 10.000 x 0,125 + 500.000 x 0,50 = 255.750 micros.
        assertEquals(255_750L, cost)
    }

    @Test
    fun `a prompt one token above the threshold is priced in the long tier`() {
        // Prompt de 100.001: o cache write 1h também conta no prompt.
        val cost = haiku.turnCostMicros(
            inputTokens = 40_000L,
            cacheReadTokens = 50_000L,
            cacheWrite5mTokens = 10_000L,
            cacheWrite1hTokens = 1L
        )

        // Faixa alta: 40.000 x 0,50 + 50.000 x 0,05 + 10.000 x 0,625 + 1 x 1,00 = 28.751 micros.
        assertEquals(28_751L, cost)
    }

    @Test
    fun `cache savings above the threshold use the long tier deltas`() {
        val savings = haiku.turnCacheSavingsMicros(
            inputTokens = 40_000L,
            cacheReadTokens = 50_000L,
            cacheWrite5mTokens = 10_000L,
            cacheWrite1hTokens = 1L
        )

        // Faixa alta: 50.000 x (0,50 - 0,05) = 22.500 micros. A faixa baixa daria 4.500.
        assertEquals(22_500L, savings)
    }

    @Test
    fun `cache savings at the threshold use the base tier deltas`() {
        val savings = haiku.turnCacheSavingsMicros(
            inputTokens = 40_000L,
            cacheReadTokens = 50_000L,
            cacheWrite5mTokens = 10_000L
        )

        // Faixa baixa: 50.000 x (0,10 - 0,01) = 4.500 micros.
        assertEquals(4_500L, savings)
    }

    @Test
    fun `a model without long tier ignores the prompt size`() {
        val turnCost = opus.turnCostMicros(inputTokens = 600_000L, outputTokens = 1_000L)

        assertEquals(opus.costMicros(inputTokens = 600_000L, outputTokens = 1_000L), turnCost)
    }

    @Test
    fun `a haiku turn above the threshold costs the long tier amount`() {
        val turn = CliSessionTurn(
            sessionId = "session-1",
            seq = 1,
            messageId = "msg-1",
            ts = Instant.fromEpochMilliseconds(0L),
            model = HAIKU_5_5,
            inputTokens = 40_000L,
            cacheReadTokens = 50_000L,
            cacheWrite5mTokens = 10_000L,
            cacheWrite1hTokens = 1L
        )

        assertEquals(28_751L, turn.costMicros)
    }
}

class ModelPricingContextStatusTest {

    @Test
    fun `next interaction above the threshold is priced in the long tier`() {
        val status = computeContextStatus(
            liveContextTokens = 100_001L,
            windowModel = HAIKU_5_5,
            lastTurnModel = HAIKU_5_5
        )

        // Faixa alta: cache read 0,05/M -> 100.001 x 0,05 = 5.000 micros. A faixa baixa daria 1.000.
        assertEquals(5_000L, status.nextInteractionCostMicros)
    }

    @Test
    fun `next interaction at the threshold is priced in the base tier`() {
        val status = computeContextStatus(
            liveContextTokens = 100_000L,
            windowModel = HAIKU_5_5,
            lastTurnModel = HAIKU_5_5
        )

        // Faixa baixa: cache read 0,01/M -> 100.000 x 0,01 = 1.000 micros.
        assertEquals(1_000L, status.nextInteractionCostMicros)
    }
}

class ModelPricingBreakdownTest {

    private val computeAnalytics = ComputeCliSessionAnalyticsUseCase()

    @Test
    fun `breakdown at the threshold prices the turn in the base tier`() {
        // Prompt de 100.000: input 40.000 + cache read 50.000 + cache write 5m 10.000.
        val analytics = computeAnalytics(
            haikuDetail(
                turn(inputTokens = 40_000L, cacheReadTokens = 50_000L, cacheWrite5mTokens = 10_000L)
            )
        )

        // Faixa baixa: input 4.000 + cache read 500 + cache write 1.250 micros.
        assertEquals(4_000L, analytics.costBreakdown.inputMicros)
        assertEquals(500L, analytics.costBreakdown.cacheReadMicros)
        assertEquals(1_250L, analytics.costBreakdown.cacheWriteMicros)
    }

    @Test
    fun `breakdown one token above the threshold prices the turn in the long tier`() {
        // Prompt de 100.001: o cache write 1h também conta no prompt.
        val analytics = computeAnalytics(
            haikuDetail(
                turn(
                    inputTokens = 40_000L,
                    cacheReadTokens = 50_000L,
                    cacheWrite5mTokens = 10_000L,
                    cacheWrite1hTokens = 1L
                )
            )
        )

        // Faixa alta: input 20.000 + cache read 2.500 + cache write (6.250 + 1) micros.
        assertEquals(20_000L, analytics.costBreakdown.inputMicros)
        assertEquals(2_500L, analytics.costBreakdown.cacheReadMicros)
        assertEquals(6_251L, analytics.costBreakdown.cacheWriteMicros)
    }

    private fun haikuDetail(vararg turns: CliSessionTurn): CliSessionDetail {
        val summary = CliSessionSummary(
            sessionId = "session-1",
            filePath = "/tmp/session-1.jsonl",
            cwd = null,
            firstTs = Instant.fromEpochMilliseconds(0L),
            lastTs = Instant.fromEpochMilliseconds(1_000L),
            primaryModel = HAIKU_5_5,
            cacheReadTokens = 0L,
            cacheWrite5mTokens = 0L,
            cacheWrite1hTokens = 0L
        )
        return CliSessionDetail(summary = summary, turns = turns.toList())
    }

    private fun turn(
        inputTokens: Long = 0L,
        cacheReadTokens: Long = 0L,
        cacheWrite5mTokens: Long = 0L,
        cacheWrite1hTokens: Long = 0L
    ): CliSessionTurn {
        return CliSessionTurn(
            sessionId = "session-1",
            seq = 1,
            messageId = "msg-1",
            ts = Instant.fromEpochMilliseconds(0L),
            model = HAIKU_5_5,
            inputTokens = inputTokens,
            cacheReadTokens = cacheReadTokens,
            cacheWrite5mTokens = cacheWrite5mTokens,
            cacheWrite1hTokens = cacheWrite1hTokens
        )
    }
}

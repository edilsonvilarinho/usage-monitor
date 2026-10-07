package com.usagemonitor.domain

import com.usagemonitor.domain.entity.CodexCliSessionTurn
import com.usagemonitor.domain.entity.CodexCliUsageDelta
import com.usagemonitor.domain.entity.codexCliSessionAnalyticsOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class CodexCliSessionAnalyticsTest {

    // Valores das três primeiras respostas do rollout 01a111fa (#393).
    private fun turn(
        seq: Int,
        input: Long,
        cached: Long,
        output: Long,
        reasoning: Long = 0L,
        at: String = "2026-10-06T16:10:${10 + seq}Z",
        requestAt: String? = null
    ) = CodexCliSessionTurn(
        sessionId = "01a111fa",
        turnId = "t",
        responseId = "resp_$seq",
        seq = seq,
        ts = Instant.parse(at),
        usage = CodexCliUsageDelta(
            inputTokens = input,
            cachedInputTokens = cached,
            outputTokens = output,
            reasoningOutputTokens = reasoning,
            totalTokens = input + output
        ),
        requestTs = requestAt?.let(Instant::parse)
    )

    @Test
    fun `context is the input of each response, in session order`() {
        val turns = listOf(
            turn(2, 48_627, 40_320, 351, 29),
            turn(0, 35_789, 22_272, 194),
            turn(1, 40_463, 35_584, 259, 41)
        )

        val analytics = codexCliSessionAnalyticsOf(turns)

        assertEquals(listOf(35_789L, 40_463L, 48_627L), analytics.contextPerTurn)
        assertEquals(listOf(194L, 259L, 351L), analytics.outputPerTurn)
        assertEquals(listOf(0L, 41L, 29L), analytics.reasoningPerTurn)
        assertEquals(22_272.0 / 35_789.0, analytics.cacheHitPerTurn[0]!!, 1e-9)
    }

    @Test
    fun `compaction shows as a drop in context`() {
        val analytics = codexCliSessionAnalyticsOf(listOf(turn(0, 250_000, 240_000, 500), turn(1, 60_000, 0, 400)))

        assertEquals(listOf(250_000L, 60_000L), analytics.contextPerTurn)
        assertEquals(0.0, analytics.cacheHitPerTurn[1]!!, 1e-9)
    }

    @Test
    fun `unmeasured throughput and empty input stay null, never zero`() {
        val analytics = codexCliSessionAnalyticsOf(listOf(
            turn(0, 0, 0, 0),
            turn(1, 1_000, 0, 300, at = "2026-10-06T16:10:20Z", requestAt = "2026-10-06T16:10:10Z")
        ))

        assertNull(analytics.cacheHitPerTurn[0])
        assertNull(analytics.throughputPerTurn[0])
        assertEquals(30.0, analytics.throughputPerTurn[1]!!, 1e-9)
    }

    @Test
    fun `no turns give empty series`() {
        val analytics = codexCliSessionAnalyticsOf(emptyList())

        assertEquals(emptyList(), analytics.contextPerTurn)
        assertEquals(emptyList(), analytics.throughputPerTurn)
    }
}

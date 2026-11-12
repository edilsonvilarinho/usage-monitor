package com.usagemonitor.domain

import com.usagemonitor.domain.entity.CodexCliSessionTurn
import com.usagemonitor.domain.entity.CodexCliUsageDelta
import com.usagemonitor.domain.entity.toCodexUsageBreakdown
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class CodexCliUsageBreakdownTest {

    @Test
    fun `turns fold along project model and member axes`() {
        val breakdown = listOf(
            turn("s1", "t1", "/home/dev/alpha", "gpt-test-a", "user", "2026-09-08T12:00:00Z", 1_000, 100),
            turn("s1", "t2", "/home/dev/alpha", "gpt-test-a", "subagent", "2026-09-08T12:30:00Z", 500, 50),
            turn("s2", "t3", "C:/work/beta/", "gpt-test-b", "user", "2026-09-08T15:00:00Z", 200, 10)
        ).toCodexUsageBreakdown()

        assertEquals(listOf("alpha", "beta"), breakdown.byProject.map { bucket -> bucket.label })
        assertEquals(listOf(2, 1), breakdown.byProject.map { bucket -> bucket.turnCount })
        assertEquals(listOf(1, 1), breakdown.byProject.map { bucket -> bucket.sessionCount })
        assertEquals(listOf(1_500L, 200L), breakdown.byProject.map { bucket -> bucket.inputTokens })

        assertEquals(listOf("gpt-test-a", "gpt-test-b"), breakdown.byModel.map { bucket -> bucket.label })
        assertEquals(listOf(2, 1), breakdown.byModel.map { bucket -> bucket.turnCount })

        assertEquals(listOf("principal", "subagent"), breakdown.byMember.map { bucket -> bucket.label })
        assertEquals(listOf(2, 1), breakdown.byMember.map { bucket -> bucket.turnCount })
        assertEquals(listOf(2, 1), breakdown.byMember.map { bucket -> bucket.sessionCount })
    }

    /** Resposta é o registro: dois registros com o mesmo turn_id contam duas respostas. */
    @Test
    fun `each record counts as one response even when turn ids repeat`() {
        val breakdown = listOf(
            turn("s1", "t1", "/home/dev/alpha", "gpt-test-a", "user", "2026-09-08T12:00:00Z", 1_000, 100),
            turn("s1", "t1", "/home/dev/alpha", "gpt-test-a", "user", "2026-09-08T12:01:00Z", 400, 40)
        ).toCodexUsageBreakdown()

        assertEquals(2, breakdown.totals.turnCount)
        assertEquals(2, breakdown.totals.unpricedTurnCount)
        assertEquals(2, breakdown.byProject.single().turnCount)
        assertEquals(2, breakdown.byModel.single().turnCount)
    }

    @Test
    fun `totals have no cost and every turn is unpriced`() {
        val breakdown = listOf(
            turn("s1", "t1", "/home/dev/alpha", "gpt-test-a", "user", "2026-09-08T12:00:00Z", 1_000, 100),
            turn("s1", "t2", "/home/dev/alpha", "gpt-test-a", "subagent", "2026-09-08T12:30:00Z", 500, 50),
            turn("s2", "t3", "C:/work/beta/", "gpt-test-b", "user", "2026-09-08T15:00:00Z", 200, 10)
        ).toCodexUsageBreakdown()

        assertEquals(3, breakdown.totals.turnCount)
        assertEquals(2, breakdown.totals.sessionCount)
        assertEquals(1_700L, breakdown.totals.inputTokens)
        assertEquals(160L, breakdown.totals.outputTokens)
        assertEquals(0L, breakdown.totals.costMicros)
        assertEquals(3, breakdown.totals.unpricedTurnCount)
    }

    @Test
    fun `heatmap buckets turns by local hour of America Sao Paulo`() {
        val breakdown = listOf(
            turn("s1", "t1", "/home/dev/alpha", "gpt-test-a", "user", "2026-09-08T12:00:00Z", 1_000, 100),
            turn("s1", "t2", "/home/dev/alpha", "gpt-test-a", "subagent", "2026-09-08T12:30:00Z", 500, 50),
            turn("s2", "t3", "C:/work/beta/", "gpt-test-b", "user", "2026-09-08T15:00:00Z", 200, 10)
        ).toCodexUsageBreakdown()

        assertEquals("America/Sao_Paulo", breakdown.heatmap.timeZoneId)
        assertEquals(2, breakdown.heatmap.cells.filter { cell -> cell.hour == 9 }.sumOf { cell -> cell.turnCount })
        assertEquals(1, breakdown.heatmap.cells.filter { cell -> cell.hour == 12 }.sumOf { cell -> cell.turnCount })
    }

    private fun turn(
        sessionId: String,
        turnId: String,
        cwd: String,
        model: String,
        threadSource: String,
        ts: String,
        inputTokens: Long,
        outputTokens: Long
    ): CodexCliSessionTurn {
        return CodexCliSessionTurn(
            sessionId = sessionId,
            turnId = turnId,
            responseId = "response-$turnId",
            seq = 0,
            ts = Instant.parse(ts),
            model = model,
            cwd = cwd,
            gitBranch = "main",
            threadSource = threadSource,
            usage = CodexCliUsageDelta(inputTokens = inputTokens, outputTokens = outputTokens)
        )
    }
}

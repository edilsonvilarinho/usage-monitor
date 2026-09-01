package com.usagemonitor.domain

import com.usagemonitor.domain.entity.CliSessionSummary
import com.usagemonitor.domain.entity.CliSessionTail
import com.usagemonitor.domain.entity.CliSessionTailOutcome
import com.usagemonitor.domain.entity.DEFAULT_STALL_THRESHOLD_MILLIS
import com.usagemonitor.domain.entity.STALLED_SESSION_MAX_AGE_MILLIS
import com.usagemonitor.domain.entity.detectStalledSessions
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

private val NOW = Instant.parse("2026-09-01T12:00:00Z")

class CliStalledSessionTest {

    @Test
    fun `a request left unanswered past the threshold is reported`() {
        val pendingSince = NOW - 3.hours
        val stalled = detectStalledSessions(
            sessions = listOf(session("a")),
            tails = mapOf("a" to pending("a", pendingSince)),
            now = NOW
        )

        val single = stalled.single()
        assertEquals("a", single.sessionId)
        assertEquals(pendingSince, single.pendingSince)
        assertEquals(3.hours.inWholeMilliseconds, single.pendingMillis)
        assertEquals("projeto", single.projectName)
    }

    /** O limiar é piso, como o dos alertas de quota: exatamente no valor já cruzou. */
    @Test
    fun `the threshold is a floor`() {
        val exactlyAtThreshold = detectStalledSessions(
            sessions = listOf(session("a")),
            tails = mapOf("a" to pending("a", NOW - DEFAULT_STALL_THRESHOLD_MILLIS.milliseconds)),
            now = NOW
        )
        assertEquals(1, exactlyAtThreshold.size)

        val oneMillisShort = detectStalledSessions(
            sessions = listOf(session("a")),
            tails = mapOf("a" to pending("a", NOW - (DEFAULT_STALL_THRESHOLD_MILLIS - 1).milliseconds)),
            now = NOW
        )
        assertTrue(oneMillisShort.isEmpty())
    }

    /**
     * Acima do teto a sessão está abandonada, não travada — terminal fechado no
     * meio de um turno deixa a cauda pendente para sempre.
     */
    @Test
    fun `a request older than the ceiling is abandoned, not stalled`() {
        val stalled = detectStalledSessions(
            sessions = listOf(session("a")),
            tails = mapOf("a" to pending("a", NOW - (STALLED_SESSION_MAX_AGE_MILLIS + 1).milliseconds)),
            now = NOW
        )

        assertTrue(stalled.isEmpty())
    }

    @Test
    fun `a finished turn is never reported`() {
        val stalled = detectStalledSessions(
            sessions = listOf(session("a")),
            tails = mapOf(
                "a" to CliSessionTail(
                    sessionId = "a",
                    outcome = CliSessionTailOutcome.TURN_COMPLETED,
                    lastTurnEndAt = NOW - 5.hours
                )
            ),
            now = NOW
        )

        assertTrue(stalled.isEmpty())
    }

    /** Sem marcador na cauda não há veredito: afirmar aqui seria chutar. */
    @Test
    fun `a tail that could not be evaluated is never reported`() {
        val stalled = detectStalledSessions(
            sessions = listOf(session("a")),
            tails = mapOf(
                "a" to CliSessionTail(
                    sessionId = "a",
                    outcome = CliSessionTailOutcome.NOT_EVALUATED,
                    lastRequestAt = NOW - 5.hours
                )
            ),
            now = NOW
        )

        assertTrue(stalled.isEmpty())
    }

    @Test
    fun `a session whose tail was not read is never reported`() {
        val stalled = detectStalledSessions(
            sessions = listOf(session("a")),
            tails = emptyMap(),
            now = NOW
        )

        assertTrue(stalled.isEmpty())
    }

    /** Pendência marcada sem carimbo não tem idade a medir. */
    @Test
    fun `a pending tail without a timestamp is never reported`() {
        val stalled = detectStalledSessions(
            sessions = listOf(session("a")),
            tails = mapOf("a" to CliSessionTail("a", CliSessionTailOutcome.PENDING_REQUEST)),
            now = NOW
        )

        assertTrue(stalled.isEmpty())
    }

    @Test
    fun `a custom threshold shortens the wait`() {
        val stalled = detectStalledSessions(
            sessions = listOf(session("a")),
            tails = mapOf("a" to pending("a", NOW - 40.minutes)),
            now = NOW,
            thresholdMillis = 30.minutes.inWholeMilliseconds
        )

        assertEquals(1, stalled.size)
    }

    /**
     * Ordem total: duas leituras iguais têm de dar listas iguais, ou o `StateFlow`
     * reemite e a tela recompõe a cada tique do laço.
     */
    @Test
    fun `the order is oldest first with the session id breaking ties`() {
        val sameAge = NOW - 3.hours
        val stalled = detectStalledSessions(
            sessions = listOf(session("b"), session("c"), session("a")),
            tails = mapOf(
                "b" to pending("b", sameAge),
                "c" to pending("c", NOW - 6.hours),
                "a" to pending("a", sameAge)
            ),
            now = NOW
        )

        assertEquals(listOf("c", "a", "b"), stalled.map { entry -> entry.sessionId })
    }

    private fun pending(sessionId: String, since: Instant): CliSessionTail {
        return CliSessionTail(
            sessionId = sessionId,
            outcome = CliSessionTailOutcome.PENDING_REQUEST,
            lastRequestAt = since
        )
    }

    private fun session(sessionId: String): CliSessionSummary {
        return CliSessionSummary(
            sessionId = sessionId,
            filePath = "/home/user/.claude/projects/projeto/$sessionId.jsonl",
            profileId = "default",
            cwd = "/home/user/projeto",
            firstTs = NOW - 8.hours,
            lastTs = NOW - 3.hours
        )
    }
}

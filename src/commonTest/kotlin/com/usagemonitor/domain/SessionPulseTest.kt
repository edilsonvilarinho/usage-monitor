package com.usagemonitor.domain

import com.usagemonitor.domain.entity.CliSessionHealth
import com.usagemonitor.domain.entity.CliSessionSummary
import com.usagemonitor.domain.entity.SessionPulse
import com.usagemonitor.domain.entity.mergeSessionPulses
import com.usagemonitor.domain.entity.toSessionPulse
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

private val NOW = Instant.parse("2026-08-13T12:00:00Z")

/** Opus tem janela de 1M tokens; 45% dela custa US$ 0,225 na próxima mensagem. */
private const val ATTENTION_CONTEXT_TOKENS = 450_000L

/** 65% da janela, acima dos 60% que definem a sessão saturada. */
private const val SATURATED_CONTEXT_TOKENS = 650_000L

private const val HEALTHY_CONTEXT_TOKENS = 100_000L

class SessionPulseTest {

    @Test
    fun `an interacted session that needs attention becomes an alert`() {
        val pulse = listOf(session("a", ATTENTION_CONTEXT_TOKENS, NOW - 1.minutes)).toSessionPulse(NOW)

        assertTrue(pulse.isPulsing)
        assertEquals(listOf(CliSessionHealth.ATTENTION), pulse.severities)
        assertEquals("a", pulse.alerts.single().sessionId)
    }

    @Test
    fun `a session without recent interaction leaves the semaphore`() {
        val pulse = listOf(session("a", SATURATED_CONTEXT_TOKENS, NOW - 6.minutes)).toSessionPulse(NOW)

        assertFalse(pulse.isPulsing)
    }

    @Test
    fun `a healthy session never becomes an alert`() {
        val pulse = listOf(session("a", HEALTHY_CONTEXT_TOKENS, NOW)).toSessionPulse(NOW)

        assertFalse(pulse.isPulsing)
    }

    /** Sem a janela do modelo não há fração — a mesma regra de `tallyHealth`. */
    @Test
    fun `a session with an unknown context window is not rated`() {
        val unknownModel = session("a", SATURATED_CONTEXT_TOKENS, NOW).copy(
            primaryModel = "modelo-que-nao-existe",
            liveContextModel = "modelo-que-nao-existe"
        )

        assertFalse(listOf(unknownModel).toSessionPulse(NOW).isPulsing)
    }

    @Test
    fun `severities are distinct and ordered by severity`() {
        val pulse = listOf(
            session("saturada", SATURATED_CONTEXT_TOKENS, NOW),
            session("atencao", ATTENTION_CONTEXT_TOKENS, NOW),
            session("outra-atencao", ATTENTION_CONTEXT_TOKENS, NOW)
        ).toSessionPulse(NOW)

        assertEquals(
            listOf(CliSessionHealth.ATTENTION, CliSessionHealth.SATURATED),
            pulse.severities
        )
        assertEquals(CliSessionHealth.SATURATED, pulse.worstHealth)
        assertEquals(2, pulse.countOf(CliSessionHealth.ATTENTION))
    }

    @Test
    fun `team alerts carry who and where`() {
        val pulse = listOf(session("a", SATURATED_CONTEXT_TOKENS, NOW))
            .toSessionPulse(NOW, memberAlias = "SUETONIO", machineLabel = "devmachine")

        val alert = pulse.alerts.single()
        assertEquals("SUETONIO", alert.memberAlias)
        assertEquals("devmachine", alert.machineLabel)
    }

    /**
     * É o que impede um pulso preservado numa falha de leitura de ficar piscando
     * para sempre: o valor antigo expira sozinho ao envelhecer.
     */
    @Test
    fun `pruning drops alerts that aged out of the window`() {
        val pulse = listOf(session("a", SATURATED_CONTEXT_TOKENS, NOW)).toSessionPulse(NOW)

        assertTrue(pulse.prunedAt(NOW + 4.minutes).isPulsing)
        assertFalse(pulse.prunedAt(NOW + 6.minutes).isPulsing)
    }

    @Test
    fun `merging keeps the worst first and drops nothing`() {
        val attention = listOf(session("a", ATTENTION_CONTEXT_TOKENS, NOW)).toSessionPulse(NOW)
        val saturated = listOf(session("b", SATURATED_CONTEXT_TOKENS, NOW)).toSessionPulse(NOW)

        val merged = listOf(attention, saturated).mergeSessionPulses()

        assertEquals(2, merged.alerts.size)
        assertEquals(CliSessionHealth.SATURATED, merged.alerts.first().health)
    }

    @Test
    fun `merging nothing yields the empty pulse`() {
        assertEquals(SessionPulse.EMPTY, listOf(SessionPulse.EMPTY, SessionPulse.EMPTY).mergeSessionPulses())
    }

    private fun session(
        sessionId: String,
        liveContextTokens: Long,
        lastTs: Instant
    ): CliSessionSummary {
        return CliSessionSummary(
            sessionId = sessionId,
            filePath = "/tmp/$sessionId.jsonl",
            cwd = "/home/dev/projetos/$sessionId",
            firstTs = lastTs - 30.minutes,
            lastTs = lastTs,
            primaryModel = "claude-opus-5",
            liveContextTokens = liveContextTokens,
            liveContextModel = "claude-opus-5"
        )
    }
}

package com.usagemonitor.domain

import com.usagemonitor.domain.entity.SourceHealthEvent
import com.usagemonitor.domain.entity.SourceHealthObservation
import com.usagemonitor.domain.entity.evaluateSourceHealth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** #408, AA18: a fonte que falha 15 min avisa uma vez, e de novo quando volta; no silêncio, espera. */
class TelegramSourceHealthTest {

    private val start = Instant.parse("2026-10-06T12:00:00Z")
    private val failing = SourceHealthObservation("cursor", "Cursor", failure = "Sessão do Cursor expirou.", lastReadingAt = start - 2.minutes)
    private val healthy = SourceHealthObservation("cursor", "Cursor", failure = null)

    @Test
    fun `a source failing for the threshold warns once and again when it is back`() {
        val first = evaluateSourceHealth(listOf(failing), emptyMap(), start, silenced = false)
        assertTrue(first.events.isEmpty())

        val due = evaluateSourceHealth(listOf(failing), first.state, start + 15.minutes, silenced = false)
        val broken = assertIs<SourceHealthEvent.Broken>(due.events.single())
        assertEquals(start, broken.failingSince)
        assertEquals(start - 2.minutes, broken.lastReadingAt)

        val still = evaluateSourceHealth(listOf(failing), due.state, start + 20.minutes, silenced = false)
        assertTrue(still.events.isEmpty())

        val back = evaluateSourceHealth(listOf(healthy), still.state, start + 21.minutes, silenced = false)
        assertIs<SourceHealthEvent.Recovered>(back.events.single())
        assertTrue(back.state.isEmpty())
    }

    @Test
    fun `silence postpones, a short failure stays quiet and a disabled source leaves`() {
        val first = evaluateSourceHealth(listOf(failing), emptyMap(), start, silenced = false)
        val muted = evaluateSourceHealth(listOf(failing), first.state, start + 16.minutes, silenced = true)
        assertTrue(muted.events.isEmpty())
        assertEquals(1, evaluateSourceHealth(listOf(failing), muted.state, start + 17.minutes, silenced = false).events.size)

        val shortBack = evaluateSourceHealth(listOf(healthy), first.state, start + 5.minutes, silenced = false)
        assertTrue(shortBack.events.isEmpty())
        assertTrue(evaluateSourceHealth(emptyList(), muted.state, start + 30.minutes, silenced = false).state.isEmpty())
    }
}

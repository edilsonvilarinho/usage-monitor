package com.usagemonitor.domain

import com.usagemonitor.domain.entity.CliSessionDetail
import com.usagemonitor.domain.entity.CliSessionSummary
import com.usagemonitor.domain.entity.CliSessionTurn
import com.usagemonitor.domain.entity.TURN_GAP_CUTOFF_MILLIS
import com.usagemonitor.domain.entity.activeTimeMillisOf
import com.usagemonitor.domain.usecase.ComputeCliSessionAnalyticsUseCase
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

private val START = Instant.parse("2026-08-01T10:00:00Z")

class CliSessionActiveTimeTest {

    @Test
    fun `consecutive gaps inside the cutoff are summed`() {
        val turns = listOf(
            turn(1, START),
            turn(2, START + 2.minutes),
            turn(3, START + 5.minutes)
        )

        assertEquals(5.minutes.inWholeMilliseconds, activeTimeMillisOf(turns))
    }

    /**
     * Sem o corte, uma sessão retomada no dia seguinte "duraria" vinte horas —
     * o intervalo é o usuário longe do teclado, não tempo de trabalho.
     */
    @Test
    fun `a long pause is not working time`() {
        val turns = listOf(
            turn(1, START),
            turn(2, START + 2.minutes),
            turn(3, START + 8.hours),
            turn(4, START + 8.hours + 3.minutes)
        )

        assertEquals(5.minutes.inWholeMilliseconds, activeTimeMillisOf(turns))
    }

    /** O corte é exclusivo: exatamente cinco minutos já é pausa. */
    @Test
    fun `a gap exactly at the cutoff is a pause`() {
        val turns = listOf(
            turn(1, START),
            turn(2, Instant.fromEpochMilliseconds(START.toEpochMilliseconds() + TURN_GAP_CUTOFF_MILLIS))
        )

        assertEquals(0L, activeTimeMillisOf(turns))
    }

    @Test
    fun `a single turn has no measurable time`() {
        assertEquals(0L, activeTimeMillisOf(listOf(turn(1, START))))
        assertEquals(0L, activeTimeMillisOf(emptyList()))
    }

    /** Subagente roda em paralelo: somar os intervalos dele contaria em dobro. */
    @Test
    fun `sidechain turns do not add active time`() {
        val analytics = ComputeCliSessionAnalyticsUseCase()(
            CliSessionDetail(
                summary = summary(),
                turns = listOf(
                    turn(1, START),
                    turn(2, START + 2.minutes, isSidechain = true),
                    turn(3, START + 3.minutes, isSidechain = true),
                    turn(4, START + 4.minutes)
                )
            )
        )

        // Só o intervalo entre os turnos 1 e 4 da thread principal.
        assertEquals(4.minutes.inWholeMilliseconds, analytics.activeTimeMillis)
    }

    @Test
    fun `turns per active hour is derived from the measured time`() {
        val analytics = ComputeCliSessionAnalyticsUseCase()(
            CliSessionDetail(
                summary = summary(),
                turns = listOf(
                    turn(1, START),
                    turn(2, START + 3.minutes),
                    turn(3, START + 6.minutes)
                )
            )
        )

        // 3 turnos em 6 minutos = 30 turnos por hora.
        assertEquals(30.0, analytics.turnsPerActiveHour)
    }

    @Test
    fun `without measured time the rate is zero`() {
        val analytics = ComputeCliSessionAnalyticsUseCase()(
            CliSessionDetail(summary = summary(), turns = listOf(turn(1, START)))
        )

        assertEquals(0L, analytics.activeTimeMillis)
        assertTrue(analytics.turnsPerActiveHour == 0.0)
    }
}

private fun turn(seq: Int, ts: Instant, isSidechain: Boolean = false): CliSessionTurn {
    return CliSessionTurn(
        sessionId = "s1",
        seq = seq,
        messageId = "msg-$seq",
        ts = ts,
        model = "claude-opus-5",
        isSidechain = isSidechain,
        outputTokens = 100L
    )
}

private fun summary(): CliSessionSummary {
    return CliSessionSummary(
        sessionId = "s1",
        filePath = "/tmp/s1.jsonl",
        firstTs = START,
        lastTs = START + 1.hours,
        primaryModel = "claude-opus-5"
    )
}

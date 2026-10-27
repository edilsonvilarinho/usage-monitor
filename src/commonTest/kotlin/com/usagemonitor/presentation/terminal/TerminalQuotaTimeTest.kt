package com.usagemonitor.presentation.terminal

import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.UsageSnapshotQuota
import com.usagemonitor.domain.entity.UsageUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class TerminalQuotaTimeTest {

    private val now = Instant.parse("2026-10-21T17:23:00Z")

    private fun quota(label: String, resetsAt: Instant?, type: PeriodType = PeriodType.INTERVAL) =
        UsageSnapshotQuota(label, type, UsageUnit.PERCENTAGE, 64, 100, 64, resetsAt, "USD")

    @Test
    fun `o tempo da janela sai do reinicio menos a duracao, pelo piso`() {
        // Reinício em 9 min numa janela de 5 h: passaram 4 h 51 min, 97% (97,0).
        val time = TerminalQuotaTime.of(quota("Claude 5h", now + 9.minutes), now)
        assertEquals(now + 9.minutes - 5.hours, time?.startsAt)
        assertEquals(97, time?.elapsedPercent)
        assertEquals(-33, time?.gap(64))
        // 2 h 43 min de 5 h = 54,33%: o piso, como a barra.
        assertEquals(54, TerminalQuotaTime.of(quota("Claude 5h", now + 2.hours + 17.minutes), now)?.elapsedPercent)
        // 5 h de 168 h = 2,98%: 2.
        assertEquals(2, TerminalQuotaTime.of(quota("Codex semanal", now + 163.hours, PeriodType.WEEKLY), now)?.elapsedPercent)
    }

    @Test
    fun `sem duracao conhecida, sem reinicio ou com a janela vencida nao ha tempo`() {
        assertNull(TerminalQuotaTime.of(quota("Go mensal", now + 100.hours, PeriodType.MONTHLY), now))
        assertNull(TerminalQuotaTime.of(quota("MiniMax-M3", now + 1.hours), now))
        assertNull(TerminalQuotaTime.of(quota("Claude 5h", null), now))
        assertNull(TerminalQuotaTime.of(quota("Claude 5h", now), now))
    }
}

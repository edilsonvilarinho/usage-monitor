package com.usagemonitor.domain

import com.usagemonitor.domain.entity.CliQuotaWindows
import com.usagemonitor.domain.entity.CliSessionRange
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

private val NOW = Instant.parse("2026-08-10T23:00:00Z")
private const val FIVE_HOURS_MILLIS = 5L * 60 * 60 * 1_000
private const val SEVEN_DAYS_MILLIS = 7L * 24 * 60 * 60 * 1_000
private const val THIRTY_DAYS_MILLIS = 30L * 24 * 60 * 60 * 1_000

class CliSessionRangeTest {

    @Test
    fun `the 5h window anchors on the quota reset when it is still ahead`() {
        val resetsAt = Instant.parse("2026-08-11T03:30:00Z")

        val window = CliSessionRange.LAST_5H.resolve(
            now = NOW,
            windows = CliQuotaWindows(fiveHourEndsAt = resetsAt)
        )

        assertEquals(resetsAt.toEpochMilliseconds() - FIVE_HOURS_MILLIS, window.cutoffMillis)
        assertEquals(resetsAt, window.endsAt)
        assertTrue(window.isAnchored)
    }

    @Test
    fun `the 7d window ignores the quota anchor`() {
        val windows = CliQuotaWindows(fiveHourEndsAt = Instant.parse("2026-08-11T03:30:00Z"))

        val window = CliSessionRange.LAST_7D.resolve(NOW, windows)

        assertEquals(NOW.toEpochMilliseconds() - SEVEN_DAYS_MILLIS, window.cutoffMillis)
        assertNull(window.endsAt)
        assertFalse(window.isAnchored)
    }

    /**
     * Regressão da issue #28: com o corte de 7d ancorado no reset semanal, uma
     * janela semanal recém-iniciada empurrava o corte para depois do corte de 5h
     * e sessões visíveis em "5h" sumiam em "7 dias".
     */
    @Test
    fun `a wider range never cuts more than a narrower one`() {
        // Janela de quota de 5h iniciada há 1h: o corte ancorado é o mais recente possível.
        val windows = CliQuotaWindows(fiveHourEndsAt = NOW + 4.hours)

        val fiveHour = CliSessionRange.LAST_5H.resolve(NOW, windows)
        val sevenDay = CliSessionRange.LAST_7D.resolve(NOW, windows)
        val thirtyDay = CliSessionRange.LAST_30D.resolve(NOW, windows)

        assertNotNull(fiveHour.cutoffMillis)
        assertNotNull(sevenDay.cutoffMillis)
        assertNotNull(thirtyDay.cutoffMillis)
        assertTrue(
            sevenDay.cutoffMillis!! < fiveHour.cutoffMillis!!,
            "7d cortou em ${sevenDay.cutoffMillis}, depois do corte de 5h ${fiveHour.cutoffMillis}"
        )
        assertTrue(thirtyDay.cutoffMillis!! < sevenDay.cutoffMillis!!)
        assertNull(CliSessionRange.ALL.resolve(NOW, windows).cutoffMillis)
    }

    @Test
    fun `only the 5h window reads the quota anchor`() {
        val windows = CliQuotaWindows(fiveHourEndsAt = Instant.parse("2026-08-11T03:30:00Z"))

        assertEquals(windows.fiveHourEndsAt, CliSessionRange.LAST_5H.resolve(NOW, windows).endsAt)
        assertNull(CliSessionRange.LAST_7D.resolve(NOW, windows).endsAt)
        assertNull(CliSessionRange.LAST_30D.resolve(NOW, windows).endsAt)
        assertNull(CliSessionRange.ALL.resolve(NOW, windows).endsAt)
    }

    @Test
    fun `without a known reset the window slides from now`() {
        val window = CliSessionRange.LAST_5H.resolve(NOW, CliQuotaWindows())

        assertEquals(NOW.toEpochMilliseconds() - FIVE_HOURS_MILLIS, window.cutoffMillis)
        assertNull(window.endsAt)
        assertFalse(window.isAnchored)
    }

    @Test
    fun `an expired reset falls back to the sliding window`() {
        // Quota vencida sem uso novo: não existe janela corrente para ancorar.
        val window = CliSessionRange.LAST_5H.resolve(
            now = NOW,
            windows = CliQuotaWindows(fiveHourEndsAt = Instant.parse("2026-08-10T20:00:00Z"))
        )

        assertEquals(NOW.toEpochMilliseconds() - FIVE_HOURS_MILLIS, window.cutoffMillis)
        assertFalse(window.isAnchored)
    }

    @Test
    fun `the 30d window ignores the quota anchors`() {
        val window = CliSessionRange.LAST_30D.resolve(
            now = NOW,
            windows = CliQuotaWindows(fiveHourEndsAt = Instant.parse("2026-08-11T03:30:00Z"))
        )

        assertEquals(NOW.toEpochMilliseconds() - THIRTY_DAYS_MILLIS, window.cutoffMillis)
        assertFalse(window.isAnchored)
    }

    @Test
    fun `the total window never cuts`() {
        val window = CliSessionRange.ALL.resolve(
            now = NOW,
            windows = CliQuotaWindows(fiveHourEndsAt = Instant.parse("2026-08-11T03:30:00Z"))
        )

        assertNull(window.cutoffMillis)
        assertNull(window.endsAt)
        assertFalse(window.isAnchored)
    }

    @Test
    fun `the default window is the 5h one`() {
        assertEquals(CliSessionRange.LAST_5H, CliSessionRange.DEFAULT)
    }
}

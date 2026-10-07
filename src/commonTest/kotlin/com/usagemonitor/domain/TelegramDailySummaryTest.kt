package com.usagemonitor.domain

import com.usagemonitor.domain.entity.QuietHours
import com.usagemonitor.domain.entity.UsageAlertSettings
import com.usagemonitor.domain.entity.dailySummaryDate
import com.usagemonitor.domain.entity.isDailySummaryDue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Quando o resumo diário do bot sai (#398, Y1). Horas em BRT (UTC-3). */
class TelegramDailySummaryTest {

    private val settings = UsageAlertSettings.DEFAULT
    private val at0759 = Instant.parse("2026-10-07T10:59:00Z")
    private val at0800 = Instant.parse("2026-10-07T11:00:00Z")
    private val at1500 = Instant.parse("2026-10-07T18:00:00Z")

    @Test
    fun `due from the chosen hour, once per local day`() {
        assertFalse(isDailySummaryDue(8, null, at0759, settings))
        assertTrue(isDailySummaryDue(8, null, at0800, settings))
        assertEquals("2026-10-07", dailySummaryDate(at0800))
        assertFalse(isDailySummaryDue(8, "2026-10-07", at1500, settings))
        assertTrue(isDailySummaryDue(8, "2026-10-06", at1500, settings))
    }

    @Test
    fun `off never sends`() {
        assertFalse(isDailySummaryDue(null, null, at1500, settings))
    }

    /** Silêncio adia: dentro dele não sai, e sai quando ele acaba, no mesmo dia. */
    @Test
    fun `quiet hours and snooze delay the summary`() {
        val quiet = settings.copy(quietHours = QuietHours(7, 9))
        assertFalse(isDailySummaryDue(8, null, at0800, quiet))
        assertTrue(isDailySummaryDue(8, null, Instant.parse("2026-10-07T12:00:00Z"), quiet))

        val snoozed = settings.copy(snoozedUntilEpochMillis = at1500.toEpochMilliseconds())
        assertFalse(isDailySummaryDue(8, null, at0800, snoozed))
        assertTrue(isDailySummaryDue(8, null, at1500, snoozed))
    }
}

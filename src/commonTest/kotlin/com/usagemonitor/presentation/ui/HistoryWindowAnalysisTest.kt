package com.usagemonitor.presentation.ui

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.QuotaHourlyDistribution
import com.usagemonitor.domain.entity.QuotaWindowSummary
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class HistoryWindowAnalysisTest {

    private fun window(start: String, exhausted: String? = null, open: Boolean = false, pace: Double? = 12.4) =
        QuotaWindowSummary(
            firstObservedAt = Instant.parse(start),
            lastObservedAt = Instant.parse(start),
            resetsAt = Instant.parse(start),
            peakPercent = 80,
            exhaustedAt = exhausted?.let(Instant::parse),
            consumedPercent = 70.0,
            averagePercentPerHour = pace,
            isOpen = open
        )

    @Test
    fun `exhaustion is counted from the first reading of the window`() {
        assertEquals("3h 12min", exhaustionLabel(window("2026-09-27T10:00:00Z", "2026-09-27T13:12:00Z")))
        assertEquals("45min", exhaustionLabel(window("2026-09-27T10:00:00Z", "2026-09-27T10:45:00Z")))
        assertEquals("2h", exhaustionLabel(window("2026-09-27T10:00:00Z", "2026-09-27T12:00:00Z")))
        assertEquals("—", exhaustionLabel(window("2026-09-27T10:00:00Z")))
    }

    @Test
    fun `open window is marked and shown in BRT`() {
        assertEquals(
            "27/09 07:00 BRT · atual",
            windowStartLabel(window("2026-09-27T10:00:00Z", open = true), AppLanguage.PT)
        )
        assertEquals("27/09 07:00 BRT", windowStartLabel(window("2026-09-27T10:00:00Z"), AppLanguage.PT))
    }

    @Test
    fun `pace without two readings is a dash, not zero`() {
        assertEquals("12 %/h", paceLabel(12.4))
        assertEquals("—", paceLabel(null))
    }

    @Test
    fun `rows are newest first and capped`() {
        val windows = (0 until 12).map { index -> window("2026-09-${10 + index}T10:00:00Z") }

        val rows = windowRowsNewestFirst(windows)

        assertEquals(HISTORY_WINDOW_ROW_LIMIT, rows.size)
        assertEquals(Instant.parse("2026-09-21T10:00:00Z"), rows.first().firstObservedAt)
        assertEquals("8 mais recentes de 12 no intervalo", windowCountSubtitle(12, AppLanguage.PT))
        assertEquals("1 janela no intervalo", windowCountSubtitle(1, AppLanguage.PT))
    }

    @Test
    fun `hourly peak names the hour and its share`() {
        val byHour = MutableList(24) { 0.0 }
        byHour[14] = 60.0
        byHour[9] = 40.0

        assertEquals(
            "Pico às 14h BRT · 60% do consumo",
            hourlyPeakLabel(QuotaHourlyDistribution(byHour), AppLanguage.PT)
        )
    }
}

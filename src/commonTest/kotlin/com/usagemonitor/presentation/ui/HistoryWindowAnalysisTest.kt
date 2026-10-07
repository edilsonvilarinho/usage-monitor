package com.usagemonitor.presentation.ui

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.QuotaHourlyDistribution
import com.usagemonitor.domain.entity.QuotaWindowSummary
import kotlin.time.Instant
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

    @Test
    fun `detail opens the open window, else the newest`() {
        val old = window("2026-09-27T01:00:00Z")
        val open = window("2026-09-27T06:00:00Z", open = true)
        val closedNewest = window("2026-09-27T11:00:00Z")

        assertEquals(open, defaultSelectedWindow(listOf(old, open)))
        assertEquals(closedNewest, defaultSelectedWindow(listOf(old, closedNewest)))
        assertEquals(null, defaultSelectedWindow(emptyList()))
    }

    @Test
    fun `window curve keeps only the readings inside the window`() {
        fun point(at: String) = com.usagemonitor.domain.entity.UsageHistoryPoint(
            capturedAt = Instant.parse(at), used = 1, total = 100, rawUsed = 0, rawTotal = 0,
            periodEndAt = Instant.parse("2026-09-27T12:00:00Z")
        )
        val target = window("2026-09-27T06:00:00Z").copy(lastObservedAt = Instant.parse("2026-09-27T08:00:00Z"))
        val points = listOf(point("2026-09-27T05:59:00Z"), point("2026-09-27T06:00:00Z"), point("2026-09-27T07:00:00Z"),
            point("2026-09-27T08:00:00Z"), point("2026-09-27T08:01:00Z"))

        assertEquals(
            listOf("2026-09-27T06:00:00Z", "2026-09-27T07:00:00Z", "2026-09-27T08:00:00Z").map(Instant::parse),
            pointsOfWindow(points, target).map { it.capturedAt }
        )
    }

    @Test
    fun `window list detail names the peak and the exhaustion only when it happened`() {
        assertEquals("pico 80 %", windowListDetail(window("2026-09-27T10:00:00Z"), AppLanguage.PT))
        assertEquals("pico 80 % · esgotou em 2h", windowListDetail(window("2026-09-27T10:00:00Z", "2026-09-27T12:00:00Z"), AppLanguage.PT))
    }

    @Test
    fun `window used before the first reading does not read as zero minutes active`() {
        val at = Instant.parse("2026-10-06T00:02:00Z")
        val usedBefore = window("2026-10-06T00:02:00Z").copy(activeFrom = at, activeUntil = at)
        val active = window("2026-10-06T00:02:00Z").copy(activeFrom = at, activeUntil = Instant.parse("2026-10-06T01:00:00Z"))

        assertEquals("usada antes da 1ª leitura", activeSpanLabel(usedBefore, AppLanguage.PT))
        assertEquals("21:02 → 22:00 · 58min", activeSpanLabel(active, AppLanguage.PT))
        assertEquals("—", activeSpanLabel(window("2026-10-06T00:02:00Z"), AppLanguage.PT))
    }
}

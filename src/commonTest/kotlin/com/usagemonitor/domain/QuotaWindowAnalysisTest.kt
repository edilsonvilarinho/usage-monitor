package com.usagemonitor.domain

import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.UsageHistoryPoint
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.domain.entity.quotaHourlyDistributionOf
import com.usagemonitor.domain.entity.quotaWindowStatsOf
import com.usagemonitor.domain.entity.quotaWindowsOf
import com.usagemonitor.domain.entity.splitIntoQuotaWindows
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QuotaWindowAnalysisTest {

    // Anthropic grava a cota em unidade crua (4.500 = 100% da 5h): os
    // percentuais saem sobre o total da própria janela, não sobre a unidade.
    private fun point(
        at: String,
        percent: Long,
        resetsAt: String,
        total: Long = 4_500L,
        hasKnownResetAt: Boolean = true
    ) = UsageHistoryPoint(
        capturedAt = Instant.parse(at),
        used = percent,
        total = 100L,
        rawUsed = percent * total / 100L,
        rawTotal = total,
        periodEndAt = Instant.parse(resetsAt),
        hasKnownResetAt = hasKnownResetAt
    )

    private val first = "2026-09-27T05:00:00Z"
    private val second = "2026-09-27T10:00:00Z"

    // Janela 1: 10% → 100% (esgota às 03h), janela 2: 0% → 30%, aberta.
    private val twoWindows = listOf(
        point("2026-09-27T01:00:00Z", 10, first),
        point("2026-09-27T02:00:00Z", 60, first),
        point("2026-09-27T03:00:00Z", 100, first),
        point("2026-09-27T04:00:00Z", 100, first),
        point("2026-09-27T06:00:00Z", 0, second),
        point("2026-09-27T07:00:00Z", 30, second)
    )

    @Test
    fun `a reset starts a new window`() {
        val windows = splitIntoQuotaWindows(twoWindows, UsageUnit.PERCENTAGE)

        assertEquals(listOf(4, 2), windows.map { it.size })
    }

    @Test
    fun `resets_at jitter inside the tolerance does not split a window`() {
        val points = listOf(
            point("2026-09-27T01:00:00Z", 10, "2026-09-27T05:00:00Z"),
            point("2026-09-27T02:00:00Z", 20, "2026-09-27T05:00:01Z")
        )

        assertEquals(1, splitIntoQuotaWindows(points, UsageUnit.PERCENTAGE).size)
    }

    @Test
    fun `each window reports peak, exhaustion, consumption and openness`() {
        val windows = quotaWindowsOf(twoWindows, UsageUnit.PERCENTAGE, PeriodType.INTERVAL)

        assertEquals(2, windows.size)
        val closed = windows[0]
        assertEquals(100, closed.peakPercent)
        assertEquals(Instant.parse("2026-09-27T03:00:00Z"), closed.exhaustedAt)
        assertEquals(90.0, closed.consumedPercent, 0.001)
        assertEquals(30.0, closed.averagePercentPerHour!!, 0.001)
        assertEquals(false, closed.isOpen)

        val open = windows[1]
        assertEquals(30, open.peakPercent)
        assertNull(open.exhaustedAt, "não esgotou é null, nunca zero")
        assertEquals(true, open.isOpen)
    }

    @Test
    fun `stats average only closed windows`() {
        val stats = quotaWindowStatsOf(quotaWindowsOf(twoWindows, UsageUnit.PERCENTAGE, PeriodType.INTERVAL))!!

        assertEquals(2, stats.windowCount)
        assertEquals(1, stats.exhaustedCount)
        assertEquals(100.0, stats.averagePeakPercent!!, 0.001)
        assertEquals(90.0, stats.averageConsumedPercent!!, 0.001)
    }

    @Test
    fun `stats without a closed window have no averages`() {
        val onlyOpen = quotaWindowsOf(twoWindows.takeLast(2), UsageUnit.PERCENTAGE, PeriodType.INTERVAL)

        val stats = quotaWindowStatsOf(onlyOpen)!!

        assertNull(stats.averagePeakPercent)
        assertNull(stats.averageConsumedPercent)
    }

    @Test
    fun `balances, reported quotas and quotas without a known reset have no windows`() {
        assertTrue(quotaWindowsOf(twoWindows, UsageUnit.CURRENCY_USD, PeriodType.INTERVAL).isEmpty())
        assertTrue(quotaWindowsOf(twoWindows, UsageUnit.PERCENTAGE, PeriodType.REPORTED).isEmpty())
        val noReset = twoWindows.map { it.copy(hasKnownResetAt = false) }
        assertTrue(quotaWindowsOf(noReset, UsageUnit.PERCENTAGE, PeriodType.INTERVAL).isEmpty())
        assertNull(quotaHourlyDistributionOf(noReset, UsageUnit.PERCENTAGE, PeriodType.INTERVAL))
    }

    // A 5h da Anthropic sem sessão vem sem `resets_at`: 0% e a sentinela.
    private fun idle(at: String) = point(at, 0, "2100-01-01T00:00:00Z", hasKnownResetAt = false)

    @Test
    fun `idle readings without a reset do not hide the windows before them`() {
        val points = twoWindows + idle("2026-09-27T11:00:00Z") + idle("2026-09-27T12:00:00Z")

        val windows = quotaWindowsOf(points, UsageUnit.PERCENTAGE, PeriodType.INTERVAL)

        assertEquals(2, windows.size)
        assertEquals(listOf(100, 30), windows.map { it.peakPercent })
        assertEquals(false, windows.last().isOpen, "a leitura mais nova é ociosa e o reinício passou")
        val distribution = quotaHourlyDistributionOf(points, UsageUnit.PERCENTAGE, PeriodType.INTERVAL)!!
        assertEquals(120.0, distribution.percentByHour.sum(), 0.001)
    }

    @Test
    fun `idle readings between two sessions do not become a window`() {
        val points = listOf(
            point("2026-09-27T01:00:00Z", 10, first),
            point("2026-09-27T02:00:00Z", 40, first),
            idle("2026-09-27T06:00:00Z"),
            idle("2026-09-27T07:00:00Z"),
            point("2026-09-27T08:00:00Z", 5, "2026-09-27T13:00:00Z"),
            point("2026-09-27T09:00:00Z", 25, "2026-09-27T13:00:00Z")
        )

        val windows = quotaWindowsOf(points, UsageUnit.PERCENTAGE, PeriodType.INTERVAL)

        assertEquals(listOf(40, 25), windows.map { it.peakPercent })
        assertEquals(listOf(false, true), windows.map { it.isOpen })
    }

    @Test
    fun `hourly distribution uses local BRT hours and ignores the reset drop`() {
        val distribution = quotaHourlyDistributionOf(
            twoWindows,
            UsageUnit.PERCENTAGE,
            PeriodType.INTERVAL,
            TimeZone.of("America/Sao_Paulo")
        )!!

        // 02:00Z = 23h BRT do dia anterior (+50), 03:00Z = 0h BRT (+40),
        // 07:00Z = 4h BRT (+30). A queda de 100 para 0 no reinício não entra.
        assertEquals(24, distribution.percentByHour.size)
        assertEquals(50.0, distribution.percentByHour[23], 0.001)
        assertEquals(40.0, distribution.percentByHour[0], 0.001)
        assertEquals(30.0, distribution.percentByHour[4], 0.001)
        assertEquals(120.0, distribution.percentByHour.sum(), 0.001)
        assertEquals(23, distribution.peakHour)
    }

    @Test
    fun `hourly distribution without growth is absent`() {
        val flat = listOf(
            point("2026-09-27T01:00:00Z", 10, first),
            point("2026-09-27T02:00:00Z", 10, first)
        )

        assertNull(quotaHourlyDistributionOf(flat, UsageUnit.PERCENTAGE, PeriodType.INTERVAL))
    }
}

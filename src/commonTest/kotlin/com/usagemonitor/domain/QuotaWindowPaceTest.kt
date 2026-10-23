package com.usagemonitor.domain

import com.usagemonitor.domain.entity.PacePathPoint
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.UsageHistoryPoint
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.domain.entity.elapsedFractionOf
import com.usagemonitor.domain.entity.exhaustionTimeOf
import com.usagemonitor.domain.entity.pacePathOf
import com.usagemonitor.domain.entity.quotaWindowLengthMillis
import com.usagemonitor.domain.entity.quotaWindowPacePathsOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Diagonal do ritmo (#463, direção AH1): caminho da janela, tempo passado e quando esgota. */
class QuotaWindowPaceTest {

    private val reset = Instant.parse("2026-10-23T22:30:00Z")
    private val fiveHours = 5.hours.inWholeMilliseconds

    private fun point(at: Instant, percent: Int, periodEnd: Instant = reset, known: Boolean = true) =
        UsageHistoryPoint(at, percent.toLong(), 100L, 0L, 0L, periodEnd, known)

    @Test
    fun `window length comes from the label, and is unknown when the source does not define it`() {
        assertEquals(fiveHours, quotaWindowLengthMillis("Claude 5h", PeriodType.INTERVAL))
        assertEquals(7 * 24.hours.inWholeMilliseconds, quotaWindowLengthMillis("Claude 7d", PeriodType.WEEKLY))
        assertEquals(7 * 24.hours.inWholeMilliseconds, quotaWindowLengthMillis("Go semanal", PeriodType.WEEKLY))
        // A intervalar do MiniMax não é de 5 horas, e a mensal varia: sem diagonal.
        assertNull(quotaWindowLengthMillis("MiniMax-M3", PeriodType.INTERVAL))
        assertNull(quotaWindowLengthMillis("Go mensal", PeriodType.MONTHLY))
    }

    @Test
    fun `path places each reading on the share of the window that had passed`() {
        val start = reset - 5.hours
        val path = pacePathOf(listOf(point(start + 4.hours, 61), point(start + 1.hours, 2)), reset, fiveHours)!!
        assertEquals(start, path.startsAt)
        // Ordem de leitura, mesmo com a entrada fora de ordem.
        assertEquals(listOf(PacePathPoint(0.2, 2.0), PacePathPoint(0.8, 61.0)), path.points)
        // Leitura de antes do início nominal fica no zero do eixo.
        assertEquals(0.0, pacePathOf(listOf(point(start - 10.minutes, 0)), reset, fiveHours)!!.points.single().elapsedFraction)
        assertNull(pacePathOf(emptyList(), reset, fiveHours))
    }

    @Test
    fun `gap to the pace line is positive above the diagonal`() {
        assertEquals(-33.0, PacePathPoint(0.97, 64.0).gapToPaceLine, 1e-9)
        assertEquals(40.0, PacePathPoint(0.54, 94.0).gapToPaceLine, 1e-9)
    }

    @Test
    fun `paths split the series at each reset and skip idle readings`() {
        val previousReset = reset - 5.hours
        val points = listOf(
            point(previousReset - 3.hours, 10, previousReset),
            point(previousReset - 1.hours, 90, previousReset),
            point(previousReset + 30.minutes, 0, reset, known = false),
            point(previousReset + 1.hours, 5),
            point(previousReset + 4.hours, 64)
        )
        val paths = quotaWindowPacePathsOf(points, UsageUnit.PERCENTAGE, fiveHours)
        assertEquals(listOf(previousReset, reset), paths.map { path -> path.resetsAt })
        assertEquals(2, paths.last().points.size)
    }

    @Test
    fun `elapsed fraction needs both ends of the window`() {
        assertEquals(0.97, elapsedFractionOf(reset - 5.hours, reset, reset - 9.minutes)!!, 1e-9)
        assertNull(elapsedFractionOf(null, reset, reset))
        assertEquals(1.0, elapsedFractionOf(reset - 5.hours, reset, reset + 1.hours))
    }

    @Test
    fun `exhaustion time only exists before the reset`() {
        val now = Instant.parse("2026-10-23T22:21:00Z")
        val weeklyReset = Instant.parse("2026-10-27T04:00:00Z")
        // 94% a 3 %/h: 2 h até 100%, bem antes do reinício.
        assertEquals(now + 2.hours, exhaustionTimeOf(94, 3.0, now, weeklyReset))
        // 64% a 20 %/h com 9 min para o reinício: cabe.
        assertNull(exhaustionTimeOf(64, 20.0, now, now + 9.minutes))
        assertNull(exhaustionTimeOf(64, null, now, weeklyReset))
        assertNull(exhaustionTimeOf(64, 0.0, now, weeklyReset))
        assertNull(exhaustionTimeOf(100, 3.0, now, weeklyReset))
    }
}

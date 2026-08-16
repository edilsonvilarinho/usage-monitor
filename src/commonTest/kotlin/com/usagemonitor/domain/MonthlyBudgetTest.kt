package com.usagemonitor.domain

import com.usagemonitor.domain.entity.AccountCreditUsage
import com.usagemonitor.domain.entity.CliUsageBucket
import com.usagemonitor.domain.entity.MICROS_PER_USD
import com.usagemonitor.domain.entity.monthlyBudgetStatusOf
import com.usagemonitor.domain.entity.startOfMonthMillis
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val BRT = TimeZone.of("America/Sao_Paulo")

class MonthlyBudgetTest {

    /**
     * O mês é o do fuso da apresentação: às 22h do dia 31 em BRT já é dia 1 em
     * UTC, e o gasto cairia no mês seguinte.
     */
    @Test
    fun `the month starts in the presentation time zone`() {
        val lateNight = Instant.parse("2026-09-01T01:00:00Z")

        val start = Instant.fromEpochMilliseconds(startOfMonthMillis(lateNight, BRT))

        // 01:00 UTC = 31/08 22:00 BRT, então o mês corrente ainda é agosto.
        assertEquals(Instant.parse("2026-08-01T03:00:00Z"), start)
    }

    @Test
    fun `without a cap there is no budget`() {
        assertNull(
            monthlyBudgetStatusOf(
                limitMicros = 0L,
                totals = CliUsageBucket(costMicros = 5L * MICROS_PER_USD),
                now = Instant.parse("2026-08-16T12:00:00Z"),
                timeZone = BRT
            )
        )
    }

    @Test
    fun `the projection extends the daily pace to the end of the month`() {
        // Dia 15 de agosto (31 dias) com US$ 150 gastos: US$ 310 projetados.
        val status = monthlyBudgetStatusOf(
            limitMicros = 200L * MICROS_PER_USD,
            totals = CliUsageBucket(costMicros = 150L * MICROS_PER_USD),
            now = Instant.parse("2026-08-15T15:00:00Z"),
            timeZone = BRT
        )!!

        assertEquals(15, status.daysElapsed)
        assertEquals(31, status.daysInMonth)
        assertEquals(310L * MICROS_PER_USD, status.projectedMicros)
        assertTrue(status.willExceed)
        assertFalse(status.isExceeded)
    }

    @Test
    fun `the cap is reported as exceeded once spending reaches it`() {
        val status = monthlyBudgetStatusOf(
            limitMicros = 100L * MICROS_PER_USD,
            totals = CliUsageBucket(costMicros = 120L * MICROS_PER_USD),
            now = Instant.parse("2026-08-20T15:00:00Z"),
            timeZone = BRT
        )!!

        assertTrue(status.isExceeded)
        assertEquals(1.2, status.share)
    }

    /** Turno sem preço torna o gasto um piso, e o status precisa dizê-lo. */
    @Test
    fun `unpriced turns make the spending a floor`() {
        val status = monthlyBudgetStatusOf(
            limitMicros = 100L * MICROS_PER_USD,
            totals = CliUsageBucket(costMicros = 10L * MICROS_PER_USD, unpricedTurnCount = 3),
            now = Instant.parse("2026-08-20T15:00:00Z"),
            timeZone = BRT
        )!!

        assertFalse(status.isSpendComplete)
    }

    @Test
    fun `february is handled on leap years`() {
        val leap = monthlyBudgetStatusOf(
            limitMicros = MICROS_PER_USD,
            totals = CliUsageBucket(),
            now = Instant.parse("2028-02-10T15:00:00Z"),
            timeZone = BRT
        )!!
        val common = monthlyBudgetStatusOf(
            limitMicros = MICROS_PER_USD,
            totals = CliUsageBucket(),
            now = Instant.parse("2026-02-10T15:00:00Z"),
            timeZone = BRT
        )!!

        assertEquals(29, leap.daysInMonth)
        assertEquals(28, common.daysInMonth)
    }

    /** Os créditos vêm em unidades menores da moeda **da conta**, não em USD. */
    @Test
    fun `account credits keep their own currency`() {
        val credits = AccountCreditUsage(
            usedMinorUnits = 27_500L,
            limitMinorUnits = 55_000L,
            currencyCode = "BRL"
        )

        assertEquals(0.5, credits.share)
        assertEquals("BRL", credits.currencyCode)
    }
}

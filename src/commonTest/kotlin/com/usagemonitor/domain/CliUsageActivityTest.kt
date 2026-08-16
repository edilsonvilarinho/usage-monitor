package com.usagemonitor.domain

import com.usagemonitor.domain.entity.CliHourlyUsageRow
import com.usagemonitor.domain.entity.CliUsageBucket
import com.usagemonitor.domain.entity.MIN_BURN_RATE_ELAPSED_MILLIS
import com.usagemonitor.domain.entity.ModelPricingTable
import com.usagemonitor.domain.entity.burnRateOf
import com.usagemonitor.domain.entity.toActivityHeatmap
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

private const val OPUS = "claude-opus-5"
private val BRT = TimeZone.of("America/Sao_Paulo")

class CliUsageActivityTest {

    /**
     * O SQLite agruparia em UTC. BRT é UTC-3, então 01:00 UTC de terça é 22:00
     * BRT de segunda — dia **e** hora mudam, e agrupar errado trocaria a
     * madrugada pela noite anterior.
     */
    @Test
    fun `hours land on the local day and hour`() {
        val heatmap = listOf(
            hourly("2026-08-11T01:00:00Z")
        ).toActivityHeatmap(BRT)

        val cell = heatmap.cells.single()
        assertEquals(DayOfWeek.MONDAY, cell.dayOfWeek)
        assertEquals(22, cell.hour)
    }

    @Test
    fun `the same local hour on different days is summed`() {
        val heatmap = listOf(
            hourly("2026-08-10T13:00:00Z", turnCount = 1),
            hourly("2026-08-17T13:00:00Z", turnCount = 2)
        ).toActivityHeatmap(BRT)

        val cell = heatmap.cells.single()
        assertEquals(DayOfWeek.MONDAY, cell.dayOfWeek)
        assertEquals(10, cell.hour)
        assertEquals(3, cell.turnCount)
    }

    /** Dois turnos de Opus pesam mais que vinte de Haiku: a intensidade é do custo. */
    @Test
    fun `intensity is relative to the window peak`() {
        val heatmap = listOf(
            hourly("2026-08-10T13:00:00Z", inputTokens = 4_000_000),
            hourly("2026-08-10T14:00:00Z", inputTokens = 1_000_000)
        ).toActivityHeatmap(BRT)

        assertEquals(1f, heatmap.intensityAt(DayOfWeek.MONDAY, 10))
        assertEquals(0.25f, heatmap.intensityAt(DayOfWeek.MONDAY, 11))
        assertEquals(0f, heatmap.intensityAt(DayOfWeek.MONDAY, 12))
    }

    @Test
    fun `an unpriced model still counts turns`() {
        val heatmap = listOf(
            hourly("2026-08-10T13:00:00Z", model = "modelo-inexistente", turnCount = 5, inputTokens = 1_000)
        ).toActivityHeatmap(BRT)

        val cell = heatmap.cells.single()
        assertEquals(5, cell.turnCount)
        assertEquals(0L, cell.costMicros)
        assertEquals(0f, heatmap.intensityAt(cell.dayOfWeek, cell.hour))
    }

    @Test
    fun `equal readings produce equal heatmaps`() {
        val rows = listOf(
            hourly("2026-08-10T13:00:00Z"),
            hourly("2026-08-11T09:00:00Z"),
            hourly("2026-08-12T20:00:00Z")
        )

        assertEquals(rows.toActivityHeatmap(BRT), rows.reversed().toActivityHeatmap(BRT))
    }

    /**
     * O denominador é o tempo decorrido, não a duração nominal: cinco horas
     * fixas subestimariam o ritmo em toda a primeira hora da janela.
     */
    @Test
    fun `the rate divides by the elapsed time not by the nominal window`() {
        val windowStart = Instant.parse("2026-08-10T12:00:00Z")
        val now = windowStart + 1.hours

        val rate = burnRateOf(
            totals = CliUsageBucket(inputTokens = 1_000L, costMicros = 2_000_000L),
            windowStart = windowStart,
            now = now
        )

        assertNotNull(rate)
        assertEquals(1_000.0, rate.tokensPerHour)
        assertEquals(2_000_000L, rate.costMicrosPerHour)
    }

    @Test
    fun `the projection extends the pace to the end of the window`() {
        val windowStart = Instant.parse("2026-08-10T12:00:00Z")
        val now = windowStart + 1.hours
        val endsAt = windowStart + 3.hours

        val rate = burnRateOf(
            totals = CliUsageBucket(costMicros = 2_000_000L),
            windowStart = windowStart,
            now = now,
            windowEndsAt = endsAt
        )

        // US$ 2 gastos em 1h, faltando 2h ao mesmo ritmo: US$ 6 no fechamento.
        assertEquals(6_000_000L, rate?.projectedCostMicros)
    }

    /** Sem `resets_at` conhecido não se projeta contra um fim inventado. */
    @Test
    fun `without a window end there is no projection`() {
        val windowStart = Instant.parse("2026-08-10T12:00:00Z")

        val rate = burnRateOf(
            totals = CliUsageBucket(costMicros = 2_000_000L),
            windowStart = windowStart,
            now = windowStart + 1.hours
        )

        assertNull(rate?.projectedCostMicros)
    }

    @Test
    fun `an open-ended window has no rate`() {
        val rate = burnRateOf(
            totals = CliUsageBucket(costMicros = 2_000_000L),
            windowStart = null,
            now = Instant.parse("2026-08-10T12:00:00Z")
        )

        assertNull(rate)
    }

    /** Um minuto com um turno caro daria "US$ 60/h": verdadeiro e inútil. */
    @Test
    fun `a window that just started has no rate`() {
        val windowStart = Instant.parse("2026-08-10T12:00:00Z")

        assertNull(
            burnRateOf(
                totals = CliUsageBucket(costMicros = 1_000_000L),
                windowStart = windowStart,
                now = windowStart + 1.minutes
            )
        )
        assertNotNull(
            burnRateOf(
                totals = CliUsageBucket(costMicros = 1_000_000L),
                windowStart = windowStart,
                now = Instant.fromEpochMilliseconds(
                    windowStart.toEpochMilliseconds() + MIN_BURN_RATE_ELAPSED_MILLIS
                )
            )
        )
    }

    @Test
    fun `the heatmap prices hours with the model table`() {
        val pricing = ModelPricingTable.forModel(OPUS)!!
        val heatmap = listOf(hourly("2026-08-10T13:00:00Z", inputTokens = 1_000_000)).toActivityHeatmap(BRT)

        assertEquals(pricing.costMicros(inputTokens = 1_000_000), heatmap.cells.single().costMicros)
        assertTrue(heatmap.peakCostMicros > 0L)
    }
}

private fun hourly(
    hourStart: String,
    model: String? = OPUS,
    turnCount: Int = 1,
    inputTokens: Long = 0L
): CliHourlyUsageRow {
    return CliHourlyUsageRow(
        hourStartMillis = Instant.parse(hourStart).toEpochMilliseconds(),
        model = model,
        turnCount = turnCount,
        inputTokens = inputTokens
    )
}

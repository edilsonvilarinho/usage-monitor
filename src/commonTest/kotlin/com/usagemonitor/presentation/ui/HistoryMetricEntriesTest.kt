package com.usagemonitor.presentation.ui

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.QuotaWindowStats
import com.usagemonitor.domain.entity.UsageForecast
import com.usagemonitor.domain.entity.UsageHistoryPoint
import com.usagemonitor.domain.entity.UsageHistorySeries
import com.usagemonitor.domain.entity.UsageUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private val BRT = TimeZone.of("America/Sao_Paulo")

private fun brt(day: Int, hour: Int): Instant {
    return LocalDateTime(2026, 9, day, hour, 0).toInstant(BRT)
}

private fun point(at: Instant, used: Long): UsageHistoryPoint {
    return UsageHistoryPoint(
        capturedAt = at,
        used = used,
        total = 1_000L,
        rawUsed = used,
        rawTotal = 1_000L,
        periodEndAt = at
    )
}

/**
 * A linha "Hoje vs. mediana diária" da tabela de métricas do Histórico.
 *
 * A função não é `@Composable`, então a decisão de mostrar ou não a linha é
 * exercitável sem cena — que é justamente o que separa a regra do desenho.
 */
class HistoryMetricEntriesTest {

    @Test
    fun `the daily median line appears when there is a baseline`() {
        val entries = historyMetricEntries(
            source = ApiSource.MINIMAX,
            series = seriesWithBaseline(todayDelta = 400L),
            language = AppLanguage.PT,
            referenceAt = brt(4, 12)
        )

        assertEquals("4,0× (3 dias)", entries.valueOf("Hoje vs. mediana diária"))
    }

    /** Sem histórico suficiente não há o que afirmar, e a linha não existe. */
    @Test
    fun `without enough history the line is absent`() {
        val entries = historyMetricEntries(
            source = ApiSource.MINIMAX,
            series = seriesWithBaseline(todayDelta = 400L, baselineDays = 1),
            language = AppLanguage.PT,
            referenceAt = brt(4, 12)
        )

        assertNull(entries.valueOf("Hoje vs. mediana diária"))
    }

    /**
     * Sem carimbo de coleta não há referência de tempo. A alternativa seria ler o
     * relógio dentro da composição, e aí o valor mudaria a cada recomposição.
     */
    @Test
    fun `without a reference timestamp the line is absent`() {
        val entries = historyMetricEntries(
            source = ApiSource.MINIMAX,
            series = seriesWithBaseline(todayDelta = 400L),
            language = AppLanguage.PT,
            referenceAt = null
        )

        assertNull(entries.valueOf("Hoje vs. mediana diária"))
    }

    /** A linha nova entra depois da comparação de janelas, não no lugar dela. */
    @Test
    fun `the line does not replace the previous period comparison`() {
        val entries = historyMetricEntries(
            source = ApiSource.MINIMAX,
            series = seriesWithBaseline(todayDelta = 400L),
            language = AppLanguage.PT,
            referenceAt = brt(4, 12)
        )

        assertEquals("Hoje vs. mediana diária", entries.last().label)
        assertEquals("Uso atual", entries.first().label)
    }

    @Test
    fun `english translates the line`() {
        val entries = historyMetricEntries(
            source = ApiSource.MINIMAX,
            series = seriesWithBaseline(todayDelta = 400L),
            language = AppLanguage.EN,
            referenceAt = brt(4, 12)
        )

        assertEquals("4.0× (3 days)", entries.valueOf("Today vs. daily median"))
    }

    /**
     * Com janelas conhecidas, "Consumido no período" — a soma das subidas de
     * todas as janelas sobre o total de uma, que dava 173% (issue #320) — dá
     * lugar ao resumo das janelas.
     */
    @Test
    fun `window summary replaces the cross-window consumed sum`() {
        val series = seriesWithBaseline(todayDelta = 400L).copy(
            windowStats = QuotaWindowStats(
                windowCount = 4,
                exhaustedCount = 1,
                averagePeakPercent = 71.6,
                averageConsumedPercent = 64.2
            )
        )

        val entries = historyMetricEntries(ApiSource.ANTHROPIC, series, AppLanguage.PT, referenceAt = null)

        assertNull(entries.valueOf("Consumido no período"))
        assertEquals("4 · 1 esgotou", entries.valueOf("Janelas no intervalo"))
        assertEquals("72 %", entries.valueOf("Pico médio por janela"))
        assertEquals("64 %", entries.valueOf("Consumo médio por janela"))
    }

    @Test
    fun `without a closed window the averages are absent, not zero`() {
        val series = seriesWithBaseline(todayDelta = 400L).copy(
            windowStats = QuotaWindowStats(
                windowCount = 1,
                exhaustedCount = 0,
                averagePeakPercent = null,
                averageConsumedPercent = null
            )
        )

        val entries = historyMetricEntries(ApiSource.ANTHROPIC, series, AppLanguage.PT, referenceAt = null)

        assertEquals("1", entries.valueOf("Janelas no intervalo"))
        assertNull(entries.valueOf("Pico médio por janela"))
        assertNull(entries.valueOf("Consumo médio por janela"))
    }

    @Test
    fun `without windows the consumed line stays`() {
        val entries = historyMetricEntries(
            ApiSource.ANTHROPIC,
            seriesWithBaseline(todayDelta = 400L),
            AppLanguage.PT,
            referenceAt = null
        )

        assertEquals("40 %", entries.valueOf("Consumido no período"))
        assertNull(entries.valueOf("Janelas no intervalo"))
    }

    private fun List<HistoryMetricEntry>.valueOf(label: String): String? {
        return firstOrNull { entry -> entry.label == label }?.value
    }

    private fun seriesWithBaseline(todayDelta: Long, baselineDays: Int = 3): UsageHistorySeries {
        val points = mutableListOf<UsageHistoryPoint>()
        repeat(baselineDays) { index ->
            val day = 3 - index
            points += point(brt(day, 9), 0L)
            points += point(brt(day, 11), 100L)
        }
        points += point(brt(4, 9), 0L)
        points += point(brt(4, 11), todayDelta)

        return UsageHistorySeries(
            quotaLabel = "Sessão 5h",
            periodType = PeriodType.INTERVAL,
            unit = UsageUnit.PERCENTAGE,
            points = points.sortedBy { item -> item.capturedAt },
            currentDisplayUsed = todayDelta,
            currentDisplayTotal = 1_000L,
            deltaDisplayUsed = todayDelta,
            averageDisplayConsumptionPerHour = 0.0,
            currentPeriodEndAt = brt(4, 12),
            forecast = UsageForecast.InsufficientData,
            riskSummary = null
        )
    }
}

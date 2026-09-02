package com.usagemonitor.domain

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.DEFAULT_SPIKE_FACTOR
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.UsageForecast
import com.usagemonitor.domain.entity.UsageHistoryPoint
import com.usagemonitor.domain.entity.UsageHistorySeries
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.domain.entity.dailyBaseline
import com.usagemonitor.domain.entity.detectSpike
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private val BRT = TimeZone.of("America/Sao_Paulo")
private val TARGET = UsageTargetKey.forSource(ApiSource.MINIMAX)
private const val QUOTA_TOTAL = 1_000L

/** Um instante a partir da hora **local** — é nela que o recorte por dia acontece. */
private fun brt(day: Int, hour: Int, minute: Int = 0): Instant {
    return LocalDateTime(2026, 9, day, hour, minute).toInstant(BRT)
}

private fun point(at: Instant, used: Long): UsageHistoryPoint {
    return UsageHistoryPoint(
        capturedAt = at,
        used = used,
        total = QUOTA_TOTAL,
        rawUsed = used,
        rawTotal = QUOTA_TOTAL,
        periodEndAt = at
    )
}

private fun series(
    points: List<UsageHistoryPoint>,
    periodType: PeriodType = PeriodType.INTERVAL,
    unit: UsageUnit = UsageUnit.PERCENTAGE,
    total: Long = QUOTA_TOTAL
): UsageHistorySeries {
    return UsageHistorySeries(
        quotaLabel = "Claude 5h",
        periodType = periodType,
        unit = unit,
        points = points,
        currentDisplayUsed = points.lastOrNull()?.displayUsed ?: 0L,
        currentDisplayTotal = total,
        deltaDisplayUsed = 0L,
        averageDisplayConsumptionPerHour = 0.0,
        currentPeriodEndAt = points.lastOrNull()?.periodEndAt ?: brt(1, 0),
        forecast = UsageForecast.InsufficientData,
        riskSummary = null
    )
}

/** Um dia que sobe [delta] entre as 9h e as 12h locais, e mais 500 à noite. */
private fun regularDay(day: Int, delta: Long): List<UsageHistoryPoint> {
    return listOf(
        point(brt(day, 9), 0L),
        point(brt(day, 12), delta),
        point(brt(day, 20), delta + 500L)
    )
}

class UsageSpikeTest {

    @Test
    fun `measures today against the median of the previous days`() {
        val points = regularDay(1, 100L) + regularDay(2, 100L) + regularDay(3, 100L) +
            listOf(point(brt(4, 9), 0L), point(brt(4, 12), 400L))

        val baseline = series(points).dailyBaseline(now = brt(4, 12, 30), timeZone = BRT)

        assertNotNull(baseline)
        assertEquals(400L, baseline.todayDelta)
        assertEquals(100L, baseline.baselineDelta)
        assertEquals(3, baseline.completeDays)
        assertEquals(4.0, baseline.factor)
    }

    /**
     * O ponto das 20h dos dias anteriores está fora do recorte das 12h30 e não pode
     * entrar: com ele, a referência seria 600 e o dia de hoje pareceria abaixo do
     * normal às 12h30 de todo dia.
     */
    @Test
    fun `the previous days are cut at the same time of day`() {
        val points = regularDay(1, 100L) + regularDay(2, 100L) + regularDay(3, 100L) +
            listOf(point(brt(4, 9), 0L), point(brt(4, 12), 100L))

        val baseline = series(points).dailyBaseline(now = brt(4, 12, 30), timeZone = BRT)

        assertNotNull(baseline)
        assertEquals(100L, baseline.baselineDelta)
        assertEquals(1.0, baseline.factor)
    }

    /**
     * Quatro dias anteriores, um deles um incidente isolado. A média daria 325 e o
     * dia de hoje sairia em 1,23× — sem aviso. A mediana devolve 100 e o aviso sai.
     */
    @Test
    fun `the median ignores a single previous incident`() {
        val points = regularDay(1, 100L) + regularDay(2, 100L) + regularDay(3, 100L) +
            regularDay(4, 1_000L) +
            listOf(point(brt(5, 9), 0L), point(brt(5, 12), 400L))

        val baseline = series(points).dailyBaseline(now = brt(5, 12, 30), timeZone = BRT)

        assertNotNull(baseline)
        assertEquals(100L, baseline.baselineDelta)
        assertEquals(4, baseline.completeDays)
        assertEquals(4.0, baseline.factor)
    }

    /**
     * A cota de 5h reinicia várias vezes dentro do dia. O reset derruba o acumulado,
     * e contá-lo como queda apagaria o consumo anterior — o dia inteiro tem de somar.
     */
    @Test
    fun `a window reset inside the day does not erase what was consumed`() {
        val today = listOf(
            point(brt(4, 9), 0L),
            point(brt(4, 10), 300L),
            point(brt(4, 11), 20L),
            point(brt(4, 12), 200L)
        )
        val points = regularDay(1, 100L) + regularDay(2, 100L) + regularDay(3, 100L) + today

        val baseline = series(points).dailyBaseline(now = brt(4, 12, 30), timeZone = BRT)

        assertNotNull(baseline)
        assertEquals(480L, baseline.todayDelta)
    }

    /** Um dia com uma leitura só não tem intervalo para medir. */
    @Test
    fun `a day with a single reading does not enter the baseline`() {
        val points = regularDay(1, 100L) + listOf(point(brt(2, 10), 50L)) +
            regularDay(3, 100L) +
            listOf(point(brt(4, 9), 0L), point(brt(4, 12), 400L))

        assertNull(series(points).dailyBaseline(now = brt(4, 12, 30), timeZone = BRT))
    }

    /** Dado insuficiente não pode virar falso positivo. */
    @Test
    fun `fewer than three complete days produce no baseline`() {
        val points = regularDay(2, 100L) + regularDay(3, 100L) +
            listOf(point(brt(4, 9), 0L), point(brt(4, 12), 900L))

        assertNull(series(points).dailyBaseline(now = brt(4, 12, 30), timeZone = BRT))
    }

    /** Sem consumo hoje ainda há referência: o veredito é que não houve anomalia. */
    @Test
    fun `a day without points of its own has no baseline`() {
        val points = regularDay(1, 100L) + regularDay(2, 100L) + regularDay(3, 100L)

        assertNull(series(points).dailyBaseline(now = brt(4, 12, 30), timeZone = BRT))
    }

    /** Mediana zerada: qualquer consumo seria "infinitas vezes acima". */
    @Test
    fun `a zero median yields no factor`() {
        val quietDay = { day: Int -> listOf(point(brt(day, 9), 0L), point(brt(day, 12), 0L)) }
        val points = quietDay(1) + quietDay(2) + quietDay(3) +
            listOf(point(brt(4, 9), 0L), point(brt(4, 12), 400L))

        val baseline = series(points).dailyBaseline(now = brt(4, 12, 30), timeZone = BRT)

        assertNotNull(baseline)
        assertEquals(0L, baseline.baselineDelta)
        assertNull(baseline.factor)
    }

    /** Mesma recusa que a média por hora e a previsão já aplicam. */
    @Test
    fun `a reported window has no baseline`() {
        val points = regularDay(1, 100L) + regularDay(2, 100L) + regularDay(3, 100L) +
            listOf(point(brt(4, 9), 0L), point(brt(4, 12), 400L))

        assertNull(
            series(points, periodType = PeriodType.REPORTED)
                .dailyBaseline(now = brt(4, 12, 30), timeZone = BRT)
        )
    }

    /** Sem total conhecido o piso de proporção não teria contra o que medir. */
    @Test
    fun `a quota without a known total has no baseline`() {
        val points = regularDay(1, 100L) + regularDay(2, 100L) + regularDay(3, 100L) +
            listOf(point(brt(4, 9), 0L), point(brt(4, 12), 400L))

        assertNull(series(points, total = 0L).dailyBaseline(now = brt(4, 12, 30), timeZone = BRT))
    }

    /** A meia-noite local é o corte, e ela não é a meia-noite UTC. */
    @Test
    fun `the day boundary follows the local time zone`() {
        // 23h do dia 3 em BRT é 02h do dia 4 em UTC. Agrupar em UTC jogaria este
        // consumo para o dia seguinte e inventaria um dia de referência.
        val points = regularDay(1, 100L) + regularDay(2, 100L) +
            listOf(point(brt(3, 9), 0L), point(brt(3, 12), 100L), point(brt(3, 23), 700L)) +
            listOf(point(brt(4, 9), 0L), point(brt(4, 12), 400L))

        val baseline = series(points).dailyBaseline(now = brt(4, 23, 30), timeZone = BRT)

        assertNotNull(baseline)
        assertEquals(3, baseline.completeDays)
        assertEquals(600L, baseline.baselineDelta)
    }

    @Test
    fun `reports a spike above the configured factor`() {
        val points = regularDay(1, 100L) + regularDay(2, 100L) + regularDay(3, 100L) +
            listOf(point(brt(4, 9), 0L), point(brt(4, 12), 400L))

        val spike = series(points).detectSpike(
            target = TARGET,
            targetLabel = "MiniMax",
            now = brt(4, 12, 30),
            timeZone = BRT
        )

        assertNotNull(spike)
        assertEquals(4.0, spike.factor)
        assertEquals("Claude 5h", spike.quotaLabel)
        assertEquals(LocalDate(2026, 9, 4), spike.localDate)
        assertEquals(QUOTA_TOTAL, spike.quotaTotal)
    }

    @Test
    fun `stays quiet below the configured factor`() {
        val points = regularDay(1, 100L) + regularDay(2, 100L) + regularDay(3, 100L) +
            listOf(point(brt(4, 9), 0L), point(brt(4, 12), 250L))

        assertNull(
            series(points).detectSpike(
                target = TARGET,
                targetLabel = "MiniMax",
                now = brt(4, 12, 30),
                timeZone = BRT,
                minFactor = DEFAULT_SPIKE_FACTOR
            )
        )
    }

    /**
     * Quatro vezes a mediana, mas 4% da cota. A razão explode onde os números são
     * pequenos demais para significar alguma coisa, e o piso é quem barra isso.
     */
    @Test
    fun `stays quiet when today is a negligible share of the quota`() {
        val tinyDay = { day: Int -> listOf(point(brt(day, 9), 0L), point(brt(day, 12), 10L)) }
        val points = tinyDay(1) + tinyDay(2) + tinyDay(3) +
            listOf(point(brt(4, 9), 0L), point(brt(4, 12), 40L))

        val baseline = series(points).dailyBaseline(now = brt(4, 12, 30), timeZone = BRT)
        assertNotNull(baseline)
        assertEquals(4.0, baseline.factor)

        assertNull(
            series(points).detectSpike(
                target = TARGET,
                targetLabel = "MiniMax",
                now = brt(4, 12, 30),
                timeZone = BRT
            )
        )
    }
}

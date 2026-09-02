package com.usagemonitor.domain

import com.usagemonitor.domain.entity.UsageHistoryPoint
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.domain.entity.positiveDeltaOf
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

private val BASE = Instant.parse("2026-09-01T00:00:00Z")

private fun point(minutesFromBase: Long, used: Long, total: Long = 1_000L): UsageHistoryPoint {
    return UsageHistoryPoint(
        capturedAt = Instant.fromEpochMilliseconds(BASE.toEpochMilliseconds() + minutesFromBase * 60_000L),
        used = used,
        total = total,
        rawUsed = used,
        rawTotal = total,
        periodEndAt = BASE
    )
}

/**
 * O consumo observado numa sequência de pontos.
 *
 * As asserções existiam só de forma indireta, através do relatório do histórico.
 * Com dois consumidores — o relatório e a linha de referência diária — a função
 * precisa do próprio teste: é aqui que o tratamento do reset fica travado.
 */
class PositiveDeltaTest {

    @Test
    fun `sums the increases of a cumulative quota`() {
        val points = listOf(point(0, 10L), point(10, 40L), point(20, 90L))

        assertEquals(80L, positiveDeltaOf(points, UsageUnit.PERCENTAGE))
    }

    /**
     * O reset derruba o acumulado a zero. Contá-lo como delta negativo apagaria o
     * consumo medido antes dele.
     */
    @Test
    fun `a window reset contributes nothing instead of a negative delta`() {
        val points = listOf(point(0, 10L), point(10, 90L), point(20, 5L), point(30, 30L))

        assertEquals(105L, positiveDeltaOf(points, UsageUnit.PERCENTAGE))
    }

    /** Saldo pré-pago: gastar é o saldo cair, então são as quedas que somam. */
    @Test
    fun `for a prepaid balance the drops are the consumption`() {
        val points = listOf(point(0, 500L), point(10, 480L), point(20, 450L))

        assertEquals(50L, positiveDeltaOf(points, UsageUnit.CURRENCY_USD))
    }

    /** Recarga sobe o saldo, e subir não é gastar. */
    @Test
    fun `a top up does not count as spending`() {
        val points = listOf(point(0, 500L), point(10, 450L), point(20, 900L), point(30, 880L))

        assertEquals(70L, positiveDeltaOf(points, UsageUnit.CURRENCY_USD))
    }

    /** Um ponto não tem intervalo para medir. */
    @Test
    fun `fewer than two points measure nothing`() {
        assertEquals(0L, positiveDeltaOf(emptyList(), UsageUnit.PERCENTAGE))
        assertEquals(0L, positiveDeltaOf(listOf(point(0, 42L)), UsageUnit.PERCENTAGE))
    }
}

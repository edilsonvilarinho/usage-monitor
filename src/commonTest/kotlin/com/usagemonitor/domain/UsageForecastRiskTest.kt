package com.usagemonitor.domain

import com.usagemonitor.domain.entity.UsageForecast
import com.usagemonitor.domain.entity.UsageRiskLevel
import com.usagemonitor.domain.entity.riskSummary
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UsageForecastRiskTest {

    private val referenceAt = Instant.parse("2025-01-01T00:00:00Z")

    @Test
    fun `ResetsBeforeExhaustion maps to ON_TRACK without exhaustion instant`() {
        val summary = UsageForecast.ResetsBeforeExhaustion.riskSummary(
            referenceAt = referenceAt,
            periodEndAt = referenceAt.plusHours(5)
        )

        assertEquals(UsageRiskLevel.ON_TRACK, summary?.level)
        assertNull(summary?.estimatedExhaustionAt)
    }

    @Test
    fun `exhaustion using less than half the remaining time maps to WILL_EXCEED`() {
        val periodEndAt = referenceAt.plusHours(10)
        val exhaustionAt = referenceAt.plusHours(4) // 40% do tempo restante

        val summary = UsageForecast.EstimatedExhaustionAt(exhaustionAt).riskSummary(
            referenceAt = referenceAt,
            periodEndAt = periodEndAt
        )

        assertEquals(UsageRiskLevel.WILL_EXCEED, summary?.level)
        assertEquals(exhaustionAt, summary?.estimatedExhaustionAt)
    }

    @Test
    fun `exhaustion at exactly half the remaining time maps to AT_RISK`() {
        val periodEndAt = referenceAt.plusHours(10)
        val exhaustionAt = referenceAt.plusHours(5) // exatamente 50%

        val summary = UsageForecast.EstimatedExhaustionAt(exhaustionAt).riskSummary(
            referenceAt = referenceAt,
            periodEndAt = periodEndAt
        )

        assertEquals(UsageRiskLevel.AT_RISK, summary?.level)
    }

    @Test
    fun `exhaustion using more than half the remaining time maps to AT_RISK`() {
        val periodEndAt = referenceAt.plusHours(10)
        val exhaustionAt = referenceAt.plusHours(9) // 90% do tempo restante

        val summary = UsageForecast.EstimatedExhaustionAt(exhaustionAt).riskSummary(
            referenceAt = referenceAt,
            periodEndAt = periodEndAt
        )

        assertEquals(UsageRiskLevel.AT_RISK, summary?.level)
        assertEquals(exhaustionAt, summary?.estimatedExhaustionAt)
    }

    @Test
    fun `NoGrowth has no risk summary`() {
        val summary = UsageForecast.NoGrowth.riskSummary(
            referenceAt = referenceAt,
            periodEndAt = referenceAt.plusHours(5)
        )

        assertNull(summary)
    }

    @Test
    fun `InsufficientData has no risk summary`() {
        val summary = UsageForecast.InsufficientData.riskSummary(
            referenceAt = referenceAt,
            periodEndAt = referenceAt.plusHours(5)
        )

        assertNull(summary)
    }

    // ------------------------------------------------------------------
    // Saldo sem reset (issue #109)
    // ------------------------------------------------------------------

    /**
     * A regressão que a issue relata, escrita como número.
     *
     * O saldo do DeepSeek grava `Instant.DISTANT_FUTURE` como fim de período. Com
     * a régua de razão, `msToExhaustion / msToReset` dá ~0,002 para uma previsão
     * a 68 dias e **qualquer** consumo vira `WILL_EXCEED`. Este teste fixa que a
     * mesma entrada, com a régua certa, é `ON_TRACK`.
     */
    @Test
    fun `saldo sem reset com folga nao e critico mesmo com periodEndAt no infinito`() {
        val exhaustionAt = referenceAt.plusHours(68 * 24)

        val summary = UsageForecast.EstimatedExhaustionAt(exhaustionAt).riskSummary(
            referenceAt = referenceAt,
            periodEndAt = Instant.DISTANT_FUTURE,
            hasKnownResetAt = false
        )

        assertEquals(UsageRiskLevel.ON_TRACK, summary?.level)
        // A data continua sendo entregue: sem reset ela é a resposta, não o aviso.
        assertEquals(exhaustionAt, summary?.estimatedExhaustionAt)
        assertEquals(false, summary?.hasKnownResetAt)
    }

    /** A mesma entrada pela régua antiga, para o contraste ficar medido. */
    @Test
    fun `a mesma previsao com reset conhecido no infinito seria critica`() {
        val exhaustionAt = referenceAt.plusHours(68 * 24)

        val summary = UsageForecast.EstimatedExhaustionAt(exhaustionAt).riskSummary(
            referenceAt = referenceAt,
            periodEndAt = Instant.DISTANT_FUTURE,
            hasKnownResetAt = true
        )

        assertEquals(UsageRiskLevel.WILL_EXCEED, summary?.level)
    }

    @Test
    fun `saldo que acaba dentro de sete dias e critico`() {
        val summary = UsageForecast.EstimatedExhaustionAt(referenceAt.plusHours(6 * 24)).riskSummary(
            referenceAt = referenceAt,
            periodEndAt = Instant.DISTANT_FUTURE,
            hasKnownResetAt = false
        )

        assertEquals(UsageRiskLevel.WILL_EXCEED, summary?.level)
    }

    @Test
    fun `saldo que acaba entre sete e catorze dias fica em atencao`() {
        val summary = UsageForecast.EstimatedExhaustionAt(referenceAt.plusHours(10 * 24)).riskSummary(
            referenceAt = referenceAt,
            periodEndAt = Instant.DISTANT_FUTURE,
            hasKnownResetAt = false
        )

        assertEquals(UsageRiskLevel.AT_RISK, summary?.level)
    }

    /** Os dois cortes são fechados embaixo: exatamente 7d já não é crítico. */
    @Test
    fun `os cortes de sete e catorze dias sao fechados embaixo`() {
        val atSeven = UsageForecast.EstimatedExhaustionAt(referenceAt.plusHours(7 * 24)).riskSummary(
            referenceAt = referenceAt,
            periodEndAt = Instant.DISTANT_FUTURE,
            hasKnownResetAt = false
        )
        val atFourteen = UsageForecast.EstimatedExhaustionAt(referenceAt.plusHours(14 * 24)).riskSummary(
            referenceAt = referenceAt,
            periodEndAt = Instant.DISTANT_FUTURE,
            hasKnownResetAt = false
        )

        assertEquals(UsageRiskLevel.AT_RISK, atSeven?.level)
        assertEquals(UsageRiskLevel.ON_TRACK, atFourteen?.level)
    }

    private fun Instant.plusHours(hours: Long): Instant {
        return Instant.fromEpochMilliseconds(this.toEpochMilliseconds() + hours * 3_600_000L)
    }
}

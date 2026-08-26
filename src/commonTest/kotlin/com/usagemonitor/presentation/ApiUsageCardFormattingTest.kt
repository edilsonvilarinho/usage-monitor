package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.AnthropicQuotaLabels
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.presentation.ui.components.expandedQuotaTitle
import com.usagemonitor.presentation.ui.components.formatCents
import com.usagemonitor.presentation.ui.components.formatCurrencyAmount
import com.usagemonitor.presentation.ui.components.orderQuotasForCard
import com.usagemonitor.presentation.ui.components.quotaDetailText
import com.usagemonitor.presentation.ui.components.resetLabel
import com.usagemonitor.presentation.ui.components.riskDotTooltipSubtitle
import com.usagemonitor.domain.entity.QuotaRiskSummary
import com.usagemonitor.domain.entity.UsageRiskLevel
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ApiUsageCardFormattingTest {

    private val fixedInstant = Instant.parse("2026-08-11T20:00:00Z")

    private val fiveHourQuota = QuotaInfo(
        label = AnthropicQuotaLabels.FIVE_HOUR,
        used = 21L,
        total = 100L,
        periodEndAt = fixedInstant,
        periodType = PeriodType.INTERVAL,
        unit = UsageUnit.PERCENTAGE,
        rawUsed = 968L,
        rawTotal = 4500L
    )

    private val sevenDayQuota = fiveHourQuota.copy(
        label = AnthropicQuotaLabels.SEVEN_DAY,
        periodType = PeriodType.WEEKLY
    )

    private val creditsQuota = QuotaInfo(
        label = AnthropicQuotaLabels.EXTRA_CREDITS,
        used = 60L,
        total = 100L,
        periodEndAt = fixedInstant,
        hasKnownResetAt = false,
        periodType = PeriodType.REPORTED,
        unit = UsageUnit.PERCENTAGE,
        rawUsed = 32784L,
        rawTotal = 55000L,
        currencyCode = "BRL"
    )

    @Test
    fun `credits quota is always the last column`() {
        val ordered = orderQuotasForCard(listOf(creditsQuota, sevenDayQuota, fiveHourQuota))

        assertEquals(
            listOf(AnthropicQuotaLabels.FIVE_HOUR, AnthropicQuotaLabels.SEVEN_DAY, AnthropicQuotaLabels.EXTRA_CREDITS),
            ordered.map { quota -> quota.label }
        )
    }

    @Test
    fun `reported quotas from other sources keep the first position`() {
        val codexQuota = QuotaInfo(
            label = "Codex atual",
            used = 42L,
            total = 100L,
            periodEndAt = fixedInstant,
            periodType = PeriodType.REPORTED,
            unit = UsageUnit.PERCENTAGE
        )

        val ordered = orderQuotasForCard(listOf(fiveHourQuota, codexQuota))

        assertEquals(listOf("Codex atual", AnthropicQuotaLabels.FIVE_HOUR), ordered.map { it.label })
    }

    @Test
    fun `credits quota shows money regardless of the usage details switch`() {
        // O card da Anthropic roda com showUsageDetails = false, porque as
        // estimativas de tokens não são confiáveis — os créditos, sim.
        assertEquals("R$327.84/R$550.00", quotaDetailText(creditsQuota, showUsageDetails = false))
        assertEquals("R$327.84/R$550.00", quotaDetailText(creditsQuota, showUsageDetails = true))
    }

    @Test
    fun `percentage quotas keep hiding the detail line`() {
        assertNull(quotaDetailText(fiveHourQuota, showUsageDetails = true))
        assertNull(quotaDetailText(fiveHourQuota, showUsageDetails = false))
    }

    @Test
    fun `credits quota has its own title instead of the reported one`() {
        assertEquals("Créditos de uso", expandedQuotaTitle(creditsQuota, AppLanguage.PT))
        assertEquals("Usage credits", expandedQuotaTitle(creditsQuota, AppLanguage.EN))
    }

    @Test
    fun `credits quota resets monthly instead of never expiring`() {
        // `now` depois do `periodEndAt`: os créditos não têm janela de quota, então
        // o rótulo deles precisa ganhar do ramo de janela vencida.
        val afterPeriodEnd = Instant.parse("2026-08-11T21:00:00Z")
        assertEquals("Reinicia no início do mês", resetLabel(creditsQuota, AppLanguage.PT, afterPeriodEnd))
        assertEquals("Resets at the start of the month", resetLabel(creditsQuota, AppLanguage.EN, afterPeriodEnd))
    }

    @Test
    fun `currency formatting follows the quota currency`() {
        assertEquals("R$550.00", formatCents(55000L, "BRL"))
        assertEquals("$3.85", formatCents(385L, "USD"))
        assertEquals("$3.85", formatCents(385L))
        assertEquals("-R$1.50", formatCurrencyAmount(-150L, "BRL"))
        assertEquals("JPY 5.00", formatCents(500L, "JPY"))
    }

    // ------------------------------------------------------------------
    // Projeção de saldo sem reset (issue #109)
    // ------------------------------------------------------------------

    /**
     * A contradição que a issue mostra em captura: o card imprime "Saldo não
     * expira" e a tooltip logo abaixo prometia um reset.
     */
    @Test
    fun `a projecao de um saldo sem reset nao fala em reset`() {
        val risk = QuotaRiskSummary(
            level = UsageRiskLevel.AT_RISK,
            estimatedExhaustionAt = Instant.parse("2026-11-02T16:38:00Z"),
            hasKnownResetAt = false
        )

        val pt = riskDotTooltipSubtitle(risk, AppLanguage.PT)
        val en = riskDotTooltipSubtitle(risk, AppLanguage.EN)

        assertEquals(false, pt.contains("reset", ignoreCase = true))
        assertEquals(false, en.contains("reset", ignoreCase = true))
        assertEquals(true, pt.contains("créditos devem acabar"))
        assertEquals(true, en.contains("credits should run out"))
    }

    /** Saldo sem consumo observado: nenhuma data, e nenhuma promessa de reset. */
    @Test
    fun `saldo sem previsao diz que nao ha estimativa`() {
        val risk = QuotaRiskSummary(
            level = UsageRiskLevel.ON_TRACK,
            estimatedExhaustionAt = null,
            hasKnownResetAt = false
        )

        val pt = riskDotTooltipSubtitle(risk, AppLanguage.PT)

        assertEquals(false, pt.contains("reset", ignoreCase = true))
        assertEquals(true, pt.contains("não há previsão de término"))
    }

    /** Cota com reset continua com a frase de sempre. */
    @Test
    fun `a projecao de uma cota com reset continua falando em reset`() {
        val risk = QuotaRiskSummary(
            level = UsageRiskLevel.WILL_EXCEED,
            estimatedExhaustionAt = Instant.parse("2026-08-11T22:00:00Z")
        )

        assertEquals(true, riskDotTooltipSubtitle(risk, AppLanguage.PT).contains("antes do reset"))
    }
}

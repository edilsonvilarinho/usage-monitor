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
        assertEquals("Reinicia no início do mês", resetLabel(creditsQuota, AppLanguage.PT))
        assertEquals("Resets at the start of the month", resetLabel(creditsQuota, AppLanguage.EN))
    }

    @Test
    fun `currency formatting follows the quota currency`() {
        assertEquals("R$550.00", formatCents(55000L, "BRL"))
        assertEquals("$3.85", formatCents(385L, "USD"))
        assertEquals("$3.85", formatCents(385L))
        assertEquals("-R$1.50", formatCurrencyAmount(-150L, "BRL"))
        assertEquals("JPY 5.00", formatCents(500L, "JPY"))
    }
}

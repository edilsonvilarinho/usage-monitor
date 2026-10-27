package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.QuotaRiskSummary
import com.usagemonitor.domain.entity.UsageRiskLevel
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.presentation.ui.components.codexCliSessionsActionLabel
import com.usagemonitor.presentation.ui.components.compactPercentageLabel
import com.usagemonitor.presentation.ui.components.currencySymbol
import com.usagemonitor.presentation.ui.components.formatBrtDateTime
import com.usagemonitor.presentation.ui.components.formatCurrencyAmount
import com.usagemonitor.presentation.ui.components.formatUsage
import com.usagemonitor.presentation.ui.components.quotaTooltipRemainingValue
import com.usagemonitor.presentation.ui.components.quotaTooltipUsageValue
import com.usagemonitor.presentation.ui.components.resetLabel
import com.usagemonitor.presentation.ui.components.resetShortLabel
import com.usagemonitor.presentation.ui.components.riskDotContentDescription
import com.usagemonitor.presentation.ui.components.riskDotTooltipSubtitle
import com.usagemonitor.presentation.ui.components.riskLevelLabel
import com.usagemonitor.presentation.ui.components.riskStatusContentDescription
import com.usagemonitor.presentation.ui.components.teamPresenceActionLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Ramos em inglês e por unidade dos textos do card e da tooltip de cota. */
class ApiUsageCardTextsTest {

    private val now = Instant.parse("2026-10-27T15:00:00Z")
    private val resetAt = Instant.parse("2026-10-30T18:30:00Z")

    private fun quota(
        period: PeriodType = PeriodType.INTERVAL,
        unit: UsageUnit = UsageUnit.PERCENTAGE,
        used: Long = 40L,
        total: Long = 100L,
        endsAt: Instant = resetAt,
        knownReset: Boolean = true,
        currency: String = "USD"
    ) = QuotaInfo(
        label = "Claude 5h",
        used = used,
        total = total,
        periodEndAt = endsAt,
        hasKnownResetAt = knownReset,
        periodType = period,
        unit = unit,
        currencyCode = currency
    )

    @Test
    fun `reset line in english for every window`() {
        val brt = formatBrtDateTime(resetAt, AppLanguage.EN)
        assertTrue(resetLabel(quota(PeriodType.WEEKLY), AppLanguage.EN, now).startsWith("Reset: "))
        assertTrue(resetLabel(quota(PeriodType.MONTHLY), AppLanguage.EN, now).endsWith(" BRT"))
        assertTrue(resetLabel(quota(PeriodType.REPORTED), AppLanguage.EN, now).startsWith("Reported reset: "))
        assertTrue(resetLabel(quota(PeriodType.INTERVAL), AppLanguage.EN, now).startsWith("Reset: "))
        assertTrue(brt.isNotBlank())
        assertEquals(
            "Window reset · collecting data",
            resetLabel(quota(endsAt = Instant.parse("2026-10-27T14:00:00Z")), AppLanguage.EN, now)
        )
        assertEquals("Reset window not available yet", resetLabel(quota(knownReset = false), AppLanguage.EN, now))
        assertEquals("Balance never expires", resetLabel(quota(unit = UsageUnit.CURRENCY_USD), AppLanguage.EN, now))
    }

    @Test
    fun `short reset is omitted when there is nothing to show`() {
        assertEquals("resetting", resetShortLabel(quota(endsAt = Instant.parse("2026-10-27T14:00:00Z")), AppLanguage.EN, now))
        assertEquals("reiniciando", resetShortLabel(quota(endsAt = Instant.parse("2026-10-27T14:00:00Z")), AppLanguage.PT, now))
        assertNull(resetShortLabel(quota(knownReset = false), AppLanguage.EN, now))
        assertNull(resetShortLabel(quota(unit = UsageUnit.CURRENCY_USD), AppLanguage.EN, now))
    }

    @Test
    fun `risk labels and explanations in english`() {
        assertEquals("Normal", riskLevelLabel(UsageRiskLevel.ON_TRACK, AppLanguage.EN))
        assertEquals("Warning", riskLevelLabel(UsageRiskLevel.AT_RISK, AppLanguage.EN))
        assertEquals("Critical", riskLevelLabel(UsageRiskLevel.WILL_EXCEED, AppLanguage.EN))

        val beforeReset = QuotaRiskSummary(UsageRiskLevel.ON_TRACK, estimatedExhaustionAt = null)
        val runsOut = QuotaRiskSummary(UsageRiskLevel.WILL_EXCEED, estimatedExhaustionAt = resetAt)
        val idleBalance = QuotaRiskSummary(UsageRiskLevel.ON_TRACK, estimatedExhaustionAt = null, hasKnownResetAt = false)

        assertEquals("Overage risk 5h: Critical", riskDotContentDescription(runsOut, "5h", AppLanguage.EN))
        assertEquals("At the current pace, the quota should reset before running out.", riskDotTooltipSubtitle(beforeReset, AppLanguage.EN))
        assertEquals(
            "At the current pace, the quota should run out before reset — estimated: ${formatBrtDateTime(resetAt, AppLanguage.EN)} BRT.",
            riskDotTooltipSubtitle(runsOut, AppLanguage.EN)
        )
        assertEquals("No usage observed in the period — no depletion estimate.", riskDotTooltipSubtitle(idleBalance, AppLanguage.EN))
        assertEquals(
            "Status Critical. Quota 5h. ${riskDotTooltipSubtitle(runsOut, AppLanguage.EN)}",
            riskStatusContentDescription("5h", runsOut, AppLanguage.EN)
        )
        assertTrue(riskStatusContentDescription("5h", runsOut, AppLanguage.PT).startsWith("Status Crítico. Cota 5h."))
    }

    @Test
    fun `tooltip usage and remaining follow the unit`() {
        assertEquals("40/100 %", quotaTooltipUsageValue(quota(), AppLanguage.EN))
        assertEquals("40 %", quotaTooltipUsageValue(quota(total = 0L), AppLanguage.EN))
        assertEquals("40/100 req", quotaTooltipUsageValue(quota(unit = UsageUnit.REQUESTS), AppLanguage.EN))
        assertEquals("40 req", quotaTooltipUsageValue(quota(unit = UsageUnit.REQUESTS, total = 0L), AppLanguage.PT))
        assertEquals("40 req", quotaTooltipUsageValue(quota(unit = UsageUnit.REQUESTS, total = 0L), AppLanguage.EN))
        assertEquals("40 tok", quotaTooltipUsageValue(quota(unit = UsageUnit.TOKENS, total = 0L), AppLanguage.EN))
        assertEquals("R$1.00", quotaTooltipUsageValue(quota(unit = UsageUnit.CURRENCY_USD, total = 100L, currency = "BRL"), AppLanguage.EN))
        assertEquals("60 req", quotaTooltipRemainingValue(quota(unit = UsageUnit.REQUESTS)))
        assertEquals("60 tok", quotaTooltipRemainingValue(quota(unit = UsageUnit.TOKENS)))
        assertEquals("60 %", quotaTooltipRemainingValue(quota()))
    }

    @Test
    fun `currency symbols and compact labels`() {
        assertEquals("€", currencySymbol("eur"))
        assertEquals("£", currencySymbol("GBP"))
        assertEquals("JPY ", currencySymbol("jpy"))
        assertEquals("-€2.05", formatCurrencyAmount(-205L, "EUR"))
        assertEquals("40%", compactPercentageLabel(quota()))
        assertEquals("25%", compactPercentageLabel(quota(unit = UsageUnit.TOKENS, used = 250L, total = 1_000L)))
        assertEquals("1.5K/2K tok", formatUsage(quota(unit = UsageUnit.TOKENS, used = 1_500L, total = 2_000L)).replace(',', '.'))
        assertEquals("3/10 req", formatUsage(quota(unit = UsageUnit.REQUESTS, used = 3L, total = 10L)))
        assertEquals("Codex CLI sessions for this account", codexCliSessionsActionLabel(AppLanguage.EN))
        assertEquals("Who is connected now", teamPresenceActionLabel(AppLanguage.EN))
        assertEquals("Quem está conectado agora", teamPresenceActionLabel(AppLanguage.PT))
    }
}

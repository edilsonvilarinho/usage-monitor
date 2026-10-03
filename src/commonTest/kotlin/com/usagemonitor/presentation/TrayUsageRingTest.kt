package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.presentation.ui.trayUsageRingFraction
import com.usagemonitor.presentation.viewmodel.HudQuotaEntry
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours

class TrayUsageRingTest {

    private val now = Instant.parse("2026-09-27T12:00:00Z")

    private fun entry(used: Long, unit: UsageUnit = UsageUnit.PERCENTAGE, endsAt: Instant = now + 2.hours): HudQuotaEntry {
        val quota = QuotaInfo(label = "q$used", used = used, total = 100L, periodEndAt = endsAt, unit = unit)
        val stats = ApiUsageStats(source = ApiSource.CODEX, apiName = "Codex", quotas = listOf(quota))
        return HudQuotaEntry(stats, quota, null)
    }

    @Test
    fun `the ring shows the highest percentage among current quotas`() {
        assertEquals(0.8f, trayUsageRingFraction(listOf(entry(30), entry(80), entry(55)), now))
    }

    @Test
    fun `balances and expired windows do not feed the ring`() {
        val entries = listOf(
            entry(20),
            entry(95, unit = UsageUnit.CURRENCY_USD),
            entry(99, endsAt = now - 1.hours)
        )

        assertEquals(0.2f, trayUsageRingFraction(entries, now))
    }

    @Test
    fun `without any eligible quota there is no ring`() {
        assertNull(trayUsageRingFraction(emptyList(), now))
        assertNull(trayUsageRingFraction(listOf(entry(50, unit = UsageUnit.CURRENCY_USD)), now))
    }
}

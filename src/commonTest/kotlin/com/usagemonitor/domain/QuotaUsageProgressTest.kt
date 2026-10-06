package com.usagemonitor.domain

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.USAGE_DETECTED_WINDOW_MILLIS
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.domain.entity.recentlyProgressed
import com.usagemonitor.domain.entity.usageProgressed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

private val RESET = Instant.parse("2026-10-06T13:05:00Z")

private fun quota(
    used: Long,
    total: Long = 100L,
    periodEndAt: Instant = RESET,
    label: String = "Go 5h",
    hasKnownResetAt: Boolean = true,
    unit: UsageUnit = UsageUnit.PERCENTAGE
) = QuotaInfo(
    label = label,
    used = used,
    total = total,
    periodEndAt = periodEndAt,
    hasKnownResetAt = hasKnownResetAt,
    periodType = PeriodType.INTERVAL,
    unit = unit
)

private fun stats(vararg quotas: QuotaInfo, source: ApiSource = ApiSource.OPENCODE_GO) =
    ApiUsageStats(source = source, apiName = source.name, quotas = quotas.toList())

class QuotaUsageProgressTest {

    @Test
    fun `used growing inside the same window is progress`() {
        assertTrue(usageProgressed(stats(quota(used = 40)), stats(quota(used = 41))))
    }

    @Test
    fun `unchanged reading is not progress`() {
        assertFalse(usageProgressed(stats(quota(used = 40)), stats(quota(used = 40))))
    }

    @Test
    fun `reset to a new window is not progress even when used looks higher`() {
        val before = stats(quota(used = 2))
        val after = stats(quota(used = 5, periodEndAt = Instant.parse("2026-10-06T18:05:00Z")))

        assertFalse(usageProgressed(before, after))
    }

    @Test
    fun `reset tolerance keeps a jittering reset in the same window`() {
        val before = stats(quota(used = 2))
        val after = stats(quota(used = 3, periodEndAt = Instant.parse("2026-10-06T13:06:00Z")))

        assertTrue(usageProgressed(before, after))
    }

    @Test
    fun `prepaid balance progresses when the remaining falls`() {
        val before = stats(quota(used = 0, total = 412, hasKnownResetAt = false, unit = UsageUnit.CURRENCY_USD, label = "Saldo"), source = ApiSource.DEEPSEEK)
        val spent = stats(quota(used = 0, total = 405, hasKnownResetAt = false, unit = UsageUnit.CURRENCY_USD, label = "Saldo"), source = ApiSource.DEEPSEEK)
        val toppedUp = stats(quota(used = 0, total = 1_405, hasKnownResetAt = false, unit = UsageUnit.CURRENCY_USD, label = "Saldo"), source = ApiSource.DEEPSEEK)

        assertTrue(usageProgressed(before, spent))
        assertFalse(usageProgressed(spent, toppedUp))
    }

    @Test
    fun `quota missing in the previous reading is not compared`() {
        assertFalse(usageProgressed(stats(quota(used = 1, label = "Go 7d")), stats(quota(used = 9))))
    }

    @Test
    fun `progress stays detected for the window and then expires`() {
        val at = Instant.parse("2026-10-06T12:00:00Z")
        val progress = mapOf("go" to at)

        assertEquals(setOf("go"), recentlyProgressed(progress, Instant.fromEpochMilliseconds(at.toEpochMilliseconds() + USAGE_DETECTED_WINDOW_MILLIS - 1)))
        assertEquals(emptySet(), recentlyProgressed(progress, Instant.fromEpochMilliseconds(at.toEpochMilliseconds() + USAGE_DETECTED_WINDOW_MILLIS)))
    }
}

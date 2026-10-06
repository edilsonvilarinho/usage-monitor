package com.usagemonitor.domain

import com.usagemonitor.data.export.JsonUsageSnapshotEncoder
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.QuotaRiskSummary
import com.usagemonitor.domain.entity.QuotaSeriesKey
import com.usagemonitor.domain.entity.UsageRiskLevel
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.domain.entity.buildUsageSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private val NOW = Instant.parse("2026-10-06T14:42:00Z")
private val RESET = Instant.parse("2026-10-06T16:05:00Z")

class UsageSnapshotTest {

    private val stats = listOf(
        ApiUsageStats(
            source = ApiSource.ANTHROPIC,
            apiName = "Anthropic",
            profileLabel = "Padrão",
            quotas = listOf(QuotaInfo("Claude 5h", 68, 100, RESET, unit = UsageUnit.PERCENTAGE))
        ),
        ApiUsageStats(
            source = ApiSource.DEEPSEEK,
            apiName = "DeepSeek",
            quotas = listOf(
                QuotaInfo("Saldo", 0, 412, Instant.DISTANT_FUTURE, hasKnownResetAt = false, periodType = PeriodType.INTERVAL, unit = UsageUnit.CURRENCY_USD)
            )
        )
    )

    @Test
    fun `snapshot keeps quota percent only for windowed quotas`() {
        val snapshot = buildUsageSnapshot(stats, setOf(UsageTargetKey.forSource(ApiSource.DEEPSEEK)), NOW)

        val anthropic = snapshot.accounts[0]
        val deepseek = snapshot.accounts[1]
        assertEquals("Anthropic · Padrão", anthropic.label)
        assertEquals(68, anthropic.quotas.single().percent)
        assertEquals(RESET, anthropic.quotas.single().resetsAt)
        assertFalse(anthropic.active)
        assertNull(deepseek.quotas.single().percent)
        assertNull(deepseek.quotas.single().resetsAt)
        assertTrue(deepseek.active)
    }

    @Test
    fun `json uses the page contract field names`() {
        val json = JsonUsageSnapshotEncoder.encode(buildUsageSnapshot(stats, emptySet(), NOW))

        assertTrue(json.contains("\"generated_at\":\"2026-10-06T14:42:00Z\""))
        assertTrue(json.contains("\"percent\":68"))
        assertTrue(json.contains("\"resets_at\":null"))
    }

    @Test
    fun `quota risk is the hud projection and expired quotas have none`() {
        val anthropicKey = UsageTargetKey.forSource(ApiSource.ANTHROPIC)
        val withExpired = stats.first().copy(
            fetchedAt = Instant.parse("2026-10-06T14:40:00Z"),
            quotas = stats.first().quotas + QuotaInfo("Claude 7d", 12, 100, Instant.parse("2026-10-06T10:00:00Z"), periodType = PeriodType.WEEKLY, unit = UsageUnit.PERCENTAGE)
        )
        val risks = mapOf(
            anthropicKey to mapOf(
                QuotaSeriesKey("Claude 5h", PeriodType.INTERVAL) to QuotaRiskSummary(UsageRiskLevel.WILL_EXCEED, null),
                QuotaSeriesKey("Claude 7d", PeriodType.WEEKLY) to QuotaRiskSummary(UsageRiskLevel.AT_RISK, null)
            )
        )

        val snapshot = buildUsageSnapshot(listOf(withExpired, stats[1]), emptySet(), NOW, risks)

        val anthropic = snapshot.accounts[0]
        assertEquals(UsageRiskLevel.WILL_EXCEED, anthropic.quotas[0].risk)
        assertNull(anthropic.quotas[1].risk)
        assertEquals(UsageRiskLevel.WILL_EXCEED, anthropic.worstRisk)
        assertNull(snapshot.accounts[1].worstRisk)
        assertEquals(Instant.parse("2026-10-06T14:40:00Z"), snapshot.lastCollectedAt)
        assertTrue(JsonUsageSnapshotEncoder.encode(snapshot).contains("\"worst_risk\":\"WILL_EXCEED\""))
    }
}

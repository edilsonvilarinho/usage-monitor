package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.DEFAULT_ANTHROPIC_PROFILE_ID
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.QuotaRiskSummary
import com.usagemonitor.domain.entity.QuotaSeriesKey
import com.usagemonitor.domain.entity.UsageRiskLevel
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.presentation.viewmodel.allSourceRisks
import com.usagemonitor.presentation.viewmodel.worstQuotaSnapshot
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

private val NOW = Instant.parse("2026-08-13T12:00:00Z")
private val ANTHROPIC_TARGET = UsageTargetKey(ApiSource.ANTHROPIC, DEFAULT_ANTHROPIC_PROFILE_ID)
private val CODEX_TARGET = UsageTargetKey.forSource(ApiSource.CODEX)
private val ANTHROPIC_QUOTA_KEY = QuotaSeriesKey("Sessão 5h", PeriodType.INTERVAL)
private val CODEX_QUOTA_KEY = QuotaSeriesKey("Codex 5h", PeriodType.INTERVAL)

class WorstQuotaSnapshotTest {

    @Test
    fun `allSourceRisks orders by level descending, worst first`() {
        val stats = listOf(
            stats(ApiSource.ANTHROPIC, ANTHROPIC_TARGET, "Sessão 5h"),
            stats(ApiSource.CODEX, CODEX_TARGET, "Codex 5h")
        )
        val riskSummaries = mapOf(
            ANTHROPIC_TARGET to mapOf(ANTHROPIC_QUOTA_KEY to QuotaRiskSummary(UsageRiskLevel.AT_RISK, NOW + 1.hours)),
            CODEX_TARGET to mapOf(CODEX_QUOTA_KEY to QuotaRiskSummary(UsageRiskLevel.WILL_EXCEED, NOW + 30.minutes))
        )

        val ordered = allSourceRisks(stats, riskSummaries, NOW)

        assertEquals(listOf(ApiSource.CODEX, ApiSource.ANTHROPIC), ordered.map { it.stats.source })
    }

    /** Empate no nível é desempatado pelo nome da fonte — ordem tem de ser total. */
    @Test
    fun `allSourceRisks breaks ties by source name`() {
        val stats = listOf(
            stats(ApiSource.CODEX, CODEX_TARGET, "Codex 5h"),
            stats(ApiSource.ANTHROPIC, ANTHROPIC_TARGET, "Sessão 5h")
        )
        val riskSummaries = mapOf(
            ANTHROPIC_TARGET to mapOf(ANTHROPIC_QUOTA_KEY to QuotaRiskSummary(UsageRiskLevel.AT_RISK, NOW + 1.hours)),
            CODEX_TARGET to mapOf(CODEX_QUOTA_KEY to QuotaRiskSummary(UsageRiskLevel.AT_RISK, NOW + 1.hours))
        )

        val ordered = allSourceRisks(stats, riskSummaries, NOW)

        assertEquals(listOf("ANTHROPIC", "CODEX"), ordered.map { it.stats.apiName })
    }

    @Test
    fun `worstQuotaSnapshot is the first entry of allSourceRisks`() {
        val stats = listOf(
            stats(ApiSource.ANTHROPIC, ANTHROPIC_TARGET, "Sessão 5h"),
            stats(ApiSource.CODEX, CODEX_TARGET, "Codex 5h")
        )
        val riskSummaries = mapOf(
            ANTHROPIC_TARGET to mapOf(ANTHROPIC_QUOTA_KEY to QuotaRiskSummary(UsageRiskLevel.AT_RISK, NOW + 1.hours)),
            CODEX_TARGET to mapOf(CODEX_QUOTA_KEY to QuotaRiskSummary(UsageRiskLevel.WILL_EXCEED, NOW + 30.minutes))
        )

        val worst = worstQuotaSnapshot(stats, riskSummaries, NOW)

        assertEquals(ApiSource.CODEX, worst?.stats?.source)
    }

    @Test
    fun `allSourceRisks is empty and worstQuotaSnapshot is null with no data`() {
        assertEquals(emptyList(), allSourceRisks(emptyList(), emptyMap(), NOW))
        assertNull(worstQuotaSnapshot(emptyList(), emptyMap(), NOW))
    }

    private fun stats(source: ApiSource, target: UsageTargetKey, label: String): ApiUsageStats {
        return ApiUsageStats(
            source = source,
            targetKey = target,
            apiName = source.name,
            quotas = listOf(
                QuotaInfo(
                    label = label,
                    used = 10L,
                    total = 100L,
                    periodEndAt = NOW + 2.hours,
                    periodType = PeriodType.INTERVAL,
                    unit = UsageUnit.PERCENTAGE
                )
            )
        )
    }
}

package com.usagemonitor.domain

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.CliUsageBucket
import com.usagemonitor.domain.entity.CodexCliModelUsage
import com.usagemonitor.domain.entity.ComparisonMetric
import com.usagemonitor.domain.entity.OutputThroughput
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.domain.entity.buildComparisonRows
import com.usagemonitor.domain.entity.heatIntensity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

private val RESET = Instant.parse("2026-10-06T18:00:00Z")

private fun quota(used: Long) = QuotaInfo("q", used, 100L, RESET, periodType = PeriodType.INTERVAL, unit = UsageUnit.PERCENTAGE)

class ModelComparisonTest {

    private val rows = buildComparisonRows(
        claudeModels = listOf(
            CliUsageBucket(label = "claude-sonnet-5", turnCount = 61, inputTokens = 100, outputTokens = 96_000, costMicros = 2_310_000),
            CliUsageBucket(label = "claude-opus-5-5", turnCount = 112, inputTokens = 200, outputTokens = 182_000, costMicros = 9_100_000)
        ),
        claudeThroughputs = mapOf("claude-opus-5-5" to OutputThroughput(94_000, 1_000_000)),
        codexModels = listOf(CodexCliModelUsage("gpt-5.6-luna", 73, 1_000, 880, 141_000, 3_100_000, OutputThroughput(33_000, 1_000_000))),
        stats = listOf(
            ApiUsageStats(source = ApiSource.ANTHROPIC, apiName = "Anthropic", quotas = listOf(quota(68), quota(41))),
            ApiUsageStats(source = ApiSource.MINIMAX, apiName = "MiniMax", quotas = listOf(quota(22)))
        )
    )

    @Test
    fun `rows follow the source enum order, then tokens and label`() {
        assertEquals(
            listOf("claude-opus-5-5", "claude-sonnet-5", "MiniMax", "gpt-5.6-luna"),
            rows.map { it.label }
        )
    }

    @Test
    fun `unmeasured metrics stay null instead of zero`() {
        val codex = rows.single { it.source == ApiSource.CODEX }
        val minimax = rows.single { it.source == ApiSource.MINIMAX }
        val sonnet = rows.single { it.label == "claude-sonnet-5" }

        assertNull(codex.costMicros)
        assertNull(minimax.totalTokens)
        assertNull(minimax.throughput)
        assertNull(sonnet.throughput)
        assertEquals(22, minimax.quotaPercent)
        assertEquals(68, sonnet.quotaPercent)
    }

    @Test
    fun `heat intensity is value over the column maximum`() {
        val opus = rows.first()
        val codex = rows.single { it.source == ApiSource.CODEX }

        assertEquals(1.0, heatIntensity(rows, opus, ComparisonMetric.THROUGHPUT))
        assertEquals(33.0 / 94.0, heatIntensity(rows, codex, ComparisonMetric.THROUGHPUT)!!, 1e-9)
        assertNull(heatIntensity(rows, codex, ComparisonMetric.COST))
    }
}

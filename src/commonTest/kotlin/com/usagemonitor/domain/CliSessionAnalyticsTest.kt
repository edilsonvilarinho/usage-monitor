package com.usagemonitor.domain

import com.usagemonitor.domain.entity.CliSessionDetail
import com.usagemonitor.domain.entity.CliSessionHealth
import com.usagemonitor.domain.entity.CliSessionSummary
import com.usagemonitor.domain.entity.CliSessionTurn
import com.usagemonitor.domain.usecase.ComputeCliSessionAnalyticsUseCase
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val OPUS = "claude-opus-5"
private const val SONNET = "claude-sonnet-5"

class CliSessionAnalyticsTest {

    private val computeAnalytics = ComputeCliSessionAnalyticsUseCase()

    @Test
    fun `cache hit rate uses reads over reads plus writes`() {
        val summary = summary(cacheReadTokens = 800L, cacheWrite5mTokens = 100L, cacheWrite1hTokens = 100L)

        assertEquals(0.8, summary.cacheHitRate)
    }

    @Test
    fun `cache hit rate is zero when there is no cache activity`() {
        val summary = summary(cacheReadTokens = 0L, cacheWrite5mTokens = 0L, cacheWrite1hTokens = 0L)

        assertEquals(0.0, summary.cacheHitRate)
    }

    @Test
    fun `cache savings use the input minus cache read delta of the turn model`() {
        val detail = detail(turn(seq = 1, cacheReadTokens = 1_000_000L))

        // Opus: (5,00 - 0,50) USD por milhão de tokens lidos do cache.
        assertEquals(4_500_000L, computeAnalytics(detail).cacheSavingsMicros)
    }

    @Test
    fun `average context is the arithmetic mean of real per turn reads`() {
        val detail = detail(
            turn(seq = 1, cacheReadTokens = 100L),
            turn(seq = 2, cacheReadTokens = 300L)
        )

        assertEquals(200L, computeAnalytics(detail).averageContextPerTurn)
    }

    @Test
    fun `sidechain turns are excluded from context metrics but not from cost`() {
        val detail = detail(
            turn(seq = 1, cacheReadTokens = 100L),
            turn(seq = 2, cacheReadTokens = 900_000L, isSidechain = true),
            turn(seq = 3, cacheReadTokens = 300L)
        )

        val analytics = computeAnalytics(detail)

        assertEquals(200L, analytics.averageContextPerTurn)
        assertEquals(300L, analytics.liveContextTokens)
        assertEquals(listOf(100L, 300L), analytics.contextPerTurn)
        assertEquals(2, analytics.mainTurnCount)
        assertEquals(1, analytics.sidechainTurnCount)
        // O gasto do subagente entra na economia acumulada.
        assertEquals(3, analytics.cumulativeSavingsMicros.size)
    }

    @Test
    fun `next interaction cost prices the live context at the cache read rate`() {
        val detail = detail(
            turn(seq = 1, cacheReadTokens = 100L),
            turn(seq = 2, cacheReadTokens = 20_000L)
        )

        // 20.000 tokens x USD 0,50/M = 10.000 micros.
        assertEquals(10_000L, computeAnalytics(detail).nextInteractionCostMicros)
    }

    @Test
    fun `context saturation is the live context over the model window`() {
        val detail = detail(
            summary = summary(primaryModel = OPUS),
            turn(seq = 1, cacheReadTokens = 800_000L)
        )

        val analytics = computeAnalytics(detail)

        assertEquals(0.8, analytics.contextSaturation)
        assertTrue(analytics.isSaturated)
    }

    @Test
    fun `session below the threshold is not saturated`() {
        val detail = detail(
            summary = summary(primaryModel = OPUS),
            turn(seq = 1, cacheReadTokens = 100_000L)
        )

        assertFalse(computeAnalytics(detail).isSaturated)
    }

    @Test
    fun `small context on a large window is healthy`() {
        // 10% da janela de 1M e mensagem barata.
        val detail = detail(summary(primaryModel = OPUS), turn(seq = 1, cacheReadTokens = 100_000L))

        assertEquals(CliSessionHealth.HEALTHY, computeAnalytics(detail).health)
    }

    @Test
    fun `context past the attention fraction raises attention`() {
        val detail = detail(summary(primaryModel = OPUS), turn(seq = 1, cacheReadTokens = 450_000L))

        assertEquals(CliSessionHealth.ATTENTION, computeAnalytics(detail).health)
    }

    @Test
    fun `context past the saturated fraction raises saturated`() {
        val detail = detail(summary(primaryModel = OPUS), turn(seq = 1, cacheReadTokens = 650_000L))

        val analytics = computeAnalytics(detail)
        assertEquals(CliSessionHealth.SATURATED, analytics.health)
        assertTrue(analytics.isSaturated)
    }

    @Test
    fun `an expensive next message saturates even on a roomy window`() {
        // 520K tokens é 52% de 1M — abaixo da fração — mas custa USD 0,26 por mensagem.
        val detail = detail(summary(primaryModel = OPUS), turn(seq = 1, cacheReadTokens = 520_000L))

        val analytics = computeAnalytics(detail)
        assertTrue(analytics.contextSaturation!! < 0.60)
        assertEquals(CliSessionHealth.SATURATED, analytics.health)
    }

    @Test
    fun `unknown window still escalates by cost alone`() {
        val detail = detail(
            summary = summary(primaryModel = "claude-opus-4-5"),
            turn(seq = 1, model = "claude-opus-4-5", cacheReadTokens = 600_000L)
        )

        val analytics = computeAnalytics(detail)
        assertNull(analytics.contextSaturation)
        assertEquals(CliSessionHealth.SATURATED, analytics.health)
    }

    @Test
    fun `session without turns is healthy`() {
        val detail = CliSessionDetail(summary = summary(), turns = emptyList())

        assertEquals(CliSessionHealth.HEALTHY, computeAnalytics(detail).health)
    }

    @Test
    fun `the legacy fixed token rule would misfire on a large window`() {
        // Regra do legado: cacheRead > 150.000 dispara. Numa janela de 1M isso é
        // 15% do contexto — motivo de o alerta antigo acender em quase metade
        // das sessões e deixar de informar.
        val detail = detail(summary(primaryModel = OPUS), turn(seq = 1, cacheReadTokens = 160_000L))

        assertEquals(CliSessionHealth.HEALTHY, computeAnalytics(detail).health)
    }

    @Test
    fun `unknown model leaves saturation undefined and never saturated`() {
        val detail = detail(
            summary = summary(primaryModel = "gpt-5-codex"),
            turn(seq = 1, model = "gpt-5-codex", cacheReadTokens = 900_000L)
        )

        val analytics = computeAnalytics(detail)

        assertNull(analytics.contextSaturation)
        assertFalse(analytics.isSaturated)
    }

    @Test
    fun `turns without pricing are counted and excluded from the cost breakdown`() {
        val detail = detail(
            turn(seq = 1, outputTokens = 1_000_000L),
            turn(seq = 2, model = "gpt-5-codex", outputTokens = 1_000_000L)
        )

        val analytics = computeAnalytics(detail)

        assertEquals(1, analytics.unpricedTurnCount)
        assertFalse(analytics.isCostComplete)
        assertEquals(25_000_000L, analytics.costBreakdown.outputMicros)
    }

    @Test
    fun `cost breakdown splits the four components`() {
        val detail = detail(
            turn(
                seq = 1,
                inputTokens = 1_000_000L,
                outputTokens = 1_000_000L,
                cacheReadTokens = 1_000_000L,
                cacheWrite5mTokens = 1_000_000L,
                cacheWrite1hTokens = 1_000_000L
            )
        )

        val breakdown = computeAnalytics(detail).costBreakdown

        assertEquals(5_000_000L, breakdown.inputMicros)
        assertEquals(25_000_000L, breakdown.outputMicros)
        assertEquals(500_000L, breakdown.cacheReadMicros)
        assertEquals(16_250_000L, breakdown.cacheWriteMicros)
        assertEquals(46_750_000L, breakdown.totalMicros)
    }

    @Test
    fun `cost is summed with the model of each turn`() {
        val detail = detail(
            turn(seq = 1, model = OPUS, outputTokens = 1_000_000L),
            turn(seq = 2, model = SONNET, outputTokens = 1_000_000L)
        )

        // 25,00 + 15,00 USD — a fórmula Sonnet fixa do legado daria 30,00.
        assertEquals(40_000_000L, computeAnalytics(detail).costBreakdown.totalMicros)
    }

    @Test
    fun `cumulative series never decrease`() {
        val detail = detail(
            turn(seq = 1, outputTokens = 1_000L, cacheReadTokens = 1_000L),
            turn(seq = 2, outputTokens = 2_000L, cacheReadTokens = 2_000L),
            turn(seq = 3, outputTokens = 3_000L, cacheReadTokens = 3_000L)
        )

        val analytics = computeAnalytics(detail)

        assertEquals(3, analytics.cumulativeCostMicros.size)
        assertEquals(analytics.cumulativeCostMicros.sorted(), analytics.cumulativeCostMicros)
        assertEquals(analytics.cumulativeSavingsMicros.sorted(), analytics.cumulativeSavingsMicros)
    }

    @Test
    fun `turns are ordered by seq before the series are built`() {
        val detail = detail(
            turn(seq = 3, cacheReadTokens = 300L),
            turn(seq = 1, cacheReadTokens = 100L),
            turn(seq = 2, cacheReadTokens = 200L)
        )

        assertEquals(listOf(100L, 200L, 300L), computeAnalytics(detail).contextPerTurn)
    }

    @Test
    fun `session without turns yields neutral analytics`() {
        val detail = CliSessionDetail(summary = summary(), turns = emptyList())

        val analytics = computeAnalytics(detail)

        assertEquals(0L, analytics.averageContextPerTurn)
        assertEquals(0L, analytics.liveContextTokens)
        assertEquals(0L, analytics.nextInteractionCostMicros)
        assertEquals(0L, analytics.costBreakdown.totalMicros)
        assertEquals(emptyList(), analytics.contextPerTurn)
    }

    @Test
    fun `cost breakdown fraction is zero when there is no cost`() {
        val breakdown = computeAnalytics(CliSessionDetail(summary(), emptyList())).costBreakdown

        assertEquals(0.0, breakdown.fractionOf(breakdown.inputMicros))
    }

    @Test
    fun `project name is the last segment of the working directory`() {
        assertEquals(
            "usage-monitor",
            summary(cwd = "C:\\Users\\edils\\workspace\\usage-monitor").projectName
        )
        assertEquals("repo", summary(cwd = "/home/user/repo/").projectName)
        assertNull(summary(cwd = null).projectName)
    }

    private fun summary(
        primaryModel: String? = OPUS,
        cwd: String? = null,
        cacheReadTokens: Long = 0L,
        cacheWrite5mTokens: Long = 0L,
        cacheWrite1hTokens: Long = 0L
    ): CliSessionSummary {
        return CliSessionSummary(
            sessionId = "session-1",
            filePath = "/tmp/session-1.jsonl",
            cwd = cwd,
            firstTs = Instant.fromEpochMilliseconds(0L),
            lastTs = Instant.fromEpochMilliseconds(1_000L),
            primaryModel = primaryModel,
            cacheReadTokens = cacheReadTokens,
            cacheWrite5mTokens = cacheWrite5mTokens,
            cacheWrite1hTokens = cacheWrite1hTokens
        )
    }

    private fun detail(vararg turns: CliSessionTurn): CliSessionDetail {
        return CliSessionDetail(summary = summary(), turns = turns.toList())
    }

    private fun detail(summary: CliSessionSummary, vararg turns: CliSessionTurn): CliSessionDetail {
        return CliSessionDetail(summary = summary, turns = turns.toList())
    }

    private fun turn(
        seq: Int,
        model: String? = OPUS,
        isSidechain: Boolean = false,
        inputTokens: Long = 0L,
        outputTokens: Long = 0L,
        cacheReadTokens: Long = 0L,
        cacheWrite5mTokens: Long = 0L,
        cacheWrite1hTokens: Long = 0L
    ): CliSessionTurn {
        return CliSessionTurn(
            sessionId = "session-1",
            seq = seq,
            messageId = "msg-$seq",
            ts = Instant.fromEpochMilliseconds(seq.toLong() * 1_000L),
            model = model,
            isSidechain = isSidechain,
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            cacheReadTokens = cacheReadTokens,
            cacheWrite5mTokens = cacheWrite5mTokens,
            cacheWrite1hTokens = cacheWrite1hTokens
        )
    }
}

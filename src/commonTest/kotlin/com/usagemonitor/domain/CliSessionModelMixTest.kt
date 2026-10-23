package com.usagemonitor.domain

import com.usagemonitor.domain.entity.CliSessionTurn
import com.usagemonitor.domain.entity.CliUsageGroupRow
import com.usagemonitor.domain.entity.CodexCliSessionTurn
import com.usagemonitor.domain.entity.CodexCliUsageDelta
import com.usagemonitor.domain.entity.ModelPricingTable
import com.usagemonitor.domain.entity.ModelShareBasis
import com.usagemonitor.domain.entity.WindowedSessionAccumulator
import com.usagemonitor.domain.entity.modelBreakdownBySession
import com.usagemonitor.domain.entity.sessionModelMixOf
import com.usagemonitor.domain.entity.toCodexModelBreakdown
import com.usagemonitor.domain.entity.toModelBreakdown
import com.usagemonitor.domain.entity.toUsageBreakdown
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

private const val OPUS = "claude-opus-5"
private const val HAIKU = "claude-haiku-4-5"
private const val UNPRICED = "claude-unknown-9"

class CliSessionModelMixTest {

    @Test
    fun `share is the slice of the cost when the session has a priced model`() {
        val buckets = listOf(
            group(model = HAIKU, outputTokens = 1_000_000L, turnCount = 9),
            group(model = OPUS, outputTokens = 1_000_000L, turnCount = 1)
        ).toUsageBreakdown().byModel

        val mix = sessionModelMixOf(buckets)

        // O mesmo volume pesa mais no Opus: a fatia segue o custo, não os turnos.
        val opusCost = ModelPricingTable.forModel(OPUS)!!.costMicros(0L, 1_000_000L, 0L, 0L, 0L).toDouble()
        val haikuCost = ModelPricingTable.forModel(HAIKU)!!.costMicros(0L, 1_000_000L, 0L, 0L, 0L).toDouble()
        assertEquals(ModelShareBasis.COST, mix.basis)
        assertEquals(listOf(OPUS, HAIKU), mix.shares.map { share -> share.bucket.label })
        assertEquals(opusCost / (opusCost + haikuCost), mix.shares.first().share!!, 1e-9)
        assertEquals(OPUS, mix.leadingModel)
    }

    @Test
    fun `unpriced model has no share on a cost basis instead of zero`() {
        val buckets = listOf(
            group(model = OPUS, outputTokens = 1_000L),
            group(model = UNPRICED, outputTokens = 5_000_000L)
        ).toUsageBreakdown().byModel

        val mix = sessionModelMixOf(buckets)

        assertEquals(ModelShareBasis.COST, mix.basis)
        assertEquals(1.0, mix.shares.single { share -> share.bucket.label == OPUS }.share!!, 1e-9)
        assertNull(mix.shares.single { share -> share.bucket.label == UNPRICED }.share)
    }

    @Test
    fun `without any cost the share falls back to tokens`() {
        val buckets = listOf(
            group(model = UNPRICED, outputTokens = 300L),
            group(model = "claude-unknown-8", outputTokens = 100L)
        ).toUsageBreakdown().byModel

        val mix = sessionModelMixOf(buckets)

        assertEquals(ModelShareBasis.TOKENS, mix.basis)
        assertEquals(listOf(0.75, 0.25), mix.shares.map { share -> share.share })
    }

    @Test
    fun `token ties are broken by the label so two reads give the same list`() {
        val first = sessionModelMixOf(
            listOf(group(model = "b-model", outputTokens = 10L), group(model = "a-model", outputTokens = 10L)).toUsageBreakdown().byModel
        )
        val second = sessionModelMixOf(
            listOf(group(model = "a-model", outputTokens = 10L), group(model = "b-model", outputTokens = 10L)).toUsageBreakdown().byModel
        )

        assertEquals(first, second)
        assertEquals(listOf("a-model", "b-model"), first.shares.map { share -> share.bucket.label })
    }

    @Test
    fun `empty breakdown is an empty mix`() {
        val mix = sessionModelMixOf(emptyList())

        assertTrue(mix.isEmpty)
        assertNull(mix.leadingModel)
    }

    @Test
    fun `rows are split per session`() {
        val bySession = listOf(
            group(sessionId = "s1", model = OPUS, turnCount = 2),
            group(sessionId = "s1", model = HAIKU, turnCount = 3),
            group(sessionId = "s2", model = HAIKU, turnCount = 1)
        ).modelBreakdownBySession()

        assertEquals(setOf(OPUS, HAIKU), bySession.getValue("s1").map { bucket -> bucket.label }.toSet())
        assertEquals(listOf(HAIKU), bySession.getValue("s2").map { bucket -> bucket.label })
    }

    @Test
    fun `turns fold into the same buckets as the index rows, sidechain included`() {
        val turns = listOf(
            turn(seq = 1, model = OPUS, outputTokens = 100L),
            turn(seq = 2, model = HAIKU, outputTokens = 50L, isSidechain = true),
            turn(seq = 3, model = OPUS, outputTokens = 200L, cacheReadTokens = 1_000L)
        )

        val breakdown = turns.toModelBreakdown()

        val opus = breakdown.single { bucket -> bucket.label == OPUS }
        assertEquals(2, opus.turnCount)
        assertEquals(300L, opus.outputTokens)
        assertEquals(1_000L, opus.cacheReadTokens)
        assertEquals(1, breakdown.single { bucket -> bucket.label == HAIKU }.turnCount)
    }

    @Test
    fun `windowed accumulator keeps the per model split`() {
        val accumulator = WindowedSessionAccumulator(sessionId = "s1")
        accumulator.addModelGroup(OPUS, 2, 0L, 10L, 0L, 1_000L, 5_000L, 100L, 0L)
        accumulator.addModelGroup(HAIKU, 4, 0L, 20L, 0L, 2_000L, 0L, 0L, 300L)

        val summary = accumulator.toSummary()

        assertEquals(listOf(OPUS, HAIKU), summary.modelBreakdown.map { bucket -> bucket.label })
        assertEquals(300L, summary.modelBreakdown.single { bucket -> bucket.label == HAIKU }.cacheWriteTokens)
        assertEquals(ModelShareBasis.COST, summary.modelMix.basis)
    }

    @Test
    fun `codex turns fold by model with distinct turn ids and a token basis`() {
        val turns = listOf(
            codexTurn(turnId = "t1", responseId = "r1", model = "gpt-5", input = 1_000L, cached = 600L, output = 100L),
            codexTurn(turnId = "t1", responseId = "r2", model = "gpt-5", input = 500L, cached = 0L, output = 100L),
            codexTurn(turnId = "t2", responseId = "r3", model = "gpt-5-mini", input = 200L, cached = 0L, output = 0L)
        )

        val breakdown = turns.toCodexModelBreakdown()
        val mix = sessionModelMixOf(breakdown)

        val gpt5 = breakdown.single { bucket -> bucket.label == "gpt-5" }
        assertEquals(1, gpt5.turnCount)
        // Entrada cobrável: 400 + 500; o `cached` vira cache lido.
        assertEquals(900L, gpt5.inputTokens)
        assertEquals(600L, gpt5.cacheReadTokens)
        assertEquals(ModelShareBasis.TOKENS, mix.basis)
        assertEquals("gpt-5", mix.leadingModel)
    }

    private fun group(
        sessionId: String = "s1",
        model: String?,
        turnCount: Int = 1,
        outputTokens: Long = 0L
    ) = CliUsageGroupRow(sessionId = sessionId, model = model, turnCount = turnCount, outputTokens = outputTokens)

    private fun turn(
        seq: Int,
        model: String?,
        outputTokens: Long = 0L,
        cacheReadTokens: Long = 0L,
        isSidechain: Boolean = false
    ) = CliSessionTurn(
        sessionId = "s1",
        seq = seq,
        messageId = "m$seq",
        ts = Instant.fromEpochMilliseconds(seq * 1_000L),
        model = model,
        isSidechain = isSidechain,
        outputTokens = outputTokens,
        cacheReadTokens = cacheReadTokens
    )

    private fun codexTurn(
        turnId: String,
        responseId: String,
        model: String,
        input: Long,
        cached: Long,
        output: Long
    ) = CodexCliSessionTurn(
        sessionId = "c1",
        turnId = turnId,
        responseId = responseId,
        seq = 0,
        ts = Instant.fromEpochMilliseconds(0L),
        model = model,
        usage = CodexCliUsageDelta(inputTokens = input, cachedInputTokens = cached, outputTokens = output, totalTokens = input + output)
    )
}

package com.usagemonitor.domain

import com.usagemonitor.domain.entity.LONG_PROMPT_THRESHOLD_TOKENS
import com.usagemonitor.domain.entity.ModelPricing
import com.usagemonitor.domain.entity.ModelPricingTable
import com.usagemonitor.domain.entity.UsageTokenCounts
import com.usagemonitor.domain.entity.longPromptPartOfTurn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Grupo da issue #525: 76 turnos de prompt baixo e 124 de prompt alto, Haiku 5.5. */
class LongPromptGroupCostTest {

    private fun haiku(): ModelPricing = assertNotNull(ModelPricingTable.forModel(HAIKU))

    @Test
    fun `long prompt part keeps the whole turn above the threshold`() {
        val turn = UsageTokenCounts(inputTokens = 200_000L, outputTokens = 1_000L)

        assertEquals(turn, longPromptPartOfTurn(turn))
    }

    @Test
    fun `long prompt part is zero at the threshold`() {
        val turn = UsageTokenCounts(inputTokens = LONG_PROMPT_THRESHOLD_TOKENS, outputTokens = 1_000L)

        assertEquals(UsageTokenCounts(), longPromptPartOfTurn(turn))
    }

    @Test
    fun `prompt tokens sum every input except output`() {
        val counts = UsageTokenCounts(
            inputTokens = 1L,
            outputTokens = 99L,
            cacheReadTokens = 2L,
            cacheWrite5mTokens = 3L,
            cacheWrite1hTokens = 4L
        )

        assertEquals(10L, counts.promptTokens)
    }

    @Test
    fun `group cost sums both tiers`() {
        val total = UsageTokenCounts(inputTokens = 28_600_000L, outputTokens = 200_000L)
        val longPart = UsageTokenCounts(inputTokens = 24_800_000L, outputTokens = 124_000L)

        assertEquals(COST_TWO_TIERS, haiku().groupCostMicros(total, longPart))
    }

    @Test
    fun `group cost with cache read matches the hand sum`() {
        val total = UsageTokenCounts(inputTokens = 250_000L, outputTokens = 2_000L, cacheReadTokens = 100_000L)
        val longPart = UsageTokenCounts(inputTokens = 200_000L, outputTokens = 1_000L, cacheReadTokens = 100_000L)

        assertEquals(113_000L, haiku().groupCostMicros(total, longPart))
    }

    @Test
    fun `group cost with tier but without long part is null`() {
        val total = UsageTokenCounts(inputTokens = 28_600_000L, outputTokens = 200_000L)

        assertNull(haiku().groupCostMicros(total, null))
    }

    @Test
    fun `group cost of model without tier ignores long part`() {
        val plain = ModelPricing.ofUsdPerMillion(input = 2.0, output = 10.0)
        val total = UsageTokenCounts(inputTokens = 28_600_000L, outputTokens = 200_000L)
        val longPart = UsageTokenCounts(inputTokens = 24_800_000L, outputTokens = 124_000L)
        val expected = plain.costMicros(inputTokens = 28_600_000L, outputTokens = 200_000L)

        assertEquals(expected, plain.groupCostMicros(total, longPart))
        assertEquals(expected, plain.groupCostMicros(total, null))
    }

    @Test
    fun `group cache savings split by tier`() {
        val total = UsageTokenCounts(inputTokens = 250_000L, outputTokens = 2_000L, cacheReadTokens = 100_000L)
        val longPart = UsageTokenCounts(inputTokens = 200_000L, outputTokens = 1_000L, cacheReadTokens = 100_000L)

        assertEquals(45_000L, haiku().groupCacheSavingsMicros(total, longPart))
    }

    @Test
    fun `group cache savings with tier but without long part is null`() {
        val total = UsageTokenCounts(inputTokens = 250_000L, outputTokens = 2_000L, cacheReadTokens = 100_000L)

        assertNull(haiku().groupCacheSavingsMicros(total, null))
    }

    private companion object {
        const val HAIKU = "claude-haiku-5-5"
        const val COST_TWO_TIERS = 13_128_000L
    }
}

package com.usagemonitor.domain

import com.usagemonitor.domain.entity.ModelPricing
import com.usagemonitor.domain.entity.ModelPricingTable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ModelPricingTableTest {

    @Test
    fun `resolves exact model id`() {
        val pricing = assertNotNull(ModelPricingTable.forModel("claude-opus-5"))

        assertEquals(5_000_000L, pricing.inputMicrosPerMillion)
        assertEquals(25_000_000L, pricing.outputMicrosPerMillion)
    }

    @Test
    fun `resolves model id with date suffix`() {
        val withSuffix = ModelPricingTable.forModel("claude-haiku-4-5-20251001")

        assertEquals(ModelPricingTable.forModel("claude-haiku-4-5"), withSuffix)
    }

    @Test
    fun `normalizes case and surrounding whitespace`() {
        val messy = ModelPricingTable.forModel("  Claude-Sonnet-5  ")

        assertEquals(ModelPricingTable.forModel("claude-sonnet-5"), messy)
    }

    @Test
    fun `rejects prefix match without separator boundary`() {
        assertNull(ModelPricingTable.forModel("claude-opus-55"))
    }

    @Test
    fun `returns null for unknown model`() {
        assertNull(ModelPricingTable.forModel("gpt-5-codex"))
    }

    @Test
    fun `returns null for missing or blank model`() {
        assertNull(ModelPricingTable.forModel(null))
        assertNull(ModelPricingTable.forModel("   "))
    }

    @Test
    fun `derives the five prices for opus`() {
        val pricing = assertNotNull(ModelPricingTable.forModel("claude-opus-5"))

        assertEquals(5_000_000L, pricing.inputMicrosPerMillion)
        assertEquals(25_000_000L, pricing.outputMicrosPerMillion)
        assertEquals(500_000L, pricing.cacheReadMicrosPerMillion)
        assertEquals(6_250_000L, pricing.cacheWrite5mMicrosPerMillion)
        assertEquals(10_000_000L, pricing.cacheWrite1hMicrosPerMillion)
    }

    @Test
    fun `derives the five prices for sonnet`() {
        val pricing = assertNotNull(ModelPricingTable.forModel("claude-sonnet-4-6"))

        assertEquals(3_000_000L, pricing.inputMicrosPerMillion)
        assertEquals(15_000_000L, pricing.outputMicrosPerMillion)
        assertEquals(300_000L, pricing.cacheReadMicrosPerMillion)
        assertEquals(3_750_000L, pricing.cacheWrite5mMicrosPerMillion)
        assertEquals(6_000_000L, pricing.cacheWrite1hMicrosPerMillion)
    }

    @Test
    fun `derives the five prices for sonnet 5`() {
        val pricing = assertNotNull(ModelPricingTable.forModel("claude-sonnet-5"))

        assertEquals(2_000_000L, pricing.inputMicrosPerMillion)
        assertEquals(10_000_000L, pricing.outputMicrosPerMillion)
        assertEquals(200_000L, pricing.cacheReadMicrosPerMillion)
        assertEquals(2_500_000L, pricing.cacheWrite5mMicrosPerMillion)
        assertEquals(4_000_000L, pricing.cacheWrite1hMicrosPerMillion)
    }

    // A tarifa do Sonnet 5 já foi a do 4.6 por engano, e nenhum teste reprovava:
    // os dois casam pelo mesmo prefixo até a fronteira e a diferença só aparece no preço.
    @Test
    fun `sonnet 5 does not share pricing with sonnet 4-6`() {
        assertNotEquals(
            ModelPricingTable.forModel("claude-sonnet-4-6"),
            ModelPricingTable.forModel("claude-sonnet-5")
        )
    }

    @Test
    fun `derives the five prices for haiku`() {
        val pricing = assertNotNull(ModelPricingTable.forModel("claude-haiku-4-5"))

        assertEquals(1_000_000L, pricing.inputMicrosPerMillion)
        assertEquals(5_000_000L, pricing.outputMicrosPerMillion)
        assertEquals(100_000L, pricing.cacheReadMicrosPerMillion)
        assertEquals(1_250_000L, pricing.cacheWrite5mMicrosPerMillion)
        assertEquals(2_000_000L, pricing.cacheWrite1hMicrosPerMillion)
    }

    @Test
    fun `derives the five prices for fable`() {
        val pricing = assertNotNull(ModelPricingTable.forModel("claude-fable-5"))

        assertEquals(10_000_000L, pricing.inputMicrosPerMillion)
        assertEquals(50_000_000L, pricing.outputMicrosPerMillion)
        assertEquals(1_000_000L, pricing.cacheReadMicrosPerMillion)
        assertEquals(12_500_000L, pricing.cacheWrite5mMicrosPerMillion)
        assertEquals(20_000_000L, pricing.cacheWrite1hMicrosPerMillion)
    }

    @Test
    fun `shares pricing across the opus family`() {
        val reference = ModelPricingTable.forModel("claude-opus-5")

        assertEquals(reference, ModelPricingTable.forModel("claude-opus-4-8"))
        assertEquals(reference, ModelPricingTable.forModel("claude-opus-4-7"))
        assertEquals(reference, ModelPricingTable.forModel("claude-opus-4-6"))
        assertEquals(reference, ModelPricingTable.forModel("claude-opus-4-5"))
    }

    @Test
    fun `mythos shares fable pricing`() {
        assertEquals(
            ModelPricingTable.forModel("claude-fable-5"),
            ModelPricingTable.forModel("claude-mythos-5")
        )
    }

    @Test
    fun `synthetic marker is a known zero cost model`() {
        val pricing = assertNotNull(ModelPricingTable.forModel("<synthetic>"))

        assertEquals(0L, pricing.inputMicrosPerMillion)
        assertEquals(0L, pricing.outputMicrosPerMillion)
        assertEquals(0L, pricing.costMicros(inputTokens = 5_000L, outputTokens = 5_000L))
    }

    @Test
    fun `computes turn cost in micros`() {
        val pricing = assertNotNull(ModelPricingTable.forModel("claude-opus-5"))

        // 1M de cada componente: 5,00 + 25,00 + 0,50 + 6,25 + 10,00 = 46,75 USD
        val cost = pricing.costMicros(
            inputTokens = 1_000_000L,
            outputTokens = 1_000_000L,
            cacheReadTokens = 1_000_000L,
            cacheWrite5mTokens = 1_000_000L,
            cacheWrite1hTokens = 1_000_000L
        )

        assertEquals(46_750_000L, cost)
    }

    @Test
    fun `computes cost of a realistic turn`() {
        val pricing = assertNotNull(ModelPricingTable.forModel("claude-opus-5"))

        // Turno real observado no transcript: 2 in / 280 out / 18167 cache read / 9633 cache write 1h.
        val cost = pricing.costMicros(
            inputTokens = 2L,
            outputTokens = 280L,
            cacheReadTokens = 18_167L,
            cacheWrite1hTokens = 9_633L
        )

        // 10 + 7000 + 9083 (trunc) + 96330 = 112423 micros ≈ USD 0,112
        assertEquals(112_423L, cost)
    }

    @Test
    fun `zero tokens cost nothing`() {
        val pricing = assertNotNull(ModelPricingTable.forModel("claude-sonnet-5"))

        assertEquals(0L, pricing.costMicros())
    }

    @Test
    fun `builds pricing from usd values`() {
        val pricing = ModelPricing.ofUsdPerMillion(input = 3.00, output = 15.00)

        assertEquals(3_000_000L, pricing.inputMicrosPerMillion)
        assertEquals(15_000_000L, pricing.outputMicrosPerMillion)
    }

    @Test
    fun `opus 5-5 has its own official prices`() {
        val pricing = assertNotNull(ModelPricingTable.forModel("claude-opus-5-5"))

        assertEquals(4_000_000L, pricing.inputMicrosPerMillion)
        assertEquals(20_000_000L, pricing.outputMicrosPerMillion)
        assertEquals(200_000L, pricing.cacheReadMicrosPerMillion)
        assertEquals(5_000_000L, pricing.cacheWrite5mMicrosPerMillion)
        assertEquals(8_000_000L, pricing.cacheWrite1hMicrosPerMillion)
    }

    @Test
    fun `sonnet 5-5 has its own official prices`() {
        val pricing = assertNotNull(ModelPricingTable.forModel("claude-sonnet-5-5"))

        assertEquals(2_000_000L, pricing.inputMicrosPerMillion)
        assertEquals(10_000_000L, pricing.outputMicrosPerMillion)
        assertEquals(100_000L, pricing.cacheReadMicrosPerMillion)
        assertEquals(2_500_000L, pricing.cacheWrite5mMicrosPerMillion)
        assertEquals(4_000_000L, pricing.cacheWrite1hMicrosPerMillion)
    }

    @Test
    fun `fable 5-1 has its own official prices`() {
        val pricing = assertNotNull(ModelPricingTable.forModel("claude-fable-5-1"))

        assertEquals(10_000_000L, pricing.inputMicrosPerMillion)
        assertEquals(50_000_000L, pricing.outputMicrosPerMillion)
        assertEquals(250_000L, pricing.cacheReadMicrosPerMillion)
        assertEquals(12_500_000L, pricing.cacheWrite5mMicrosPerMillion)
        assertEquals(20_000_000L, pricing.cacheWrite1hMicrosPerMillion)
    }

    @Test
    fun `mythos 5-1 shares fable 5-1 pricing`() {
        assertEquals(
            ModelPricingTable.forModel("claude-fable-5-1"),
            ModelPricingTable.forModel("claude-mythos-5-1")
        )
    }

    @Test
    fun `haiku 5-5 has base prices and a long prompt tier`() {
        val pricing = assertNotNull(ModelPricingTable.forModel("claude-haiku-5-5"))

        assertEquals(100_000L, pricing.inputMicrosPerMillion)
        assertEquals(500_000L, pricing.outputMicrosPerMillion)
        assertEquals(10_000L, pricing.cacheReadMicrosPerMillion)
        assertEquals(125_000L, pricing.cacheWrite5mMicrosPerMillion)
        assertEquals(200_000L, pricing.cacheWrite1hMicrosPerMillion)

        val longPrompt = assertNotNull(pricing.longPrompt)
        assertEquals(100_000L, longPrompt.thresholdTokens)
        assertEquals(500_000L, longPrompt.pricing.inputMicrosPerMillion)
        assertEquals(2_500_000L, longPrompt.pricing.outputMicrosPerMillion)
        assertEquals(50_000L, longPrompt.pricing.cacheReadMicrosPerMillion)
        assertEquals(625_000L, longPrompt.pricing.cacheWrite5mMicrosPerMillion)
        assertEquals(1_000_000L, longPrompt.pricing.cacheWrite1hMicrosPerMillion)
    }

    @Test
    fun `5-x versions do not inherit the previous minor pricing`() {
        assertNotEquals(ModelPricingTable.forModel("claude-opus-5"), ModelPricingTable.forModel("claude-opus-5-5"))
        assertNotEquals(ModelPricingTable.forModel("claude-sonnet-5"), ModelPricingTable.forModel("claude-sonnet-5-5"))
        assertNotEquals(ModelPricingTable.forModel("claude-fable-5"), ModelPricingTable.forModel("claude-fable-5-1"))
        assertNotEquals(ModelPricingTable.forModel("claude-fable-5"), ModelPricingTable.forModel("claude-mythos-5-1"))
    }

    @Test
    fun `accepts an eight digit date suffix on a 5-5 model`() {
        assertEquals(
            ModelPricingTable.forModel("claude-opus-5-5"),
            ModelPricingTable.forModel("claude-opus-5-5-20270101")
        )
    }

    @Test
    fun `rejects a later minor version and a non date suffix`() {
        assertNull(ModelPricingTable.forModel("claude-opus-5-6"))
        assertNull(ModelPricingTable.forModel("claude-opus-5-5-beta"))
    }

    @Test
    fun `rejects date suffixes that are not exactly eight digits`() {
        assertNull(ModelPricingTable.forModel("claude-opus-5-5-2027010"))
        assertNull(ModelPricingTable.forModel("claude-opus-5-5-202701011"))
    }

    @Test
    fun `keeps haiku 4-5 date suffix matching`() {
        assertNotNull(ModelPricingTable.forModel("claude-haiku-4-5-20251001"))
    }

    @Test
    fun `computes opus 5-5 cost of a long real turn`() {
        val pricing = assertNotNull(ModelPricingTable.forModel("claude-opus-5-5"))

        // 100 in + 49.360 out + 8.405.121 cache read + 218.174 cache write 1h
        val cost = pricing.costMicros(
            inputTokens = 100L,
            outputTokens = 49_360L,
            cacheReadTokens = 8_405_121L,
            cacheWrite1hTokens = 218_174L
        )

        assertEquals(4_414_016L, cost)
    }

    @Test
    fun `haiku 5-5 long tier applies only above the threshold`() {
        val pricing = assertNotNull(ModelPricingTable.forModel("claude-haiku-5-5"))

        assertEquals(100_000L, pricing.forPrompt(100_000L).inputMicrosPerMillion)
        assertEquals(500_000L, pricing.forPrompt(100_001L).inputMicrosPerMillion)
    }

    @Test
    fun `model without long tier keeps base pricing for any prompt`() {
        val pricing = assertNotNull(ModelPricingTable.forModel("claude-opus-5-5"))

        assertEquals(pricing, pricing.forPrompt(10_000_000L))
    }

    @Test
    fun `cache read divisor overrides the default tenth`() {
        val pricing = ModelPricing.ofUsdPerMillion(input = 4.00, output = 20.00, cacheReadDivisor = 20L)

        assertEquals(200_000L, pricing.cacheReadMicrosPerMillion)
    }

    @Test
    fun `cache read divisor must be positive`() {
        assertFailsWith<IllegalArgumentException> {
            ModelPricing(inputMicrosPerMillion = 1L, outputMicrosPerMillion = 1L, cacheReadDivisor = 0L)
        }
    }

    @Test
    fun `long tier needs both usd values`() {
        val pricing = ModelPricing.ofUsdPerMillion(
            input = 0.10,
            output = 0.50,
            longPromptInput = 0.50,
            longPromptOutput = 2.50
        )

        assertNotNull(pricing.longPrompt)
        assertFailsWith<IllegalArgumentException> {
            ModelPricing.ofUsdPerMillion(input = 0.10, output = 0.50, longPromptInput = 0.50)
        }
    }
}

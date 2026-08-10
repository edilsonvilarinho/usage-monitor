package com.usagemonitor.domain

import com.usagemonitor.domain.entity.ModelPricing
import com.usagemonitor.domain.entity.ModelPricingTable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
}

package com.usagemonitor.data

import com.usagemonitor.data.mapper.AntigravityUsageParser
import com.usagemonitor.domain.entity.UsageUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class AntigravityUsageParserTest {
    @Test
    fun `parses only values explicitly printed by the quota panel`() {
        val result = AntigravityUsageParser.parse(
            """
                \u001B[1;36mModel Quotas\u001B[0m
                Gemini 3.5 Flash 42% used
                Model: Gemini 3.1 Pro
                Used: 1,200 tokens
                Remaining: 800 tokens
                Limit: 2,000 tokens
                Resets in 2h 15m
                Model: Gemini 2.5 Flash
                Remaining: 75%
            """.trimIndent()
        )

        assertEquals(3, result.size)
        assertEquals("Gemini 3.5 Flash", result[0].modelName)
        assertEquals(42.0, result[0].usedPercent)
        assertNull(result[0].remainingPercent)
        assertEquals("Gemini 3.1 Pro", result[1].modelName)
        assertEquals(1_200L, result[1].used)
        assertEquals(800L, result[1].remaining)
        assertEquals(2_000L, result[1].limit)
        assertEquals("2h 15m", result[1].resetDescription)
        assertEquals(UsageUnit.TOKENS, result[1].unit)
        assertNull(result[2].usedPercent)
        assertEquals(75.0, result[2].remainingPercent)
    }

    @Test
    fun `parses a model row with explicit remaining count and limit`() {
        val result = AntigravityUsageParser.parse(
            "Quota & Credits\nGemini 3 Pro 140 / 500 requests remaining"
        ).single()

        assertEquals("Gemini 3 Pro", result.modelName)
        assertEquals(140L, result.remaining)
        assertEquals(500L, result.limit)
        assertEquals(UsageUnit.REQUESTS, result.unit)
        assertNull(result.used)
        assertNull(result.usedPercent)
    }

    @Test
    fun `color styling does not split model names or reported percentages`() {
        val result = AntigravityUsageParser.parse(
            "Model Quotas\nModel: \u001B[36mGemini 3.1 Pro\u001B[0m\n" +
                "Used: \u001B[32m27%\u001B[0m\nRemaining: 73%"
        ).single()

        assertEquals("Gemini 3.1 Pro", result.modelName)
        assertEquals(27.0, result.usedPercent)
        assertEquals(73.0, result.remainingPercent)
    }

    @Test
    fun parsesAntigravityGroupedWeeklyRemainingPanelWithoutExposingAccountIdentity() {
        val result = AntigravityUsageParser.parse(
            """
                Models & Quota
                Account: private.person@example.com
                GEMINI MODELS
                Models within this group: Gemini Flash, Gemini Pro
                Weekly Limit Remaining
                [████████████████████████████████████] 99.49%
                Refreshes in 167h 58m
                CLAUDE AND GPT MODELS
                Models within this group: Claude Opus, Claude Sonnet, GPT-OSS
                Weekly Limit Remaining
                [████████████████████████████████████] 100.00%
                Quota available
            """.trimIndent()
        )

        assertEquals(2, result.size)
        assertEquals("Gemini models", result[0].modelName)
        assertEquals(99.49, result[0].remainingPercent)
        assertEquals(UsageUnit.PERCENTAGE, result[0].unit)
        assertEquals("167h 58m", result[0].resetDescription)
        assertEquals("Claude and GPT models", result[1].modelName)
        assertEquals(100.0, result[1].remainingPercent)
        assertEquals(UsageUnit.PERCENTAGE, result[1].unit)
        assertFalse(result.any { quota -> quota.modelName.contains("private.person") })
    }

    @Test
    fun `does not infer a percentage from remaining percentage or counts`() {
        val result = AntigravityUsageParser.parse(
            "Model Quotas\nGemini 3 Flash 80% remaining\nModel: Gemini 3 Pro\nRemaining: 2,000 tokens\nLimit: 10,000 tokens"
        )

        assertNull(result[0].usedPercent)
        assertEquals(80.0, result[0].remainingPercent)
        assertNull(result[1].usedPercent)
        assertNull(result[1].remainingPercent)
    }

    @Test
    fun `unknown screen authentication and empty quota panel fail closed`() {
        assertFailsWith<IllegalStateException> {
            AntigravityUsageParser.parse("welcome to agy")
        }
        assertFailsWith<IllegalStateException> {
            AntigravityUsageParser.parse("Model Quotas\nSign in to continue")
        }
        assertFailsWith<IllegalStateException> {
            AntigravityUsageParser.parse("Model Quotas\nGemini 3 Pro")
        }
    }

    @Test
    fun `out of range percentages are not usage`() {
        assertFailsWith<IllegalStateException> {
            AntigravityUsageParser.parse("Model Quotas\nGemini 3 Pro 100.01% used")
        }
    }
}

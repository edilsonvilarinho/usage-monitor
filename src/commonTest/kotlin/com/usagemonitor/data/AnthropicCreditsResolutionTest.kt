package com.usagemonitor.data

import com.usagemonitor.data.dto.AnthropicExtraUsage
import com.usagemonitor.data.mapper.AnthropicCreditsOutcome
import com.usagemonitor.data.mapper.resolveExtraCredits
import com.usagemonitor.domain.entity.AnthropicQuotaLabels
import com.usagemonitor.domain.entity.QuotaBlockReason
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AnthropicCreditsResolutionTest {

    private val json = Json { ignoreUnknownKeys = true }

    // Payload real de conta com créditos ligados e saldo esgotado (probe em 2026-11-12).
    private val outOfCreditsPayload = """
        {
          "is_enabled": false,
          "monthly_limit": 57000,
          "used_credits": 0,
          "utilization": 0,
          "currency": "BRL",
          "decimal_places": 2,
          "disabled_reason": "out_of_credits",
          "user_disabled": false,
          "spend_limit_reached": false,
          "credits_ever_enabled": true
        }
    """.trimIndent()

    // Payload real de conta que nunca teve créditos: sem limite, sem motivo.
    private val neverEnabledPayload = """
        {
          "is_enabled": false,
          "monthly_limit": null,
          "disabled_reason": null,
          "credits_ever_enabled": false
        }
    """.trimIndent()

    private fun extraUsageOf(payload: String): AnthropicExtraUsage {
        return json.decodeFromString<AnthropicExtraUsage>(payload)
    }

    @Test
    fun `keeps the credits quota blocked when the server ran out of credits`() {
        val resolution = resolveExtraCredits(extraUsage = extraUsageOf(outOfCreditsPayload), spend = null)

        assertEquals(AnthropicCreditsOutcome.QUOTA_FROM_EXTRA_USAGE, resolution.outcome)
        val credits = assertNotNull(resolution.quota)
        assertEquals(AnthropicQuotaLabels.EXTRA_CREDITS, credits.label)
        assertEquals(QuotaBlockReason.OUT_OF_CREDITS, credits.blockReason)
        assertEquals(0L, credits.used)
        assertEquals(100L, credits.total)
        assertEquals(0L, credits.rawUsed)
        assertEquals(57000L, credits.rawTotal)
        assertEquals("BRL", credits.currencyCode)
    }

    @Test
    fun `keeps the credits quota blocked when the monthly spend limit was reached`() {
        // Sem amostra real: a forma vem do DTO e da issue de origem (#567).
        val payload = """
            {
              "is_enabled": false,
              "monthly_limit": 57000,
              "used_credits": 57000.0,
              "utilization": 100.0,
              "currency": "BRL",
              "decimal_places": 2,
              "disabled_reason": null,
              "user_disabled": false,
              "spend_limit_reached": true,
              "credits_ever_enabled": true
            }
        """.trimIndent()

        val credits = assertNotNull(resolveExtraCredits(extraUsage = extraUsageOf(payload), spend = null).quota)

        assertEquals(QuotaBlockReason.SPEND_LIMIT_REACHED, credits.blockReason)
        assertEquals(100L, credits.used)
        assertEquals(57000L, credits.rawUsed)
        assertEquals(57000L, credits.rawTotal)
    }

    @Test
    fun `prefers out of credits when the server reports both reasons`() {
        // Ordem da regra do plano: disabled_reason "out_of_credits" vem antes de spend_limit_reached.
        val payload = """
            {
              "is_enabled": false,
              "monthly_limit": 57000,
              "used_credits": 57000.0,
              "utilization": 100.0,
              "currency": "BRL",
              "disabled_reason": "out_of_credits",
              "user_disabled": false,
              "spend_limit_reached": true,
              "credits_ever_enabled": true
            }
        """.trimIndent()

        val credits = assertNotNull(resolveExtraCredits(extraUsage = extraUsageOf(payload), spend = null).quota)

        assertEquals(QuotaBlockReason.OUT_OF_CREDITS, credits.blockReason)
    }

    @Test
    fun `hides the credits quota when the user disabled it, even with an out of credits reason`() {
        val payload = """
            {
              "is_enabled": false,
              "monthly_limit": 57000,
              "used_credits": 0,
              "utilization": 0,
              "currency": "BRL",
              "disabled_reason": "out_of_credits",
              "user_disabled": true,
              "spend_limit_reached": false,
              "credits_ever_enabled": true
            }
        """.trimIndent()

        val resolution = resolveExtraCredits(extraUsage = extraUsageOf(payload), spend = null)

        assertNull(resolution.quota)
        assertEquals(AnthropicCreditsOutcome.DISABLED, resolution.outcome)
    }

    @Test
    fun `hides the credits quota when the disabled reason is unknown`() {
        val payload = """
            {
              "is_enabled": false,
              "monthly_limit": 57000,
              "used_credits": 0,
              "utilization": 0,
              "currency": "BRL",
              "disabled_reason": "some_future_reason",
              "user_disabled": false,
              "spend_limit_reached": false,
              "credits_ever_enabled": true
            }
        """.trimIndent()

        val resolution = resolveExtraCredits(extraUsage = extraUsageOf(payload), spend = null)

        assertNull(resolution.quota)
        assertEquals(AnthropicCreditsOutcome.DISABLED, resolution.outcome)
    }

    @Test
    fun `hides the credits quota when the blocked account has no positive monthly limit`() {
        val payload = """
            {
              "is_enabled": false,
              "monthly_limit": 0,
              "used_credits": 0,
              "utilization": 0,
              "currency": "BRL",
              "disabled_reason": "out_of_credits",
              "user_disabled": false,
              "spend_limit_reached": false,
              "credits_ever_enabled": true
            }
        """.trimIndent()

        val resolution = resolveExtraCredits(extraUsage = extraUsageOf(payload), spend = null)

        assertNull(resolution.quota)
        assertEquals(AnthropicCreditsOutcome.DISABLED, resolution.outcome)
    }

    @Test
    fun `hides the credits quota for an account that never had credits`() {
        val resolution = resolveExtraCredits(extraUsage = extraUsageOf(neverEnabledPayload), spend = null)

        assertNull(resolution.quota)
        assertEquals(AnthropicCreditsOutcome.DISABLED, resolution.outcome)
    }

    @Test
    fun `enabled credits carry no block reason`() {
        val payload = """
            {
              "is_enabled": true,
              "monthly_limit": 57000,
              "used_credits": 55134.0,
              "utilization": 96.72631578947369,
              "currency": "BRL",
              "disabled_reason": null,
              "user_disabled": false,
              "spend_limit_reached": false,
              "credits_ever_enabled": true
            }
        """.trimIndent()

        val credits = assertNotNull(resolveExtraCredits(extraUsage = extraUsageOf(payload), spend = null).quota)

        assertNull(credits.blockReason)
    }
}

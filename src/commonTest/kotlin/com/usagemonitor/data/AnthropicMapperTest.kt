package com.usagemonitor.data

import com.usagemonitor.data.dto.AnthropicExtraUsage
import com.usagemonitor.data.dto.AnthropicSpend
import com.usagemonitor.data.dto.AnthropicSpendAmount
import com.usagemonitor.data.dto.AnthropicUsageResponse
import com.usagemonitor.data.dto.AnthropicUsageWindow
import com.usagemonitor.data.mapper.AnthropicMapper
import com.usagemonitor.domain.entity.AnthropicQuotaLabels
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.domain.entity.isExtraCreditsQuota
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AnthropicMapperTest {

    private val sampleResponse = AnthropicUsageResponse(
        fiveHour = AnthropicUsageWindow(utilization = 15.0, resetsAt = "2025-01-01T05:00:00Z"),
        sevenDay = AnthropicUsageWindow(utilization = 5.0, resetsAt = "2025-01-07T00:00:00Z"),
    )

    @Test
    fun `maps apiName to Anthropic`() {
        assertEquals("Anthropic", AnthropicMapper.toUsageStats(sampleResponse).apiName)
    }

    @Test
    fun `maps source to Anthropic`() {
        assertEquals(ApiSource.ANTHROPIC, AnthropicMapper.toUsageStats(sampleResponse).source)
    }

    @Test
    fun `produces two quotas for five_hour and seven_day`() {
        val result = AnthropicMapper.toUsageStats(sampleResponse)
        assertEquals(2, result.quotas.size)
    }

    @Test
    fun `five_hour maps to INTERVAL with correct values`() {
        val quota = AnthropicMapper.toUsageStats(sampleResponse).quotas[0]
        assertEquals("Claude 5h", quota.label)
        assertEquals(PeriodType.INTERVAL, quota.periodType)
        assertEquals(15L, quota.used)
        assertEquals(100L, quota.total)
        assertEquals(UsageUnit.PERCENTAGE, quota.unit)
        assertEquals(675L, quota.rawUsed)
        assertEquals(4500L, quota.rawTotal)
    }

    @Test
    fun `seven_day maps to WEEKLY with correct values`() {
        val quota = AnthropicMapper.toUsageStats(sampleResponse).quotas[1]
        assertEquals("Claude 7d", quota.label)
        assertEquals(PeriodType.WEEKLY, quota.periodType)
        assertEquals(5L, quota.used)
        assertEquals(100L, quota.total)
        assertEquals(UsageUnit.PERCENTAGE, quota.unit)
        assertEquals(2250L, quota.rawUsed)
        assertEquals(45000L, quota.rawTotal)
    }

    @Test
    fun `parses ISO 8601 resets_at to Instant`() {
        val result = AnthropicMapper.toUsageStats(sampleResponse)
        assertEquals(
            "2025-01-01T05:00:00Z",
            result.quotas[0].periodEndAt.toString()
        )
        assertEquals(
            "2025-01-07T00:00:00Z",
            result.quotas[1].periodEndAt.toString()
        )
    }

    @Test
    fun `utilization 0 maps to used 0`() {
        val response = sampleResponse.copy(
            fiveHour = AnthropicUsageWindow(utilization = 0.0, resetsAt = "2025-01-01T05:00:00Z")
        )
        assertEquals(0L, AnthropicMapper.toUsageStats(response).quotas[0].used)
    }

    @Test
    fun `utilization 1 maps to used 1`() {
        val response = sampleResponse.copy(
            fiveHour = AnthropicUsageWindow(utilization = 1.0, resetsAt = "2025-01-01T05:00:00Z")
        )
        assertEquals(1L, AnthropicMapper.toUsageStats(response).quotas[0].used)
        assertEquals(45L, AnthropicMapper.toUsageStats(response).quotas[0].rawUsed)
    }

    @Test
    fun `deserializes null resets_at without failing`() {
        val json = Json { ignoreUnknownKeys = true }
        val payload = """
            {
              "five_hour": { "utilization": 0.0, "resets_at": null },
              "seven_day": { "utilization": 98.0, "resets_at": "2026-05-01T11:59:59.727703+00:00" }
            }
        """.trimIndent()

        val response = json.decodeFromString<AnthropicUsageResponse>(payload)

        assertEquals(null, response.fiveHour.resetsAt)
        assertEquals("2026-05-01T11:59:59.727703+00:00", response.sevenDay.resetsAt)
    }

    @Test
    fun `keeps five_hour quota when resets_at is null`() {
        val response = sampleResponse.copy(
            fiveHour = AnthropicUsageWindow(utilization = 0.0, resetsAt = null)
        )

        val result = AnthropicMapper.toUsageStats(response)

        assertEquals(2, result.quotas.size)
        assertEquals("Claude 5h", result.quotas.first().label)
        assertEquals(false, result.quotas.first().hasKnownResetAt)
    }

    @Test
    fun `keeps quotas even when no reset window is available`() {
        val response = AnthropicUsageResponse(
            fiveHour = AnthropicUsageWindow(utilization = 0.0, resetsAt = null),
            sevenDay = AnthropicUsageWindow(utilization = 0.0, resetsAt = null)
        )

        val result = AnthropicMapper.toUsageStats(response)

        assertEquals(2, result.quotas.size)
        assertEquals(false, result.quotas.all { quota -> quota.hasKnownResetAt })
    }

    @Test
    fun `produces no credits quota when extra_usage is absent`() {
        val result = AnthropicMapper.toUsageStats(sampleResponse)

        assertEquals(2, result.quotas.size)
        assertNull(result.quotas.firstOrNull { quota -> quota.isExtraCreditsQuota })
    }

    @Test
    fun `produces no credits quota when extra_usage is disabled`() {
        // Payload real das contas sem créditos: tudo nulo além de is_enabled.
        val response = sampleResponse.copy(
            extraUsage = AnthropicExtraUsage(
                isEnabled = false,
                monthlyLimit = null,
                usedCredits = null,
                utilization = null,
                currency = null,
                decimalPlaces = null,
                creditsEverEnabled = false
            )
        )

        val result = AnthropicMapper.toUsageStats(response)

        assertEquals(2, result.quotas.size)
        assertNull(result.quotas.firstOrNull { quota -> quota.isExtraCreditsQuota })
    }

    @Test
    fun `maps enabled extra_usage to a third quota in the account currency`() {
        val json = Json { ignoreUnknownKeys = true }
        val payload = """
            {
              "five_hour": { "utilization": 21.5, "resets_at": "2026-08-11T20:00:00Z" },
              "seven_day": { "utilization": 50.0, "resets_at": "2026-08-15T20:00:00Z" },
              "extra_usage": {
                "is_enabled": true,
                "monthly_limit": 55000,
                "used_credits": 32784.0,
                "utilization": 59.60727272727273,
                "currency": "BRL",
                "decimal_places": 2,
                "disabled_reason": null,
                "user_disabled": false,
                "spend_limit_reached": false,
                "credits_ever_enabled": true,
                "daily": null,
                "weekly": null
              },
              "spend": {
                "used": { "amount_minor": 32784, "currency": "BRL", "exponent": 2 },
                "limit": { "amount_minor": 55000, "currency": "BRL", "exponent": 2 },
                "percent": 60,
                "severity": "normal",
                "enabled": true,
                "disabled_reason": null,
                "cap": { "money": { "amount_minor": 55000, "currency": "BRL", "exponent": 2 }, "credits": null },
                "balance": null,
                "auto_reload": null,
                "can_purchase_credits": false,
                "can_toggle": false
              }
            }
        """.trimIndent()

        val response = json.decodeFromString<AnthropicUsageResponse>(payload)
        val result = AnthropicMapper.toUsageStats(response)

        assertEquals(3, result.quotas.size)

        val credits = result.quotas[2]
        assertEquals(AnthropicQuotaLabels.EXTRA_CREDITS, credits.label)
        assertEquals(PeriodType.REPORTED, credits.periodType)
        assertEquals(UsageUnit.PERCENTAGE, credits.unit)
        // utilization 59,607% arredondado; os valores exatos ficam nos brutos.
        assertEquals(60L, credits.used)
        assertEquals(100L, credits.total)
        assertEquals(32784L, credits.rawUsed)
        assertEquals(55000L, credits.rawTotal)
        assertEquals("BRL", credits.currencyCode)
        assertEquals(false, credits.hasKnownResetAt)
    }

    @Test
    fun `falls back to the spend currency when extra_usage omits it`() {
        val response = sampleResponse.copy(
            extraUsage = AnthropicExtraUsage(
                isEnabled = true,
                monthlyLimit = 55000L,
                usedCredits = 32784.0,
                utilization = 59.60727272727273,
                currency = null
            ),
            spend = AnthropicSpend(
                used = AnthropicSpendAmount(amountMinor = 32784L, currency = "BRL", exponent = 2),
                limit = AnthropicSpendAmount(amountMinor = 55000L, currency = "BRL", exponent = 2),
                percent = 60.0,
                enabled = true
            )
        )

        val credits = AnthropicMapper.toUsageStats(response).quotas[2]

        assertEquals("BRL", credits.currencyCode)
    }

    @Test
    fun `ignores enabled extra_usage without a monthly limit`() {
        val response = sampleResponse.copy(
            extraUsage = AnthropicExtraUsage(
                isEnabled = true,
                monthlyLimit = null,
                usedCredits = 0.0
            )
        )

        assertEquals(2, AnthropicMapper.toUsageStats(response).quotas.size)
    }
}

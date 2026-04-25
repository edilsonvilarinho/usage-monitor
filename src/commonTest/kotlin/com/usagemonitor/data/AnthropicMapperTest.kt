package com.usagemonitor.data

import com.usagemonitor.data.dto.AnthropicUsageResponse
import com.usagemonitor.data.dto.AnthropicUsageWindow
import com.usagemonitor.data.mapper.AnthropicMapper
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.UsageUnit
import kotlin.test.Test
import kotlin.test.assertEquals

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
        assertEquals(UsageUnit.TOKENS, quota.unit)
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
        assertEquals(UsageUnit.TOKENS, quota.unit)
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
}

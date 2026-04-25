package com.usagemonitor.data

import com.usagemonitor.data.dto.AnthropicRateLimitDto
import com.usagemonitor.data.mapper.AnthropicMapper
import com.usagemonitor.domain.entity.UsageUnit
import kotlin.test.Test
import kotlin.test.assertEquals

class AnthropicMapperTest {

    @Test
    fun `maps apiName to Anthropic`() {
        val dto = AnthropicRateLimitDto(
            tokensLimit = 200000L,
            tokensRemaining = 150000L,
            tokensReset = "2025-01-01T00:01:00Z"
        )
        val result = AnthropicMapper.toUsageStats(dto)
        assertEquals("Anthropic", result.apiName)
    }

    @Test
    fun `calculates used tokens as limit minus remaining`() {
        val dto = AnthropicRateLimitDto(
            tokensLimit = 200000L,
            tokensRemaining = 150000L,
            tokensReset = "2025-01-01T00:01:00Z"
        )
        val result = AnthropicMapper.toUsageStats(dto)
        val quota = result.quotas[0]

        // used = 200000 - 150000 = 50000
        assertEquals(50000L, quota.used)
        assertEquals(200000L, quota.total)
    }

    @Test
    fun `used is never negative when remaining exceeds limit`() {
        // Cenário defensivo: API retorna remaining > limit (edge case)
        val dto = AnthropicRateLimitDto(
            tokensLimit = 1000L,
            tokensRemaining = 1200L,
            tokensReset = "2025-01-01T00:01:00Z"
        )
        val result = AnthropicMapper.toUsageStats(dto)
        assertEquals(0L, result.quotas[0].used)
    }

    @Test
    fun `unit is TOKENS`() {
        val dto = AnthropicRateLimitDto(
            tokensLimit = 200000L,
            tokensRemaining = 100000L,
            tokensReset = "2025-01-01T00:01:00Z"
        )
        val result = AnthropicMapper.toUsageStats(dto)
        assertEquals(UsageUnit.TOKENS, result.quotas[0].unit)
    }

    @Test
    fun `parses ISO 8601 reset timestamp`() {
        val resetString = "2025-06-15T12:30:00Z"
        val dto = AnthropicRateLimitDto(
            tokensLimit = 200000L,
            tokensRemaining = 200000L,
            tokensReset = resetString
        )
        val result = AnthropicMapper.toUsageStats(dto)
        // Verifica que o parse não lançou exceção e retornou o instante correto
        assertEquals(
            resetString,
            result.quotas[0].periodEndAt.toString()
        )
    }
}

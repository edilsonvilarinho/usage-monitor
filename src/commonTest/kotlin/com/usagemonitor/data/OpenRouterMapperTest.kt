package com.usagemonitor.data

import com.usagemonitor.data.dto.OpenRouterCreditsDto
import com.usagemonitor.data.dto.OpenRouterCreditsResponse
import com.usagemonitor.data.mapper.OpenRouterMapper
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.OpenRouterQuotaLabels
import com.usagemonitor.domain.entity.UsageUnit
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class OpenRouterMapperTest {

    /** Valores da chamada real registrada na issue #138: $5 comprados, $0 gastos. */
    @Test
    fun `maps balance to a single currency quota`() {
        val stats = OpenRouterMapper.toUsageStats(
            OpenRouterCreditsResponse(
                data = OpenRouterCreditsDto(totalCredits = 5.0, totalUsage = 0.0)
            )
        )

        assertEquals(ApiSource.OPENROUTER, stats.source)
        assertEquals("OpenRouter", stats.apiName)
        assertEquals(1, stats.quotas.size)

        val quota = stats.quotas.single()
        assertEquals(OpenRouterQuotaLabels.BALANCE, quota.label)
        assertEquals(500L, quota.total)
        assertEquals(0L, quota.used)
        assertEquals(UsageUnit.CURRENCY_USD, quota.unit)
        assertEquals(Instant.DISTANT_FUTURE, quota.periodEndAt)
        assertFalse(quota.hasKnownResetAt)
    }

    @Test
    fun `subtracts usage from purchased credits`() {
        val stats = OpenRouterMapper.toUsageStats(
            OpenRouterCreditsResponse(
                data = OpenRouterCreditsDto(totalCredits = 20.0, totalUsage = 7.5)
            )
        )

        assertEquals(1250L, stats.quotas.single().total)
    }

    /**
     * Uso acima do comprado (ex.: BYOK ou taxa) não pode virar saldo negativo
     * exibido — a UI de saldo assume `total >= 0`.
     */
    @Test
    fun `clamps negative balance to zero`() {
        val stats = OpenRouterMapper.toUsageStats(
            OpenRouterCreditsResponse(
                data = OpenRouterCreditsDto(totalCredits = 5.0, totalUsage = 8.0)
            )
        )

        assertEquals(0L, stats.quotas.single().total)
    }

    @Test
    fun `zero credits maps to zero cents without failing`() {
        val stats = OpenRouterMapper.toUsageStats(OpenRouterCreditsResponse())

        assertEquals(0L, stats.quotas.single().total)
    }
}

package com.usagemonitor.data.mapper

import com.usagemonitor.data.dto.AnthropicRateLimitDto
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.UsageUnit
import kotlinx.datetime.Instant

/**
 * Mapper: converte o DTO dos headers Anthropic para a entidade do domain.
 */
object AnthropicMapper {

    fun toUsageStats(dto: AnthropicRateLimitDto): ApiUsageStats {
        // O campo tokensReset vem como ISO 8601: "2025-01-01T00:01:00Z"
        val resetInstant = Instant.parse(dto.tokensReset)

        // `used` não é retornado diretamente — calculamos como (limite - restante)
        val tokensUsed = (dto.tokensLimit - dto.tokensRemaining).coerceAtLeast(0L)

        return ApiUsageStats(
            apiName = "Anthropic",
            quotas = listOf(
                QuotaInfo(
                    label = "Tokens",
                    used = tokensUsed,
                    total = dto.tokensLimit,
                    periodEndAt = resetInstant,
                    unit = UsageUnit.TOKENS
                )
            )
        )
    }
}

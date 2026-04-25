package com.usagemonitor.data.mapper

import com.usagemonitor.data.dto.AnthropicUsageResponse
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.UsageUnit
import kotlinx.datetime.Instant

object AnthropicMapper {

    // Escala de 0-100 para representar utilização (0.0-1.0) como inteiro
    private const val SCALE = 100L

    fun toUsageStats(response: AnthropicUsageResponse): ApiUsageStats {
        val fiveHourEnd = Instant.parse(response.fiveHour.resetsAt)
        val sevenDayEnd = Instant.parse(response.sevenDay.resetsAt)

        // Converte utilização para inteiro 0-100 para uso no arco
        val fiveHourUsed = (response.fiveHour.utilization * SCALE).toLong().coerceIn(0L, SCALE)
        val sevenDayUsed = (response.sevenDay.utilization * SCALE).toLong().coerceIn(0L, SCALE)

        return ApiUsageStats(
            apiName = "Anthropic",
            quotas = listOf(
                QuotaInfo(
                    label = "Claude 5h",
                    used = fiveHourUsed,
                    total = SCALE,
                    periodEndAt = fiveHourEnd,
                    periodType = PeriodType.INTERVAL,
                    unit = UsageUnit.PERCENTAGE
                ),
                QuotaInfo(
                    label = "Claude 7d",
                    used = sevenDayUsed,
                    total = SCALE,
                    periodEndAt = sevenDayEnd,
                    periodType = PeriodType.WEEKLY,
                    unit = UsageUnit.PERCENTAGE
                )
            )
        )
    }
}

package com.usagemonitor.data.mapper

import com.usagemonitor.data.dto.AnthropicUsageResponse
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.UsageUnit
import kotlinx.datetime.Instant
import kotlin.math.roundToLong

object AnthropicMapper {

    private const val MAX_CAPACITY_5H = 4500L
    private const val MAX_CAPACITY_7D = 45000L
    private const val SCALE = 100L

    fun toUsageStats(response: AnthropicUsageResponse): ApiUsageStats {
        val fiveHourEnd = Instant.parse(response.fiveHour.resetsAt)
        val sevenDayEnd = Instant.parse(response.sevenDay.resetsAt)

        val fiveHourUsed = response.fiveHour.utilization.toLong().coerceIn(0L, 100L)
        val sevenDayUsed = response.sevenDay.utilization.toLong().coerceIn(0L, 100L)

        val fiveHourRaw = (response.fiveHour.utilization * MAX_CAPACITY_5H / 100).roundToLong()
        val sevenDayRaw = (response.sevenDay.utilization * MAX_CAPACITY_7D / 100).roundToLong()

        return ApiUsageStats(
            apiName = "Anthropic",
            quotas = listOf(
                QuotaInfo(
                    label = "Claude 5h",
                    used = fiveHourUsed,
                    total = SCALE,
                    periodEndAt = fiveHourEnd,
                    periodType = PeriodType.INTERVAL,
                    unit = UsageUnit.TOKENS,
                    rawUsed = fiveHourRaw,
                    rawTotal = MAX_CAPACITY_5H
                ),
                QuotaInfo(
                    label = "Claude 7d",
                    used = sevenDayUsed,
                    total = SCALE,
                    periodEndAt = sevenDayEnd,
                    periodType = PeriodType.WEEKLY,
                    unit = UsageUnit.TOKENS,
                    rawUsed = sevenDayRaw,
                    rawTotal = MAX_CAPACITY_7D
                )
            )
        )
    }
}

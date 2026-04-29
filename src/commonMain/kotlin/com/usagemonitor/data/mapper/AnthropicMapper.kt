package com.usagemonitor.data.mapper

import com.usagemonitor.data.dto.AnthropicUsageResponse
import com.usagemonitor.data.dto.AnthropicUsageWindow
import com.usagemonitor.domain.entity.ApiSource
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
    private val UNKNOWN_RESET_AT = Instant.parse("2100-01-01T00:00:00Z")

    fun toUsageStats(response: AnthropicUsageResponse): ApiUsageStats {
        val quotas = buildList {
            add(createQuota(
                label = "Claude 5h",
                periodType = PeriodType.INTERVAL,
                window = response.fiveHour,
                maxCapacity = MAX_CAPACITY_5H
            ))

            add(createQuota(
                label = "Claude 7d",
                periodType = PeriodType.WEEKLY,
                window = response.sevenDay,
                maxCapacity = MAX_CAPACITY_7D
            ))
        }

        return ApiUsageStats(
            source = ApiSource.ANTHROPIC,
            apiName = "Anthropic",
            quotas = quotas
        )
    }

    private fun createQuota(
        label: String,
        periodType: PeriodType,
        window: AnthropicUsageWindow,
        maxCapacity: Long
    ): QuotaInfo {
        val resetsAt = window.resetsAt
        val periodEndAt = if (resetsAt != null) {
            Instant.parse(resetsAt)
        } else {
            UNKNOWN_RESET_AT
        }
        val used = window.utilization.toLong().coerceIn(0L, 100L)
        val rawUsed = (window.utilization * maxCapacity / 100).roundToLong()

        return QuotaInfo(
            label = label,
            used = used,
            total = SCALE,
            periodEndAt = periodEndAt,
            hasKnownResetAt = resetsAt != null,
            periodType = periodType,
            unit = UsageUnit.TOKENS,
            rawUsed = rawUsed,
            rawTotal = maxCapacity
        )
    }
}

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

    fun toUsageStats(response: AnthropicUsageResponse): ApiUsageStats {
        val quotas = buildList {
            createQuota(
                label = "Claude 5h",
                periodType = PeriodType.INTERVAL,
                window = response.fiveHour,
                maxCapacity = MAX_CAPACITY_5H
            )?.let(::add)

            createQuota(
                label = "Claude 7d",
                periodType = PeriodType.WEEKLY,
                window = response.sevenDay,
                maxCapacity = MAX_CAPACITY_7D
            )?.let(::add)
        }

        if (quotas.isEmpty()) {
            throw IllegalStateException(
                "Anthropic returned usage data without active reset windows. " +
                    "Open Claude Code CLI and authenticate again if the problem persists."
            )
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
    ): QuotaInfo? {
        val resetsAt = window.resetsAt ?: return null
        val periodEndAt = Instant.parse(resetsAt)
        val used = window.utilization.toLong().coerceIn(0L, 100L)
        val rawUsed = (window.utilization * maxCapacity / 100).roundToLong()

        return QuotaInfo(
            label = label,
            used = used,
            total = SCALE,
            periodEndAt = periodEndAt,
            periodType = periodType,
            unit = UsageUnit.TOKENS,
            rawUsed = rawUsed,
            rawTotal = maxCapacity
        )
    }
}

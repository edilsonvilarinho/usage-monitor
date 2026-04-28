package com.usagemonitor.data.mapper

import com.usagemonitor.data.dto.CodexUsageResponse
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.UsageUnit
import kotlinx.datetime.Instant

object CodexMapper {

    private const val PERCENT_SCALE = 100L

    fun toUsageStats(response: CodexUsageResponse): ApiUsageStats {
        val primaryResetAt = Instant.fromEpochSeconds(response.rateLimit.primaryWindow.resetAt)
        val secondaryResetAt = Instant.fromEpochSeconds(response.rateLimit.secondaryWindow.resetAt)

        return ApiUsageStats(
            source = ApiSource.CODEX,
            apiName = "Codex",
            quotas = listOf(
                QuotaInfo(
                    label = "Codex 5h",
                    used = response.rateLimit.primaryWindow.usedPercent.coerceIn(0L, PERCENT_SCALE),
                    total = PERCENT_SCALE,
                    periodEndAt = primaryResetAt,
                    periodType = PeriodType.INTERVAL,
                    unit = UsageUnit.PERCENTAGE
                ),
                QuotaInfo(
                    label = "Codex 7d",
                    used = response.rateLimit.secondaryWindow.usedPercent.coerceIn(0L, PERCENT_SCALE),
                    total = PERCENT_SCALE,
                    periodEndAt = secondaryResetAt,
                    periodType = PeriodType.WEEKLY,
                    unit = UsageUnit.PERCENTAGE
                )
            )
        )
    }
}

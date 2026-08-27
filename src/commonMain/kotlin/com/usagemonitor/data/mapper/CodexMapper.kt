package com.usagemonitor.data.mapper

import com.usagemonitor.data.dto.CodexUsageResponse
import com.usagemonitor.data.dto.CodexUsageWindowDto
import com.usagemonitor.data.dto.CodexWeeklyUsageResponse
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageNotice
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.UsageUnit
import kotlinx.datetime.Instant

object CodexMapper {

    private const val PERCENT_SCALE = 100L

    fun toIntervalQuota(response: CodexUsageResponse): QuotaInfo {
        return QuotaInfo(
            label = "Codex 5h",
            used = response.rateLimit.primaryWindow.usedPercent.coerceIn(0L, PERCENT_SCALE),
            total = PERCENT_SCALE,
            periodEndAt = Instant.fromEpochSeconds(response.rateLimit.primaryWindow.resetAt),
            periodType = PeriodType.INTERVAL,
            unit = UsageUnit.PERCENTAGE
        )
    }

    fun toWeeklyQuota(window: CodexUsageWindowDto): QuotaInfo {
        return QuotaInfo(
            label = "Codex 7d",
            used = window.usedPercent.coerceIn(0L, PERCENT_SCALE),
            total = PERCENT_SCALE,
            periodEndAt = Instant.fromEpochSeconds(window.resetAt),
            periodType = PeriodType.WEEKLY,
            unit = UsageUnit.PERCENTAGE
        )
    }

    fun toWeeklyQuota(response: CodexWeeklyUsageResponse): QuotaInfo {
        return QuotaInfo(
            label = "Codex 7d",
            used = response.usedPercent.coerceIn(0L, PERCENT_SCALE),
            total = PERCENT_SCALE,
            periodEndAt = Instant.fromEpochSeconds(response.resetAt),
            periodType = PeriodType.WEEKLY,
            unit = UsageUnit.PERCENTAGE
        )
    }

    fun mergeStableUsage(
        intervalQuota: QuotaInfo,
        weeklyQuota: QuotaInfo
    ): ApiUsageStats {
        return ApiUsageStats(
            source = ApiSource.CODEX,
            apiName = "Codex",
            quotas = listOf(intervalQuota, weeklyQuota)
        )
    }

    fun mergeDegradedUsage(
        intervalQuota: QuotaInfo,
        weeklyQuota: QuotaInfo
    ): ApiUsageStats {
        return ApiUsageStats(
            source = ApiSource.CODEX,
            apiName = "Codex",
            quotas = listOf(intervalQuota, weeklyQuota),
            notices = setOf(ApiUsageNotice.SOURCE_UNSTABLE)
        )
    }
}

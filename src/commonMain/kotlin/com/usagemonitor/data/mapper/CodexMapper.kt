package com.usagemonitor.data.mapper

import com.usagemonitor.data.dto.CodexUsageResponse
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

    fun toFiveHourQuota(response: CodexUsageResponse): QuotaInfo {
        return QuotaInfo(
            label = "Codex atual",
            used = response.rateLimit.primaryWindow.usedPercent.coerceIn(0L, PERCENT_SCALE),
            total = PERCENT_SCALE,
            periodEndAt = Instant.fromEpochSeconds(response.rateLimit.primaryWindow.resetAt),
            periodType = PeriodType.REPORTED,
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

    fun mergeUsage(
        fiveHourQuota: QuotaInfo,
        weeklyQuota: QuotaInfo?
    ): ApiUsageStats {
        val quotas = buildList {
            add(fiveHourQuota)
            if (weeklyQuota != null) {
                add(weeklyQuota)
            }
        }

        return ApiUsageStats(
            source = ApiSource.CODEX,
            apiName = "Codex",
            quotas = quotas,
            notices = buildSet {
                add(ApiUsageNotice.SOURCE_UNSTABLE)
                if (weeklyQuota == null) {
                    add(ApiUsageNotice.WEEKLY_QUOTA_UNAVAILABLE)
                }
            }
        )
    }
}

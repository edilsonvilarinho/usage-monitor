package com.usagemonitor.data.mapper

import com.usagemonitor.data.dto.CodexUsageResponse
import com.usagemonitor.data.dto.CodexUsageWindowDto
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageNotice
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.UsageUnit
import kotlinx.datetime.Instant

object CodexMapper {

    private const val PERCENT_SCALE = 100L
    private const val SECONDS_PER_DAY = 24L * 60L * 60L
    private const val FIVE_HOUR_SECONDS = 5L * 60L * 60L
    private const val SEVEN_DAY_SECONDS = 7L * SECONDS_PER_DAY
    private const val MONTHLY_MIN_SECONDS = 28L * SECONDS_PER_DAY
    private const val MONTHLY_MAX_SECONDS = 31L * SECONDS_PER_DAY

    fun toUsageStats(response: CodexUsageResponse): ApiUsageStats {
        val windows = listOfNotNull(
            response.rateLimit.primaryWindow,
            response.rateLimit.secondaryWindow
        )
        val quotasByPeriod = linkedMapOf<PeriodType, QuotaInfo>()
        var hasUnknownWindow = false
        var hasDuplicateWindow = false

        windows.forEach { window ->
            val mapping = mapWindow(window)
            if (mapping.periodType == PeriodType.REPORTED) {
                hasUnknownWindow = true
            }
            if (quotasByPeriod.containsKey(mapping.periodType)) {
                hasDuplicateWindow = true
            } else {
                quotasByPeriod[mapping.periodType] = mapping.quota
            }
        }

        val notices = buildSet {
            if (hasUnknownWindow || hasDuplicateWindow) {
                add(ApiUsageNotice.SOURCE_UNSTABLE)
            }
        }

        return ApiUsageStats(
            source = ApiSource.CODEX,
            apiName = "Codex",
            quotas = quotasByPeriod.values.sortedBy { quota -> periodRank(quota.periodType) },
            notices = notices
        )
    }

    private fun mapWindow(window: CodexUsageWindowDto): CodexWindowMapping {
        val periodType = when {
            window.limitWindowSeconds == FIVE_HOUR_SECONDS -> PeriodType.INTERVAL
            window.limitWindowSeconds == SEVEN_DAY_SECONDS -> PeriodType.WEEKLY
            window.limitWindowSeconds in MONTHLY_MIN_SECONDS..MONTHLY_MAX_SECONDS -> PeriodType.MONTHLY
            else -> PeriodType.REPORTED
        }
        val label = when (periodType) {
            PeriodType.INTERVAL -> "Codex 5h"
            PeriodType.WEEKLY -> "Codex 7d"
            PeriodType.MONTHLY -> "Codex mensal"
            PeriodType.REPORTED -> "Codex atual"
        }

        return CodexWindowMapping(
            periodType = periodType,
            quota = QuotaInfo(
                label = label,
                used = window.usedPercent.coerceIn(0L, PERCENT_SCALE),
                total = PERCENT_SCALE,
                periodEndAt = Instant.fromEpochSeconds(window.resetAt),
                periodType = periodType,
                unit = UsageUnit.PERCENTAGE
            )
        )
    }

    private fun periodRank(periodType: PeriodType): Int {
        return when (periodType) {
            PeriodType.INTERVAL -> 0
            PeriodType.WEEKLY -> 1
            PeriodType.MONTHLY -> 2
            PeriodType.REPORTED -> 3
        }
    }

    private data class CodexWindowMapping(
        val periodType: PeriodType,
        val quota: QuotaInfo
    )
}

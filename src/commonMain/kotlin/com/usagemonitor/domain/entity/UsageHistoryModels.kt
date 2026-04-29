package com.usagemonitor.domain.entity

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

enum class HistoryRange {
    LAST_24_HOURS,
    LAST_7_DAYS,
    LAST_30_DAYS;

    fun windowStart(now: Instant): Instant {
        return when (this) {
            LAST_24_HOURS -> now.minus(24, kotlinx.datetime.DateTimeUnit.HOUR, TimeZone.UTC)
            LAST_7_DAYS -> now.minus(7, kotlinx.datetime.DateTimeUnit.DAY, TimeZone.UTC)
            LAST_30_DAYS -> now.minus(30, kotlinx.datetime.DateTimeUnit.DAY, TimeZone.UTC)
        }
    }
}

data class UsageHistoryPoint(
    val capturedAt: Instant,
    val used: Long,
    val total: Long,
    val rawUsed: Long,
    val rawTotal: Long,
    val periodEndAt: Instant
) {
    val displayUsed: Long
        get() = if (rawUsed > 0L) rawUsed else used

    val displayTotal: Long
        get() = if (rawTotal > 0L) rawTotal else total

    val normalizedUsage: Float
        get() = if (total > 0L) {
            (used.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
}

sealed interface UsageForecast {
    data object InsufficientData : UsageForecast
    data object NoGrowth : UsageForecast
    data object ResetsBeforeExhaustion : UsageForecast
    data class EstimatedExhaustionAt(val instant: Instant) : UsageForecast
}

data class UsageHistorySeries(
    val quotaLabel: String,
    val periodType: PeriodType,
    val unit: UsageUnit,
    val points: List<UsageHistoryPoint>,
    val currentDisplayUsed: Long,
    val currentDisplayTotal: Long,
    val deltaDisplayUsed: Long,
    val averageDisplayConsumptionPerHour: Double,
    val currentPeriodEndAt: Instant,
    val forecast: UsageForecast
)

data class ApiUsageHistoryReport(
    val source: ApiSource,
    val range: HistoryRange,
    val lastUpdatedAt: Instant?,
    val series: List<UsageHistorySeries>
) {
    val isEmpty: Boolean
        get() = series.isEmpty()
}

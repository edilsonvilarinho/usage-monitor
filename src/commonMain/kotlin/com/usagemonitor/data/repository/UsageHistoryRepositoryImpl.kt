package com.usagemonitor.data.repository

import com.usagemonitor.data.datasource.UsageHistoryDataSource
import com.usagemonitor.data.dto.UsageSnapshotRecord
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageHistoryReport
import com.usagemonitor.domain.entity.HistoryRange
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.UsageForecast
import com.usagemonitor.domain.entity.UsageHistoryPoint
import com.usagemonitor.domain.entity.UsageHistorySeries
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.repository.UsageHistoryRepository
import kotlinx.datetime.Instant

class UsageHistoryRepositoryImpl(
    private val dataSource: UsageHistoryDataSource
) : UsageHistoryRepository {

    override suspend fun recordSnapshot(stats: ApiUsageStats, capturedAt: Instant) {
        dataSource.insertSnapshot(stats, capturedAt)
    }

    override suspend fun getHistoryReport(
        source: ApiSource,
        range: HistoryRange,
        now: Instant
    ): ApiUsageHistoryReport {
        val records = dataSource.readSnapshots(source, range.windowStart(now))
        val groupedSeries = records
            .groupBy { record -> HistorySeriesKey(record.quotaLabel, record.periodType) }
            .map { (key, groupRecords) -> buildSeries(key, groupRecords.sortedBy { it.capturedAt }) }
            .sortedWith(compareBy({ it.periodType.ordinal }, { it.quotaLabel }))

        return ApiUsageHistoryReport(
            source = source,
            range = range,
            lastUpdatedAt = records.maxOfOrNull { it.capturedAt },
            series = groupedSeries
        )
    }

    private fun buildSeries(
        key: HistorySeriesKey,
        records: List<UsageSnapshotRecord>
    ): UsageHistorySeries {
        val points = records.map(::toHistoryPoint)
        val currentPoint = points.last()
        val deltaDisplayUsed = calculatePositiveDelta(points)
        val hoursObserved = calculateObservedHours(points)
        val averagePerHour = if (hoursObserved > 0.0) deltaDisplayUsed.toDouble() / hoursObserved else 0.0
        val forecast = calculateForecast(points)

        return UsageHistorySeries(
            quotaLabel = key.quotaLabel,
            periodType = key.periodType,
            unit = records.last().unit,
            points = points,
            currentDisplayUsed = currentPoint.displayUsed,
            currentDisplayTotal = currentPoint.displayTotal,
            deltaDisplayUsed = deltaDisplayUsed,
            averageDisplayConsumptionPerHour = averagePerHour,
            currentPeriodEndAt = currentPoint.periodEndAt,
            forecast = forecast
        )
    }

    private fun calculateForecast(points: List<UsageHistoryPoint>): UsageForecast {
        val activeSegment = currentSegment(points)
        if (activeSegment.size < MIN_POINTS_FOR_FORECAST) {
            return UsageForecast.InsufficientData
        }

        val observedHours = calculateObservedHours(activeSegment)
        if (observedHours < MIN_HOURS_FOR_FORECAST) {
            return UsageForecast.InsufficientData
        }

        val positiveDelta = calculatePositiveDelta(activeSegment)
        if (positiveDelta <= 0L) {
            return UsageForecast.NoGrowth
        }

        val lastPoint = activeSegment.last()
        val remaining = (lastPoint.displayTotal - lastPoint.displayUsed).coerceAtLeast(0L)
        val averagePerHour = positiveDelta.toDouble() / observedHours

        if (averagePerHour <= 0.0) {
            return UsageForecast.NoGrowth
        }

        if (remaining <= 0L) {
            return UsageForecast.EstimatedExhaustionAt(lastPoint.capturedAt)
        }

        val millisUntilExhaustion = ((remaining / averagePerHour) * MILLIS_PER_HOUR).toLong()
        val estimatedInstant = Instant.fromEpochMilliseconds(
            lastPoint.capturedAt.toEpochMilliseconds() + millisUntilExhaustion
        )

        return if (estimatedInstant > lastPoint.periodEndAt) {
            UsageForecast.ResetsBeforeExhaustion
        } else {
            UsageForecast.EstimatedExhaustionAt(estimatedInstant)
        }
    }

    private fun currentSegment(points: List<UsageHistoryPoint>): List<UsageHistoryPoint> {
        if (points.size <= 1) {
            return points
        }

        var segmentStartIndex = 0
        for (index in 1 until points.size) {
            val previous = points[index - 1]
            val current = points[index]
            val resetDetected = current.displayUsed < previous.displayUsed ||
                current.periodEndAt != previous.periodEndAt
            if (resetDetected) {
                segmentStartIndex = index
            }
        }

        return points.subList(segmentStartIndex, points.size)
    }

    private fun calculatePositiveDelta(points: List<UsageHistoryPoint>): Long {
        var delta = 0L
        for (index in 1 until points.size) {
            val current = points[index]
            val previous = points[index - 1]
            val growth = current.displayUsed - previous.displayUsed
            if (growth > 0L) {
                delta += growth
            }
        }
        return delta
    }

    private fun calculateObservedHours(points: List<UsageHistoryPoint>): Double {
        if (points.size <= 1) {
            return 0.0
        }

        val first = points.first().capturedAt.toEpochMilliseconds()
        val last = points.last().capturedAt.toEpochMilliseconds()
        return (last - first).toDouble() / MILLIS_PER_HOUR
    }

    private fun toHistoryPoint(record: UsageSnapshotRecord): UsageHistoryPoint {
        return UsageHistoryPoint(
            capturedAt = record.capturedAt,
            used = record.used,
            total = record.total,
            rawUsed = record.rawUsed,
            rawTotal = record.rawTotal,
            periodEndAt = record.periodEndAt
        )
    }

    private data class HistorySeriesKey(
        val quotaLabel: String,
        val periodType: PeriodType
    )

    private companion object {
        const val MILLIS_PER_HOUR = 3_600_000.0
        // Mínimo de pontos do segmento atual para projetar exaustão sem ruído de amostragem.
        const val MIN_POINTS_FOR_FORECAST = 3
        // Janela temporal mínima (em horas) — abaixo disso, taxa instantânea é instável.
        const val MIN_HOURS_FOR_FORECAST = 0.5
    }
}

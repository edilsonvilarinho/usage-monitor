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
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.domain.entity.cumulativePositiveDelta
import com.usagemonitor.domain.entity.isSamePeriod
import com.usagemonitor.domain.entity.riskSummary
import com.usagemonitor.domain.entity.UsageAccountContext
import com.usagemonitor.domain.entity.UsageAccountKey
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.repository.UsageHistoryRepository
import kotlinx.datetime.Instant

class UsageHistoryRepositoryImpl(
    private val dataSource: UsageHistoryDataSource
) : UsageHistoryRepository {

    override suspend fun recordSnapshot(stats: ApiUsageStats, capturedAt: Instant) {
        dataSource.insertSnapshot(stats, capturedAt)
    }

    override suspend fun listAccounts(source: ApiSource): List<UsageAccountContext> {
        return dataSource.readAccounts(source)
    }

    override suspend fun getHistoryReport(
        source: ApiSource,
        range: HistoryRange,
        now: Instant
    ): ApiUsageHistoryReport {
        val records = dataSource.readSnapshots(source, range.windowStart(now))
        return buildReport(source, range, records, accountContext = null)
    }

    override suspend fun getHistoryReport(
        source: ApiSource,
        accountKey: UsageAccountKey?,
        range: HistoryRange,
        now: Instant
    ): ApiUsageHistoryReport {
        val records = dataSource.readSnapshots(source, accountKey, range.windowStart(now))
        val accountContext = if (accountKey == null) {
            null
        } else {
            dataSource.readAccounts(source).firstOrNull { account -> account.key == accountKey }
        }
        return buildReport(source, range, records, accountContext)
    }

    private fun buildReport(
        source: ApiSource,
        range: HistoryRange,
        records: List<UsageSnapshotRecord>,
        accountContext: UsageAccountContext?
    ): ApiUsageHistoryReport {
        val groupedSeries = records
            .groupBy { record -> HistorySeriesKey(record.quotaLabel, record.periodType) }
            .map { (key, groupRecords) -> buildSeries(key, groupRecords.sortedBy { it.capturedAt }, range) }
            .sortedWith(compareBy<UsageHistorySeries>({ historySeriesRank(source, it) }, { it.quotaLabel }))

        return ApiUsageHistoryReport(
            source = source,
            range = range,
            lastUpdatedAt = records.maxOfOrNull { it.capturedAt },
            series = groupedSeries,
            accountContext = accountContext
        )
    }

    private fun buildSeries(
        key: HistorySeriesKey,
        records: List<UsageSnapshotRecord>,
        range: HistoryRange
    ): UsageHistorySeries {
        val points = records.map(::toHistoryPoint)
        val renderPoints = if (range == HistoryRange.TOTAL) {
            downsamplePoints(points, MAX_TOTAL_POINTS_PER_SERIES)
        } else {
            points
        }
        val unit = records.first().unit
        val currentPoint = renderPoints.last()
        val deltaDisplayUsed = calculatePositiveDelta(renderPoints, unit)
        val hoursObserved = calculateObservedHours(renderPoints)
        val averagePerHour = if (key.periodType == PeriodType.REPORTED) {
            0.0
        } else if (hoursObserved > 0.0) {
            deltaDisplayUsed.toDouble() / hoursObserved
        } else {
            0.0
        }
        val forecast = if (key.periodType == PeriodType.REPORTED) {
            UsageForecast.InsufficientData
        } else {
            calculateForecast(renderPoints, unit)
        }

        return UsageHistorySeries(
            quotaLabel = key.quotaLabel,
            periodType = key.periodType,
            unit = records.last().unit,
            points = renderPoints,
            currentDisplayUsed = currentPoint.displayUsed,
            currentDisplayTotal = currentPoint.displayTotal,
            deltaDisplayUsed = deltaDisplayUsed,
            averageDisplayConsumptionPerHour = averagePerHour,
            currentPeriodEndAt = currentPoint.periodEndAt,
            forecast = forecast,
            riskSummary = forecast.riskSummary(
                referenceAt = currentPoint.capturedAt,
                periodEndAt = currentPoint.periodEndAt
            )
        )
    }

    private fun calculateForecast(points: List<UsageHistoryPoint>, unit: UsageUnit): UsageForecast {
        val activeSegment = currentSegment(points, unit)
        if (activeSegment.size < MIN_POINTS_FOR_FORECAST) {
            return UsageForecast.InsufficientData
        }

        val observedHours = calculateObservedHours(activeSegment)
        if (observedHours < MIN_HOURS_FOR_FORECAST) {
            return UsageForecast.InsufficientData
        }

        val positiveDelta = calculatePositiveDelta(activeSegment, unit)
        if (positiveDelta <= 0L) {
            return UsageForecast.NoGrowth
        }

        val lastPoint = activeSegment.last()
        if (unit != UsageUnit.CURRENCY_USD && lastPoint.displayTotal <= 0L) {
            return UsageForecast.InsufficientData
        }
        val remaining = if (unit == UsageUnit.CURRENCY_USD) {
            lastPoint.displayUsed.coerceAtLeast(0L)
        } else {
            (lastPoint.displayTotal - lastPoint.displayUsed).coerceAtLeast(0L)
        }
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

    private fun currentSegment(points: List<UsageHistoryPoint>, unit: UsageUnit): List<UsageHistoryPoint> {
        if (points.size <= 1) {
            return points
        }

        var segmentStartIndex = 0
        for (index in 1 until points.size) {
            val previous = points[index - 1]
            val current = points[index]
            val periodChanged = !isSamePeriod(current.periodEndAt, previous.periodEndAt)
            val resetDetected = if (unit == UsageUnit.CURRENCY_USD) {
                periodChanged
            } else {
                current.displayUsed < previous.displayUsed || periodChanged
            }
            if (resetDetected) {
                segmentStartIndex = index
            }
        }

        return points.subList(segmentStartIndex, points.size)
    }

    private fun downsamplePoints(points: List<UsageHistoryPoint>, maxPoints: Int): List<UsageHistoryPoint> {
        if (points.size <= maxPoints) {
            return points
        }

        val sampled = LinkedHashSet<UsageHistoryPoint>()
        val lastIndex = points.lastIndex
        for (index in 0 until maxPoints) {
            val scaledIndex = ((index.toDouble() * lastIndex) / (maxPoints - 1)).toInt()
            sampled += points[scaledIndex]
        }
        return sampled.toList()
    }

    private fun calculatePositiveDelta(points: List<UsageHistoryPoint>, unit: UsageUnit): Long {
        return cumulativePositiveDelta(points, unit)
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

    private fun historySeriesRank(source: ApiSource, series: UsageHistorySeries): Int {
        if (source == ApiSource.CODEX) {
            return when (series.periodType) {
                PeriodType.REPORTED -> 0
                PeriodType.INTERVAL -> 1
                PeriodType.WEEKLY -> 2
            }
        }

        return when (series.periodType) {
            PeriodType.INTERVAL -> 0
            PeriodType.WEEKLY -> 1
            PeriodType.REPORTED -> 2
        }
    }

    private companion object {
        const val MILLIS_PER_HOUR = 3_600_000.0
        // Mínimo de pontos do segmento atual para projetar exaustão sem ruído de amostragem.
        const val MIN_POINTS_FOR_FORECAST = 3
        // Janela temporal mínima (em horas) — abaixo disso, taxa instantânea é instável.
        const val MIN_HOURS_FOR_FORECAST = 0.5
        const val MAX_TOTAL_POINTS_PER_SERIES = 720
    }
}

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
import com.usagemonitor.domain.entity.UsagePeriodComparison
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.domain.entity.isSamePeriod
import com.usagemonitor.domain.entity.positiveDeltaOf
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
        // Lê a partir do início da janela **anterior** para poder comparar as
        // duas; os pontos anteriores não entram no gráfico, só no delta.
        val readFrom = range.previousWindowStart(now) ?: range.windowStart(now)
        val records = dataSource.readSnapshots(source, readFrom)
        return buildReport(source, range, records, accountContext = null, now = now)
    }

    override suspend fun getHistoryReport(
        source: ApiSource,
        accountKey: UsageAccountKey?,
        range: HistoryRange,
        now: Instant
    ): ApiUsageHistoryReport {
        val readFrom = range.previousWindowStart(now) ?: range.windowStart(now)
        val records = dataSource.readSnapshots(source, accountKey, readFrom)
        val accountContext = if (accountKey == null) {
            null
        } else {
            dataSource.readAccounts(source).firstOrNull { account -> account.key == accountKey }
        }
        return buildReport(source, range, records, accountContext, now)
    }

    private fun buildReport(
        source: ApiSource,
        range: HistoryRange,
        records: List<UsageSnapshotRecord>,
        accountContext: UsageAccountContext?,
        now: Instant
    ): ApiUsageHistoryReport {
        val currentWindowStart = range.windowStart(now)
        val hasPreviousWindow = range.previousWindowStart(now) != null

        val groupedSeries = records
            .groupBy { record -> HistorySeriesKey(record.quotaLabel, record.periodType) }
            .mapNotNull { (key, groupRecords) ->
                val sorted = groupRecords.sortedBy { it.capturedAt }
                val current = sorted.filter { record -> record.capturedAt >= currentWindowStart }
                // Série que só tem ponto na janela anterior não é desta janela:
                // publicá-la mostraria dado velho como se fosse atual.
                if (current.isEmpty()) {
                    return@mapNotNull null
                }
                val previous = if (hasPreviousWindow) {
                    sorted.filter { record -> record.capturedAt < currentWindowStart }
                } else {
                    emptyList()
                }
                buildSeries(key, current, previous, range)
            }
            .sortedWith(compareBy<UsageHistorySeries>({ historySeriesRank(source, it) }, { it.quotaLabel }))

        return ApiUsageHistoryReport(
            source = source,
            range = range,
            // Carimbo da janela corrente: o ponto mais recente da anterior seria
            // sempre mais velho que o corte e faria a tela dizer que está parada.
            lastUpdatedAt = records.filter { it.capturedAt >= currentWindowStart }.maxOfOrNull { it.capturedAt },
            series = groupedSeries,
            accountContext = accountContext
        )
    }

    private fun buildSeries(
        key: HistorySeriesKey,
        records: List<UsageSnapshotRecord>,
        previousRecords: List<UsageSnapshotRecord>,
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
        val deltaDisplayUsed = positiveDeltaOf(renderPoints, unit)
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
                periodEndAt = currentPoint.periodEndAt,
                // Do **último** ponto, não do primeiro: é ele que descreve a cota
                // como ela é agora, e é isso que faz a coleta seguinte à migração
                // já corrigir uma série cujas linhas antigas não tinham a coluna.
                hasKnownResetAt = currentPoint.hasKnownResetAt
            ),
            comparison = buildComparison(deltaDisplayUsed, previousRecords, unit),
            // Mesmos registros que já alimentam a comparação, só que crus: o
            // gráfico é quem decide como desenhar, este repositório só para
            // de descartá-los depois de calcular o delta.
            previousWindowPoints = previousRecords.map(::toHistoryPoint)
        )
    }

    /**
     * Compara o consumo desta janela com o da anterior.
     *
     * Sem ponto na janela anterior não há comparação: zero ali significaria
     * "não consumiu nada", quando o que houve foi "não havia dado".
     */
    private fun buildComparison(
        currentDelta: Long,
        previousRecords: List<UsageSnapshotRecord>,
        unit: UsageUnit
    ): UsagePeriodComparison? {
        if (previousRecords.isEmpty()) {
            return null
        }
        return UsagePeriodComparison(
            currentDelta = currentDelta,
            previousDelta = positiveDeltaOf(previousRecords.map(::toHistoryPoint), unit)
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

        val positiveDelta = positiveDeltaOf(activeSegment, unit)
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

        // Sem reset conhecido não há o que a previsão possa perder para: o saldo
        // acaba na data e pronto. Consultar `periodEndAt` aqui é o que fazia o
        // DeepSeek — que grava `Instant.DISTANT_FUTURE` — nunca cair neste ramo,
        // e o Kilo e o OpenCode — que gravam o próprio `capturedAt` — caírem
        // nele sempre (issue #109).
        if (!lastPoint.hasKnownResetAt) {
            return UsageForecast.EstimatedExhaustionAt(estimatedInstant)
        }

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
            periodEndAt = record.periodEndAt,
            hasKnownResetAt = record.hasKnownResetAt
        )
    }

    private data class HistorySeriesKey(
        val quotaLabel: String,
        val periodType: PeriodType
    )

    private fun historySeriesRank(source: ApiSource, series: UsageHistorySeries): Int {
        return when (series.periodType) {
            PeriodType.INTERVAL -> 0
            PeriodType.WEEKLY -> 1
            PeriodType.MONTHLY -> 2
            PeriodType.REPORTED -> 3
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

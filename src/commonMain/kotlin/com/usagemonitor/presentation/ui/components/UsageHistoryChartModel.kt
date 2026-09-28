package com.usagemonitor.presentation.ui.components

import androidx.compose.foundation.layout.width
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.UsageHistoryPoint
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.domain.entity.isSamePeriod
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

internal data class ValueAxis(
    val min: Float,
    val max: Float
)

internal data class ChartPlotPoint(
    val index: Int,
    val point: UsageHistoryPoint,
    val x: Float,
    val y: Float
)

internal data class ResetMarker(
    val representativeIndex: Int,
    val count: Int
)

internal data class HistoryTooltipModel(
    val title: String?,
    val subtitle: String,
    val metrics: List<TooltipMetric>
)

internal fun filteredPoints(points: List<UsageHistoryPoint>, unit: UsageUnit): List<UsageHistoryPoint> {
    return if (unit == UsageUnit.CURRENCY_USD) {
        points.filter { it.displayUsed > 0L }
    } else {
        points
    }
}

internal fun buildTimelineFractions(points: List<UsageHistoryPoint>): List<Float> {
    if (points.isEmpty()) {
        return emptyList()
    }
    if (points.size == 1) {
        return listOf(0f)
    }

    val start = points.first().capturedAt.toEpochMilliseconds()
    val end = points.last().capturedAt.toEpochMilliseconds()
    if (end <= start) {
        val maxIndex = max(points.lastIndex, 1)
        return points.indices.map { index -> index.toFloat() / maxIndex.toFloat() }
    }

    val total = (end - start).toFloat()
    return points.map { point ->
        ((point.capturedAt.toEpochMilliseconds() - start) / total).coerceIn(0f, 1f)
    }
}

internal fun buildValueAxis(points: List<UsageHistoryPoint>, unit: UsageUnit): ValueAxis? {
    val values = points.map { it.displayUsed }
    if (values.isEmpty()) {
        return null
    }

    return when (unit) {
        UsageUnit.CURRENCY_USD -> buildAbsoluteAxis(values, minimumDisplayRange = 100f)
        UsageUnit.REQUESTS -> buildAbsoluteAxis(values, minimumDisplayRange = 10f)
        else -> null
    }
}

internal fun buildPlotPoints(
    points: List<UsageHistoryPoint>,
    chartWidth: Float,
    chartHeight: Float,
    axis: ValueAxis?,
    horizontalInsetPx: Float = HISTORY_PLOT_HORIZONTAL_INSET_PX
): List<ChartPlotPoint> {
    if (points.isEmpty() || chartWidth <= 0f || chartHeight <= 0f) {
        return emptyList()
    }

    val plotValues = if (axis != null) {
        val range = (axis.max - axis.min).coerceAtLeast(1f)
        points.map { point ->
            ((point.displayUsed.toFloat() - axis.min) / range).coerceIn(0f, 1f)
        }
    } else {
        points.map { it.normalizedUsage }
    }
    val xFractions = buildTimelineFractions(points)
    val inset = resolvePlotHorizontalInset(chartWidth, horizontalInsetPx)
    val usableWidth = (chartWidth - inset * 2f).coerceAtLeast(0f)

    return points.mapIndexed { index, point ->
        val xFraction = if (points.size == 1) 0.5f else xFractions[index]
        val y = chartHeight - (plotValues[index] * chartHeight)
        ChartPlotPoint(
            index = index,
            point = point,
            x = if (points.size == 1) {
                chartWidth * xFraction
            } else {
                inset + (usableWidth * xFraction)
            },
            y = y
        )
    }
}

internal fun resolvePlotHorizontalInset(
    chartWidth: Float,
    preferredInsetPx: Float = HISTORY_PLOT_HORIZONTAL_INSET_PX
): Float {
    if (chartWidth <= 0f) {
        return 0f
    }

    return preferredInsetPx.coerceAtMost((chartWidth / 2f) - 1f).coerceAtLeast(0f)
}

internal fun findClosestPlotPointIndex(plotPoints: List<ChartPlotPoint>, pointerX: Float): Int? {
    if (plotPoints.isEmpty()) {
        return null
    }

    var closestPoint = plotPoints.first()
    var bestDistance = abs(pointerX - closestPoint.x)

    for (point in plotPoints.drop(1)) {
        val distance = abs(pointerX - point.x)
        if (distance <= bestDistance) {
            closestPoint = point
            bestDistance = distance
        }
    }

    return closestPoint.index
}

internal fun clampTooltipLeft(
    desiredCenterX: Float,
    tooltipWidth: Float,
    containerWidth: Float,
    horizontalPadding: Float = HISTORY_TOOLTIP_PADDING_PX
): Float {
    if (containerWidth <= 0f) {
        return horizontalPadding
    }

    val unclampedLeft = desiredCenterX - (tooltipWidth / 2f)
    val minLeft = horizontalPadding
    val maxLeft = (containerWidth - tooltipWidth - horizontalPadding).coerceAtLeast(minLeft)
    return unclampedLeft.coerceIn(minLeft, maxLeft)
}

internal fun framePointerToPlotPointerX(
    framePointerX: Float,
    plotStartX: Float,
    plotWidth: Float
): Float? {
    if (plotWidth <= 0f) {
        return null
    }

    val translatedPointerX = framePointerX - plotStartX
    if (translatedPointerX < 0f || translatedPointerX > plotWidth) {
        return null
    }

    return translatedPointerX
}

internal fun coerceFramePointerToPlotPointerX(
    framePointerX: Float,
    plotStartX: Float,
    plotWidth: Float
): Float? {
    if (plotWidth <= 0f) {
        return null
    }

    return (framePointerX - plotStartX).coerceIn(0f, plotWidth)
}

internal fun buildHistoryTooltipModel(
    activePoint: ChartPlotPoint?,
    points: List<UsageHistoryPoint>,
    unit: UsageUnit,
    language: AppLanguage,
    title: String?,
    subtitle: String?,
    comparisonPoint: UsageHistoryPoint? = null
): HistoryTooltipModel? {
    if (activePoint == null) {
        return null
    }

    val point = activePoint.point
    val fallbackComparisonPoint = points.getOrNull(activePoint.index - 1)
    val timestamp = formatTooltipTimestamp(point.capturedAt)
    val contextualSubtitle = if (subtitle.isNullOrBlank()) {
        timestamp
    } else {
        "$subtitle · $timestamp"
    }

    return HistoryTooltipModel(
        title = title,
        subtitle = contextualSubtitle,
        metrics = buildHistoryTooltipMetrics(
            point = point,
            comparisonPoint = comparisonPoint ?: fallbackComparisonPoint,
            unit = unit,
            language = language
        )
    )
}

internal fun buildHistoryTooltipMetrics(
    point: UsageHistoryPoint,
    comparisonPoint: UsageHistoryPoint?,
    unit: UsageUnit,
    language: AppLanguage
): List<TooltipMetric> {
    val usageLabel = when (unit) {
        UsageUnit.CURRENCY_USD -> if (language == AppLanguage.PT) "Saldo" else "Balance"
        else -> if (language == AppLanguage.PT) "Uso" else "Usage"
    }
    val changeLabel = if (language == AppLanguage.PT) "Variação" else "Change"
    val windowLabel = if (language == AppLanguage.PT) "Janela" else "Window"

    return buildList {
        add(
            TooltipMetric(
                label = usageLabel,
                value = formatTooltipUsageValue(point = point, unit = unit, language = language)
            )
        )
        add(
            TooltipMetric(
                label = changeLabel,
                value = formatTooltipDeltaValue(
                    point = point,
                    comparisonPoint = comparisonPoint,
                    unit = unit,
                    language = language
                )
            )
        )
        add(
            TooltipMetric(
                label = windowLabel,
                value = formatTooltipWindowValue(
                    instant = point.periodEndAt,
                    unit = unit,
                    language = language
                )
            )
        )
    }
}

internal fun buildTimeReferenceLabels(points: List<UsageHistoryPoint>): List<String> {
    if (points.isEmpty()) {
        return emptyList()
    }

    val middlePoint = points[points.lastIndex / 2]
    return listOf(
        formatTimeReference(points.first().capturedAt, points.first().capturedAt, points.last().capturedAt),
        formatTimeReference(middlePoint.capturedAt, points.first().capturedAt, points.last().capturedAt),
        formatTimeReference(points.last().capturedAt, points.first().capturedAt, points.last().capturedAt)
    )
}

internal fun detectHistoryRangeAnnotations(
    points: List<UsageHistoryPoint>,
    unit: UsageUnit
): HistoryRangeAnnotations? {
    if (points.isEmpty()) {
        return null
    }

    val resetIndices = mutableListOf<Int>()
    for (index in 1 until points.size) {
        val previous = points[index - 1]
        val current = points[index]
        val periodChanged = !isSamePeriod(current.periodEndAt, previous.periodEndAt)
        val usageDropped = unit != UsageUnit.CURRENCY_USD && current.displayUsed < previous.displayUsed
        if (periodChanged || usageDropped) {
            resetIndices += index
        }
    }

    return HistoryRangeAnnotations(
        startIndex = 0,
        endIndex = points.lastIndex,
        resetIndices = resetIndices
    )
}

internal fun clusterResetIndices(
    resetIndices: List<Int>,
    plotPoints: List<ChartPlotPoint>,
    minPixelGap: Float
): List<ResetMarker> {
    if (resetIndices.isEmpty()) {
        return emptyList()
    }

    val sortedIndices = resetIndices.sorted()
    val clusters = mutableListOf<MutableList<Int>>()

    for (index in sortedIndices) {
        val point = plotPoints.getOrNull(index) ?: continue
        val lastCluster = clusters.lastOrNull()
        val lastPoint = lastCluster?.lastOrNull()?.let(plotPoints::getOrNull)
        if (lastCluster != null && lastPoint != null && abs(point.x - lastPoint.x) < minPixelGap) {
            lastCluster += index
        } else {
            clusters += mutableListOf(index)
        }
    }

    return clusters.map { cluster ->
        ResetMarker(representativeIndex = cluster.last(), count = cluster.size)
    }
}

internal fun zoomedPoints(
    points: List<UsageHistoryPoint>,
    zoomRange: ClosedFloatingPointRange<Float>
): List<UsageHistoryPoint> {
    if (points.isEmpty() || zoomRange == 0f..1f) {
        return points
    }

    val fractions = buildTimelineFractions(points)
    val filtered = points.filterIndexed { index, _ ->
        fractions[index] >= zoomRange.start && fractions[index] <= zoomRange.endInclusive
    }
    return filtered.ifEmpty { points }
}

internal fun applyChartScroll(
    current: ClosedFloatingPointRange<Float>,
    scrollDeltaY: Float,
    scrollDeltaX: Float,
    pointerFraction: Float,
    shiftPressed: Boolean,
    minWidth: Float = HISTORY_MIN_ZOOM_WIDTH_FRACTION
): ClosedFloatingPointRange<Float> {
    val width = current.endInclusive - current.start
    val effectivePanDelta = if (shiftPressed) scrollDeltaY else scrollDeltaX
    val isPan = shiftPressed || abs(scrollDeltaX) > abs(scrollDeltaY)

    if (isPan) {
        if (effectivePanDelta == 0f) {
            return current
        }
        val panDelta = effectivePanDelta * width * HISTORY_PAN_SENSITIVITY
        var newStart = current.start + panDelta
        var newEnd = current.endInclusive + panDelta
        if (newStart < 0f) {
            newEnd -= newStart
            newStart = 0f
        }
        if (newEnd > 1f) {
            newStart -= (newEnd - 1f)
            newEnd = 1f
        }
        return newStart.coerceAtLeast(0f)..newEnd.coerceAtMost(1f)
    }

    if (scrollDeltaY == 0f) {
        return current
    }

    val zoomingIn = scrollDeltaY < 0f
    val zoomFactor = if (zoomingIn) HISTORY_ZOOM_STEP_FACTOR else 1f / HISTORY_ZOOM_STEP_FACTOR
    val newWidth = (width * zoomFactor).coerceIn(minWidth, 1f)
    val anchor = pointerFraction.coerceIn(0f, 1f)
    var newStart = anchor - (anchor - current.start) * (newWidth / width)
    var newEnd = newStart + newWidth
    if (newStart < 0f) {
        newEnd -= newStart
        newStart = 0f
    }
    if (newEnd > 1f) {
        newStart -= (newEnd - 1f)
        newEnd = 1f
    }
    return newStart.coerceAtLeast(0f)..newEnd.coerceAtMost(1f)
}

private fun buildAbsoluteAxis(values: List<Long>, minimumDisplayRange: Float): ValueAxis? {
    val minValue = values.minOrNull()?.toFloat() ?: return null
    val maxValue = values.maxOrNull()?.toFloat() ?: return null
    val rawRange = (maxValue - minValue).coerceAtLeast(0f)
    val displayRange = max(rawRange * 1.25f, minimumDisplayRange)
    val axisMax = maxValue.coerceAtLeast(minValue)
    val axisMin = (axisMax - displayRange).coerceAtLeast(0f)

    return ValueAxis(
        min = axisMin,
        max = axisMax
    )
}

internal fun buildHistoryTooltipTop(
    pointY: Float,
    tooltipHeight: Float,
    frameHeight: Float
): Float {
    if (tooltipHeight <= 0f || frameHeight <= 0f) {
        return HISTORY_TOOLTIP_PADDING_PX
    }

    val desiredTop = pointY - tooltipHeight - HISTORY_TOOLTIP_OFFSET_PX
    val maxTop = (frameHeight - tooltipHeight - HISTORY_TOOLTIP_PADDING_PX)
        .coerceAtLeast(HISTORY_TOOLTIP_PADDING_PX)
    return desiredTop.coerceIn(HISTORY_TOOLTIP_PADDING_PX, maxTop)
}

private fun formatTimeReference(instant: Instant, rangeStart: Instant, rangeEnd: Instant): String {
    val totalHours = (rangeEnd.toEpochMilliseconds() - rangeStart.toEpochMilliseconds()) / 3_600_000.0
    val localDateTime = instant.toLocalDateTime(TimeZone.of("America/Sao_Paulo"))

    return if (totalHours > 48.0) {
        "${localDateTime.date.dayOfMonth.toString().padStart(2, '0')}/${localDateTime.date.monthNumber.toString().padStart(2, '0')}"
    } else {
        "${localDateTime.hour.toString().padStart(2, '0')}:${localDateTime.minute.toString().padStart(2, '0')}"
    }
}

private fun formatTooltipUsageValue(
    point: UsageHistoryPoint,
    unit: UsageUnit,
    language: AppLanguage
): String {
    return when (unit) {
        UsageUnit.CURRENCY_USD -> formatCurrencyValue(point.displayUsed)
        UsageUnit.REQUESTS -> {
            if (point.displayTotal > 0L) {
                val percentage = formatPercentage(point.displayUsed, point.displayTotal)
                "${formatCountValue(point.displayUsed)}/${formatCountValue(point.displayTotal)} req ($percentage)"
            } else {
                "${formatCountValue(point.displayUsed)} req"
            }
        }

        UsageUnit.PERCENTAGE -> {
            if (point.total > 0L) {
                "${point.used}/${point.total} % (${formatPercentage(point.used, point.total)})"
            } else {
                "${point.used} %"
            }
        }

        UsageUnit.TOKENS -> {
            if (point.displayTotal > 0L) {
                val percentage = formatPercentage(point.displayUsed, point.displayTotal)
                "${formatCountValue(point.displayUsed)}/${formatCountValue(point.displayTotal)} tok ($percentage)"
            } else {
                "${formatCountValue(point.displayUsed)} tok"
            }
        }
    }
}

private fun formatTooltipDeltaValue(
    point: UsageHistoryPoint,
    comparisonPoint: UsageHistoryPoint?,
    unit: UsageUnit,
    language: AppLanguage
): String {
    if (comparisonPoint == null) {
        return if (language == AppLanguage.PT) "Sem base anterior" else "No previous point"
    }

    val baseUnavailableLabel = if (language == AppLanguage.PT) "base indisponível" else "base unavailable"
    return when (unit) {
        UsageUnit.CURRENCY_USD -> {
            val delta = point.displayUsed - comparisonPoint.displayUsed
            val absolute = formatSignedCurrencyValue(delta)
            appendRelativeVariation(
                absoluteValue = absolute,
                deltaValue = delta.toDouble(),
                baseValue = comparisonPoint.displayUsed.toDouble(),
                baseUnavailableLabel = baseUnavailableLabel
            )
        }

        UsageUnit.REQUESTS -> {
            val delta = point.displayUsed - comparisonPoint.displayUsed
            val absolute = "${formatSignedCountValue(delta)} req"
            appendRelativeVariation(
                absoluteValue = absolute,
                deltaValue = delta.toDouble(),
                baseValue = comparisonPoint.displayUsed.toDouble(),
                baseUnavailableLabel = baseUnavailableLabel
            )
        }

        UsageUnit.PERCENTAGE -> {
            val delta = point.used - comparisonPoint.used
            val absolute = "${formatSignedCountValue(delta)} p.p."
            appendRelativeVariation(
                absoluteValue = absolute,
                deltaValue = delta.toDouble(),
                baseValue = comparisonPoint.used.toDouble(),
                baseUnavailableLabel = baseUnavailableLabel
            )
        }

        UsageUnit.TOKENS -> {
            val delta = point.displayUsed - comparisonPoint.displayUsed
            val absolute = "${formatSignedCountValue(delta)} tok"
            appendRelativeVariation(
                absoluteValue = absolute,
                deltaValue = delta.toDouble(),
                baseValue = comparisonPoint.displayUsed.toDouble(),
                baseUnavailableLabel = baseUnavailableLabel
            )
        }
    }
}

private fun appendRelativeVariation(
    absoluteValue: String,
    deltaValue: Double,
    baseValue: Double,
    baseUnavailableLabel: String
): String {
    if (baseValue <= 0.0) {
        return "$absoluteValue ($baseUnavailableLabel)"
    }

    val relativePercent = (deltaValue * 100.0 / baseValue).roundToInt()
    val relativePrefix = if (relativePercent > 0) "+" else ""
    return "$absoluteValue (${relativePrefix}${relativePercent}%)"
}

private fun formatTooltipWindowValue(
    instant: Instant,
    unit: UsageUnit,
    language: AppLanguage
): String {
    val localDateTime = instant.toLocalDateTime(TimeZone.of("America/Sao_Paulo"))
    if (unit == UsageUnit.CURRENCY_USD && localDateTime.year >= 9999) {
        return if (language == AppLanguage.PT) "Sem expiração" else "No expiry"
    }

    return formatTooltipTimestamp(instant)
}

private fun formatTooltipTimestamp(instant: Instant): String {
    val localDateTime = instant.toLocalDateTime(TimeZone.of("America/Sao_Paulo"))
    val day = localDateTime.date.dayOfMonth.toString().padStart(2, '0')
    val month = localDateTime.date.monthNumber.toString().padStart(2, '0')
    val hour = localDateTime.hour.toString().padStart(2, '0')
    val minute = localDateTime.minute.toString().padStart(2, '0')
    return "$day/$month $hour:$minute BRT"
}

private fun formatCentsLabel(cents: Long): String {
    val dollars = cents / 100
    val remainder = abs(cents % 100)
    return "\$${dollars}.${remainder.toString().padStart(2, '0')}"
}

internal fun buildValueLabels(axis: ValueAxis?, unit: UsageUnit): List<String> {
    if (axis == null) {
        return emptyList()
    }

    val middle = ((axis.max + axis.min) / 2f).toLong()
    return when (unit) {
        UsageUnit.CURRENCY_USD -> listOf(
            formatCentsLabel(axis.max.toLong()),
            formatCentsLabel(middle),
            formatCentsLabel(axis.min.toLong())
        )

        UsageUnit.REQUESTS -> listOf(
            formatCountValue(axis.max.toLong()),
            formatCountValue(middle),
            formatCountValue(axis.min.toLong())
        )

        else -> emptyList()
    }
}

private fun formatCurrencyValue(cents: Long): String {
    val sign = if (cents < 0L) "-" else ""
    val absoluteCents = abs(cents)
    val dollars = absoluteCents / 100
    val remainder = absoluteCents % 100
    return "${sign}\$${dollars}.${remainder.toString().padStart(2, '0')}"
}

private fun formatSignedCurrencyValue(cents: Long): String {
    val prefix = if (cents > 0L) "+" else ""
    return prefix + formatCurrencyValue(cents)
}

private fun formatCountValue(value: Long): String {
    return when {
        value >= 1_000_000L -> "${trimDecimal(value / 1_000_000.0)}M"
        value >= 1_000L -> "${trimDecimal(value / 1_000.0)}K"
        else -> value.toString()
    }
}

private fun formatSignedCountValue(value: Long): String {
    val prefix = if (value > 0L) "+" else ""
    return prefix + formatCountValue(value)
}

private fun formatPercentage(used: Long, total: Long): String {
    if (total <= 0L) {
        return "—"
    }

    val percentage = (used * 100.0 / total.toDouble()).roundToInt()
    return "$percentage%"
}

private fun trimDecimal(value: Double): String {
    val text = "%.1f".format(value)
    return text.removeSuffix(".0").removeSuffix(",0")
}

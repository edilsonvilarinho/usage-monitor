package com.usagemonitor.presentation.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.UsageHistoryPoint
import com.usagemonitor.domain.entity.UsageUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.max

@Composable
fun UsageHistoryLineChart(
    points: List<UsageHistoryPoint>,
    unit: UsageUnit,
    modifier: Modifier = Modifier
) {
    val lineColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.88f)
    val fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val axisTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val renderPoints = filteredPoints(points, unit)
    val timeLabels = buildTimeReferenceLabels(renderPoints)
    val valueAxis = buildValueAxis(renderPoints, unit)
    val valueLabels = buildValueLabels(valueAxis, unit)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(144.dp)
        ) {
            if (valueLabels.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .height(120.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    valueLabels.forEach { label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = axisTextColor
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .padding(start = if (valueLabels.isNotEmpty()) 48.dp else 0.dp)
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .height(120.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 1.75.dp.toPx()
                    val gridStroke = 1.dp.toPx()

                    drawLine(
                        color = gridColor,
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = gridStroke
                    )
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, size.height * 0.5f),
                        end = Offset(size.width, size.height * 0.5f),
                        strokeWidth = gridStroke
                    )
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, size.height),
                        end = Offset(size.width, size.height),
                        strokeWidth = gridStroke
                    )

                    if (renderPoints.size <= 1) {
                        return@Canvas
                    }

                    val plotValues = if (valueAxis != null) {
                        val range = (valueAxis.max - valueAxis.min).coerceAtLeast(1f)
                        renderPoints.map { point ->
                            ((point.displayUsed.toFloat() - valueAxis.min) / range).coerceIn(0f, 1f)
                        }
                    } else {
                        renderPoints.map { it.normalizedUsage }
                    }
                    val xFractions = buildTimelineFractions(renderPoints)
                    val path = Path()
                    val fillPath = Path()

                    renderPoints.forEachIndexed { index, _ ->
                        val x = size.width * xFractions[index]
                        val y = size.height - (plotValues[index] * size.height)

                        if (index == 0) {
                            path.moveTo(x, y)
                            fillPath.moveTo(x, size.height)
                            fillPath.lineTo(x, y)
                        } else {
                            path.lineTo(x, y)
                            fillPath.lineTo(x, y)
                        }
                    }

                    val lastX = size.width * xFractions.last()
                    fillPath.lineTo(lastX, size.height)
                    fillPath.close()

                    if (unit == UsageUnit.CURRENCY_USD) {
                        drawPath(path = fillPath, color = fillColor)
                    }

                    drawPath(
                        path = path,
                        color = lineColor,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }
            }
        }

        if (timeLabels.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = if (valueLabels.isNotEmpty()) 48.dp else 0.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                timeLabels.forEachIndexed { index, label ->
                    Text(
                        modifier = Modifier.weight(1f),
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = axisTextColor,
                        textAlign = when (index) {
                            0 -> TextAlign.Start
                            1 -> TextAlign.Center
                            else -> TextAlign.End
                        }
                    )
                }
            }
        }
    }
}

internal data class ValueAxis(
    val min: Float,
    val max: Float
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

private fun formatTimeReference(instant: Instant, rangeStart: Instant, rangeEnd: Instant): String {
    val totalHours = (rangeEnd.toEpochMilliseconds() - rangeStart.toEpochMilliseconds()) / 3_600_000.0
    val localDateTime = instant.toLocalDateTime(TimeZone.of("America/Sao_Paulo"))

    return if (totalHours > 48.0) {
        "${localDateTime.date.dayOfMonth.toString().padStart(2, '0')}/${localDateTime.date.monthNumber.toString().padStart(2, '0')}"
    } else {
        "${localDateTime.hour.toString().padStart(2, '0')}:${localDateTime.minute.toString().padStart(2, '0')}"
    }
}

private fun formatCentsLabel(cents: Long): String {
    val dollars = cents / 100
    val remainder = kotlin.math.abs(cents % 100)
    return "\$${dollars}.${remainder.toString().padStart(2, '0')}"
}

private fun buildValueLabels(axis: ValueAxis?, unit: UsageUnit): List<String> {
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
            formatCountLabel(axis.max.toLong()),
            formatCountLabel(middle),
            formatCountLabel(axis.min.toLong())
        )
        else -> emptyList()
    }
}

private fun formatCountLabel(value: Long): String {
    return when {
        value >= 1_000_000L -> "${trimDecimal(value / 1_000_000.0)}M"
        value >= 1_000L -> "${trimDecimal(value / 1_000.0)}K"
        else -> value.toString()
    }
}

private fun trimDecimal(value: Double): String {
    val text = "%.1f".format(value)
    return text.removeSuffix(".0").removeSuffix(",0")
}

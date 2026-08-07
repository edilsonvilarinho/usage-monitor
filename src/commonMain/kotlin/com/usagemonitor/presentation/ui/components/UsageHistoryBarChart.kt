package com.usagemonitor.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.UsageBucketGranularity
import com.usagemonitor.domain.entity.UsageBucketTotal
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.presentation.ui.formatBucketLabel
import com.usagemonitor.presentation.ui.formatCumulativeConsumption

private val BAR_CHART_HEIGHT = 96.dp
private val BAR_GAP = 3.dp
private const val MIN_BAR_HEIGHT_FRACTION = 0.03f

/**
 * Gráfico de barras de consumo acumulado por bucket de calendário (hora/dia/
 * semana, conforme o Intervalo selecionado). Diferente do `UsageHistoryLineChart`
 * (que sempre mostra o histórico completo do ciclo de reset), este componente
 * é o único afetado pelo seletor "Intervalo" — sem zoom/pan/drag-compare, só
 * hover simples por barra.
 */
@Composable
internal fun UsageHistoryBarChart(
    buckets: List<UsageBucketTotal>,
    granularity: UsageBucketGranularity,
    unit: UsageUnit,
    totalForRatio: Long,
    language: AppLanguage,
    modifier: Modifier = Modifier
) {
    if (buckets.isEmpty()) {
        Text(
            text = if (language == AppLanguage.PT) {
                "Sem dados para o período selecionado."
            } else {
                "No data for the selected period."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier
        )
        return
    }

    val barColor = MaterialTheme.colorScheme.primary
    val emptyBarColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val axisTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val maxDelta = buckets.maxOf { it.delta }.coerceAtLeast(1L)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(BAR_CHART_HEIGHT),
            horizontalArrangement = Arrangement.spacedBy(BAR_GAP),
            verticalAlignment = Alignment.Bottom
        ) {
            buckets.forEach { bucket ->
                val heightFraction = (bucket.delta.toFloat() / maxDelta.toFloat())
                    .coerceIn(MIN_BAR_HEIGHT_FRACTION, 1f)
                HoverTooltipBox(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    title = formatBucketLabel(bucket.bucketStart, granularity, language),
                    metrics = listOf(
                        TooltipMetric(
                            label = if (language == AppLanguage.PT) "Consumido" else "Consumed",
                            value = formatCumulativeConsumption(
                                delta = bucket.delta.toDouble(),
                                total = totalForRatio,
                                unit = unit,
                                language = language
                            )
                        )
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(heightFraction)
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .background(if (bucket.delta > 0L) barColor else emptyBarColor)
                    )
                }
            }
        }

        if (buckets.size > 1) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = formatBucketLabel(buckets.first().bucketStart, granularity, language),
                    style = MaterialTheme.typography.labelSmall,
                    color = axisTextColor,
                    textAlign = TextAlign.Start
                )
                Text(
                    modifier = Modifier.weight(1f),
                    text = formatBucketLabel(buckets.last().bucketStart, granularity, language),
                    style = MaterialTheme.typography.labelSmall,
                    color = axisTextColor,
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

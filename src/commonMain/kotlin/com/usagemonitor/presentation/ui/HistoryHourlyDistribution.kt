package com.usagemonitor.presentation.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.QuotaHourlyDistribution
import com.usagemonitor.presentation.ui.theme.AppSpacing

private val HOURLY_CHART_HEIGHT = 56.dp

/** Consumo por hora do dia (BRT): 24 barras, frase do pico abaixo. */
@Composable
internal fun HistoryHourlyDistribution(
    distribution: QuotaHourlyDistribution,
    color: Color,
    language: AppLanguage
) {
    val summary = hourlyPeakLabel(distribution, language)
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val trackColor = MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.md),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
    ) {
        Text(
            text = if (language == AppLanguage.PT) "Consumo por hora do dia (BRT)" else "Usage by hour of day (BRT)",
            style = MaterialTheme.typography.labelSmall,
            color = axisColor
        )
        // As barras não carregam número: a frase abaixo delas é o que diz a hora
        // de pico, e é ela que o leitor de tela recebe.
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(HOURLY_CHART_HEIGHT)
                .semantics { contentDescription = summary }
        ) {
            val values = distribution.percentByHour
            val max = values.maxOrNull()?.takeIf { it > 0.0 } ?: 1.0
            val slot = size.width / values.size
            val barWidth = slot * 0.7f
            values.forEachIndexed { hour, value ->
                val left = hour * slot + (slot - barWidth) / 2f
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(left, size.height - 1.dp.toPx()),
                    size = Size(barWidth, 1.dp.toPx())
                )
                val barHeight = (value / max).toFloat() * size.height
                if (barHeight > 0f) {
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(left, size.height - barHeight),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(2.dp.toPx())
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("0h", "6h", "12h", "18h", "23h").forEachIndexed { index, label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = axisColor,
                    modifier = Modifier.weight(1f),
                    textAlign = when (index) {
                        0 -> TextAlign.Start
                        4 -> TextAlign.End
                        else -> TextAlign.Center
                    }
                )
            }
        }
        Text(
            text = summary,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

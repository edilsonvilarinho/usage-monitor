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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.layout
import com.usagemonitor.presentation.ui.components.AppTooltipSurface
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private val HOURLY_CHART_HEIGHT = 56.dp
private val HOURLY_TOOLTIP_WIDTH = 190.dp
private val HOURLY_TOOLTIP_GAP = 4.dp
private const val HOURS_IN_DAY = 24

const val HISTORY_HOURLY_CHART_TAG = "historyHourlyChart"
const val HISTORY_HOURLY_TOOLTIP_TAG = "historyHourlyTooltip"

/** A hora sob o ponteiro, de 0 a 23; `null` fora da área. */
internal fun hourAt(x: Float, width: Float): Int? {
    if (width <= 0f || x < 0f || x > width) {
        return null
    }
    return (x / width * HOURS_IN_DAY).toInt().coerceIn(0, HOURS_IN_DAY - 1)
}

/** "17h–18h BRT · 53% do consumo"; hora sem subida diz que não houve consumo. */
internal fun hourlyTooltipLabel(distribution: QuotaHourlyDistribution, hour: Int, language: AppLanguage): String {
    val range = "${hour}h–${hour + 1}h BRT"
    val value = distribution.percentByHour.getOrNull(hour) ?: 0.0
    val total = distribution.percentByHour.sum()
    if (value <= 0.0 || total <= 0.0) {
        return if (language == AppLanguage.PT) "$range · sem consumo" else "$range · no usage"
    }
    val share = (value / total * 100.0).roundToLong()
    return if (language == AppLanguage.PT) "$range · $share% do consumo" else "$range · $share% of usage"
}

/** Consumo por hora do dia (BRT): 24 barras, frase do pico abaixo. */
@OptIn(ExperimentalComposeUiApi::class)
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
        // de pico, e é ela que o leitor de tela recebe. A hora de cada barra e a
        // parte dela no consumo aparecem no hover (#392 — sem isso as barras não
        // se explicavam).
        var hoveredHour by remember(distribution) { mutableStateOf<Int?>(null) }
        val hoverColor = MaterialTheme.colorScheme.surfaceVariant
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val density = LocalDensity.current
            val plotWidthPx = with(density) { maxWidth.toPx() }
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HOURLY_CHART_HEIGHT)
                    .testTag(HISTORY_HOURLY_CHART_TAG)
                    // Enter também: o primeiro movimento sobre a área chega como Enter.
                    .onPointerEvent(PointerEventType.Enter) { event ->
                        hoveredHour = hourAt(event.changes.first().position.x, size.width.toFloat())
                    }
                    .onPointerEvent(PointerEventType.Move) { event ->
                        hoveredHour = hourAt(event.changes.first().position.x, size.width.toFloat())
                    }
                    .onPointerEvent(PointerEventType.Exit) { hoveredHour = null }
                    .semantics { contentDescription = summary }
            ) {
            val values = distribution.percentByHour
            val max = values.maxOrNull()?.takeIf { it > 0.0 } ?: 1.0
            val slot = size.width / values.size
            val barWidth = slot * 0.7f
            hoveredHour?.let { hour ->
                drawRect(color = hoverColor, topLeft = Offset(hour * slot, 0f), size = Size(slot, size.height))
            }
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
            hoveredHour?.let { hour ->
                val tooltipWidthPx = with(density) { HOURLY_TOOLTIP_WIDTH.toPx() }
                val anchorX = plotWidthPx * (hour + 0.5f) / HOURS_IN_DAY
                val left = (anchorX - tooltipWidthPx / 2f).coerceIn(0f, (plotWidthPx - tooltipWidthPx).coerceAtLeast(0f))
                // Acima das barras e com altura zero no layout: por cima delas o
                // Surface capturaria o ponteiro, o Canvas receberia Exit, a bolha
                // sumiria e voltaria a cada quadro.
                AppTooltipSurface(
                    modifier = Modifier
                        .width(HOURLY_TOOLTIP_WIDTH)
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            layout(placeable.width, 0) {
                                placeable.place(left.roundToInt(), -placeable.height - HOURLY_TOOLTIP_GAP.roundToPx())
                            }
                        }
                        .testTag(HISTORY_HOURLY_TOOLTIP_TAG)
                ) {
                    Text(
                        text = hourlyTooltipLabel(distribution, hour, language),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs)
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

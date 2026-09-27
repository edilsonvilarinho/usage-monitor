package com.usagemonitor.presentation.ui.components

import androidx.compose.ui.graphics.Color
import com.usagemonitor.domain.entity.UsageHistoryPoint
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Série sobreposta à principal no mesmo gráfico (issue #320): a janela semanal
 * desenhada junto da de 5 horas.
 *
 * A série principal continua dona de tudo que depende de índice — zoom, ponto em
 * foco, reinícios e linha do período anterior. A sobreposta só é traçada e lida no
 * tooltip, **pelo tempo**: as duas séries são gravadas na mesma coleta, mas nada
 * garante a mesma contagem de pontos (uma cota pode faltar numa leitura), então
 * casar por índice poria o ponto de uma hora ao lado do de outra.
 *
 * Só para séries percentuais: o eixo é 0–100% para as duas, e é isso que permite
 * lê-las contra a mesma grade.
 */
internal data class HistoryChartOverlay(
    val points: List<UsageHistoryPoint>,
    val label: String,
    val color: Color
)

/**
 * Pontos da sobreposta no espaço do gráfico, dentro do intervalo de tempo que a
 * série principal mostra — com zoom, o recorte da principal é o que define o
 * trecho, e a sobreposta acompanha pelo carimbo, não pela fração de índice.
 */
internal fun buildOverlayPlotPoints(
    overlayPoints: List<UsageHistoryPoint>,
    reference: List<UsageHistoryPoint>,
    chartWidth: Float,
    chartHeight: Float,
    horizontalInsetPx: Float = HISTORY_PLOT_HORIZONTAL_INSET_PX
): List<ChartPlotPoint> {
    if (reference.size < 2 || chartWidth <= 0f || chartHeight <= 0f) {
        return emptyList()
    }

    val start = reference.first().capturedAt.toEpochMilliseconds()
    val end = reference.last().capturedAt.toEpochMilliseconds()
    if (end <= start) {
        return emptyList()
    }

    val span = (end - start).toFloat()
    val inset = resolvePlotHorizontalInset(chartWidth, horizontalInsetPx)
    val usableWidth = (chartWidth - inset * 2f).coerceAtLeast(0f)

    return overlayPoints
        .filter { point ->
            val at = point.capturedAt.toEpochMilliseconds()
            at in start..end
        }
        .mapIndexed { index, point ->
            val fraction = (point.capturedAt.toEpochMilliseconds() - start) / span
            ChartPlotPoint(
                index = index,
                point = point,
                x = inset + usableWidth * fraction,
                y = chartHeight - point.normalizedUsage * chartHeight
            )
        }
}

/** O ponto da sobreposta mais próximo no tempo do ponto em foco da principal. */
internal fun findOverlayPointAt(
    overlayPlotPoints: List<ChartPlotPoint>,
    active: ChartPlotPoint
): ChartPlotPoint? {
    val target = active.point.capturedAt.toEpochMilliseconds()
    return overlayPlotPoints.minByOrNull { candidate ->
        abs(candidate.point.capturedAt.toEpochMilliseconds() - target)
    }
}

/** Linha da sobreposta no tooltip: o rótulo da série e o percentual naquele instante. */
internal fun overlayTooltipMetric(label: String, point: UsageHistoryPoint): TooltipMetric {
    return TooltipMetric(
        label = label,
        value = "${(point.normalizedUsage * 100f).roundToInt()}%"
    )
}

/**
 * O tooltip da principal com uma linha por sobreposta, logo abaixo da linha de
 * uso. A linha de uso passa a levar o nome da série: com duas séries, "Uso" sem
 * dizer de qual janela é ambíguo.
 */
internal fun withOverlayMetrics(
    model: HistoryTooltipModel,
    seriesLabel: String?,
    overlayPoints: List<Pair<HistoryChartOverlay, ChartPlotPoint>>
): HistoryTooltipModel {
    if (overlayPoints.isEmpty() || model.metrics.isEmpty()) {
        return model
    }

    val usage = model.metrics.first()
    val namedUsage = if (seriesLabel.isNullOrBlank()) usage else usage.copy(label = seriesLabel)
    val overlayMetrics = overlayPoints.map { (overlay, plotPoint) ->
        overlayTooltipMetric(overlay.label, plotPoint.point)
    }
    return model.copy(metrics = listOf(namedUsage) + overlayMetrics + model.metrics.drop(1))
}

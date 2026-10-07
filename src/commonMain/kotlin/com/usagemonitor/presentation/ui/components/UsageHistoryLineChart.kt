package com.usagemonitor.presentation.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.UsageHistoryPoint
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.presentation.ui.theme.appTween

internal const val HISTORY_TOOLTIP_PADDING_PX = 8f
internal const val HISTORY_TOOLTIP_OFFSET_PX = 10f
internal const val HISTORY_PLOT_HORIZONTAL_INSET_PX = 14f
private val HISTORY_PLOT_HEIGHT = 120.dp
private val HISTORY_TOOLTIP_BAND_HEIGHT = 48.dp
private val HISTORY_FRAME_HEIGHT = HISTORY_PLOT_HEIGHT + HISTORY_TOOLTIP_BAND_HEIGHT
private const val HISTORY_RESET_CLUSTER_GAP_PX = 24f
internal const val HISTORY_MIN_ZOOM_WIDTH_FRACTION = 0.05f
internal const val HISTORY_ZOOM_STEP_FACTOR = 0.85f
internal const val HISTORY_PAN_SENSITIVITY = 0.1f

private const val HISTORY_REVEAL_MILLIS = 900

internal data class HistoryRangeAnnotations(
    val startIndex: Int,
    val endIndex: Int,
    val resetIndices: List<Int>
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun UsageHistoryLineChart(
    points: List<UsageHistoryPoint>,
    unit: UsageUnit,
    language: AppLanguage,
    chartSelectionKey: String,
    modifier: Modifier = Modifier,
    tooltipTitle: String? = null,
    tooltipSubtitle: String? = null,
    /**
     * Cor da série.
     *
     * Entra por parâmetro porque a identidade é da **fonte**: o histórico da
     * Anthropic é azul, o do Codex é verde-água, e os dois desenhavam a mesma
     * linha `primary` — a cor que o card do dashboard usa para distingui-los não
     * chegava até aqui.
     */
    accentColor: Color = MaterialTheme.colorScheme.primary,
    /**
     * Pontos da janela **anterior**, de mesma duração (issue #215). Vazio
     * some com a linha de referência — mesma condição de
     * `UsageHistorySeries.comparison` ser `null`.
     *
     * **Desenhada só sem zoom.** O zoom recorta [points] por fração de
     * índice; aplicar o mesmo recorte aqui exigiria os dois pontos terem a
     * mesma densidade de amostragem, que não é garantida — a leitura de cada
     * janela é independente. Sem essa garantia, a linha tracejada poderia
     * descrever um trecho de tempo diferente do que a corrente mostra
     * ampliada, e um comparativo que compara períodos diferentes é pior que
     * nenhum. "Ver tudo" devolve a comparação.
     */
    previousPoints: List<UsageHistoryPoint> = emptyList(),
    /**
     * Nome da série principal na legenda e no tooltip. Só aparece quando há
     * [overlays] — com uma série só, o título do card já diz o que é a linha.
     */
    seriesLabel: String? = null,
    /**
     * Séries sobrepostas (issue #320), traçadas sem preenchimento sobre a mesma
     * grade. Com sobreposição a linha do período anterior some: três traçados
     * mais o tracejado deixariam de ser legíveis, e a comparação continua na
     * tabela de métricas.
     */
    overlays: List<HistoryChartOverlay> = emptyList(),
    /** Faixa ativa de cada janela (#382); só cota com janela — saldo não tem. */
    showActiveSpans: Boolean = false,
    /** Chave da faixa ativa sob o gráfico (#392); `null` não desenha chave. */
    activeSpanKey: HistoryActiveSpanKey? = null
) {
    val lineColor = accentColor
    val fillColor = accentColor.copy(alpha = 0.12f)
    // A grade é a mesma cor de borda do resto do app, e não o texto com alpha:
    // duas linhas de referência com tons diferentes numa tela só.
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val axisTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val chartIndicatorColor = accentColor
    val chartIndicatorHaloColor = MaterialTheme.colorScheme.surface
    val renderPoints = filteredPoints(points, unit)

    var revealed by remember { mutableStateOf(false) }
    var frameSize by remember(renderPoints) { mutableStateOf(IntSize.Zero) }
    var plotSize by remember(renderPoints) { mutableStateOf(IntSize.Zero) }
    var tooltipSize by remember(renderPoints) { mutableStateOf(IntSize.Zero) }
    var hoveredIndex by remember(renderPoints, chartSelectionKey) { mutableStateOf<Int?>(null) }
    var zoomRange by remember(renderPoints, chartSelectionKey) { mutableStateOf(0f..1f) }
    val density = LocalDensity.current

    val revealFraction by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        // Pela política: com "Reduzir animações" a linha aparece inteira.
        animationSpec = appTween(durationMillis = HISTORY_REVEAL_MILLIS, easing = FastOutSlowInEasing),
        label = "lineReveal"
    )

    val windowedPoints = remember(renderPoints, zoomRange) {
        zoomedPoints(renderPoints, zoomRange)
    }

    LaunchedEffect(renderPoints, chartSelectionKey) {
        revealed = false
        hoveredIndex = null
        tooltipSize = IntSize.Zero
        delay(40)
        revealed = true
    }

    LaunchedEffect(zoomRange, chartSelectionKey) {
        hoveredIndex = null
    }

    val timeLabels = buildTimeReferenceLabels(windowedPoints)
    val valueAxis = buildValueAxis(windowedPoints, unit)
    val valueLabels = buildValueLabels(valueAxis, unit)
    val plotInset = remember(plotSize) {
        resolvePlotHorizontalInset(plotSize.width.toFloat())
    }
    val plotPoints = remember(windowedPoints, valueAxis, plotSize) {
        buildPlotPoints(
            points = windowedPoints,
            chartWidth = plotSize.width.toFloat(),
            chartHeight = plotSize.height.toFloat(),
            axis = valueAxis,
            horizontalInsetPx = plotInset
        )
    }
    val previousRenderPoints = remember(previousPoints, unit) { filteredPoints(previousPoints, unit) }
    // Só sem zoom — ver o comentário de `previousPoints`.
    val previousPlotPoints = remember(previousRenderPoints, valueAxis, plotSize, zoomRange, overlays) {
        if (zoomRange != 0f..1f || overlays.isNotEmpty()) {
            emptyList()
        } else {
            buildPlotPoints(
                points = previousRenderPoints,
                chartWidth = plotSize.width.toFloat(),
                chartHeight = plotSize.height.toFloat(),
                axis = valueAxis,
                horizontalInsetPx = plotInset
            )
        }
    }
    val rangeAnnotations = remember(windowedPoints, unit) {
        detectHistoryRangeAnnotations(windowedPoints, unit)
    }
    val resetClusterPoints = remember(rangeAnnotations, plotPoints) {
        clusterResetIndices(
            resetIndices = rangeAnnotations?.resetIndices.orEmpty(),
            plotPoints = plotPoints,
            minPixelGap = HISTORY_RESET_CLUSTER_GAP_PX
        ).mapNotNull { marker ->
            plotPoints.getOrNull(marker.representativeIndex)?.let { point -> marker to point }
        }
    }
    val activePoint = hoveredIndex?.let { index -> plotPoints.getOrNull(index) }
    val overlayPlots = remember(overlays, windowedPoints, plotSize, plotInset) {
        overlays.map { overlay ->
            overlay to buildOverlayPlotPoints(
                overlayPoints = overlay.points,
                reference = windowedPoints,
                chartWidth = plotSize.width.toFloat(),
                chartHeight = plotSize.height.toFloat(),
                horizontalInsetPx = plotInset
            )
        }
    }
    val overlayActivePoints = overlayPlots.mapNotNull { (overlay, plot) ->
        activePoint?.let { active -> findOverlayPointAt(plot, active) }?.let { point -> overlay to point }
    }
    val tooltipModel = remember(
        activePoint,
        windowedPoints,
        unit,
        language,
        tooltipTitle,
        tooltipSubtitle
    ) {
        buildHistoryTooltipModel(
            activePoint = activePoint,
            points = windowedPoints,
            unit = unit,
            language = language,
            title = tooltipTitle,
            subtitle = tooltipSubtitle
        )
    }?.let { model ->
        withOverlayMetrics(model, seriesLabel, overlayActivePoints)
    }

    Column(
        modifier = modifier.fillMaxWidth().appModalRevealRow(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (overlays.isNotEmpty()) {
            HistoryChartLegend(
                entries = listOf((seriesLabel ?: "") to lineColor) +
                    overlays.map { overlay -> overlay.label to overlay.color },
                textColor = axisTextColor
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(HISTORY_FRAME_HEIGHT)
                .onSizeChanged { frameSize = it }
        ) {
            if (zoomRange != 0f..1f) {
                AppButton(
                    label = if (language == AppLanguage.PT) "Ver tudo" else "View all",
                    onClick = { zoomRange = 0f..1f },
                    tone = AppButtonTone.GHOST,
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            }

            if (valueLabels.isNotEmpty()) {
                HistoryValueAxisLabels(
                    labels = valueLabels,
                    color = axisTextColor,
                    modifier = Modifier.align(Alignment.BottomStart)
                )
            }

            val startPadding = if (valueLabels.isNotEmpty()) 48.dp else 0.dp
            val startPaddingPx = with(density) { startPadding.toPx() }
            val plotTop = (frameSize.height - plotSize.height).coerceAtLeast(0)

            Box(
                modifier = Modifier
                    .padding(start = startPadding)
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(HISTORY_PLOT_HEIGHT)
                    .onSizeChanged { plotSize = it }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawHistoryPlot(
                        colors = HistoryPlotColors(
                            line = lineColor,
                            fill = fillColor,
                            grid = gridColor,
                            indicator = chartIndicatorColor,
                            indicatorHalo = chartIndicatorHaloColor
                        ),
                        plotPoints = plotPoints,
                        previousPlotPoints = previousPlotPoints,
                        overlayLines = overlayPlots.map { (overlay, plot) -> plot to overlay.color },
                        overlayActivePoints = overlayActivePoints.map { (overlay, point) -> point to overlay.color },
                        resetClusterPoints = resetClusterPoints,
                        rangeAnnotations = rangeAnnotations,
                        activePoint = activePoint,
                        activeSpans = if (showActiveSpans) activeSpanPlotRanges(windowedPoints, unit, plotPoints) else emptyList(),
                        revealFraction = revealFraction
                    )
                }

                HistoryRangeAnnotationLabels(
                    resetClusters = resetClusterPoints,
                    plotWidth = plotSize.width.toFloat(),
                    plotInset = plotInset,
                    language = language
                )
            }

            if (activePoint != null && tooltipModel != null) {
                HistoryTooltipLayer(
                    model = tooltipModel,
                    left = clampTooltipLeft(
                        desiredCenterX = activePoint.x,
                        tooltipWidth = tooltipSize.width.toFloat(),
                        containerWidth = plotSize.width.toFloat(),
                        horizontalPadding = plotInset
                    ),
                    top = buildHistoryTooltipTop(
                        pointY = activePoint.y + plotTop,
                        tooltipHeight = tooltipSize.height.toFloat(),
                        frameHeight = frameSize.height.toFloat()
                    ),
                    onSizeChanged = { tooltipSize = it },
                    modifier = Modifier
                        .padding(start = startPadding)
                        .align(Alignment.TopStart)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .historyChartPointerInput(
                        plotStartX = startPaddingPx,
                        plotWidth = { plotSize.width.toFloat() },
                        plotPoints = plotPoints,
                        windowedPoints = windowedPoints,
                        zoomRange = { zoomRange },
                        onZoomRangeChange = { range -> zoomRange = range },
                        onHoveredIndexChange = { index -> hoveredIndex = index }
                    )
            )
        }

        if (timeLabels.isNotEmpty()) {
            HistoryTimeAxisLabels(
                labels = timeLabels,
                color = axisTextColor,
                startPadding = if (valueLabels.isNotEmpty()) 48.dp else 0.dp
            )
        }

        HistoryChartFooter(
            showPreviousNote = previousPlotPoints.size > 1,
            activeSpanKeyText = activeSpanKey
                ?.takeIf { showActiveSpans && windowedPoints.isNotEmpty() }
                ?.let { key -> activeSpanKeyText(key, windowedPoints.first().capturedAt, windowedPoints.last().capturedAt) },
            lineColor = lineColor,
            textColor = axisTextColor,
            language = language
        )
    }
}



@Composable
private fun HistoryValueAxisLabels(labels: List<String>, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.height(HISTORY_PLOT_HEIGHT),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        labels.forEach { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
        }
    }
}

@Composable
private fun HistoryTimeAxisLabels(labels: List<String>, color: Color, startPadding: Dp) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = startPadding),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        labels.forEachIndexed { index, label ->
            Text(
                modifier = Modifier.weight(1f),
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                textAlign = when (index) {
                    0 -> TextAlign.Start
                    1 -> TextAlign.Center
                    else -> TextAlign.End
                }
            )
        }
    }
}

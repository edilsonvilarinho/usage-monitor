package com.usagemonitor.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CodexCliSessionAnalytics
import com.usagemonitor.domain.entity.CodexCliSessionDetail
import com.usagemonitor.domain.entity.codexCliSessionAnalyticsOf
import com.usagemonitor.presentation.ui.components.AppButton
import com.usagemonitor.presentation.ui.components.AppButtonTone
import com.usagemonitor.presentation.ui.components.AppCellValue
import com.usagemonitor.presentation.ui.components.AppColumnHeaderLabel
import com.usagemonitor.presentation.ui.components.AppColumnHeaderRow
import com.usagemonitor.presentation.ui.components.AppDataRow
import com.usagemonitor.presentation.ui.components.AppDataSurface
import com.usagemonitor.presentation.ui.components.AppDataSurfaceFlush
import com.usagemonitor.presentation.ui.components.AppMetricBlock
import com.usagemonitor.presentation.ui.components.AppSectionHeader
import com.usagemonitor.presentation.ui.components.AppToolbar
import com.usagemonitor.presentation.ui.components.BinMode
import com.usagemonitor.presentation.ui.components.TurnSeries
import com.usagemonitor.presentation.ui.components.TurnSeriesChart
import com.usagemonitor.presentation.ui.theme.AppAccents
import com.usagemonitor.presentation.ui.theme.AppSpacing
import kotlin.math.roundToLong

const val CODEX_TURN_ROW_TAG_PREFIX = "codexTurnRow:"

private val CODEX_TURN_NUMBER_COLUMN = 52.dp
private val CODEX_TURN_VALUE_COLUMN = 96.dp

/**
 * Detalhe de uma sessão do Codex CLI (#393, direção T3): metadados, blocos,
 * a grade 2×2 de gráficos por resposta (contexto, cache, saída, vazão) e a
 * tabela de respostas. Sem custo — o app não tem tarifa do Codex — e sem
 * percentual da janela de contexto, que o índice não lê.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CodexCliSessionDetailPane(
    detail: CodexCliSessionDetail,
    language: AppLanguage,
    onCloseDetail: () -> Unit,
    modifier: Modifier = Modifier
) {
    val summary = detail.summary
    val pt = language == AppLanguage.PT
    val analytics = remember(detail.turns) { codexCliSessionAnalyticsOf(detail.turns) }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
    ) {
        AppToolbar(spacing = AppSpacing.sm) {
            AppButton(label = if (pt) "Voltar" else "Back", onClick = onCloseDetail, tone = AppButtonTone.GHOST)
            Text(shortCodexId(summary.sessionId), style = MaterialTheme.typography.titleMedium)
        }

        AppDataSurface(modifier = Modifier.fillMaxWidth(), contentPadding = 0.dp, verticalArrangement = Arrangement.Top) {
            AppSectionHeader(
                title = summary.projectName ?: if (pt) "Projeto desconhecido" else "Unknown project",
                subtitle = "${codexApplicationLabel(summary)} · ${summary.source.name.lowercase()}"
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
            ) {
                CodexMetadataValue(if (pt) "Diretório" else "Directory", summary.cwd ?: "—")
                CodexMetadataValue("Branch", summary.gitBranch ?: "—")
                CodexMetadataValue(if (pt) "Versão CLI" else "CLI version", summary.cliVersion ?: "—")
                CodexMetadataValue(if (pt) "Período" else "Period", "${formatInstant(summary.firstTs)} → ${formatInstant(summary.lastTs)}")
            }
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            AppMetricBlock(label = if (pt) "Respostas" else "Responses", value = summary.responseCount.toString(), modifier = Modifier.width(CODEX_METRIC_BLOCK_WIDTH))
            AppMetricBlock(label = if (pt) "Tokens (com cache)" else "Tokens (cached)", value = formatQuantity(summary.totalTokens), modifier = Modifier.width(CODEX_METRIC_BLOCK_WIDTH))
            AppMetricBlock(label = "Cache", value = codexCachePercent(summary), modifier = Modifier.width(CODEX_METRIC_BLOCK_WIDTH))
            AppMetricBlock(label = if (pt) "Entrada" else "Input", value = formatQuantity(summary.inputTokens), modifier = Modifier.width(CODEX_METRIC_BLOCK_WIDTH))
            AppMetricBlock(
                label = if (pt) "Saída" else "Output",
                value = formatQuantity(summary.outputTokens),
                footer = "${if (pt) "Raciocínio" else "Reasoning"} ${formatQuantity(summary.reasoningOutputTokens)}",
                modifier = Modifier.width(CODEX_METRIC_BLOCK_WIDTH)
            )
        }

        if (detail.turns.isNotEmpty()) {
            CodexTurnChartGrid(analytics, language)
        }

        CodexTurnTable(detail, analytics, language)
    }
}

@Composable
private fun CodexTurnChartGrid(analytics: CodexCliSessionAnalytics, language: AppLanguage) {
    val accents = AppAccents.current
    val pt = language == AppLanguage.PT
    val visibleOutput = analytics.outputPerTurn.zip(analytics.reasoningPerTurn) { output, reasoning -> (output - reasoning).coerceAtLeast(0L) }
    CliTurnChartGrid(
        cells = listOf(
            {
                DetailSection(title = if (pt) "Contexto por resposta" else "Context per response", accent = accents.cacheRead, language = language) {
                    TurnSeriesChart(
                        series = listOf(TurnSeries(if (pt) "Contexto" else "Context", analytics.contextPerTurn, accents.cacheRead, BinMode.LAST)),
                        height = TURN_CHART_GRID_HEIGHT,
                        valueFormatter = { value -> formatQuantity(value) },
                        highlightDrops = true
                    )
                }
            },
            {
                DetailSection(title = if (pt) "Cache por resposta" else "Cache per response", accent = accents.cacheRead, language = language) {
                    TurnSeriesChart(
                        series = listOf(TurnSeries("Cache", fractionsAsBasisPoints(analytics.cacheHitPerTurn), accents.cacheRead, BinMode.LAST)),
                        height = TURN_CHART_GRID_HEIGHT,
                        valueFormatter = ::formatBasisPointsPercent
                    )
                }
            },
            {
                DetailSection(title = if (pt) "Saída por resposta" else "Output per response", accent = accents.output, language = language) {
                    TurnSeriesChart(
                        series = listOf(
                            TurnSeries(if (pt) "Visível" else "Visible", visibleOutput, accents.output, BinMode.SUM),
                            TurnSeries(if (pt) "Raciocínio" else "Reasoning", analytics.reasoningPerTurn, accents.input, BinMode.SUM)
                        ),
                        stacked = true,
                        height = TURN_CHART_GRID_HEIGHT,
                        valueFormatter = { value -> formatQuantity(value) }
                    )
                }
            },
            {
                // Unidade no título: "33 tok/s" não cabe na coluna do eixo e saía cortado.
                DetailSection(title = if (pt) "Vazão (tok/s)" else "Throughput (tok/s)", accent = accents.savings, language = language) {
                    TurnSeriesChart(
                        series = listOf(TurnSeries(if (pt) "Vazão" else "Throughput", ratesAsLong(analytics.throughputPerTurn), accents.savings, BinMode.MAX)),
                        height = TURN_CHART_GRID_HEIGHT,
                        valueFormatter = { value -> value.toString() },
                        emptyLabel = if (pt) "sem resposta medida" else "no measured response"
                    )
                }
            }
        )
    )
}

@Composable
private fun CodexTurnTable(detail: CodexCliSessionDetail, analytics: CodexCliSessionAnalytics, language: AppLanguage) {
    val pt = language == AppLanguage.PT
    val ordered = remember(detail.turns) {
        detail.turns.sortedWith(compareBy({ turn -> turn.seq }, { turn -> turn.ts }, { turn -> turn.responseId }))
    }
    AppDataSurfaceFlush(
        modifier = Modifier.fillMaxWidth(),
        header = {
            Column {
                AppSectionHeader(
                    title = if (pt) "Uso por resposta" else "Usage by response",
                    subtitle = "${detail.summary.responseCount} ${if (pt) "resposta(s)" else "response(s)"} · ${formatQuantity(detail.summary.totalTokens)} tokens",
                    markerColor = AppAccents.current.codex
                )
                AppColumnHeaderRow(startGutter = 0.dp, modifier = Modifier.padding(vertical = AppSpacing.sm)) {
                    AppColumnHeaderLabel(label = "#", modifier = Modifier.width(CODEX_TURN_NUMBER_COLUMN))
                    AppColumnHeaderLabel(label = if (pt) "Modelo" else "Model", modifier = Modifier.weight(1f))
                    AppColumnHeaderLabel(label = if (pt) "Contexto" else "Context", modifier = Modifier.width(CODEX_TURN_VALUE_COLUMN))
                    AppColumnHeaderLabel(label = "Cache", modifier = Modifier.width(CODEX_TURN_VALUE_COLUMN))
                    AppColumnHeaderLabel(label = if (pt) "Saída" else "Output", modifier = Modifier.width(CODEX_TURN_VALUE_COLUMN))
                    AppColumnHeaderLabel(label = if (pt) "Vazão" else "Throughput", modifier = Modifier.width(CODEX_TURN_VALUE_COLUMN))
                }
            }
        }
    ) {
        ordered.forEachIndexed { index, turn ->
            AppDataRow(modifier = Modifier.testTag("$CODEX_TURN_ROW_TAG_PREFIX${turn.seq}"), showDivider = index != ordered.lastIndex) {
                AppCellValue(value = "#${turn.seq + 1}", modifier = Modifier.width(CODEX_TURN_NUMBER_COLUMN))
                AppCellValue(value = turn.model ?: "—", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                AppCellValue(value = formatQuantity(analytics.contextPerTurn[index]), modifier = Modifier.width(CODEX_TURN_VALUE_COLUMN))
                AppCellValue(
                    value = analytics.cacheHitPerTurn[index]?.let { fraction -> "${(fraction * 100.0).roundToLong()} %" } ?: "—",
                    modifier = Modifier.width(CODEX_TURN_VALUE_COLUMN)
                )
                AppCellValue(value = formatQuantity(analytics.outputPerTurn[index]), modifier = Modifier.width(CODEX_TURN_VALUE_COLUMN))
                AppCellValue(value = formatThroughput(turn.throughput), modifier = Modifier.width(CODEX_TURN_VALUE_COLUMN))
            }
        }
    }
}

@Composable
private fun CodexMetadataValue(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelMedium)
    }
}

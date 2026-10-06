package com.usagemonitor.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CliSessionRange
import com.usagemonitor.domain.entity.ComparisonMetric
import com.usagemonitor.domain.entity.ComparisonRow
import com.usagemonitor.domain.entity.heatIntensity
import com.usagemonitor.presentation.ui.components.AppBanner
import com.usagemonitor.presentation.ui.components.AppButton
import com.usagemonitor.presentation.ui.components.AppCellValue
import com.usagemonitor.presentation.ui.components.AppColumnHeaderLabel
import com.usagemonitor.presentation.ui.components.AppColumnHeaderRow
import com.usagemonitor.presentation.ui.components.AppDataRow
import com.usagemonitor.presentation.ui.components.AppDataSurfaceFlush
import com.usagemonitor.presentation.ui.components.AppEmptyState
import com.usagemonitor.presentation.ui.components.AppErrorState
import com.usagemonitor.presentation.ui.components.AppLoadingState
import com.usagemonitor.presentation.ui.components.AppSegment
import com.usagemonitor.presentation.ui.components.AppSegmentedControl
import com.usagemonitor.presentation.ui.components.AppToolbar
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.components.AppWindowScaffold
import com.usagemonitor.presentation.ui.theme.AppAccents
import com.usagemonitor.presentation.ui.theme.AppSpacing
import com.usagemonitor.presentation.viewmodel.CliExportOutcome
import com.usagemonitor.presentation.viewmodel.ComparisonUiState
import com.usagemonitor.presentation.viewmodel.ComparisonViewModel

private val COMPARISON_METRIC_COLUMN = 104.dp

/** Opacidade máxima da célula: a cor é apoio, o número tem de continuar legível. */
private const val HEAT_MAX_ALPHA = 0.45f

internal const val COMPARISON_TABLE_TAG = "comparisonTable"

/**
 * Comparação entre modelos e APIs (#386, direção P7 — mapa de calor): uma linha
 * por modelo (Claude, Codex) ou por fonte, uma coluna por métrica, a cor da
 * célula proporcional ao maior valor da coluna, o número sempre escrito.
 */
@Composable
fun ComparisonScreen(viewModel: ComparisonViewModel, language: AppLanguage) {
    val state by viewModel.uiState.collectAsState()
    ComparisonContent(
        state = state,
        language = language,
        onSelectRange = viewModel::setRange,
        onExportReport = { viewModel.exportReport(language) },
        onRetry = viewModel::refresh
    )
}

@Composable
internal fun ComparisonContent(
    state: ComparisonUiState,
    language: AppLanguage,
    onSelectRange: (CliSessionRange) -> Unit,
    onExportReport: () -> Unit,
    onRetry: () -> Unit
) {
    AppWindowScaffold(
        modifier = Modifier.fillMaxSize(),
        statusBar = {
            val success = state as? ComparisonUiState.Success
            if (success != null) {
                Text(
                    text = if (language == AppLanguage.PT) "Lido ${formatInstant(success.readAt)}" else "Read ${formatInstant(success.readAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    ) {
        AppToolbar(spacing = AppSpacing.sm) {
            Text(ComparisonLabels.title(language), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            AppSegmentedControl(
                options = CliSessionRange.entries.map { range -> AppSegment(CliSessionsLabels.rangeLabel(range, language)) },
                selectedIndex = CliSessionRange.entries.indexOf((state as? ComparisonUiState.Success)?.range ?: CliSessionRange.LAST_7D),
                onSelect = { index -> onSelectRange(CliSessionRange.entries[index]) }
            )
            AppButton(label = ExportLabels.exportPdf(language), onClick = onExportReport)
        }
        when (state) {
            ComparisonUiState.Loading -> AppLoadingState(
                message = if (language == AppLanguage.PT) "Lendo uso local…" else "Reading local usage…",
                modifier = Modifier.fillMaxSize()
            )
            is ComparisonUiState.Error -> AppErrorState(
                message = state.message,
                retryLabel = if (language == AppLanguage.PT) "Tentar novamente" else "Retry",
                onRetry = onRetry,
                modifier = Modifier.fillMaxSize()
            )
            is ComparisonUiState.Success -> ComparisonBody(state, language)
        }
    }
}

@Composable
private fun ComparisonBody(state: ComparisonUiState.Success, language: AppLanguage) {
    val outcome = state.exportOutcome
    if (outcome != null) {
        AppBanner(
            title = when (outcome) {
                is CliExportOutcome.Saved -> if (language == AppLanguage.PT) "Exportação concluída" else "Export complete"
                is CliExportOutcome.Failed -> if (language == AppLanguage.PT) "Falha na exportação" else "Export failed"
            },
            description = when (outcome) {
                is CliExportOutcome.Saved -> outcome.path
                is CliExportOutcome.Failed -> outcome.message
            },
            tone = if (outcome is CliExportOutcome.Saved) AppTone.OK else AppTone.CRITICAL
        )
    }
    if (state.rows.isEmpty()) {
        AppEmptyState(message = ComparisonLabels.empty(language), modifier = Modifier.fillMaxSize())
        return
    }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
    ) {
        AppDataSurfaceFlush(
            modifier = Modifier.testTag(COMPARISON_TABLE_TAG),
            header = {
                AppColumnHeaderRow(startGutter = 0.dp) {
                    AppColumnHeaderLabel(ComparisonLabels.modelColumn(language), Modifier.weight(1f))
                    ComparisonMetric.entries.forEach { metric ->
                        AppColumnHeaderLabel(ComparisonLabels.metric(metric, language), Modifier.width(COMPARISON_METRIC_COLUMN))
                    }
                }
            }
        ) {
            Column {
                state.rows.forEachIndexed { index, row ->
                    ComparisonHeatRow(state.rows, row, language, showDivider = index != state.rows.lastIndex)
                }
            }
        }
        ComparisonLabels.notes(language).forEach { note ->
            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ComparisonHeatRow(rows: List<ComparisonRow>, row: ComparisonRow, language: AppLanguage, showDivider: Boolean) {
    val accent = accentColorForHistorySource(row.source, AppAccents.current)
    AppDataRow(showDivider = showDivider) {
        Column(Modifier.weight(1f)) {
            AppCellValue(row.label)
            Text(
                text = row.source.name.lowercase(),
                style = MaterialTheme.typography.labelSmall,
                color = accent
            )
        }
        ComparisonMetric.entries.forEach { metric ->
            val intensity = heatIntensity(rows, row, metric)
            Box(
                modifier = Modifier
                    .width(COMPARISON_METRIC_COLUMN)
                    .background(accent.copy(alpha = ((intensity ?: 0.0) * HEAT_MAX_ALPHA).toFloat()))
                    .padding(horizontal = AppSpacing.xs, vertical = AppSpacing.xs),
                contentAlignment = Alignment.CenterEnd
            ) {
                AppCellValue(comparisonCell(row, metric, language), Modifier.fillMaxWidth())
            }
        }
    }
}

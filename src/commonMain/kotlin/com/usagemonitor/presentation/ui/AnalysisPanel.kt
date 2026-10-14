package com.usagemonitor.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.SessionAnalysis
import com.usagemonitor.presentation.ui.components.AppCellValue
import com.usagemonitor.presentation.ui.components.AppColumnHeaderLabel
import com.usagemonitor.presentation.ui.components.AppColumnHeaderRow
import com.usagemonitor.presentation.ui.components.AppDataRow
import com.usagemonitor.presentation.ui.components.AppDataSurface
import com.usagemonitor.presentation.ui.components.AppDataSurfaceFlush
import com.usagemonitor.presentation.ui.components.AppMetricBlock
import com.usagemonitor.presentation.ui.components.AppProgressTrack
import com.usagemonitor.presentation.ui.components.AppSectionHeader
import com.usagemonitor.presentation.ui.components.AppStatusPill
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.components.accentColorFor
import com.usagemonitor.presentation.ui.components.color
import com.usagemonitor.presentation.ui.theme.AppAccents
import com.usagemonitor.presentation.ui.theme.AppSpacing

internal const val ANALYSIS_PANEL_TAG = "analysisPanel"

/**
 * Painel único da janela "Análise" (rodada AD, direção AD1): as duas janelas lado a lado,
 * as últimas 24 h, e intervalos e antes × agora em tabela. Sem abas — o mesmo número não
 * aparece em duas vistas. Rola inteiro; nunca `LazyColumn` (regra do projeto).
 */
@Composable
internal fun AnalysisPanel(analysis: SessionAnalysis, language: AppLanguage, modifier: Modifier = Modifier) {
    val windows = listOfNotNull(analysis.fiveHour, analysis.weekly).map { window -> AnalysisPanelModel.windowCard(window, analysis.generatedAt, language) }
    val accents = AppAccents.current
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).testTag(ANALYSIS_PANEL_TAG),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
    ) {
        if (windows.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
                windows.forEach { model -> AnalysisWindowCard(model, language, Modifier.weight(1f)) }
            }
            // Uma vez para os dois cards: o que cada bloco de ritmo mede.
            Text(AnalysisLabels.paceLegend(language), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val bars = AnalysisPanelModel.hours(analysis)
        AppDataSurface {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(AnalysisLabels.hoursTitle(language), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.weight(1f))
                Text(AnalysisPanelModel.peakLabel(bars, language), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AnalysisHoursChart(bars, barColor = accentColorFor(analysis.source, accents), lineColor = accents.output, language = language)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            AnalysisIntervalsTable(analysis, language, Modifier.weight(1f))
            AnalysisVersusTable(analysis, language, Modifier.weight(1f))
        }
        Text(
            text = AnalysisPanelModel.speedLine(analysis, language),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AnalysisWindowCard(model: AnalysisWindowCardModel, language: AppLanguage, modifier: Modifier) {
    AppDataSurface(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
            Text(model.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            AppStatusPill(label = model.verdict, tone = model.verdictTone)
        }
        // O reinício fica ao lado do número, não no cabeçalho: a 640dp o cabeçalho não cabe nos dois.
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${model.percent}%", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.weight(1f))
            model.resetLabel?.let { label ->
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        // A marca é onde a cota chega no reinício; a linha abaixo diz isso em texto.
        AppProgressTrack(
            fraction = model.percent / 100f,
            tone = model.verdictTone,
            marker = model.projectedPercent?.let { value -> value.coerceAtMost(100) / 100f }
        )
        Row {
            Text(model.usedLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.weight(1f))
            model.projectionLabel?.let { label ->
                Text(label, style = MaterialTheme.typography.labelSmall, color = model.verdictTone.color())
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
            AppMetricBlock(label = AnalysisLabels.paceNow(language), value = model.paceNow, footer = "pp/h", modifier = Modifier.weight(1f))
            AppMetricBlock(label = AnalysisLabels.paceAverage(language), value = model.paceAverage, footer = "pp/h", modifier = Modifier.weight(1f))
            AppMetricBlock(
                label = AnalysisLabels.paceSafe(language),
                value = model.safePace,
                footer = model.safeVerdict ?: "pp/h",
                footerColor = if (model.safeVerdict == null) null else model.safeTone.color(),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun AnalysisIntervalsTable(analysis: SessionAnalysis, language: AppLanguage, modifier: Modifier) {
    val rows = AnalysisPanelModel.intervals(analysis, language)
    AppDataSurfaceFlush(modifier = modifier, header = { AppSectionHeader(title = AnalysisLabels.intervalsTitle(language)) }) {
        AppColumnHeaderRow(startGutter = 0.dp, modifier = Modifier.padding(top = AppSpacing.xs)) {
            // A coluna do intervalo dispensa legenda: "1 h", "7 d" se explicam, e a 640dp ela truncaria.
            Spacer(Modifier.weight(SPAN_WEIGHT))
            AppColumnHeaderLabel("pp/h 5h", Modifier.weight(1f), textAlign = TextAlign.End)
            AppColumnHeaderLabel("tok/s", Modifier.weight(1f), textAlign = TextAlign.End)
            AppColumnHeaderLabel(AnalysisLabels.versusWeek(language), Modifier.weight(RATIO_WEIGHT), textAlign = TextAlign.End)
        }
        rows.forEachIndexed { index, row ->
            AppDataRow(showDivider = index != rows.lastIndex) {
                AppCellValue(row.span, Modifier.weight(SPAN_WEIGHT))
                AppCellValue(row.pace, Modifier.weight(1f), textAlign = TextAlign.End)
                AppCellValue(row.tokensPerSecond, Modifier.weight(1f), textAlign = TextAlign.End)
                AppCellValue(row.ratio, Modifier.weight(RATIO_WEIGHT), color = toneOrDefault(row.ratioTone), textAlign = TextAlign.End)
            }
        }
    }
}

@Composable
private fun AnalysisVersusTable(analysis: SessionAnalysis, language: AppLanguage, modifier: Modifier) {
    val rows = AnalysisPanelModel.versus(analysis, language)
    AppDataSurfaceFlush(modifier = modifier, header = { AppSectionHeader(title = AnalysisLabels.versusTitle(language)) }) {
        if (rows == null) {
            Text(
                AnalysisLabels.noPreviousWindow(language),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(AppSpacing.md)
            )
            return@AppDataSurfaceFlush
        }
        AppColumnHeaderRow(startGutter = 0.dp, modifier = Modifier.padding(top = AppSpacing.xs)) {
            Spacer(Modifier.weight(LABEL_WEIGHT))
            AppColumnHeaderLabel(AnalysisLabels.now(language), Modifier.weight(1f), textAlign = TextAlign.End)
            AppColumnHeaderLabel(AnalysisLabels.before(language), Modifier.weight(1f), textAlign = TextAlign.End)
            AppColumnHeaderLabel("Δ", Modifier.weight(1f), textAlign = TextAlign.End)
        }
        rows.forEach { row ->
            AppDataRow {
                AppCellValue(row.label, Modifier.weight(LABEL_WEIGHT))
                AppCellValue(row.now, Modifier.weight(1f), textAlign = TextAlign.End)
                AppCellValue(row.before, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
                AppCellValue(row.change, Modifier.weight(1f), color = toneOrDefault(row.changeTone), textAlign = TextAlign.End)
            }
        }
        AnalysisPanelModel.versusFootnote(analysis, language)?.let { note ->
            Text(
                note,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm)
            )
        }
    }
}

@Composable
private fun toneOrDefault(tone: AppTone) = if (tone == AppTone.NEUTRAL) MaterialTheme.colorScheme.onSurface else tone.color()

private const val SPAN_WEIGHT = 0.9f
private const val RATIO_WEIGHT = 1.3f
private const val LABEL_WEIGHT = 1.9f

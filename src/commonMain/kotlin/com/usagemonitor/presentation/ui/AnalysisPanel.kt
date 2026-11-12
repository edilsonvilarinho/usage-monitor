package com.usagemonitor.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AnalysisPeriod
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.SessionAnalysis
import com.usagemonitor.presentation.ui.components.AppCellValue
import com.usagemonitor.presentation.ui.components.AppColumnHeaderLabel
import com.usagemonitor.presentation.ui.components.AppColumnHeaderRow
import com.usagemonitor.presentation.ui.components.AppDataRow
import com.usagemonitor.presentation.ui.components.AppDataSurface
import com.usagemonitor.presentation.ui.components.AppDataSurfaceFlush
import com.usagemonitor.presentation.ui.components.AppSectionHeader
import com.usagemonitor.presentation.ui.components.AppStatusPill
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.components.accentColorFor
import com.usagemonitor.presentation.ui.components.color
import com.usagemonitor.presentation.ui.theme.AppAccents
import com.usagemonitor.presentation.ui.theme.AppSpacing

internal const val ANALYSIS_PANEL_TAG = "analysisPanel"

/**
 * Painel único da janela "Análise" (rodada AD, direção AD1; janelas na diagonal do ritmo desde
 * a #463, direção AH1): as duas janelas lado a lado, cada uma com a diagonal e a frase; o bloco de
 * barras do período escolhido (#538, A05b; "Agora" não tem barras); e o ritmo por intervalo em
 * tabela. As janelas anteriores ficam desenhadas atrás da aberta — a tabela "esta janela × anterior"
 * saiu. Rola inteiro; nunca `LazyColumn` (regra do projeto).
 */
@Composable
internal fun AnalysisPanel(analysis: SessionAnalysis, language: AppLanguage, modifier: Modifier = Modifier) {
    val windows = listOfNotNull(analysis.fiveHour, analysis.weekly)
    val accents = AppAccents.current
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).testTag(ANALYSIS_PANEL_TAG),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
    ) {
        if (windows.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
                windows.forEach { window ->
                    // A semanal usa o acento da saída, como no histórico; a 5h, o da fonte.
                    val lineColor = if (window.periodType == PeriodType.WEEKLY) accents.output else accentColorFor(analysis.source, accents)
                    AnalysisWindowCard(AnalysisPanelModel.windowCard(window, analysis.generatedAt, language), lineColor, language, Modifier.weight(1f))
                }
            }
        }
        AnalysisBarsBlock(analysis, language, accents.output)
        AnalysisIntervalsTable(analysis, language, Modifier.fillMaxWidth())
        Text(
            text = AnalysisPanelModel.speedLine(analysis, language),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Bloco de barras do período (#538, A05b): 24 h = horas atuais; 7 d, 30 d e tudo = pico por dia ou semana. "Agora" não tem bloco. */
@Composable
private fun AnalysisBarsBlock(analysis: SessionAnalysis, language: AppLanguage, lineColor: Color) {
    val title = AnalysisPanelModel.barsTitle(analysis.period, language) ?: return
    val barColor = accentColorFor(analysis.source, AppAccents.current)
    AppDataSurface {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.weight(1f))
            if (analysis.period == AnalysisPeriod.HOURS_24) {
                Text(AnalysisPanelModel.peakLabel(AnalysisPanelModel.hours(analysis), language), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        AnalysisPanelModel.periodSummary(analysis, language)?.let { summary ->
            Text(summary, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (analysis.period == AnalysisPeriod.HOURS_24) {
            AnalysisHoursChart(AnalysisPanelModel.hours(analysis), barColor = barColor, lineColor = lineColor, language = language)
        } else {
            AnalysisPeriodBars(analysis.periodBars, barColor = barColor)
        }
    }
}

@Composable
private fun AnalysisWindowCard(model: AnalysisWindowCardModel, lineColor: Color, language: AppLanguage, modifier: Modifier) {
    AppDataSurface(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
            Text(model.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            AppStatusPill(label = model.verdict, tone = model.verdictTone)
        }
        // #538, A05a: a frase do veredito fica abaixo da pílula; a da diagonal é o contentDescription do Canvas.
        Text(model.verdictSentence, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        // O reinício em linha própria: a 640dp o cabeçalho não cabe com ele.
        model.resetLabel?.let { label ->
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        model.diagonal?.let { diagonal -> PaceDiagonalChart(diagonal, lineColor, language) }
        AnalysisFactsGrid(model.facts)
    }
}

/** Fatos da janela em grade de duas colunas, com fio no topo (fundo, não borda). */
@Composable
private fun AnalysisFactsGrid(facts: List<AnalysisFact>) {
    if (facts.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(top = AppSpacing.xs), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
        Spacer(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
        facts.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
                pair.forEach { fact -> AnalysisFactCell(fact, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AnalysisFactCell(fact: AnalysisFact, modifier: Modifier) {
    Column(modifier) {
        Text(fact.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(fact.value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun AnalysisIntervalsTable(analysis: SessionAnalysis, language: AppLanguage, modifier: Modifier) {
    val rows = AnalysisPanelModel.intervals(analysis, language)
    AppDataSurfaceFlush(modifier = modifier, header = { AppSectionHeader(title = AnalysisPanelModel.intervalsTitle(analysis.period, language)) }) {
        AppColumnHeaderRow(startGutter = 0.dp, modifier = Modifier.padding(top = AppSpacing.xs)) {
            // A coluna do intervalo dispensa legenda: "1 h", "7 d" se explicam, e a 640dp ela truncaria.
            Spacer(Modifier.weight(SPAN_WEIGHT))
            AppColumnHeaderLabel(AnalysisLabels.paceColumn(language), Modifier.weight(1f), textAlign = TextAlign.End)
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
private fun toneOrDefault(tone: AppTone) = if (tone == AppTone.NEUTRAL) MaterialTheme.colorScheme.onSurface else tone.color()

private const val SPAN_WEIGHT = 0.9f
private const val RATIO_WEIGHT = 1.3f

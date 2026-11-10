package com.usagemonitor.presentation.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.usagemonitor.domain.entity.AnalysisPeriod
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.components.AppBanner
import com.usagemonitor.presentation.ui.components.AppButton
import com.usagemonitor.presentation.ui.components.AppEmptyState
import com.usagemonitor.presentation.ui.components.AppLoadingState
import com.usagemonitor.presentation.ui.components.AppSegment
import com.usagemonitor.presentation.ui.components.AppSegmentedControl
import com.usagemonitor.presentation.ui.components.AppStatusPill
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.components.AppToolbar
import com.usagemonitor.presentation.ui.components.AppWindowScaffold
import com.usagemonitor.presentation.ui.theme.AppSpacing
import com.usagemonitor.presentation.viewmodel.AnalysisUiState
import com.usagemonitor.presentation.viewmodel.AnalysisViewModel
import com.usagemonitor.presentation.viewmodel.CliExportOutcome

internal const val ANALYSIS_LIVE_TAG = "analysisLive"

/** Rótulos da janela "Análise" (rodadas AB e AD), no idioma do app. */
internal object AnalysisLabels {
    fun title(accountLabel: String?, language: AppLanguage): String =
        localized(language, "Análise", "Analysis") + (accountLabel?.let { label -> " · $label" }.orEmpty())

    fun loading(language: AppLanguage): String = localized(language, "Lendo o histórico e o uso local…", "Reading the history and local usage…")

    fun empty(language: AppLanguage): String = localized(
        language,
        "Sem histórico desta conta ainda. A análise aparece depois de algumas coletas.",
        "No history for this account yet. The analysis shows up after a few collections."
    )

    fun versusWeek(language: AppLanguage): String = localized(language, "vs 7 d", "vs 7 d")
    fun paceColumn(language: AppLanguage): String = localized(language, "%/h da 5h", "%/h of 5h")
    fun periodTitle(language: AppLanguage): String = localized(language, "Período", "Period")

    /** Rótulo do seletor de período (#538, A05b): "Agora" é a janela atual, sem barras. */
    fun periodLabel(period: AnalysisPeriod, language: AppLanguage): String = when (period) {
        AnalysisPeriod.NOW -> localized(language, "Agora", "Now")
        AnalysisPeriod.HOURS_24 -> "24 h"
        AnalysisPeriod.DAYS_7 -> "7 d"
        AnalysisPeriod.DAYS_30 -> "30 d"
        AnalysisPeriod.DAYS_90 -> "90 d"
    }
}

/**
 * Janela "Análise" (rodada AB, B20; painel único ao vivo na rodada AD): a análise da sessão
 * de uma conta, aberta pelo card e pelo balão da conta na HUD. Relê sozinha enquanto está
 * visível ([AnalysisViewModel]); no lugar do "Atualizar", a hora da leitura na tela.
 */
@Composable
fun AnalysisScreen(viewModel: AnalysisViewModel, language: AppLanguage) {
    val state by viewModel.uiState.collectAsState()
    val label by viewModel.accountLabel.collectAsState()
    val period by viewModel.period.collectAsState()
    AnalysisContent(
        state = state,
        accountLabel = label,
        language = language,
        onExportPdf = if (viewModel.canExport) ({ viewModel.exportPdf(language) }) else null,
        period = period,
        onSelectPeriod = viewModel::setPeriod
    )
}

@Composable
internal fun AnalysisContent(
    state: AnalysisUiState,
    accountLabel: String?,
    language: AppLanguage,
    /** "Relatório PDF" (B21b); `null` esconde o botão. */
    onExportPdf: (() -> Unit)? = null,
    /** Período escolhido no seletor (#538, A05b); a tela só mostra, quem decide é o view model. */
    period: AnalysisPeriod = AnalysisPeriod.NOW,
    onSelectPeriod: (AnalysisPeriod) -> Unit = {}
) {
    AppWindowScaffold(modifier = Modifier.fillMaxSize()) {
        AppToolbar(spacing = AppSpacing.sm) {
            Text(AnalysisLabels.title(accountLabel, language), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.weight(1f))
            if (state is AnalysisUiState.Success) {
                AppStatusPill(
                    label = AnalysisPanelModel.liveLabel(state.analysis.generatedAt, state.liveFailedAt, language),
                    tone = if (state.liveFailedAt == null) AppTone.OK else AppTone.WARNING,
                    modifier = Modifier.testTag(ANALYSIS_LIVE_TAG)
                )
                if (onExportPdf != null) {
                    AppButton(label = ExportLabels.exportPdf(language), onClick = onExportPdf)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
            Text(AnalysisLabels.periodTitle(language), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AppSegmentedControl(
                options = AnalysisPeriod.entries.map { entry -> AppSegment(AnalysisLabels.periodLabel(entry, language)) },
                selectedIndex = AnalysisPeriod.entries.indexOf(period),
                onSelect = { index -> onSelectPeriod(AnalysisPeriod.entries[index]) }
            )
        }
        val outcome = (state as? AnalysisUiState.Success)?.exportOutcome
        if (outcome != null) {
            AppBanner(
                title = when (outcome) {
                    is CliExportOutcome.Saved -> CommonLabels.exportDone(language)
                    is CliExportOutcome.Failed -> CommonLabels.exportFailed(language)
                },
                description = when (outcome) {
                    is CliExportOutcome.Saved -> outcome.path
                    is CliExportOutcome.Failed -> outcome.message
                },
                tone = if (outcome is CliExportOutcome.Saved) AppTone.OK else AppTone.CRITICAL
            )
        }
        when (state) {
            AnalysisUiState.Loading -> AppLoadingState(message = AnalysisLabels.loading(language), modifier = Modifier.fillMaxSize())
            AnalysisUiState.Empty -> AppEmptyState(message = AnalysisLabels.empty(language), modifier = Modifier.fillMaxSize())
            is AnalysisUiState.Success -> AnalysisPanel(state.analysis, language, Modifier.weight(1f))
        }
    }
}

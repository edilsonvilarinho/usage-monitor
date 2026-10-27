package com.usagemonitor.presentation.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.components.AppBanner
import com.usagemonitor.presentation.ui.components.AppButton
import com.usagemonitor.presentation.ui.components.AppEmptyState
import com.usagemonitor.presentation.ui.components.AppLoadingState
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

    fun hoursTitle(language: AppLanguage): String = localized(language, "Últimas 24 h", "Last 24 h")
    fun intervalsTitle(language: AppLanguage): String = localized(language, "Ritmo por intervalo", "Pace by interval")
    fun versusWeek(language: AppLanguage): String = localized(language, "vs 7 d", "vs 7 d")
    fun paceColumn(language: AppLanguage): String = localized(language, "%/h da 5h", "%/h of 5h")
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
    AnalysisContent(
        state, label, language,
        onExportPdf = if (viewModel.canExport) ({ viewModel.exportPdf(language) }) else null
    )
}

@Composable
internal fun AnalysisContent(
    state: AnalysisUiState,
    accountLabel: String?,
    language: AppLanguage,
    /** "Relatório PDF" (B21b); `null` esconde o botão. */
    onExportPdf: (() -> Unit)? = null
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

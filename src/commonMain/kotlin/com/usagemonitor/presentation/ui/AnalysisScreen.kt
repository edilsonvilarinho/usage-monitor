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
    private fun pick(language: AppLanguage, pt: String, en: String): String = if (language == AppLanguage.PT) pt else en

    fun title(accountLabel: String?, language: AppLanguage): String =
        pick(language, "Análise", "Analysis") + (accountLabel?.let { label -> " · $label" }.orEmpty())

    fun loading(language: AppLanguage): String = pick(language, "Lendo o histórico e o uso local…", "Reading the history and local usage…")

    fun empty(language: AppLanguage): String = pick(
        language,
        "Sem histórico desta conta ainda. A análise aparece depois de algumas coletas.",
        "No history for this account yet. The analysis shows up after a few collections."
    )

    // Os três blocos de ritmo dividem o card: a 640dp cada um tem ~85dp, por isso rótulo
    // curto e o que cada um mede numa linha só abaixo deles.
    fun paceNow(language: AppLanguage): String = pick(language, "agora", "now")
    fun paceAverage(language: AppLanguage): String = pick(language, "média", "average")
    fun paceSafe(language: AppLanguage): String = pick(language, "seguro", "safe")
    fun paceLegend(language: AppLanguage): String = pick(
        language,
        "agora: última 1 h · média: desde o início da janela · seguro: chega a 100% no reinício",
        "now: last 1 h · average: since the window start · safe: reaches 100% at the reset"
    )
    fun hoursTitle(language: AppLanguage): String = pick(language, "Últimas 24 h", "Last 24 h")
    fun intervalsTitle(language: AppLanguage): String = pick(language, "Ritmo por intervalo", "Pace by interval")
    fun versusWeek(language: AppLanguage): String = pick(language, "vs 7 d", "vs 7 d")
    fun versusTitle(language: AppLanguage): String = pick(language, "Esta janela × anterior", "This window × previous")
    fun now(language: AppLanguage): String = pick(language, "agora", "now")
    fun before(language: AppLanguage): String = pick(language, "antes", "before")
    fun noPreviousWindow(language: AppLanguage): String = pick(language, "Sem janela 5h anterior no histórico.", "No previous 5h window in the history.")
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
        when (state) {
            AnalysisUiState.Loading -> AppLoadingState(message = AnalysisLabels.loading(language), modifier = Modifier.fillMaxSize())
            AnalysisUiState.Empty -> AppEmptyState(message = AnalysisLabels.empty(language), modifier = Modifier.fillMaxSize())
            is AnalysisUiState.Success -> AnalysisPanel(state.analysis, language, Modifier.weight(1f))
        }
    }
}

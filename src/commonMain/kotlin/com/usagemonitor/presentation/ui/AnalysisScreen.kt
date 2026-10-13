package com.usagemonitor.presentation.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.usagemonitor.domain.entity.AnalysisView
import com.usagemonitor.domain.entity.AnalysisViewKind
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.SessionAnalysis
import com.usagemonitor.presentation.ui.components.AppButton
import com.usagemonitor.presentation.ui.components.AppDataSurface
import com.usagemonitor.presentation.ui.components.AppEmptyState
import com.usagemonitor.presentation.ui.components.AppLoadingState
import com.usagemonitor.presentation.ui.components.AppTab
import com.usagemonitor.presentation.ui.components.AppTabs
import com.usagemonitor.presentation.ui.components.AppToolbar
import com.usagemonitor.presentation.ui.components.AppWindowScaffold
import com.usagemonitor.presentation.ui.theme.AppSpacing
import com.usagemonitor.presentation.viewmodel.AnalysisUiState
import com.usagemonitor.presentation.viewmodel.AnalysisViewModel

internal const val ANALYSIS_BODY_TAG = "analysisBody"

/** Rótulos da janela "Análise" (rodada AB), no idioma do app. */
internal object AnalysisLabels {
    fun title(accountLabel: String?, language: AppLanguage): String =
        (if (language == AppLanguage.PT) "Análise" else "Analysis") + (accountLabel?.let { label -> " · $label" }.orEmpty())

    fun refresh(language: AppLanguage): String = if (language == AppLanguage.PT) "Atualizar" else "Refresh"

    fun loading(language: AppLanguage): String = if (language == AppLanguage.PT) "Lendo o histórico e o uso local…" else "Reading the history and local usage…"

    fun empty(language: AppLanguage): String = if (language == AppLanguage.PT) {
        "Sem histórico desta conta ainda. A análise aparece depois de algumas coletas."
    } else {
        "No history for this account yet. The analysis shows up after a few collections."
    }

    /** As abas da janela: as vistas de texto do bot, na mesma ordem. */
    fun views(): List<AnalysisView> = TelegramAnalysisMessages.availableViews.filter { view -> view.kind == AnalysisViewKind.TEXT }
}

/**
 * Janela "Análise" (rodada AB, B20): a análise da sessão de uma conta, aberta pelo card e
 * pelo balão da conta na HUD. As abas são as vistas do `/analise` do bot; o texto é o mesmo,
 * sem as marcas do Telegram, em Plex Mono (`label*`, número é `label*`).
 */
@Composable
fun AnalysisScreen(viewModel: AnalysisViewModel, language: AppLanguage) {
    val state by viewModel.uiState.collectAsState()
    val label by viewModel.accountLabel.collectAsState()
    AnalysisContent(state, label, language, onSelect = viewModel::selectView, onRefresh = viewModel::refresh)
}

@Composable
internal fun AnalysisContent(
    state: AnalysisUiState,
    accountLabel: String?,
    language: AppLanguage,
    onSelect: (AnalysisView) -> Unit,
    onRefresh: () -> Unit
) {
    AppWindowScaffold(modifier = Modifier.fillMaxSize()) {
        AppToolbar(spacing = AppSpacing.sm) {
            Text(AnalysisLabels.title(accountLabel, language), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.weight(1f))
            AppButton(label = AnalysisLabels.refresh(language), onClick = onRefresh)
        }
        when (state) {
            AnalysisUiState.Loading -> AppLoadingState(message = AnalysisLabels.loading(language), modifier = Modifier.fillMaxSize())
            AnalysisUiState.Empty -> AppEmptyState(message = AnalysisLabels.empty(language), modifier = Modifier.fillMaxSize())
            is AnalysisUiState.Success -> AnalysisBody(state.analysis, state.view, language, onSelect)
        }
    }
}

@Composable
private fun ColumnScope.AnalysisBody(analysis: SessionAnalysis, view: AnalysisView, language: AppLanguage, onSelect: (AnalysisView) -> Unit) {
    val views = AnalysisLabels.views()
    val selected = views.indexOf(view).takeIf { index -> index >= 0 } ?: 0
    AppTabs(
        tabs = views.map { item -> AppTab(TelegramAnalysisMessages.viewName(item, language)) },
        selectedIndex = selected,
        onSelect = { index -> onSelect(views[index]) }
    )
    val text = TelegramAnalysisMessages.text(views[selected], analysis, language)?.let(TelegramBotMessages::plain).orEmpty()
    AppDataSurface(modifier = Modifier.fillMaxWidth().weight(1f)) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag(ANALYSIS_BODY_TAG)
            )
        }
    }
}

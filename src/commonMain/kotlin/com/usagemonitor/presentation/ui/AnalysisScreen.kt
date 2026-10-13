package com.usagemonitor.presentation.ui

import androidx.compose.foundation.Image
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
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import com.usagemonitor.presentation.ui.components.AppSegment
import com.usagemonitor.presentation.ui.components.AppSegmentedControl
import com.usagemonitor.presentation.ui.components.AppTab
import com.usagemonitor.presentation.ui.components.AppTabs
import com.usagemonitor.presentation.ui.components.AppToolbar
import com.usagemonitor.presentation.ui.components.AppWindowScaffold
import com.usagemonitor.presentation.ui.theme.AppSpacing
import com.usagemonitor.presentation.viewmodel.AnalysisUiState
import com.usagemonitor.presentation.viewmodel.AnalysisViewModel

internal const val ANALYSIS_BODY_TAG = "analysisBody"
internal const val ANALYSIS_IMAGE_TAG = "analysisImage"

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

    /**
     * As abas da janela: as vistas do bot na mesma ordem — as de imagem só quando há quem as
     * desenhe ([withImages]); o PDF não é aba, é exportação.
     */
    fun views(withImages: Boolean): List<AnalysisView> = TelegramAnalysisMessages.availableViews.filter { view ->
        view.kind == AnalysisViewKind.TEXT || (withImages && view.kind == AnalysisViewKind.IMAGE)
    }

    fun kindName(kind: AnalysisViewKind, language: AppLanguage): String = when (kind) {
        AnalysisViewKind.TEXT -> if (language == AppLanguage.PT) "Texto" else "Text"
        AnalysisViewKind.IMAGE -> if (language == AppLanguage.PT) "Gráficos" else "Charts"
        AnalysisViewKind.FILE -> "PDF"
    }

    /** O nome da aba sem o emoji que o Telegram usa para "isto é imagem": a Plex não tem esses glifos. */
    fun tabName(view: AnalysisView, language: AppLanguage): String =
        TelegramAnalysisMessages.viewName(view, language).removePrefix("🖼 ").removePrefix("📄 ")
}

/** Desenha a vista de imagem; vem de `desktopMain`, que tem o Skia do bot. */
typealias AnalysisImageProvider = (AnalysisView, SessionAnalysis, AppLanguage) -> ImageBitmap?

/**
 * Janela "Análise" (rodada AB, B20): a análise da sessão de uma conta, aberta pelo card e
 * pelo balão da conta na HUD. As abas são as vistas do `/analise` do bot; o texto é o mesmo,
 * sem as marcas do Telegram, em Plex Mono (`label*`, número é `label*`).
 */
@Composable
fun AnalysisScreen(viewModel: AnalysisViewModel, language: AppLanguage, imageFor: AnalysisImageProvider? = null) {
    val state by viewModel.uiState.collectAsState()
    val label by viewModel.accountLabel.collectAsState()
    AnalysisContent(state, label, language, onSelect = viewModel::selectView, onRefresh = viewModel::refresh, imageFor = imageFor)
}

@Composable
internal fun AnalysisContent(
    state: AnalysisUiState,
    accountLabel: String?,
    language: AppLanguage,
    onSelect: (AnalysisView) -> Unit,
    onRefresh: () -> Unit,
    imageFor: AnalysisImageProvider? = null
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
            is AnalysisUiState.Success -> AnalysisBody(state.analysis, state.view, language, onSelect, imageFor)
        }
    }
}

@Composable
private fun ColumnScope.AnalysisBody(
    analysis: SessionAnalysis,
    view: AnalysisView,
    language: AppLanguage,
    onSelect: (AnalysisView) -> Unit,
    imageFor: AnalysisImageProvider?
) {
    val all = AnalysisLabels.views(withImages = imageFor != null)
    val current = all.firstOrNull { item -> item == view } ?: all.first()
    // Doze abas não cabem numa linha na janela mínima: o segmentado escolhe o grupo (texto
    // ou imagens) e as abas, a vista dentro dele — papéis distintos, como pede o design system.
    val kinds = all.map { item -> item.kind }.distinct()
    if (kinds.size > 1) {
        AppSegmentedControl(
            options = kinds.map { kind -> AppSegment(AnalysisLabels.kindName(kind, language)) },
            selectedIndex = kinds.indexOf(current.kind),
            onSelect = { index -> onSelect(all.first { item -> item.kind == kinds[index] }) }
        )
    }
    val views = all.filter { item -> item.kind == current.kind }
    val selected = views.indexOf(current)
    val shown = current
    AppTabs(
        tabs = views.map { item -> AppTab(AnalysisLabels.tabName(item, language)) },
        selectedIndex = selected,
        onSelect = { index -> onSelect(views[index]) }
    )
    AppDataSurface(modifier = Modifier.fillMaxWidth().weight(1f)) {
        if (shown.kind == AnalysisViewKind.IMAGE && imageFor != null) {
            // Os mesmos PNGs do bot (B21): um desenho só para as duas portas.
            val bitmap = remember(analysis, shown, language) { imageFor(shown, analysis, language) }
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = TelegramAnalysisImageLabels.title(shown, analysis, language),
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth().testTag(ANALYSIS_IMAGE_TAG)
                    )
                }
            }
        } else {
            val text = TelegramAnalysisMessages.text(shown, analysis, language)?.let(TelegramBotMessages::plain).orEmpty()
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
}

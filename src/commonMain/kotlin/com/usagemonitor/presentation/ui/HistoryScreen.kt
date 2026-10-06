package com.usagemonitor.presentation.ui

import com.usagemonitor.presentation.ui.components.AppStateCrossfade
import com.usagemonitor.presentation.ui.components.AppModalRevealScope
import com.usagemonitor.presentation.ui.components.rememberSettledRevealKey
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.HistoryRange
import com.usagemonitor.domain.entity.UsageAccountContext
import com.usagemonitor.domain.entity.isObservedActivitySource
import com.usagemonitor.presentation.ui.components.AppButton
import com.usagemonitor.presentation.ui.components.AppButtonTone
import com.usagemonitor.presentation.ui.components.AppWindowScaffold
import com.usagemonitor.presentation.ui.theme.AppAccents
import com.usagemonitor.presentation.ui.theme.AppMotion
import com.usagemonitor.presentation.ui.theme.AppSpacing
import com.usagemonitor.presentation.ui.theme.appTween
import com.usagemonitor.presentation.viewmodel.HistoryUiState
import com.usagemonitor.presentation.viewmodel.HistoryViewModel

/**
 * Âncoras da tela de Histórico.
 *
 * Os três seletores — fonte, conta e intervalo — são hoje três blocos com
 * rótulo próprio e viram uma barra de controles só. O rótulo da conta é o mais
 * frágil dos três como âncora de teste: é `email — workspace`, texto longo e
 * livre, que já aparece também no card do dashboard.
 */
const val HISTORY_SOURCE_CHIP_TAG_PREFIX = "historySourceChip:"
const val HISTORY_ACCOUNT_CHIP_TAG_PREFIX = "historyAccountChip:"
const val HISTORY_RANGE_CHIP_TAG_PREFIX = "historyRangeChip:"

fun historySourceChipTag(source: ApiSource): String = "$HISTORY_SOURCE_CHIP_TAG_PREFIX${source.name}"

fun historyAccountChipTag(account: UsageAccountContext): String =
    "$HISTORY_ACCOUNT_CHIP_TAG_PREFIX${account.key.providerAccountId}/${account.key.workspaceId}"

fun historyRangeChipTag(range: HistoryRange): String = "$HISTORY_RANGE_CHIP_TAG_PREFIX${range.name}"

/** Opacidade do conteúdo anterior enquanto a nova leitura não chega. */
private const val REFRESHING_CONTENT_ALPHA = 0.55f

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel, language: AppLanguage, onBack: () -> Unit,
    focusedSource: ApiSource? = null, showSourceSelector: Boolean = true,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val source = when (val current = state) {
        is HistoryUiState.Success -> current.selectedSource
        is HistoryUiState.Empty -> current.selectedSource
        is HistoryUiState.Error -> current.selectedSource
        HistoryUiState.Loading -> focusedSource
    }
    val success = state as? HistoryUiState.Success
    var expandedMenu by remember(source) { mutableStateOf<HistoryFilterMenu?>(null) }
    val settledScope = success?.takeUnless { it.isRefreshing }?.let { listOf(it.selectedSource, it.selectedAccount?.key, it.selectedRange) }
    LaunchedEffect(settledScope) {
        if (settledScope != null) scrollState.scrollTo(0)
    }
    val status: (@Composable RowScope.() -> Unit)? = if (success == null) null else {
        { Text(lastUpdatedLabel(success.report.lastUpdatedAt, language), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
    AppWindowScaffold(modifier = modifier.fillMaxSize(), contentPadding = AppSpacing.lg, spacing = AppSpacing.md, statusBar = status) {
        HistoryHeader(language, source, showSourceSelector, onBack)
        HistoryFilters(state, viewModel, language, showSourceSelector, expandedMenu, { expandedMenu = it })
        if (success?.exportOutcome != null) {
            Text(exportOutcomeMessage(success.exportOutcome, language), style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("historyExportOutcome"))
        }
        // Só o relatório rola: o contexto e os comandos permanecem acessíveis.
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.fillMaxSize().verticalScroll(scrollState).padding(end = AppSpacing.md).testTag("historyReportViewport")) {
                AppStateCrossfade(state = state, label = "historyStateContent") { current ->
                    when (current) {
                        HistoryUiState.Loading -> Text(if (language == AppLanguage.PT) "Carregando histórico..." else "Loading history...", style = MaterialTheme.typography.bodyLarge)
                        is HistoryUiState.Empty -> Text(if (language == AppLanguage.PT) "Ainda não há snapshots salvos. Faça algumas atualizações bem-sucedidas no dashboard para começar." else "There are no saved snapshots yet. Run a few successful dashboard refreshes to get started.", style = MaterialTheme.typography.bodyLarge)
                        is HistoryUiState.Error -> Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                            Text(if (language == AppLanguage.PT) "Erro ao carregar histórico" else "Failed to load history", color = MaterialTheme.colorScheme.error)
                            Text(current.message, style = MaterialTheme.typography.bodyMedium)
                            AppButton(if (language == AppLanguage.PT) "Tentar novamente" else "Try again", viewModel::refresh)
                        }
                        is HistoryUiState.Success -> {
                            val alpha by animateFloatAsState(if (current.isRefreshing) REFRESHING_CONTENT_ALPHA else 1f, animationSpec = appTween(AppMotion.normal), label = "historyRefreshingAlpha")
                            val revealKey = rememberSettledRevealKey(key = listOf(current.selectedSource, current.selectedAccount, current.selectedRange, current.selectedQuotaView), settled = !current.isRefreshing)
                            Column(Modifier.graphicsLayer { this.alpha = alpha }) {
                                AppModalRevealScope(replayKey = revealKey) { HistoryReportContent(current, language) }
                            }
                        }
                    }
                }
            }
            VerticalScrollbar(rememberScrollbarAdapter(scrollState), Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        }
    }
}

/** O relatório abaixo dos controles: vazio, saldo DeepSeek, atividade observada ou por cota. */
@Composable
private fun HistoryReportContent(current: HistoryUiState.Success, language: AppLanguage) {
    if (current.report.series.isEmpty()) {
        Text(
            text = if (language == AppLanguage.PT) {
                "Sem dados para o intervalo selecionado."
            } else {
                "No data for the selected range."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    val accentColor = accentColorForHistorySource(
        source = current.report.source,
        accents = AppAccents.current
    )
    if (current.report.source == ApiSource.DEEPSEEK) {
        DeepSeekHistoryContent(
            report = current.report,
            accentColor = accentColor,
            language = language,
            selectedRange = current.selectedRange
        )
    } else if (current.report.source.isObservedActivitySource()) {
        OpenCodeHistoryContent(
            report = current.report,
            accentColor = accentColor,
            language = language,
            selectedRange = current.selectedRange
        )
    } else {
        GroupedHistoryContent(
            state = current,
            accentColor = accentColor,
            language = language
        )
    }
}

@Composable
private fun HistoryHeader(
    language: AppLanguage,
    selectedSource: ApiSource?,
    showSourceSelector: Boolean,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = historyTitle(
                    selectedSource = selectedSource,
                    showSourceSelector = showSourceSelector,
                    language = language
                ),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (showSourceSelector) Text(
                text = historySubtitle(
                    selectedSource = selectedSource,
                    showSourceSelector = showSourceSelector,
                    language = language
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (showSourceSelector) {
            AppButton(
                label = if (language == AppLanguage.PT) "Voltar" else "Back",
                onClick = onBack,
                tone = AppButtonTone.GHOST
            )
        }
    }
}

package com.usagemonitor.presentation.ui

import com.usagemonitor.presentation.ui.components.AppStateCrossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.datetime.Instant
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.HistoryRange
import com.usagemonitor.domain.entity.UsageAccountContext
import com.usagemonitor.domain.entity.UsageHistorySeries
import com.usagemonitor.domain.entity.isObservedActivitySource
import com.usagemonitor.domain.entity.requiresUsageAccount
import com.usagemonitor.presentation.ui.components.AppButton
import com.usagemonitor.presentation.ui.components.AppButtonTone
import com.usagemonitor.presentation.ui.components.AppDataSurface
import com.usagemonitor.presentation.ui.components.AppDataSurfaceFlush
import com.usagemonitor.presentation.ui.components.AppSectionHeader
import com.usagemonitor.presentation.ui.components.AppSegment
import com.usagemonitor.presentation.ui.components.AppSegmentedControl
import com.usagemonitor.presentation.ui.components.AppWindowScaffold
import com.usagemonitor.presentation.ui.components.UsageHistoryLineChart
import com.usagemonitor.presentation.ui.theme.AppAccents
import com.usagemonitor.presentation.ui.theme.AppMotion
import com.usagemonitor.presentation.ui.theme.AppSpacing
import com.usagemonitor.presentation.ui.theme.appTween
import com.usagemonitor.presentation.viewmodel.HistoryQuotaView
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
    viewModel: HistoryViewModel,
    language: AppLanguage,
    onBack: () -> Unit,
    focusedSource: ApiSource? = null,
    showSourceSelector: Boolean = true,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val selectedSourceForHeader = when (val current = state) {
        is HistoryUiState.Empty -> current.selectedSource
        is HistoryUiState.Error -> current.selectedSource
        is HistoryUiState.Success -> current.selectedSource
        HistoryUiState.Loading -> focusedSource
    }

    // Sem `Success` não há coleta a datar, e uma barra de 30dp vazia é cromo que
    // não informa nada. O tipo da variável é anotado porque é ele que faz a
    // lambda de dentro do `let` ser reconhecida como `@Composable`.
    val statusBarContent: (@Composable RowScope.() -> Unit)? =
        (state as? HistoryUiState.Success)?.let { success ->
            {
                Text(
                    text = lastUpdatedLabel(success.report.lastUpdatedAt, language),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

    // Corpo de janela do sistema: fundo, grade de espaçamento e a barra de estado
    // como última linha, fora da área rolável. O padding entra na coluna interna,
    // não no scaffold, porque é ela que rola — com o padding no scaffold, a
    // margem de baixo cortaria o conteúdo em vez de acompanhá-lo.
    AppWindowScaffold(
        modifier = modifier.fillMaxSize(),
        contentPadding = 0.dp,
        spacing = 0.dp,
        statusBar = statusBarContent
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(AppSpacing.lg)
                    .padding(end = AppSpacing.md),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
            ) {
                HistoryHeader(
                    language = language,
                    selectedSource = selectedSourceForHeader,
                    showSourceSelector = showSourceSelector,
                    onBack = onBack
                )

                // Mesmo defeito do dashboard: a lambda ignorava o argumento e
                // lia o estado de fora, e os dois slots desenhavam o novo.
                AppStateCrossfade(
                    state = state,
                    label = "historyStateContent"
                ) { current ->
                    when (current) {
                        is HistoryUiState.Loading -> {
                            Text(
                                text = if (language == AppLanguage.PT) "Carregando histórico..." else "Loading history...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        is HistoryUiState.Empty -> {
                            Text(
                                text = if (language == AppLanguage.PT) {
                                    "Ainda não há snapshots salvos. Faça algumas atualizações bem-sucedidas no dashboard para começar."
                                } else {
                                    "There are no saved snapshots yet. Run a few successful dashboard refreshes to get started."
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        is HistoryUiState.Error -> {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = if (language == AppLanguage.PT) "Erro ao carregar histórico" else "Failed to load history",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = current.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        is HistoryUiState.Success -> {
                            // Durante a releitura o conteúdo anterior fica, esmaecido:
                            // diz que está mudando sem trocar a tela por "Carregando".
                            val contentAlpha by animateFloatAsState(
                                targetValue = if (current.isRefreshing) REFRESHING_CONTENT_ALPHA else 1f,
                                animationSpec = appTween(AppMotion.normal),
                                label = "historyRefreshingAlpha"
                            )
                            Column(
                                modifier = Modifier.graphicsLayer { alpha = contentAlpha },
                                verticalArrangement = Arrangement.spacedBy(20.dp)
                            ) {
                                HistoryControls(
                                    availableSources = current.availableSources,
                                    selectedSource = current.selectedSource,
                                    availableAccounts = current.availableAccounts,
                                    selectedAccount = current.selectedAccount,
                                    selectedRange = current.selectedRange,
                                    showSourceSelector = showSourceSelector,
                                    language = language,
                                    onSelectSource = viewModel::selectSource,
                                    onSelectAccount = viewModel::selectAccount,
                                    onSelectRange = viewModel::selectRange,
                                    selectedQuotaView = current.selectedQuotaView,
                                    quotaViewLabels = quotaViewLabels(current.report, language),
                                    onSelectQuotaView = viewModel::selectQuotaView
                                )

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
                                } else {
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
                            }
                        }
                    }
                }
            }

            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(scrollState),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
            )
        }
    }
}

/**
 * O rótulo da última coleta, agora no rodapé.
 *
 * Ele era um `Text` solto acima da primeira série, repetido em dois dos quatro
 * ramos por fonte — o do Codex e o genérico —, e ausente nos outros dois. Como
 * barra de estado ele vale para as quatro, sai da área rolável e para de
 * competir com o gráfico pelo topo da janela.
 */

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
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = historyTitle(
                    selectedSource = selectedSource,
                    showSourceSelector = showSourceSelector,
                    language = language
                ),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
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

/**
 * Fonte, conta e intervalo numa barra só.
 *
 * Eram três blocos empilhados, cada um com o próprio título e a própria fileira
 * de chips: quase duzentos dp de altura antes do primeiro ponto do gráfico. Aqui
 * os três viram controle segmentado com o rótulo ao lado, e a barra quebra em
 * mais de uma linha quando a janela é estreita — daí o `FlowRow`, e não `Row`.
 *
 * O rótulo de cada grupo continua na tela ("API", "Conta", "Intervalo"): sem
 * ele, três segmentados lado a lado não dizem o que escolhem.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryControls(
    availableSources: List<ApiSource>,
    selectedSource: ApiSource,
    availableAccounts: List<UsageAccountContext>,
    selectedAccount: UsageAccountContext?,
    selectedRange: HistoryRange,
    showSourceSelector: Boolean,
    language: AppLanguage,
    onSelectSource: (ApiSource) -> Unit,
    onSelectAccount: (UsageAccountContext) -> Unit,
    onSelectRange: (HistoryRange) -> Unit,
    selectedQuotaView: HistoryQuotaView = HistoryQuotaView.BOTH,
    /** Rótulo de cada opção, na ordem do enum; `null` esconde o controle. */
    quotaViewLabels: List<String>? = null,
    onSelectQuotaView: (HistoryQuotaView) -> Unit = {}
) {
    AppDataSurface(contentPadding = AppSpacing.sm) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            if (showSourceSelector) {
                HistoryControlGroup(label = if (language == AppLanguage.PT) "API" else "API") {
                    AppSegmentedControl(
                        options = availableSources.map { source ->
                            AppSegment(label = sourceLabel(source), testTag = historySourceChipTag(source))
                        },
                        selectedIndex = availableSources.indexOf(selectedSource),
                        onSelect = { index -> onSelectSource(availableSources[index]) }
                    )
                }
            }

            if (selectedSource.requiresUsageAccount) {
                HistoryControlGroup(label = if (language == AppLanguage.PT) "Conta" else "Account") {
                if (availableAccounts.isEmpty()) {
                    Text(
                        text = if (language == AppLanguage.PT) {
                            "Nenhuma conta identificada. Atualize o card após concluir o login."
                        } else {
                            "No account identified. Refresh the card after sign-in completes."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    AppSegmentedControl(
                        options = availableAccounts.map { account ->
                            AppSegment(
                                label = account.displayLabel,
                                testTag = historyAccountChipTag(account)
                            )
                        },
                        selectedIndex = availableAccounts.indexOfFirst { account ->
                            account.key == selectedAccount?.key
                        },
                        onSelect = { index -> onSelectAccount(availableAccounts[index]) }
                    )
                }
                }
            }

            HistoryControlGroup(label = if (language == AppLanguage.PT) "Intervalo" else "Range") {
                AppSegmentedControl(
                    options = HistoryRange.entries.map { range ->
                        AppSegment(label = rangeLabel(range, language), testTag = historyRangeChipTag(range))
                    },
                    selectedIndex = HistoryRange.entries.indexOf(selectedRange),
                    onSelect = { index -> onSelectRange(HistoryRange.entries[index]) }
                )
            }

            if (quotaViewLabels != null) {
                HistoryControlGroup(label = if (language == AppLanguage.PT) "Cota" else "Quota") {
                    AppSegmentedControl(
                        options = HistoryQuotaView.entries.mapIndexed { index, view ->
                            AppSegment(label = quotaViewLabels[index], testTag = historyQuotaViewChipTag(view))
                        },
                        selectedIndex = HistoryQuotaView.entries.indexOf(selectedQuotaView),
                        onSelect = { index -> onSelectQuotaView(HistoryQuotaView.entries[index]) }
                    )
                }
            }
        }
    }
}

/** Rótulo e controle na mesma linha de base, como um par. */
@Composable
private fun HistoryControlGroup(label: String, content: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        content()
    }
}

@Composable
internal fun HistorySeriesCard(
    source: ApiSource,
    series: UsageHistorySeries,
    index: Int,
    accentColor: Color,
    language: AppLanguage,
    chartSelectionKey: String,
    titleOverride: String? = null,
    subtitleOverride: String? = null,
    weeklySummary: UsageHistorySeries? = null,
    /** Carimbo do último ponto coletado; base da linha de referência diária. */
    referenceAt: Instant? = null,
    /** Qual janela mostrar quando há [weeklySummary]; sem ela não tem efeito. */
    quotaView: HistoryQuotaView = HistoryQuotaView.BOTH
) {
    var visible by remember { mutableStateOf(false) }
    val cardAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = appTween(AppMotion.normal, easing = AppMotion.enterEasing),
        label = "seriesCardAlpha$index"
    )
    val cardOffsetY by animateFloatAsState(
        targetValue = if (visible) 0f else 28f,
        animationSpec = appTween(AppMotion.slow, easing = AppMotion.enterEasing),
        label = "seriesCardOffsetY$index"
    )
    LaunchedEffect(Unit) {
        delay(index * AppMotion.stagger)
        visible = true
    }

    val title = titleOverride ?: historySeriesDisplayTitle(
        source = source,
        series = series,
        language = language
    )
    val subtitle = subtitleOverride ?: historySeriesDisplaySubtitle(
        source = source,
        series = series,
        language = language
    )

    // Painel neutro com cabeçalho: a faixa de 3dp que atravessava a altura toda
    // do card virou o marcador de 2dp do cabeçalho, o mesmo que identifica a
    // fonte no dashboard. A cor sai da moldura e entra na linha do gráfico, que
    // é onde ela realmente distingue uma série da outra.
    AppDataSurfaceFlush(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = cardAlpha
                translationY = cardOffsetY
            },
        header = {
            AppSectionHeader(
                title = title,
                subtitle = subtitle,
                markerColor = accentColor
            )
        }
    ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AppSpacing.md),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
            ) {
                val chartSeries = if (quotaView == HistoryQuotaView.WEEKLY && weeklySummary != null) {
                    weeklySummary
                } else {
                    series
                }
                UsageHistoryLineChart(
                    points = chartSeries.points,
                    unit = chartSeries.unit,
                    language = language,
                    chartSelectionKey = chartSelectionKey + ":" + quotaView.name,
                    tooltipTitle = title,
                    tooltipSubtitle = subtitle,
                    accentColor = accentColor,
                    previousPoints = chartSeries.previousWindowPoints,
                    seriesLabel = quotaWindowLabel(chartSeries, language),
                    overlays = historyChartOverlays(
                        weeklySummary = weeklySummary.takeIf { quotaView == HistoryQuotaView.BOTH },
                        primary = series,
                        color = AppAccents.current.output,
                        language = language
                    )
                )

                if (weeklySummary != null) {
                    if (quotaView != HistoryQuotaView.WEEKLY) {
                        HistoryMetricsPanel(
                            title = intervalSummaryLabel(language),
                            source = source,
                            series = series,
                            language = language,
                            referenceAt = referenceAt
                        )
                        HistoryWindowAnalysisPanel(series = series, accentColor = accentColor, language = language)
                    }
                    if (quotaView != HistoryQuotaView.INTERVAL) {
                        HistoryMetricsPanel(
                            title = weeklySummaryLabel(language),
                            source = source,
                            series = weeklySummary,
                            language = language,
                            referenceAt = referenceAt
                        )
                        HistoryWindowAnalysisPanel(
                            series = weeklySummary,
                            accentColor = AppAccents.current.output,
                            language = language
                        )
                    }
                } else {
                    HistoryMetrics(
                        source = source,
                        series = series,
                        language = language,
                        referenceAt = referenceAt
                    )
                    HistoryWindowAnalysisPanel(series = series, accentColor = accentColor, language = language)
                }
            }
    }
}

package com.usagemonitor.presentation.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import kotlin.math.roundToLong
import kotlinx.coroutines.delay
import kotlinx.datetime.Instant
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageHistoryReport
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.DeepSeekQuotaLabels
import com.usagemonitor.domain.entity.HistoryRange
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.UsageAccountContext
import com.usagemonitor.domain.entity.UsageHistorySeries
import com.usagemonitor.domain.entity.UsageUnit
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
import com.usagemonitor.presentation.ui.theme.AppMotion
import com.usagemonitor.presentation.ui.theme.AppSpacing
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

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    language: AppLanguage,
    onBack: () -> Unit,
    focusedSource: ApiSource? = null,
    showSourceSelector: Boolean = true,
    onRequiredHeightChanged: (Dp) -> Unit = {},
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
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .verticalScroll(scrollState)
                    .then(rememberModalContentHeightReporter(onRequiredHeightChanged))
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

                AnimatedContent(
                    targetState = state::class,
                    transitionSpec = {
                        (fadeIn(tween(AppMotion.normal, easing = AppMotion.enterEasing)) +
                            slideInVertically(tween(AppMotion.slow, easing = AppMotion.enterEasing)) { it / 10 })
                            .togetherWith(fadeOut(tween(AppMotion.fast, easing = AppMotion.exitEasing)))
                            .using(SizeTransform(clip = false))
                    },
                    label = "historyStateContent"
                ) { _ ->
                    when (val current = state) {
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
                            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
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
                                    onSelectRange = viewModel::selectRange
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
                                    val accentColor = accentColorForHistorySource(current.report.source)
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
                                    } else if (current.report.source == ApiSource.CODEX) {
                                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                            current.report.series.forEachIndexed { index, series ->
                                                key(
                                                    series.quotaLabel +
                                                        current.selectedAccount?.key.toString() +
                                                        current.selectedRange.name
                                                ) {
                                                    HistorySeriesCard(
                                                        source = current.report.source,
                                                        series = series,
                                                        index = index,
                                                        accentColor = accentColor,
                                                        language = language,
                                                        chartSelectionKey = buildQuotaChartSelectionKey(
                                                            source = current.report.source,
                                                            quotaLabel = series.quotaLabel,
                                                            periodType = series.periodType,
                                                            selectedRange = current.selectedRange
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        val cardModels = remember(current.report.series) {
                                            buildGenericHistoryGroups(current.report.series)
                                        }

                                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                            cardModels.forEachIndexed { index, model ->
                                                key(
                                                    model.baseLabel +
                                                        current.selectedAccount?.key.toString() +
                                                        current.selectedRange.name
                                                ) {
                                                    HistorySeriesCard(
                                                        source = current.report.source,
                                                        series = model.chartSeries,
                                                        index = index,
                                                        accentColor = accentColor,
                                                        language = language,
                                                        chartSelectionKey = buildQuotaChartSelectionKey(
                                                            source = current.report.source,
                                                            quotaLabel = model.chartSeries.quotaLabel,
                                                            periodType = model.chartSeries.periodType,
                                                            selectedRange = current.selectedRange
                                                        ),
                                                        titleOverride = model.baseLabel,
                                                        subtitleOverride = genericHistorySubtitle(language),
                                                        weeklySummary = model.weeklySummary
                                                    )
                                                }
                                            }
                                        }
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
    onSelectRange: (HistoryRange) -> Unit
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
private fun DeepSeekHistoryContent(
    report: ApiUsageHistoryReport,
    accentColor: Color,
    language: AppLanguage,
    selectedRange: HistoryRange
) {
    val primarySeries = report.series
        .firstOrNull { series -> series.quotaLabel.equals(DeepSeekQuotaLabels.BALANCE, ignoreCase = true) }
        ?: report.series.first()
    val extraSeries = report.series.filterNot { series -> series == primarySeries }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        key(primarySeries.quotaLabel + selectedRange.name) {
            DeepSeekHistoryCard(
                title = deepSeekSeriesTitle(primarySeries, language),
                subtitle = deepSeekSeriesSubtitle(primarySeries, language),
                series = primarySeries,
                lastUpdatedAt = report.lastUpdatedAt,
                accentColor = accentColor,
                index = 0,
                language = language,
                chartSelectionKey = buildQuotaChartSelectionKey(
                    source = report.source,
                    quotaLabel = primarySeries.quotaLabel,
                    periodType = primarySeries.periodType,
                    selectedRange = selectedRange
                )
            )
        }

        extraSeries.forEachIndexed { i, series ->
            key(series.quotaLabel + selectedRange.name) {
                DeepSeekHistoryCard(
                    title = deepSeekSeriesTitle(series, language),
                    subtitle = deepSeekSeriesSubtitle(series, language),
                    series = series,
                    lastUpdatedAt = null,
                    accentColor = accentColor,
                    index = i + 1,
                    language = language,
                    chartSelectionKey = buildQuotaChartSelectionKey(
                        source = report.source,
                        quotaLabel = series.quotaLabel,
                        periodType = series.periodType,
                        selectedRange = selectedRange
                    )
                )
            }
        }
    }
}

@Composable
private fun OpenCodeHistoryContent(
    report: ApiUsageHistoryReport,
    accentColor: Color,
    language: AppLanguage,
    selectedRange: HistoryRange
) {
    val modelReports = remember(report.series, selectedRange) {
        buildOpenCodeHistoryGroups(report.series, selectedRange)
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = lastUpdatedLabel(report.lastUpdatedAt, language),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        modelReports.forEachIndexed { index, modelReport ->
            key(modelReport.modelName + selectedRange.name) {
                OpenCodeHistoryCard(
                    modelReport = modelReport,
                    accentColor = accentColor,
                    index = index,
                    language = language,
                    chartSelectionKey = buildQuotaChartSelectionKey(
                        source = report.source,
                        quotaLabel = modelReport.modelName,
                        periodType = modelReport.chartSeries.periodType,
                        selectedRange = selectedRange
                    )
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeepSeekHistoryCard(
    title: String,
    subtitle: String,
    series: UsageHistorySeries,
    lastUpdatedAt: Instant?,
    accentColor: Color,
    index: Int,
    language: AppLanguage,
    chartSelectionKey: String
) {
    var visible by remember { mutableStateOf(false) }
    val cardAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(AppMotion.normal, easing = AppMotion.enterEasing),
        label = "cardAlpha$index"
    )
    val cardOffsetY by animateFloatAsState(
        targetValue = if (visible) 0f else 28f,
        animationSpec = tween(AppMotion.slow, easing = AppMotion.enterEasing),
        label = "cardOffsetY$index"
    )
    LaunchedEffect(Unit) {
        delay(index * AppMotion.stagger)
        visible = true
    }

    // Mesma anatomia de `HistorySeriesCard`: painel neutro, cabeçalho com o
    // marcador de 2dp e nenhuma sombra. A faixa de 3dp que atravessava a altura
    // toda, a superfície com alpha e a elevação de 6 eram os três restos do card
    // anterior que sobreviveram à passada da Fase E — esta tela só é composta com
    // a DeepSeek selecionada, e nenhuma captura passava por aqui.
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
                HistoryMetricTable(
                    entries = buildList {
                        add(
                            HistoryMetricEntry(
                                label = if (language == AppLanguage.PT) "Saldo atual" else "Current balance",
                                value = formatCents(series.currentDisplayUsed)
                            )
                        )
                        add(
                            HistoryMetricEntry(
                                label = if (language == AppLanguage.PT) "Gasto no período" else "Spent in range",
                                value = formatCents(series.deltaDisplayUsed)
                            )
                        )
                        add(
                            HistoryMetricEntry(
                                label = if (language == AppLanguage.PT) "Ritmo médio" else "Average pace",
                                value = formatCents(series.averageDisplayConsumptionPerHour.toLong()) + "/h"
                            )
                        )
                        if (lastUpdatedAt != null) {
                            add(
                                HistoryMetricEntry(
                                    label = if (language == AppLanguage.PT) "Última coleta" else "Last snapshot",
                                    value = formatInstant(lastUpdatedAt)
                                )
                            )
                        }
                    }
                )

                UsageHistoryLineChart(
                    points = series.points,
                    unit = series.unit,
                    language = language,
                    chartSelectionKey = chartSelectionKey,
                    tooltipTitle = title,
                    tooltipSubtitle = subtitle,
                    accentColor = accentColor
                )

                deepSeekForecastText(series.forecast, language)?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OpenCodeHistoryCard(
    modelReport: OpenCodeHistoryModelReport,
    accentColor: Color,
    index: Int,
    language: AppLanguage,
    chartSelectionKey: String
) {
    var visible by remember { mutableStateOf(false) }
    val cardAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(AppMotion.normal, easing = AppMotion.enterEasing),
        label = "openCodeHistoryCardAlpha$index"
    )
    val cardOffsetY by animateFloatAsState(
        targetValue = if (visible) 0f else 28f,
        animationSpec = tween(AppMotion.slow, easing = AppMotion.enterEasing),
        label = "openCodeHistoryCardOffsetY$index"
    )
    LaunchedEffect(Unit) {
        delay(index * AppMotion.stagger)
        visible = true
    }

    AppDataSurfaceFlush(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = cardAlpha
                translationY = cardOffsetY
            },
        header = {
            AppSectionHeader(
                title = modelReport.modelName,
                subtitle = openCodeHistorySubtitle(
                    periodType = modelReport.chartSeries.periodType,
                    language = language
                ),
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
                UsageHistoryLineChart(
                    points = modelReport.chartSeries.points,
                    unit = modelReport.chartSeries.unit,
                    language = language,
                    chartSelectionKey = chartSelectionKey,
                    tooltipTitle = modelReport.modelName,
                    tooltipSubtitle = openCodeHistorySubtitle(
                        periodType = modelReport.chartSeries.periodType,
                        language = language
                    ),
                    accentColor = accentColor
                )

                HistoryMetricTable(
                    entries = listOf(
                        HistoryMetricEntry(
                            label = if (language == AppLanguage.PT) "Requisições nas últimas 5h" else "Requests in last 5h",
                            value = localizedRequests(modelReport.requests5h, language)
                        ),
                        HistoryMetricEntry(
                            label = if (language == AppLanguage.PT) "Requisições nos últimos 7 dias" else "Requests in last 7 days",
                            value = localizedRequests(modelReport.requests7d, language)
                        ),
                        HistoryMetricEntry(
                            label = if (language == AppLanguage.PT) "Variação observada" else "Observed change",
                            value = localizedRequests(modelReport.chartSeries.deltaDisplayUsed, language)
                        ),
                        HistoryMetricEntry(
                            label = if (language == AppLanguage.PT) "Média por hora" else "Average per hour",
                            value = localizedRequests(
                                modelReport.chartSeries.averageDisplayConsumptionPerHour.roundToLong(),
                                language
                            ) + "/h"
                        ),
                        HistoryMetricEntry(
                            label = if (language == AppLanguage.PT) "Previsão" else "Forecast",
                            value = if (language == AppLanguage.PT) "Limite indisponível" else "Limit unavailable"
                        )
                    )
                )
            }
    }
}

@Composable
private fun HistorySeriesCard(
    source: ApiSource,
    series: UsageHistorySeries,
    index: Int,
    accentColor: Color,
    language: AppLanguage,
    chartSelectionKey: String,
    titleOverride: String? = null,
    subtitleOverride: String? = null,
    weeklySummary: UsageHistorySeries? = null
) {
    var visible by remember { mutableStateOf(false) }
    val cardAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(AppMotion.normal, easing = AppMotion.enterEasing),
        label = "seriesCardAlpha$index"
    )
    val cardOffsetY by animateFloatAsState(
        targetValue = if (visible) 0f else 28f,
        animationSpec = tween(AppMotion.slow, easing = AppMotion.enterEasing),
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
                UsageHistoryLineChart(
                    points = series.points,
                    unit = series.unit,
                    language = language,
                    chartSelectionKey = chartSelectionKey,
                    tooltipTitle = title,
                    tooltipSubtitle = subtitle,
                    accentColor = accentColor
                )

                if (weeklySummary != null) {
                    HistoryMetricsPanel(
                        title = intervalSummaryLabel(language),
                        source = source,
                        series = series,
                        language = language
                    )
                    HistoryMetricsPanel(
                        title = weeklySummaryLabel(language),
                        source = source,
                        series = weeklySummary,
                        language = language
                    )
                } else {
                    HistoryMetrics(
                        source = source,
                        series = series,
                        language = language
                    )
                }
            }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryMetricsPanel(
    title: String,
    source: ApiSource,
    series: UsageHistorySeries,
    language: AppLanguage
) {
    AppDataSurfaceFlush(
        header = { AppSectionHeader(title = title) }
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(AppSpacing.sm)) {
            HistoryMetrics(
                source = source,
                series = series,
                language = language
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
/**
 * As métricas da série, em duas colunas de pares rótulo→valor.
 *
 * Duas colunas e não uma: com uma, a tela do OpenCode — que tem duas séries por
 * modelo — passava de 2.400px de altura. Não é `FlowRow`: ali a linha mede pelo
 * conteúdo, o `weight` do valor fica sem referência e o Compose deixa o texto
 * **sem posicionar** — `isPlaced` falso, nó na árvore e nada na tela. Duas
 * `Column` com `weight(1f)` dentro de uma `Row` de largura cheia dão ao peso a
 * referência que ele precisa.
 */
@Composable
private fun HistoryMetrics(
    source: ApiSource,
    series: UsageHistorySeries,
    language: AppLanguage
) {
    HistoryMetricTable(
        entries = historyMetricEntries(source = source, series = series, language = language)
    )
}

/**
 * A tabela de métricas: duas colunas de pares rótulo→valor.
 *
 * Duas colunas e não uma: com uma, a tela do OpenCode — que tem duas séries por
 * modelo — passava de 2.400px de altura. E **não** é `FlowRow`: ali a linha mede
 * pelo conteúdo, o `weight` do valor fica sem referência e o Compose deixa o
 * texto sem posicionar — `isPlaced` falso, nó presente na árvore e nada na tela,
 * que é como este layout falhou da primeira vez. Duas `Column` com `weight(1f)`
 * dentro de uma `Row` de largura cheia dão ao peso a referência que falta.
 */
@Composable
private fun HistoryMetricTable(entries: List<HistoryMetricEntry>) {
    // Ímpar sobra para a esquerda: um buraco no fim da segunda coluna lê melhor
    // que um no meio da primeira.
    val half = (entries.size + 1) / 2

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.lg)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            entries.take(half).forEach { entry ->
                MetricItem(label = entry.label, value = entry.value)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            entries.drop(half).forEach { entry ->
                MetricItem(label = entry.label, value = entry.value)
            }
        }
    }
}

/** Um par de métrica. Nome e valor já formatados e já traduzidos. */
private data class HistoryMetricEntry(val label: String, val value: String)

/**
 * As métricas que a série publica, na ordem em que a tela as mostra.
 *
 * Separada do desenho porque a escolha de quais métricas existem depende do
 * tipo de período, da unidade e da fonte — regra de apresentação que não tem
 * nada a ver com o layout de duas colunas.
 */
private fun historyMetricEntries(
    source: ApiSource,
    series: UsageHistorySeries,
    language: AppLanguage
): List<HistoryMetricEntry> {
    val entries = mutableListOf<HistoryMetricEntry>()

    if (series.periodType == PeriodType.REPORTED) {
        entries += HistoryMetricEntry(
            label = if (language == AppLanguage.PT) "Uso atual" else "Current usage",
            value = "${currentUsagePercent(series.currentDisplayUsed, series.currentDisplayTotal)} / 100 %"
        )
        entries += HistoryMetricEntry(
            label = if (language == AppLanguage.PT) "Variação observada" else "Observed change",
            value = formatPercentageOfTotal(series.deltaDisplayUsed.toDouble(), series.currentDisplayTotal)
        )
        if (source == ApiSource.CODEX) {
            entries += HistoryMetricEntry(
                label = if (language == AppLanguage.PT) "Último reinício reportado" else "Last reported reset",
                value = formatInstant(series.currentPeriodEndAt)
            )
        }
    } else if (series.unit == UsageUnit.CURRENCY_USD) {
        entries += HistoryMetricEntry(
            label = if (language == AppLanguage.PT) "Saldo atual" else "Current balance",
            value = formatCents(series.currentDisplayUsed)
        )
        entries += HistoryMetricEntry(
            label = if (language == AppLanguage.PT) "Consumido no período" else "Consumed in range",
            value = formatCents(series.deltaDisplayUsed)
        )
        entries += HistoryMetricEntry(
            label = if (language == AppLanguage.PT) "Média por hora" else "Average per hour",
            value = formatCents(series.averageDisplayConsumptionPerHour.toLong()) + "/h"
        )
    } else if (series.unit == UsageUnit.REQUESTS) {
        if (series.currentDisplayTotal > 0L) {
            entries += HistoryMetricEntry(
                label = if (language == AppLanguage.PT) "Uso atual" else "Current usage",
                value = "${formatQuantity(series.currentDisplayUsed)}/${formatQuantity(series.currentDisplayTotal)} req"
            )
            entries += HistoryMetricEntry(
                label = if (language == AppLanguage.PT) "Consumido no período" else "Consumed in range",
                value = "${formatQuantity(series.deltaDisplayUsed)} req"
            )
        } else {
            entries += HistoryMetricEntry(
                label = if (language == AppLanguage.PT) "Requisições na janela" else "Requests in window",
                value = "${formatQuantity(series.currentDisplayUsed)} req"
            )
            entries += HistoryMetricEntry(
                label = if (language == AppLanguage.PT) "Variação observada" else "Observed change",
                value = "${formatQuantity(series.deltaDisplayUsed)} req"
            )
        }
        entries += HistoryMetricEntry(
            label = if (language == AppLanguage.PT) "Média por hora" else "Average per hour",
            value = "${formatQuantity(series.averageDisplayConsumptionPerHour.roundToLong())} req/h"
        )
    } else {
        entries += HistoryMetricEntry(
            label = if (language == AppLanguage.PT) "Uso atual" else "Current usage",
            value = "${currentUsagePercent(series.currentDisplayUsed, series.currentDisplayTotal)} / 100 %"
        )
        entries += HistoryMetricEntry(
            label = if (language == AppLanguage.PT) "Consumido no período" else "Consumed in range",
            value = formatPercentageOfTotal(series.deltaDisplayUsed.toDouble(), series.currentDisplayTotal)
        )
        entries += HistoryMetricEntry(
            label = if (language == AppLanguage.PT) "Média por hora" else "Average per hour",
            value = formatPercentageOfTotal(series.averageDisplayConsumptionPerHour, series.currentDisplayTotal) + "/h"
        )
    }

    if (series.periodType != PeriodType.REPORTED) {
        entries += HistoryMetricEntry(
            label = if (language == AppLanguage.PT) "Previsão" else "Forecast",
            value = if (series.unit == UsageUnit.REQUESTS && series.currentDisplayTotal <= 0L) {
                if (language == AppLanguage.PT) "Limite indisponível" else "Limit unavailable"
            } else {
                forecastLabel(series.forecast, language)
            }
        )
    }

    val comparison = series.comparison
    if (comparison != null) {
        entries += HistoryMetricEntry(
            label = if (language == AppLanguage.PT) "vs. período anterior" else "vs. previous period",
            value = periodComparisonLabel(comparison, language)
        )
    }

    return entries
}

/**
 * Uma métrica: rótulo à esquerda, valor à direita, largura fixa.
 *
 * Era rótulo em cima e valor embaixo, num `FlowRow` cujas colunas mudavam de
 * largura conforme o texto — sete métricas viravam sete larguras diferentes e
 * nenhum valor alinhava com o de baixo. Com a largura fixa os pares formam
 * colunas de verdade, e o `FlowRow` decide quantas cabem.
 */
@Composable
private fun MetricItem(
    label: String,
    value: String
) {
    Row(
        // Largura cheia: "A janela deve reiniciar antes do limite" é um valor de
        // métrica, e numa coluna estreita o rótulo ao lado quebrava letra a letra.
        modifier = Modifier.fillMaxWidth().padding(vertical = AppSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
    }
}




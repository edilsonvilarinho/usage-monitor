package com.usagemonitor.presentation.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.usagemonitor.data.export.UsageExportFormat
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CliSessionAnalytics
import com.usagemonitor.domain.entity.CliSessionDetail
import com.usagemonitor.domain.entity.CliSessionHealth
import com.usagemonitor.domain.entity.CliSessionHealthTally
import com.usagemonitor.domain.entity.CliSessionRange
import com.usagemonitor.domain.entity.CliSessionSummary
import com.usagemonitor.presentation.ui.components.BinMode
import com.usagemonitor.presentation.ui.components.CopySessionCommandButton
import com.usagemonitor.presentation.ui.components.DepthSurface
import com.usagemonitor.presentation.ui.components.HoverTooltipBox
import com.usagemonitor.presentation.ui.components.TooltipMetric
import com.usagemonitor.presentation.ui.components.TurnSeries
import com.usagemonitor.presentation.ui.components.TurnSeriesChart
import com.usagemonitor.presentation.ui.theme.AppAccents
import com.usagemonitor.presentation.ui.theme.AppElevation
import com.usagemonitor.presentation.ui.theme.AppShapes
import com.usagemonitor.presentation.ui.theme.darkAppAccents
import com.usagemonitor.presentation.viewmodel.CliExportOutcome
import com.usagemonitor.presentation.viewmodel.CliSessionDetailUiState
import com.usagemonitor.presentation.viewmodel.CliSessionsUiState
import com.usagemonitor.presentation.viewmodel.CliSessionsView
import com.usagemonitor.presentation.viewmodel.CliSessionsViewModel

// A paleta inteira é `internal` porque a tela de time usa a mesma codificação de
// cor: custo em azul, tokens em verde, cache gravado em laranja, economia em
// ciano. Duas paletas para o mesmo significado fariam o usuário reaprender a ler
// ao trocar de janela — e o painel de detalhe agora é o mesmo nas duas.
//
// Os valores em si mudaram de casa: moram em `theme/AppAccents.kt`, que tem uma
// variante por tema. Estes nomes continuam aqui, amarrados à variante **escura**,
// para as telas que ainda não migraram (`ApiUsageCard`, `HistoryScreen`, charts)
// renderizarem exatamente como hoje. Código novo lê `AppAccents.current`.
internal val INPUT_COLOR = darkAppAccents.input
internal val OUTPUT_COLOR = darkAppAccents.output
internal val CACHE_READ_COLOR = darkAppAccents.cacheRead
internal val CACHE_WRITE_COLOR = darkAppAccents.cacheWrite
internal val SAVINGS_COLOR = darkAppAccents.savings
internal val NEUTRAL_ACCENT = darkAppAccents.neutral

/** Faixa reservada à barra de rolagem, que flutua sobre o conteúdo. */
internal val SCROLLBAR_GUTTER = 12.dp

/**
 * Brilho quase apagado para os blocos do detalhe.
 *
 * O default de `DepthSurface` (0.22) é o do dashboard, onde cada card é uma API
 * distinta e a cor identifica. Aqui os blocos são a mesma sessão vista de
 * ângulos diferentes: com o brilho cheio a tela vira uma pilha de retângulos
 * coloridos e nada se destaca. A cor fica no dado, não no fundo.
 */
private const val QUIET_GLOW_ALPHA = 0.06f

/** Mais baixo que o default de `TurnSeriesChart`: são vários numa página só. */
private val DETAIL_CHART_HEIGHT = 120.dp

internal const val LIST_SCROLLBAR_TAG = "cliSessionsListScrollbar"
internal const val DETAIL_SCROLLBAR_TAG = "cliSessionsDetailScrollbar"
const val TAB_SESSIONS_TAG = "cliSessionsTabSessions"
const val TAB_BREAKDOWN_TAG = "cliSessionsTabBreakdown"
const val EXPORT_CSV_TAG = "cliSessionsExportCsv"
const val EXPORT_JSON_TAG = "cliSessionsExportJson"
const val EXPORT_PDF_TAG = "cliSessionsExportPdf"

/** Único componente stateful: lê o estado do ViewModel e delega para filhos puros. */
@Composable
fun CliSessionsScreen(
    viewModel: CliSessionsViewModel,
    language: AppLanguage,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    CliSessionsContent(
        state = state,
        language = language,
        onSelectRange = { range -> viewModel.setRange(range) },
        onOpenSession = { sessionId -> viewModel.openSession(sessionId) },
        onCloseDetail = { viewModel.closeDetail() },
        onToggleAdvanced = { viewModel.toggleAdvanced() },
        onToggleGlossary = { viewModel.toggleGlossary() },
        onSelectView = { view -> viewModel.setView(view) },
        onExport = { format -> viewModel.exportCurrentView(format) },
        onExportReport = { viewModel.exportReport(language) },
        modifier = modifier
    )
}

@Composable
internal fun CliSessionsContent(
    state: CliSessionsUiState,
    language: AppLanguage,
    onSelectRange: (CliSessionRange) -> Unit,
    onOpenSession: (String) -> Unit,
    onCloseDetail: () -> Unit,
    // Com default para não arrastar as chamadas que não exercitam os blocos.
    onToggleAdvanced: () -> Unit = {},
    onToggleGlossary: () -> Unit = {},
    onSelectView: (CliSessionsView) -> Unit = {},
    onExport: (UsageExportFormat) -> Unit = {},
    onExportReport: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (state) {
            is CliSessionsUiState.Loading -> CenteredMessage(CliSessionsLabels.loading(language))

            is CliSessionsUiState.Error -> CenteredMessage(state.message)

            is CliSessionsUiState.Success -> {
                val detail = state.detail
                if (detail == null) {
                    CliSessionsList(
                        state = state,
                        language = language,
                        onSelectRange = onSelectRange,
                        onOpenSession = onOpenSession,
                        onSelectView = onSelectView,
                        onExport = onExport,
                        onExportReport = onExportReport
                    )
                } else {
                    CliSessionDetailPane(
                        detail = detail,
                        language = language,
                        advancedExpanded = state.advancedExpanded,
                        glossaryExpanded = state.glossaryExpanded,
                        onCloseDetail = onCloseDetail,
                        onToggleAdvanced = onToggleAdvanced,
                        onToggleGlossary = onToggleGlossary
                    )
                }
            }
        }
    }
}

@Composable
internal fun CenteredMessage(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ----------------------------------------------------------------------------
// Lista
// ----------------------------------------------------------------------------

@Composable
private fun CliSessionsList(
    state: CliSessionsUiState.Success,
    language: AppLanguage,
    onSelectRange: (CliSessionRange) -> Unit,
    onOpenSession: (String) -> Unit,
    onSelectView: (CliSessionsView) -> Unit = {},
    onExport: (UsageExportFormat) -> Unit = {},
    onExportReport: () -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CliSessionsHeader(
            state = state,
            language = language,
            onSelectRange = onSelectRange,
            onSelectView = onSelectView,
            onExport = onExport,
            onExportReport = onExportReport
        )

        if (state.indexWarning != null) {
            NoticeText(state.indexWarning, MaterialTheme.colorScheme.error)
        }

        // Acima das duas abas: a troca de janela desatualiza a lista e o resumo.
        if (state.isRefreshing) {
            Text(
                text = BreakdownLabels.refreshing(language),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().testTag(REFRESHING_NOTICE_TAG)
            )
        }

        if (state.view == CliSessionsView.BREAKDOWN) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                CliUsageBreakdownPane(
                    breakdown = state.breakdown,
                    errorMessage = state.breakdownError,
                    language = language,
                    budget = state.budget,
                    accountCredits = state.accountCredits
                )
            }
            return@Column
        }

        if (state.sessions.isEmpty()) {
            CenteredMessage(CliSessionsLabels.emptyInRange(state.range, state.rangeAnchored, language))
            return@Column
        }

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            val listState = rememberLazyListState()

            LazyColumn(
                state = listState,
                // A barra fica por cima da área de conteúdo; sem a folga à direita
                // ela cobriria a borda dos cards, que ocupam a largura inteira.
                modifier = Modifier.fillMaxSize().padding(end = SCROLLBAR_GUTTER),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items = state.sessions, key = { session -> session.sessionId }) { session ->
                    CliSessionRow(
                        session = session,
                        language = language,
                        onOpen = { onOpenSession(session.sessionId) }
                    )
                }
            }

            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(listState),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .testTag(LIST_SCROLLBAR_TAG)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CliSessionsHeader(
    state: CliSessionsUiState.Success,
    language: AppLanguage,
    onSelectRange: (CliSessionRange) -> Unit,
    onSelectView: (CliSessionsView) -> Unit = {},
    onExport: (UsageExportFormat) -> Unit = {},
    onExportReport: () -> Unit = {}
) {
    DepthSurface(
        accent = CACHE_READ_COLOR,
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.large,
        elevation = AppElevation.dialog,
        contentPadding = 16.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state.profileLabel != null) {
                Text(
                    text = state.profileLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = CACHE_READ_COLOR,
                    fontWeight = FontWeight.SemiBold
                )
            }
            LiveBadge(language = language)
            Text(
                text = CliSessionsLabels.lastChange(
                    instantLabel = state.lastChangedAt?.let { instant -> formatInstant(instant) },
                    language = language
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(6.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column {
                Text(
                    text = CliSessionsLabels.sessionCount(state.sessions.size, language),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                // O veredito por sessão está na linha, mas some da vista assim que
                // a lista rola. Aqui ele responde de uma vez se há sessão pedindo
                // /compact, sem varrer a lista inteira.
                HealthTallyText(tally = state.healthTally, language = language)
                Text(
                    text = CliSessionsLabels.estimatedCostNotice(language),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column {
                Text(
                    text = formatQuantity(state.totalTokens),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = CACHE_READ_COLOR
                )
                Text(
                    text = CliSessionsLabels.columnTokens(language),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // Sem a composição o total parece volume de conteúdo, quando é
                // dominado por cache lido: cada turno relê o contexto inteiro.
                Text(
                    text = CliSessionsLabels.tokensBreakdown(
                        inputTokens = state.totalInputTokens,
                        outputTokens = state.totalOutputTokens,
                        cacheReadTokens = state.totalCacheReadTokens,
                        cacheWriteTokens = state.totalCacheWriteTokens,
                        language = language
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column {
                Text(
                    text = if (state.isTotalCostComplete) {
                        formatMicrosUsdShort(state.totalCostMicros)
                    } else {
                        "${formatMicrosUsdShort(state.totalCostMicros)}+"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = INPUT_COLOR
                )
                Text(
                    text = if (state.range == CliSessionRange.ALL) {
                        CliSessionsLabels.estimatedTotal(language)
                    } else {
                        CliSessionsLabels.estimatedTotalInRange(
                            range = state.range,
                            endsAt = state.rangeEndsAt,
                            isAnchored = state.rangeAnchored,
                            language = language
                        )
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Só aparece quando há tempo medido: uma coluna com "—" no cabeçalho
            // ocuparia espaço para não dizer nada.
            val activeMillis = state.totalActiveMillis
            if (activeMillis != null && activeMillis > 0L) {
                Column {
                    Text(
                        text = formatActiveTime(activeMillis),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = OUTPUT_COLOR
                    )
                    Text(
                        text = CliSessionsLabels.activeTime(language),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Linha própria: junto das métricas, a quebra do FlowRow dependia da
        // largura dos números, então os chips mudavam de lugar conforme os dados.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (entry in CliSessionRange.entries) {
                FilterChip(
                    selected = state.range == entry,
                    onClick = { onSelectRange(entry) },
                    label = { Text(CliSessionsLabels.rangeLabel(entry, language)) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Abas depois dos chips de janela: a janela vale para as duas leituras,
        // então trocá-la é a escolha de fora e a aba é a de dentro.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = state.view == CliSessionsView.SESSIONS,
                onClick = { onSelectView(CliSessionsView.SESSIONS) },
                label = { Text(BreakdownLabels.tabSessions(language)) },
                modifier = Modifier.testTag(TAB_SESSIONS_TAG)
            )
            FilterChip(
                selected = state.view == CliSessionsView.BREAKDOWN,
                onClick = { onSelectView(CliSessionsView.BREAKDOWN) },
                label = { Text(BreakdownLabels.tabBreakdown(language)) },
                modifier = Modifier.testTag(TAB_BREAKDOWN_TAG)
            )

            // A exportação segue a aba aberta e a janela escolhida: exportar um
            // recorte diferente do que está na tela seria surpresa.
            TextButton(
                onClick = { onExport(UsageExportFormat.CSV) },
                modifier = Modifier.testTag(EXPORT_CSV_TAG)
            ) {
                Text(ExportLabels.exportCsv(language))
            }
            TextButton(
                onClick = { onExport(UsageExportFormat.JSON) },
                modifier = Modifier.testTag(EXPORT_JSON_TAG)
            ) {
                Text(ExportLabels.exportJson(language))
            }
            // O relatório não segue a aba: ele é o recorte inteiro da janela, com
            // sessões e resumo juntos. Seguir a aba daria dois PDFs pela metade.
            TextButton(
                onClick = onExportReport,
                modifier = Modifier.testTag(EXPORT_PDF_TAG)
            ) {
                Text(ExportLabels.exportPdf(language))
            }
        }

        val exportOutcome = state.exportOutcome
        if (exportOutcome != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = exportOutcomeMessage(exportOutcome, language),
                style = MaterialTheme.typography.labelSmall,
                color = if (exportOutcome is CliExportOutcome.Failed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

/** Sinal de que a tela se atualiza sozinha — o botão de atualizar não existe mais. */
@Composable
internal fun LiveBadge(language: AppLanguage) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(AppShapes.small)
                .background(CACHE_READ_COLOR)
        )
        Text(
            text = CliSessionsLabels.live(language),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = CACHE_READ_COLOR
        )
    }
}

/**
 * Linha de sessão da lista.
 *
 * `internal` porque o modal de time a reaproveita ao expandir um integrante: a
 * sessão de um colega tem de ser lida exatamente como a sessão da própria
 * máquina, com as mesmas colunas e o mesmo veredito de saturação.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CliSessionRow(
    session: CliSessionSummary,
    language: AppLanguage,
    onOpen: () -> Unit,
    /** `false` no modal do time: o transcript daquela sessão não está nesta máquina. */
    isLocalSession: Boolean = true,
    /** Ação destrutiva opcional; ausente nas listas locais e para não administradores. */
    onRemove: (() -> Unit)? = null,
    removeButtonTag: String? = null
) {
    val status = session.contextStatus
    val statusColor = healthColor(status.health)

    DepthSurface(
        accent = statusColor,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        glowAlpha = 0.16f,
        contentPadding = 14.dp
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.width(170.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(AppShapes.small)
                        .background(statusColor)
                )
                // Com peso, o botão de copiar cabe sempre: a coluna cede espaço em
                // vez de empurrá-lo para fora da largura fixa da célula.
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = shortSessionId(session.sessionId),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formatInstant(session.lastTs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // O clique do botão é consumido por ele: copiar não abre o detalhe.
                CopySessionCommandButton(
                    sessionId = session.sessionId,
                    language = language,
                    isLocalSession = isLocalSession
                )
            }

            Column(modifier = Modifier.width(140.dp)) {
                Text(
                    text = session.projectName ?: "—",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = CliSessionsLabels.turnsLabel(session.turnCount, language),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Larguras fixas: sem elas cada linha dimensiona pelo próprio número e
            // as colunas deixam de alinhar entre as sessões.
            MetricText(
                label = CliSessionsLabels.columnTokens(language),
                value = formatQuantity(session.totalTokens),
                modifier = Modifier.width(106.dp)
            )

            Column(modifier = Modifier.width(84.dp)) {
                MetricText(CliSessionsLabels.columnCache(language), formatPercent(session.cacheHitRate))
                Spacer(modifier = Modifier.height(4.dp))
                MeterBar(fraction = session.cacheHitRate, color = CACHE_READ_COLOR, height = 4.dp)
            }

            MetricText(
                label = CliSessionsLabels.columnCost(language),
                value = if (session.isCostComplete) {
                    formatMicrosUsd(session.costMicros)
                } else {
                    "${formatMicrosUsd(session.costMicros)}+"
                },
                valueColor = INPUT_COLOR,
                modifier = Modifier.width(96.dp)
            )

            // Tempo de trabalho, não duração: as pausas acima de cinco minutos
            // ficam de fora. Sem medida e sem intervalo saem os dois como "—" —
            // "0min" seria lido como sessão instantânea.
            MetricText(
                label = CliSessionsLabels.activeTime(language),
                value = session.activeMillis
                    ?.takeIf { millis -> millis > 0L }
                    ?.let { millis -> formatActiveTime(millis) }
                    ?: "—",
                modifier = Modifier.width(72.dp)
            )

            // O veredito que antes exigia abrir o detalhe. Vem com o número que o
            // gerou: status sem a evidência não dá para conferir nem para confiar.
            Column(modifier = Modifier.width(210.dp)) {
                Text(
                    text = CliSessionsLabels.healthShort(status.health, language),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = statusColor
                )
                Text(
                    text = CliSessionsLabels.healthReason(
                        saturationLabel = status.contextSaturation?.let { value -> formatPercent(value) },
                        nextCostLabel = formatMicrosUsd(status.nextInteractionCostMicros),
                        language = language
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (onRemove != null) {
                IconButton(
                    onClick = onRemove,
                    modifier = if (removeButtonTag != null) {
                        Modifier.testTag(removeButtonTag)
                    } else {
                        Modifier
                    }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = TeamUsageLabels.removeSession(language),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

// ----------------------------------------------------------------------------
// Detalhe
// ----------------------------------------------------------------------------

@Composable
private fun CliSessionDetailPane(
    detail: CliSessionDetailUiState,
    language: AppLanguage,
    advancedExpanded: Boolean,
    glossaryExpanded: Boolean,
    onCloseDetail: () -> Unit,
    onToggleAdvanced: () -> Unit,
    onToggleGlossary: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onCloseDetail) {
                Text(CliSessionsLabels.back(language))
            }
            Text(
                text = shortSessionId(detail.sessionId),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            CopySessionCommandButton(
                sessionId = detail.sessionId,
                language = language,
                showLabel = true
            )
        }

        when (detail) {
            is CliSessionDetailUiState.Loading -> CenteredMessage(CliSessionsLabels.loading(language))
            is CliSessionDetailUiState.Error -> CenteredMessage(detail.message)
            is CliSessionDetailUiState.Ready -> CliSessionDetailBody(
                detail = detail.result.detail,
                analytics = detail.result.analytics,
                language = language,
                advancedExpanded = advancedExpanded,
                glossaryExpanded = glossaryExpanded,
                onToggleAdvanced = onToggleAdvanced,
                onToggleGlossary = onToggleGlossary
            )
        }
    }
}

@Composable
private fun CliSessionDetailBody(
    detail: CliSessionDetail,
    analytics: CliSessionAnalytics,
    language: AppLanguage,
    advancedExpanded: Boolean,
    glossaryExpanded: Boolean,
    onToggleAdvanced: () -> Unit,
    onToggleGlossary: () -> Unit
) {
    val scrollState = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                // Mesma razão da lista: a barra flutua sobre o conteúdo.
                .padding(end = SCROLLBAR_GUTTER),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CliSessionDetailSections(
                detail = detail,
                analytics = analytics,
                language = language,
                advancedExpanded = advancedExpanded,
                glossaryExpanded = glossaryExpanded,
                onToggleAdvanced = onToggleAdvanced,
                onToggleGlossary = onToggleGlossary
            )
        }

        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(scrollState),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .testTag(DETAIL_SCROLLBAR_TAG)
        )
    }
}

/**
 * As seções do detalhe, emitidas direto no `Column` rolável do chamador.
 *
 * Divididas em duas camadas. A de cima responde à pergunta que traz o usuário
 * aqui — *dá para continuar nesta sessão?* — com o veredito, a identificação e
 * quatro números. A de baixo, recolhida, guarda a apuração: a composição dos
 * tokens, a distribuição do custo e os gráficos por turno.
 *
 * Nada foi removido na divisão; a camada de baixo é a mesma de antes.
 *
 * `internal` porque o modal de time monta o mesmo painel para a sessão de um
 * colega: dois detalhes com a mesma anatomia não podem ter duas implementações.
 */
@Composable
internal fun CliSessionDetailSections(
    detail: CliSessionDetail,
    analytics: CliSessionAnalytics,
    language: AppLanguage,
    advancedExpanded: Boolean,
    glossaryExpanded: Boolean,
    onToggleAdvanced: () -> Unit,
    onToggleGlossary: () -> Unit,
    /**
     * Aviso de que os turnos não vieram — servidor de time anterior à rota de
     * detalhe. Preenchido, as seções que dependem de turno **não** são compostas:
     * um gráfico vazio se leria como sessão sem atividade, e a distribuição de
     * custo estimada a partir do modelo predominante seria número inventado.
     */
    missingTurnsNotice: String? = null
) {
    val summary = detail.summary

    SessionHealthBanner(analytics = analytics, language = language)

    SessionMetadataCard(summary = summary, language = language)

    if (missingTurnsNotice != null) {
        NoticeText(missingTurnsNotice, MaterialTheme.colorScheme.error)
    }

    // Integridade do dado não é detalhe avançado: se o custo está incompleto,
    // todo número desta tela está incompleto.
    if (summary.stale) {
        NoticeText(CliSessionsLabels.staleNotice(language), MaterialTheme.colorScheme.error)
    }
    if (!analytics.isCostComplete) {
        NoticeText(
            CliSessionsLabels.unpricedNotice(analytics.unpricedTurnCount, language),
            MaterialTheme.colorScheme.error
        )
    }
    if (analytics.sidechainTurnCount > 0) {
        NoticeText(
            CliSessionsLabels.sidechainNotice(analytics.sidechainTurnCount, language),
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    SessionSummaryRow(summary = summary, analytics = analytics, language = language)

    if (missingTurnsNotice == null) {
        DetailSection(
            title = CliSessionsLabels.contextPerTurnChart(language),
            accent = CACHE_READ_COLOR,
            // Duas dúvidas de uma vez: o que a curva mede e o que o ▼ marca.
            help = listOf(GlossaryTerm.CONTEXT_PER_TURN, GlossaryTerm.COMPACTION),
            language = language
        ) {
            TurnSeriesChart(
                series = listOf(
                    TurnSeries(
                        label = CliSessionsLabels.chartContextLegend(language),
                        values = analytics.contextPerTurn,
                        color = CACHE_READ_COLOR,
                        binMode = BinMode.LAST
                    )
                ),
                height = DETAIL_CHART_HEIGHT,
                valueFormatter = { value -> formatQuantity(value) },
                highlightDrops = true
            )
        }

        AdvancedDisclosure(
            expanded = advancedExpanded,
            language = language,
            onToggle = onToggleAdvanced
        ) {
            SessionAdvancedSections(
                summary = summary,
                analytics = analytics,
                language = language
            )
        }
    }

    GlossaryPanel(
        expanded = glossaryExpanded,
        language = language,
        onToggle = onToggleGlossary
    )
}

/**
 * Os quatro números que decidem se vale continuar: quanto já custou, quanto
 * volume passou, quanto disso o cache absorveu e quanto da janela do modelo já
 * foi ocupada.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SessionSummaryRow(
    summary: CliSessionSummary,
    analytics: CliSessionAnalytics,
    language: AppLanguage
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // O custo vem do resumo, e não da apuração por turno: é o mesmo número
        // que a linha da lista mostra para esta sessão, e existe mesmo quando os
        // turnos não estão disponíveis. A distribuição por componente, que só o
        // turno prova, continua saindo do `costBreakdown`, no bloco Avançado.
        MetricCard(
            label = CliSessionsLabels.columnCost(language),
            value = formatMicrosUsd(summary.costMicros),
            accent = INPUT_COLOR,
            help = GlossaryTerm.ESTIMATED_COST,
            language = language
        )
        MetricCard(
            label = CliSessionsLabels.columnTokens(language),
            value = formatQuantity(summary.totalTokens),
            accent = CACHE_READ_COLOR,
            help = GlossaryTerm.TOTAL_TOKENS,
            language = language
        )
        MetricCard(
            label = CliSessionsLabels.cacheHitRate(language),
            value = formatPercent(analytics.cacheHitRate),
            accent = CACHE_READ_COLOR,
            help = GlossaryTerm.CACHE_HIT_RATE,
            language = language
        )
        MetricCard(
            label = CliSessionsLabels.saturation(language),
            value = analytics.contextSaturation?.let { value -> formatPercent(value) } ?: "—",
            accent = healthColor(analytics.health),
            help = GlossaryTerm.CONTEXT_WINDOW,
            language = language
        )
        // Só aparece quando há intervalo para medir: numa sessão de um turno
        // "0min" seria lido como sessão instantânea, e não como não medida.
        if (analytics.activeTimeMillis > 0L) {
            MetricCard(
                label = CliSessionsLabels.activeTime(language),
                value = formatActiveTime(analytics.activeTimeMillis),
                accent = OUTPUT_COLOR,
                language = language
            )
        }
    }
}

/** Tudo o que estava na tela antes da divisão e que não cabe no resumo. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SessionAdvancedSections(
    summary: CliSessionSummary,
    analytics: CliSessionAnalytics,
    language: AppLanguage
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        MetricCard(
            label = CliSessionsLabels.input(language),
            value = formatQuantity(summary.inputTokens),
            accent = INPUT_COLOR,
            language = language
        )
        MetricCard(
            label = CliSessionsLabels.output(language),
            value = formatQuantity(summary.outputTokens),
            accent = OUTPUT_COLOR,
            language = language
        )
        MetricCard(
            label = CliSessionsLabels.cacheRead(language),
            value = formatQuantity(summary.cacheReadTokens),
            accent = CACHE_READ_COLOR,
            help = GlossaryTerm.CACHE_READ,
            language = language
        )
        MetricCard(
            label = CliSessionsLabels.cacheWrite(language),
            value = formatQuantity(summary.cacheWriteTokens),
            accent = CACHE_WRITE_COLOR,
            help = GlossaryTerm.CACHE_WRITE,
            language = language
        )
    }

    DetailSection(
        title = CliSessionsLabels.cacheHitRate(language),
        accent = CACHE_READ_COLOR,
        trailing = formatPercent(analytics.cacheHitRate),
        help = listOf(GlossaryTerm.CACHE_HIT_RATE),
        language = language
    ) {
        MeterBar(fraction = analytics.cacheHitRate, color = CACHE_READ_COLOR)
    }

    DetailSection(
        title = CliSessionsLabels.costDistribution(language),
        accent = INPUT_COLOR,
        trailing = formatMicrosUsd(analytics.costBreakdown.totalMicros),
        help = listOf(GlossaryTerm.COST_DISTRIBUTION),
        language = language
    ) {
        CostDistributionBar(analytics = analytics)
        Spacer(modifier = Modifier.height(8.dp))
        CostDistributionLegend(analytics = analytics, language = language)
    }

    DetailSection(
        title = CliSessionsLabels.savings(language),
        accent = SAVINGS_COLOR,
        trailing = formatMicrosUsd(analytics.cacheSavingsMicros),
        help = listOf(GlossaryTerm.SAVINGS),
        language = language
    ) {
        NoticeText(CliSessionsLabels.savingsExplanation(language), MaterialTheme.colorScheme.onSurfaceVariant)
    }

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        MetricCard(
            label = CliSessionsLabels.averageContext(language),
            value = formatQuantity(analytics.averageContextPerTurn),
            accent = CACHE_READ_COLOR,
            help = GlossaryTerm.AVERAGE_CONTEXT,
            language = language
        )
        MetricCard(
            label = CliSessionsLabels.liveContext(language),
            value = formatQuantity(analytics.liveContextTokens),
            accent = CACHE_READ_COLOR,
            help = GlossaryTerm.LIVE_CONTEXT,
            language = language
        )
        MetricCard(
            label = CliSessionsLabels.nextInteraction(language),
            value = formatMicrosUsd(analytics.nextInteractionCostMicros),
            accent = INPUT_COLOR,
            help = GlossaryTerm.NEXT_INTERACTION,
            language = language
        )
    }

    DetailSection(
        title = CliSessionsLabels.cacheWritePerTurnChart(language),
        accent = CACHE_WRITE_COLOR,
        help = listOf(GlossaryTerm.CACHE_WRITE_PER_TURN),
        language = language
    ) {
        TurnSeriesChart(
            series = listOf(
                TurnSeries("5m", analytics.cacheWrite5mPerTurn, CACHE_WRITE_COLOR, BinMode.SUM),
                TurnSeries("1h", analytics.cacheWrite1hPerTurn, OUTPUT_COLOR, BinMode.SUM)
            ),
            stacked = true,
            height = DETAIL_CHART_HEIGHT,
            valueFormatter = { value -> formatQuantity(value) }
        )
    }

    DetailSection(
        title = CliSessionsLabels.costVersusSavingsChart(language),
        accent = SAVINGS_COLOR,
        help = listOf(GlossaryTerm.COST_VERSUS_SAVINGS),
        language = language
    ) {
        TurnSeriesChart(
            series = listOf(
                TurnSeries(
                    label = CliSessionsLabels.chartCostLegend(language),
                    values = analytics.cumulativeCostMicros,
                    color = INPUT_COLOR,
                    binMode = BinMode.MAX
                ),
                TurnSeries(
                    label = CliSessionsLabels.chartSavingsLegend(language),
                    values = analytics.cumulativeSavingsMicros,
                    color = SAVINGS_COLOR,
                    binMode = BinMode.MAX
                )
            ),
            height = DETAIL_CHART_HEIGHT,
            valueFormatter = { value -> formatMicrosUsdShort(value) }
        )
    }
}

/**
 * Cabeçalho clicável que revela [content].
 *
 * O conteúdo não é composto enquanto está fechado — são dois gráficos e sete
 * cards que não têm por que existir na árvore só para ficarem invisíveis.
 */
@Composable
internal fun AdvancedDisclosure(
    expanded: Boolean,
    language: AppLanguage,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DepthSurface(
            accent = NEUTRAL_ACCENT,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
            glowAlpha = QUIET_GLOW_ALPHA,
            contentPadding = 14.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (expanded) "▾" else "▸",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = CliSessionsLabels.advancedToggle(language),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = CliSessionsLabels.advancedHint(language),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (expanded) {
            content()
        }
    }
}

// ----------------------------------------------------------------------------
// Peças reutilizadas
// ----------------------------------------------------------------------------

/**
 * Recomendação sobre continuar ou recomeçar a sessão.
 *
 * O legado mostrava só um aviso binário de saturação e ele acendia em quase
 * metade das sessões. Aqui o status é graduado e sempre diz *por quê*: o alerta
 * sem o número que o gerou não dá para conferir nem para confiar.
 */
@Composable
internal fun SessionHealthBanner(analytics: CliSessionAnalytics, language: AppLanguage) {
    val health = analytics.health
    val accent = healthColor(health)

    DepthSurface(
        accent = accent,
        modifier = Modifier.fillMaxWidth(),
        glowAlpha = if (health == CliSessionHealth.HEALTHY) 0.14f else 0.26f,
        contentPadding = 14.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 4.dp, height = 40.dp)
                    .clip(AppShapes.small)
                    .background(accent)
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = CliSessionsLabels.healthTitle(health, language),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = accent
                    )
                    Text(
                        text = CliSessionsLabels.healthReason(
                            saturationLabel = analytics.contextSaturation?.let { value -> formatPercent(value) },
                            nextCostLabel = formatMicrosUsd(analytics.nextInteractionCostMicros),
                            language = language
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = CliSessionsLabels.healthAdvice(health, language),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/**
 * Onde a sessão rodou. A máquina não vem do transcript — o Claude Code não a
 * registra — e sim de quem indexou o arquivo, que é a mesma máquina no uso normal.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SessionMetadataCard(summary: CliSessionSummary, language: AppLanguage) {
    DepthSurface(
        accent = NEUTRAL_ACCENT,
        modifier = Modifier.fillMaxWidth(),
        glowAlpha = 0.10f,
        contentPadding = 14.dp
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricText(CliSessionsLabels.machine(language), summary.hostName ?: "—")
            MetricText(CliSessionsLabels.projectPath(language), summary.cwd ?: "—")
            MetricText(CliSessionsLabels.branch(language), summary.gitBranch ?: "—")
            MetricText(
                label = CliSessionsLabels.period(language),
                value = "${formatInstant(summary.firstTs)} → ${formatInstant(summary.lastTs)}"
            )
        }
    }
}

/**
 * "1 saturada · 2 em atenção" no cabeçalho, ou nada quando não há o que alertar.
 *
 * A cor é a do pior caso presente: um "em atenção" laranja ao lado de um
 * "saturada" vermelho diluiria o segundo.
 *
 * `internal` porque os dois modais têm o mesmo problema — o do time ainda pior,
 * já que lá o veredito vive dois níveis abaixo, dentro de um integrante recolhido.
 */
@Composable
internal fun HealthTallyText(tally: CliSessionHealthTally, language: AppLanguage) {
    val label = CliSessionsLabels.healthTally(tally, language) ?: return
    val accent = if (tally.saturated > 0) {
        healthColor(CliSessionHealth.SATURATED)
    } else {
        healthColor(CliSessionHealth.ATTENTION)
    }

    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = accent
    )
}

/**
 * Cor do veredito de saúde.
 *
 * [accents] tem default escuro para as telas que ainda não migraram continuarem
 * compilando com a cor de hoje. Quem está dentro de uma composição deve passar
 * `AppAccents.current` — é o que faz o veredito continuar legível no tema claro.
 */
internal fun healthColor(
    health: CliSessionHealth,
    accents: AppAccents = darkAppAccents
): Color {
    return when (health) {
        CliSessionHealth.HEALTHY -> accents.cacheRead
        CliSessionHealth.ATTENTION -> accents.cacheWrite
        CliSessionHealth.SATURATED -> accents.saturated
    }
}

@Composable
internal fun DetailSection(
    title: String,
    accent: Color,
    // Sem default: um `help` acompanhado de um idioma implícito renderizaria
    // português no meio da tela em inglês, e o compilador não reclamaria.
    language: AppLanguage,
    trailing: String? = null,
    help: List<GlossaryTerm> = emptyList(),
    content: @Composable () -> Unit
) {
    DepthSurface(
        accent = accent,
        modifier = Modifier.fillMaxWidth(),
        glowAlpha = QUIET_GLOW_ALPHA,
        contentPadding = 14.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(width = 3.dp, height = 14.dp)
                        .clip(AppShapes.small)
                        .background(accent)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (help.isNotEmpty()) {
                    HelpDot(terms = help, language = language)
                }
            }
            if (trailing != null) {
                Text(
                    text = trailing,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = accent
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        content()
    }
}

@Composable
internal fun MetricCard(
    label: String,
    value: String,
    accent: Color,
    // Mesma razão de [DetailSection]: idioma implícito vaza português.
    language: AppLanguage,
    footer: String? = null,
    help: GlossaryTerm? = null
) {
    DepthSurface(
        accent = accent,
        modifier = Modifier.width(168.dp),
        glowAlpha = QUIET_GLOW_ALPHA,
        contentPadding = 12.dp
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // O rótulo cede a largura ao `?`: truncado ele ainda se lê, mas o
                // ícone empurrado para fora do card some.
                modifier = Modifier.weight(1f, fill = false)
            )
            if (help != null) {
                HelpDot(terms = listOf(help), language = language)
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (footer != null) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = footer,
                style = MaterialTheme.typography.labelSmall,
                color = accent
            )
        }
    }
}

/**
 * Só o valor, sem a legenda.
 *
 * Existe para listas que carregam as legendas numa faixa de cabeçalho: repeti-las
 * dentro de cada linha dobra o texto da lista sem acrescentar informação. Fica ao
 * lado de [MetricText] e com a mesma tipografia de valor de propósito — as duas
 * anatomias têm de cair na mesma linha de base quando aparecem lado a lado.
 */
@Composable
internal fun MetricValue(
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    Text(
        text = value,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.SemiBold,
        color = valueColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

/** Legenda de coluna, na faixa de cabeçalho de uma lista tabular. */
@Composable
internal fun ColumnHeaderLabel(
    label: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

// `internal`, e não `private`, porque a tela de time reaproveita estes blocos:
// duas listas com a mesma anatomia não podem ter duas implementações de célula.
@Composable
internal fun MetricText(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = valueColor
        )
    }
}

@Composable
internal fun NoticeText(message: String, color: Color) {
    Text(text = message, style = MaterialTheme.typography.labelSmall, color = color)
}

/**
 * O `?` ao lado de um título, com a definição no hover.
 *
 * A tooltip é persistente (ver `HoverTooltipBox`): explicação de três linhas não
 * se lê no tempo de uma tooltip que some sozinha.
 */
@Composable
internal fun HelpDot(terms: List<GlossaryTerm>, language: AppLanguage) {
    val entries = terms.map { term -> CliSessionsGlossary.entry(term, language) }
    val first = entries.first()

    HoverTooltipBox(
        title = first.title,
        subtitle = first.explanation,
        // Os termos seguintes entram como métricas para não empilhar tooltips:
        // um gráfico pode carregar duas dúvidas, e são duas linhas, não dois `?`.
        metrics = entries.drop(1).map { entry -> TooltipMetric(entry.title, entry.explanation) }
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(AppShapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "?",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * "Como ler esta tela": o glossário inteiro, recolhido.
 *
 * Existe porque o `?` só responde a quem já sabe onde tem dúvida. Quem não
 * conhece o vocabulário precisa de um lugar único para lê-lo de ponta a ponta.
 */
@Composable
internal fun GlossaryPanel(
    expanded: Boolean,
    language: AppLanguage,
    onToggle: () -> Unit
) {
    DepthSurface(
        accent = NEUTRAL_ACCENT,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
        glowAlpha = QUIET_GLOW_ALPHA,
        contentPadding = 14.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (expanded) "▾" else "▸",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = CliSessionsLabels.glossaryTitle(language),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (!expanded) {
            return@DepthSurface
        }

        Spacer(modifier = Modifier.height(12.dp))

        for (term in CliSessionsGlossary.readingOrder) {
            val entry = CliSessionsGlossary.entry(term, language)
            Text(
                text = entry.title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = entry.explanation,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
internal fun MeterBar(
    fraction: Double,
    color: Color,
    height: androidx.compose.ui.unit.Dp = 10.dp
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(AppShapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0.0, 1.0).toFloat())
                .fillMaxHeight()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(color.copy(alpha = 0.7f), color)
                    )
                )
        )
    }
}

@Composable
internal fun CostDistributionBar(analytics: CliSessionAnalytics) {
    val breakdown = analytics.costBreakdown

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(AppShapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        for ((value, color) in costSegments(analytics)) {
            val weight = breakdown.fractionOf(value).toFloat()
            if (weight <= 0f) {
                continue
            }
            Box(
                modifier = Modifier
                    .weight(weight)
                    .fillMaxHeight()
                    .background(Brush.verticalGradient(listOf(color, color.copy(alpha = 0.72f))))
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CostDistributionLegend(analytics: CliSessionAnalytics, language: AppLanguage) {
    val breakdown = analytics.costBreakdown
    val labels = listOf(
        CliSessionsLabels.input(language),
        CliSessionsLabels.output(language),
        CliSessionsLabels.cacheRead(language),
        CliSessionsLabels.cacheWrite(language)
    )

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        costSegments(analytics).forEachIndexed { index, (value, color) ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(8.dp).clip(AppShapes.small).background(color))
                Text(
                    text = "${labels[index]} ${formatPercent(breakdown.fractionOf(value))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun costSegments(analytics: CliSessionAnalytics): List<Pair<Long, Color>> {
    val breakdown = analytics.costBreakdown
    return listOf(
        breakdown.inputMicros to INPUT_COLOR,
        breakdown.outputMicros to OUTPUT_COLOR,
        breakdown.cacheReadMicros to CACHE_READ_COLOR,
        breakdown.cacheWriteMicros to CACHE_WRITE_COLOR
    )
}

package com.usagemonitor.presentation.ui

import com.usagemonitor.presentation.ui.components.appItemMotion
import com.usagemonitor.presentation.ui.components.AppStateCrossfade
import com.usagemonitor.presentation.ui.components.AppModalRevealScope
import com.usagemonitor.presentation.ui.components.rememberSettledRevealKey
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.UsageExportFormat
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CliSessionRange
import com.usagemonitor.presentation.ui.components.AppLoadingState
import com.usagemonitor.presentation.ui.components.AppErrorState
import com.usagemonitor.presentation.ui.components.AppEmptyState
import com.usagemonitor.presentation.ui.components.AppButton
import com.usagemonitor.domain.entity.combinedThroughput
import com.usagemonitor.presentation.ui.components.AppMetricBlock
import com.usagemonitor.presentation.ui.components.AppSegment
import com.usagemonitor.presentation.ui.components.AppSegmentedControl
import com.usagemonitor.presentation.ui.components.AppTab
import com.usagemonitor.presentation.ui.components.AppTabs
import com.usagemonitor.presentation.ui.components.AppToolbar
import com.usagemonitor.presentation.ui.components.AppWindowScaffold
import com.usagemonitor.presentation.ui.theme.AppSpacing
import com.usagemonitor.presentation.viewmodel.CliExportOutcome
import com.usagemonitor.presentation.viewmodel.CliSessionsUiState
import com.usagemonitor.presentation.viewmodel.CliSessionsView
import com.usagemonitor.presentation.viewmodel.CliSessionsViewModel

// A codificação de cor (custo em azul, tokens em verde, cache gravado em
// laranja, economia em ciano) é a mesma da tela de time — o painel de detalhe é
// compartilhado entre as duas — e vive em `AppAccents`, com uma variante por
// tema. Esta tela lia `INPUT_COLOR`/`OUTPUT_COLOR`/`CACHE_READ_COLOR`/
// `CACHE_WRITE_COLOR`/`SAVINGS_COLOR`, constantes de topo de arquivo amarradas à
// variante escura — resolvidas uma vez por processo, não pelo tema em vigor.
// Contra a `surface` clara, `#4CAF50` (cache/tokens) dava 2,64:1, abaixo dos
// 4,5:1 exigidos (`AppAccentsContrastTest`). Todo uso nesta tela agora lê
// `AppAccents.current` em vez das constantes cruas.

/** Faixa reservada à barra de rolagem, que flutua sobre o conteúdo. */
internal val SCROLLBAR_GUTTER = 12.dp

/**
 * Largura de um bloco de métrica.
 *
 * Fixa e igual para todos: é ela que faz a fileira alinhar. Com cada bloco
 * medindo pelo próprio conteúdo, o de tokens ficava três vezes mais largo que o
 * de sessões e a grade deixava de ser grade.
 */
internal val METRIC_BLOCK_WIDTH = 168.dp

internal const val LIST_SCROLLBAR_TAG = "cliSessionsListScrollbar"
internal const val DETAIL_SCROLLBAR_TAG = "cliSessionsDetailScrollbar"
const val TAB_SESSIONS_TAG = "cliSessionsTabSessions"
const val TAB_BREAKDOWN_TAG = "cliSessionsTabBreakdown"
const val EXPORT_CSV_TAG = "cliSessionsExportCsv"
const val EXPORT_JSON_TAG = "cliSessionsExportJson"
const val EXPORT_PDF_TAG = "cliSessionsExportPdf"

/**
 * Bloco de total de sessões do cabeçalho.
 *
 * O número deixou de vir emendado à palavra ("1 sessão") e virou valor de um
 * bloco com rótulo próprio, então não há mais um texto único que prove a
 * contagem: a âncora é o bloco, e o assert procura o número dentro dele.
 */
const val TOTAL_SESSIONS_BLOCK_TAG = "cliSessionsTotalSessions"

/**
 * Âncoras da lista de sessões.
 *
 * A linha é hoje um card com células de largura fixa e vira uma linha de tabela.
 * O que os testes usam para encontrá-la é o id truncado em 8 caracteres, que
 * também aparece dentro do detalhe e no comando de retomada — texto que
 * identifica a sessão, não a linha.
 */
const val CLI_SESSION_ROW_TAG_PREFIX = "cliSessionRow:"

/** O id completo, não o truncado: é ele que identifica a sessão sem ambiguidade. */
fun cliSessionRowTag(sessionId: String): String = "$CLI_SESSION_ROW_TAG_PREFIX$sessionId"

/** Faixa de legendas da lista de sessões, na tela da máquina e no bloco do time. */
const val CLI_SESSION_COLUMN_HEADER_TAG = "cliSessionColumnHeader"

/** Marca de "sem resposta" de uma linha; ausente quando a sessão respondeu. */
const val CLI_SESSION_STALLED_TAG_PREFIX = "cliSessionStalled:"

fun cliSessionStalledTag(sessionId: String): String = "$CLI_SESSION_STALLED_TAG_PREFIX$sessionId"

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
        AppStateCrossfade(state, key = { current -> if (current is CliSessionsUiState.Success) "success:${current.detail != null}" else current::class }) { state ->
    when (state) {
                is CliSessionsUiState.Loading -> AppLoadingState(CliSessionsLabels.loading(language))

                is CliSessionsUiState.Error -> AppErrorState(state.message)

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
    // Aviso de recarga à esquerda e carimbo da última alteração à direita, como
    // no protótipo. Os dois eram linhas no topo, empurrando a lista para baixo a
    // cada tique — e o aviso de recarga aparece e some, então ali ele deslocava
    // tudo o que estava sendo lido.
    AppWindowScaffold(
        modifier = Modifier.fillMaxSize(),
        contentPadding = AppSpacing.lg,
        spacing = AppSpacing.md,
        statusBar = {
            if (state.isRefreshing) {
                Text(
                    text = BreakdownLabels.refreshing(language),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.testTag(REFRESHING_NOTICE_TAG)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = CliSessionsLabels.lastChange(
                    instantLabel = state.lastChangedAt?.let { instant -> formatInstant(instant) },
                    language = language
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    ) {
        // Aba nova nasce num escopo que toca o E9; a faixa troca a chave só
        // quando a leitura nova chega, e aí os totais do cabeçalho tocam junto.
        val revealKey = rememberSettledRevealKey(state.range, settled = !state.isRefreshing)
        AppModalRevealScope(replayKey = revealKey) {
            CliSessionsHeader(
                state = state,
                language = language,
                onSelectRange = onSelectRange,
                onSelectView = onSelectView,
                onExport = onExport,
                onExportReport = onExportReport
            )
        }

        if (state.indexWarning != null) {
            NoticeText(state.indexWarning, MaterialTheme.colorScheme.error)
        }

        if (state.view == CliSessionsView.BREAKDOWN) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                AppModalRevealScope(replayKey = revealKey) {
                    CliUsageBreakdownPane(
                        breakdown = state.breakdown,
                        errorMessage = state.breakdownError,
                        language = language,
                        budget = state.budget,
                        accountCredits = state.accountCredits
                    )
                }
            }
            return@AppWindowScaffold
        }

        if (state.sessions.isEmpty()) {
            AppEmptyState(CliSessionsLabels.emptyInRange(state.range, state.rangeAnchored, language))
            return@AppWindowScaffold
        }

        // Voltar para a aba Sessões compõe a lista de novo: o escopo nasce e toca o
        // E9; a faixa nova toca quando a leitura dela chega.
        AppModalRevealScope(replayKey = revealKey) {
            CliSessionsTable(state, language, onOpenSession, Modifier.weight(1f))
        }
    }
}

@Composable
private fun CliSessionsTable(
    state: CliSessionsUiState.Success,
    language: AppLanguage,
    onOpenSession: (String) -> Unit,
    listModifier: Modifier
) {
    // Fora da `LazyColumn`, e não `stickyHeader`: a faixa é do painel, não da
    // rolagem, e é o mesmo desenho que a tela de presença já usa.
    CliSessionColumnHeader(
        language = language,
        modifier = Modifier.padding(end = SCROLLBAR_GUTTER)
    )

    Box(modifier = listModifier.fillMaxWidth()) {
        val listState = rememberLazyListState()

        LazyColumn(
            state = listState,
            // A barra fica por cima da área de conteúdo; sem a folga à direita
            // ela cobriria a borda do painel, que ocupa a largura inteira.
            //
            // Sem espaço entre itens: a linha traz a própria divisória, e um
            // vão entre elas desfaria a leitura de tabela.
            modifier = Modifier.fillMaxSize().padding(end = SCROLLBAR_GUTTER)
        ) {
            items(items = state.sessions, key = { session -> session.sessionId }) { session ->
                Box(modifier = appItemMotion()) {
                    CliSessionRow(
                        session = session,
                        language = language,
                        onOpen = { onOpenSession(session.sessionId) },
                        stalledForMillis = state.stalledSessions[session.sessionId]
                    )
                }
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
    // Sem painel em volta: o corpo da janela já é a superfície, e um retângulo
    // com borda envolvendo barra de controles, métricas e abas transformava o
    // cabeçalho inteiro num bloco só — que é o que o protótipo desenha solto.
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
    ) {
        // Abas **antes** das métricas, como no protótipo: a aba escolhe o que a
        // janela mostra, e os totais são conteúdo dela. Depois delas, os totais
        // pareciam pertencer só à aba de sessões.
        //
        // `Row` e não `FlowRow`: as abas levam `weight` para empurrar o resto para
        // a direita, e peso dentro de um `FlowRow` fica sem referência de largura.
        AppToolbar(spacing = AppSpacing.sm) {
            AppTabs(
                tabs = listOf(
                    AppTab(label = BreakdownLabels.tabSessions(language), testTag = TAB_SESSIONS_TAG),
                    AppTab(label = BreakdownLabels.tabBreakdown(language), testTag = TAB_BREAKDOWN_TAG)
                ),
                selectedIndex = if (state.view == CliSessionsView.BREAKDOWN) 1 else 0,
                onSelect = { index ->
                    onSelectView(
                        if (index == 1) CliSessionsView.BREAKDOWN else CliSessionsView.SESSIONS
                    )
                },
                modifier = Modifier.weight(1f)
            )

            if (state.profileLabel != null) {
                Text(
                    text = state.profileLabel,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // O carimbo da última alteração desceu para a barra de estado; aqui
            // fica só o selo de leitura ao vivo, que é estado do laço e não dado.
            LiveBadge(language = language)

            // A janela vale para as duas leituras, então trocá-la é a escolha de
            // fora e a aba é a de dentro — as duas na mesma faixa.
            AppSegmentedControl(
                options = CliSessionRange.entries.map { entry ->
                    AppSegment(label = CliSessionsLabels.rangeLabel(entry, language))
                },
                selectedIndex = CliSessionRange.entries.indexOf(state.range),
                onSelect = { index -> onSelectRange(CliSessionRange.entries[index]) }
            )

            // A exportação segue a aba aberta e a janela escolhida: exportar um
            // recorte diferente do que está na tela seria surpresa.
            AppButton(
                label = ExportLabels.exportCsv(language),
                onClick = { onExport(UsageExportFormat.CSV) },
                modifier = Modifier.testTag(EXPORT_CSV_TAG)
            )
            AppButton(
                label = ExportLabels.exportJson(language),
                onClick = { onExport(UsageExportFormat.JSON) },
                modifier = Modifier.testTag(EXPORT_JSON_TAG)
            )
            // O relatório não segue a aba: ele é o recorte inteiro da janela, com
            // sessões e resumo juntos. Seguir a aba daria dois PDFs pela metade.
            AppButton(
                label = ExportLabels.exportPdf(language),
                onClick = onExportReport,
                modifier = Modifier.testTag(EXPORT_PDF_TAG)
            )
        }

        // Blocos de métrica, não colunas de texto soltas: eram quatro pares
        // valor/rótulo flutuando sobre o mesmo painel, e o cabeçalho lia como
        // parágrafo. A borda de cada bloco é o que separa uma medida da outra.
        //
        // O rótulo vem em cima e o valor embaixo: numa fileira de quatro, o olho
        // varre os rótulos para achar o que procura, não os números.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            AppMetricBlock(
                label = CliSessionsLabels.columnSessions(language),
                value = state.sessions.size.toString(),
                // O veredito por sessão está na linha, mas some da vista assim
                // que a lista rola. Aqui ele responde de uma vez se há sessão
                // pedindo /compact, sem varrer a lista inteira.
                footer = CliSessionsLabels.healthTally(state.healthTally, language),
                footerColor = healthTallyColor(state.healthTally),
                modifier = Modifier.width(METRIC_BLOCK_WIDTH).testTag(TOTAL_SESSIONS_BLOCK_TAG)
            )

            AppMetricBlock(
                label = CliSessionsLabels.columnTokens(language),
                value = formatQuantity(state.totalTokens),
                modifier = Modifier.width(METRIC_BLOCK_WIDTH)
            )

            AppMetricBlock(
                label = CliSessionsLabels.columnCost(language),
                value = if (state.isTotalCostComplete) {
                    formatMicrosUsdShort(state.totalCostMicros)
                } else {
                    "${formatMicrosUsdShort(state.totalCostMicros)}+"
                },
                modifier = Modifier.width(METRIC_BLOCK_WIDTH)
            )

            // Só aparece quando há tempo medido: um bloco com "—" ocuparia
            // espaço para não dizer nada.
            val activeMillis = state.totalActiveMillis
            if (activeMillis != null && activeMillis > 0L) {
                AppMetricBlock(
                    label = CliSessionsLabels.activeTime(language),
                    value = formatActiveTime(activeMillis),
                    modifier = Modifier.width(METRIC_BLOCK_WIDTH)
                )
            }

            // Vazão do conjunto (total sobre total, #381); sem turno medido o
            // bloco não aparece, pelo mesmo motivo do tempo ativo.
            val throughput = state.sessions.map { session -> session.throughput }.combinedThroughput()
            if (throughput?.tokensPerSecond != null) {
                AppMetricBlock(
                    label = CliSessionsLabels.throughput(language),
                    value = formatThroughput(throughput),
                    footer = CliSessionsLabels.throughputFooter(language),
                    modifier = Modifier.width(METRIC_BLOCK_WIDTH)
                )
            }
        }

        // As qualificações longas ficam fora dos blocos: dentro deles, o footer
        // de tokens sozinho media três vezes a largura do bloco de sessões e a
        // fileira perdia o alinhamento que a grade de métricas existe para dar.
        //
        // Sem a composição o total parece volume de conteúdo, quando é dominado
        // por cache lido: cada turno relê o contexto inteiro.
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

        Text(
            text = CliSessionsLabels.estimatedCostNotice(language),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val exportOutcome = state.exportOutcome
        if (exportOutcome != null) {
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

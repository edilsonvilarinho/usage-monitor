package com.usagemonitor.presentation.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.usagemonitor.presentation.ui.components.AppLoadingState
import com.usagemonitor.presentation.ui.components.AppErrorState
import com.usagemonitor.presentation.ui.components.AppEmptyState
import com.usagemonitor.presentation.ui.components.AppBanner
import com.usagemonitor.presentation.ui.components.AppBorderWidth
import com.usagemonitor.presentation.ui.components.AppButton
import com.usagemonitor.presentation.ui.components.AppButtonTone
import com.usagemonitor.presentation.ui.components.AppCellValue
import com.usagemonitor.presentation.ui.components.AppColumnHeaderLabel
import com.usagemonitor.presentation.ui.components.AppColumnHeaderRow
import com.usagemonitor.presentation.ui.components.AppDataRow
import com.usagemonitor.presentation.ui.components.AppDataSurface
import com.usagemonitor.presentation.ui.components.AppDataSurfaceFlush
import com.usagemonitor.presentation.ui.components.AppIconButton
import com.usagemonitor.presentation.ui.components.AppMetricBlock
import com.usagemonitor.presentation.ui.components.AppProgressTrack
import com.usagemonitor.presentation.ui.components.AppSectionHeader
import com.usagemonitor.presentation.ui.components.AppSegment
import com.usagemonitor.presentation.ui.components.AppSegmentedControl
import com.usagemonitor.presentation.ui.components.AppStatusIndicator
import com.usagemonitor.presentation.ui.components.AppTab
import com.usagemonitor.presentation.ui.components.AppTabs
import com.usagemonitor.presentation.ui.components.AppToolbar
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.components.AppWindowScaffold
import com.usagemonitor.presentation.ui.components.BinMode
import com.usagemonitor.presentation.ui.components.CopySessionCommandButton
import com.usagemonitor.presentation.ui.components.HoverTooltipBox
import com.usagemonitor.presentation.ui.components.TooltipMetric
import com.usagemonitor.presentation.ui.components.TurnSeries
import com.usagemonitor.presentation.ui.components.TurnSeriesChart
import com.usagemonitor.presentation.ui.theme.AppAccents
import com.usagemonitor.presentation.ui.theme.AppShapes
import com.usagemonitor.presentation.ui.theme.AppSpacing
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

/** Mais baixo que o default de `TurnSeriesChart`: são vários numa página só. */
private val DETAIL_CHART_HEIGHT = 120.dp

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

// Larguras das colunas da lista de sessões, num lugar só: a faixa de legendas e
// as linhas têm de cair no mesmo x.
//
// O somatório não é livre e é ele que sustenta a faixa de cabeçalho. Com a janela
// em 960dp, as seis colunas mais o vão de 12dp entre elas dão 766; somados os
// 24dp de padding da linha, os 12 da barra de rolagem, os 32 do corpo da janela e
// os 26 do botão de remover do modo administrativo, sobram 874 — abaixo do piso
// da janela. Passar disso faria a linha quebrar, e uma faixa de legendas sobre
// linha quebrada promete um alinhamento que o conteúdo não cumpre.
//
// O veredito de saturação **não** é coluna: ele desceu para uma segunda linha da
// própria linha, com a razão que o gerou ao lado. Como coluna ele media 210dp e
// era o que estourava o orçamento.
private val SESSION_COLUMN_ID = 170.dp
private val SESSION_COLUMN_PROJECT = 130.dp
private val SESSION_COLUMN_TOKENS = 136.dp
private val SESSION_COLUMN_CACHE = 90.dp
private val SESSION_COLUMN_COST = 96.dp
private val SESSION_COLUMN_ACTIVE_TIME = 84.dp

/** Mesma pegada do `AppIconButton`, para o cabeçalho reservar a casa certa. */
private val SESSION_ACTION_SLOT = 26.dp

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
            return@AppWindowScaffold
        }

        if (state.sessions.isEmpty()) {
            AppEmptyState(CliSessionsLabels.emptyInRange(state.range, state.rangeAnchored, language))
            return@AppWindowScaffold
        }

        // Fora da `LazyColumn`, e não `stickyHeader`: a faixa é do painel, não da
        // rolagem, e é o mesmo desenho que a tela de presença já usa.
        CliSessionColumnHeader(
            language = language,
            modifier = Modifier.padding(end = SCROLLBAR_GUTTER)
        )

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
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
                    CliSessionRow(
                        session = session,
                        language = language,
                        onOpen = { onOpenSession(session.sessionId) },
                        stalledForMillis = state.stalledSessions[session.sessionId]
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

/**
 * Sinal de que a tela se atualiza sozinha — o botão de atualizar não existe mais.
 *
 * É [AppStatusIndicator], a única insígnia de estado do sistema, e não um ponto
 * próprio: ponto **e** palavra, e o tom saindo de [AppTone]. Ele desenhava o
 * ponto com `CACHE_READ_COLOR`, que é `darkAppAccents.cacheRead` congelado num
 * `val` de topo de arquivo — resolvido uma vez por processo, sem ler o tema em
 * vigor. No tema claro aquele verde dá 2,64:1 contra a `surface`, e a primitiva
 * o troca por `AppAccents.current.cacheRead`, que passa nos dois.
 */
@Composable
internal fun LiveBadge(language: AppLanguage) {
    AppStatusIndicator(
        label = CliSessionsLabels.live(language),
        tone = AppTone.OK
    )
}

/**
 * Linha de sessão da lista.
 *
 * `internal` porque o modal de time a reaproveita ao expandir um integrante: a
 * sessão de um colega tem de ser lida exatamente como a sessão da própria
 * máquina, com as mesmas colunas e o mesmo veredito de saturação.
 */
/**
 * Faixa de legendas da lista de sessões, uma vez para a lista inteira.
 *
 * `internal` porque o bloco de sessões do modal do time reaproveita a mesma
 * lista: duas faixas com as mesmas colunas divergiriam no primeiro ajuste.
 *
 * [hasActionColumn] reserva a casa do botão de remover do modo administrativo,
 * que fica fora do fluxo de colunas.
 */
@Composable
internal fun CliSessionColumnHeader(
    language: AppLanguage,
    modifier: Modifier = Modifier,
    hasActionColumn: Boolean = false
) {
    AppColumnHeaderRow(
        // Sem marcador na linha de sessão: a faixa começa onde a primeira célula
        // começa.
        startGutter = 0.dp,
        modifier = modifier.testTag(CLI_SESSION_COLUMN_HEADER_TAG)
    ) {
        AppColumnHeaderLabel(
            label = CliSessionsLabels.columnSession(language),
            modifier = Modifier.width(SESSION_COLUMN_ID)
        )
        AppColumnHeaderLabel(
            label = CliSessionsLabels.columnProject(language),
            modifier = Modifier.width(SESSION_COLUMN_PROJECT)
        )
        AppColumnHeaderLabel(
            label = CliSessionsLabels.columnTokens(language),
            modifier = Modifier.width(SESSION_COLUMN_TOKENS)
        )
        AppColumnHeaderLabel(
            label = CliSessionsLabels.columnCache(language),
            modifier = Modifier.width(SESSION_COLUMN_CACHE)
        )
        AppColumnHeaderLabel(
            label = CliSessionsLabels.columnCost(language),
            modifier = Modifier.width(SESSION_COLUMN_COST)
        )
        AppColumnHeaderLabel(
            label = CliSessionsLabels.activeTime(language),
            modifier = Modifier.width(SESSION_COLUMN_ACTIVE_TIME)
        )
        if (hasActionColumn) {
            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(SESSION_ACTION_SLOT))
        }
    }
}

/**
 * Uma sessão como linha de tabela.
 *
 * Era um card por sessão, com brilho de acento e 14dp de padding: numa janela de
 * cinco sessões a lista já pedia rolagem. Depois virou linha, mas com o rótulo
 * repetido dentro de cada célula — a concessão que a passada de agosto registrou,
 * porque as células somavam quase 1.000dp e a janela abre em 960.
 *
 * O que desfaz a concessão é o veredito sair do fluxo de colunas: ele media 210dp
 * e desceu para uma **segunda linha** da própria linha, junto da razão que o
 * gerou, que é como o protótipo desenha. Com ele fora, as seis colunas cabem, a
 * linha não quebra e a legenda pode viver uma vez só na faixa de cabeçalho.
 *
 * `Row` e não `FlowRow` justamente por isso: quebrar é o que a faixa de legendas
 * não admite.
 *
 * O status continua sendo **ponto e palavra** — cor sozinha não informa — e
 * continua vindo com o número que o gerou.
 */
@Composable
internal fun CliSessionRow(
    session: CliSessionSummary,
    language: AppLanguage,
    onOpen: () -> Unit,
    /**
     * `false` quando o transcript não está nesta máquina — a sessão de um colega
     * na lista do time. Ali a linha **não** oferece o botão de copiar: o
     * `--resume` cairia num seletor vazio, e a issue #102 pede que a sessão de
     * outro integrante não seja copiável.
     */
    isLocalSession: Boolean = true,
    /** Ação destrutiva opcional; ausente nas listas locais e para não administradores. */
    onRemove: (() -> Unit)? = null,
    removeButtonTag: String? = null,
    /** A lista tem coluna de ação; esta linha reserva a casa mesmo sem botão. */
    hasActionColumn: Boolean = onRemove != null,
    /**
     * Há quanto tempo o último pedido desta sessão está sem resposta; `null` é o
     * caso normal — respondeu, ou não foi possível avaliar.
     *
     * Sempre `null` na lista do time: a marca sai da cauda do transcript, que só
     * existe na máquina onde a sessão rodou.
     */
    stalledForMillis: Long? = null
) {
    val status = session.contextStatus
    val statusTone = healthTone(status.health)

    AppDataRow(
        modifier = Modifier.testTag(cliSessionRowTag(session.sessionId)),
        onClick = onOpen
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.width(SESSION_COLUMN_ID),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Com peso, o botão de copiar cabe sempre: a coluna cede
                    // espaço em vez de empurrá-lo para fora da largura fixa.
                    Column(modifier = Modifier.weight(1f)) {
                        // Identidade e o carimbo que a qualifica, não duas
                        // medidas: a legenda "Sessão" nomeia as duas linhas.
                        AppCellValue(value = shortSessionId(session.sessionId))
                        Text(
                            text = formatInstant(session.lastTs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    // O clique do botão é consumido por ele: copiar não abre o detalhe.
                    if (isLocalSession) {
                        CopySessionCommandButton(
                            sessionId = session.sessionId,
                            language = language
                        )
                    }
                }

                Column(modifier = Modifier.width(SESSION_COLUMN_PROJECT)) {
                    AppCellValue(value = session.projectName ?: "—")
                    Text(
                        text = CliSessionsLabels.turnsLabel(session.turnCount, language),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                AppCellValue(
                    value = formatQuantity(session.totalTokens),
                    modifier = Modifier.width(SESSION_COLUMN_TOKENS)
                )

                Column(modifier = Modifier.width(SESSION_COLUMN_CACHE)) {
                    AppCellValue(value = formatPercent(session.cacheHitRate))
                    Spacer(modifier = Modifier.height(AppSpacing.xs))
                    AppProgressTrack(fraction = session.cacheHitRate.toFloat(), tone = AppTone.OK)
                }

                AppCellValue(
                    value = if (session.isCostComplete) {
                        formatMicrosUsd(session.costMicros)
                    } else {
                        "${formatMicrosUsd(session.costMicros)}+"
                    },
                    modifier = Modifier.width(SESSION_COLUMN_COST)
                )

                // Tempo de trabalho, não duração: as pausas acima de cinco
                // minutos ficam de fora. Sem medida e sem intervalo sai o
                // travessão — "0min" seria lido como sessão instantânea.
                AppCellValue(
                    value = session.activeMillis
                        ?.takeIf { millis -> millis > 0L }
                        ?.let { millis -> formatActiveTime(millis) }
                        ?: "—",
                    modifier = Modifier.width(SESSION_COLUMN_ACTIVE_TIME)
                )
            }

            // Segunda linha, e não sétima coluna: como coluna o veredito media
            // 210dp e era ele que fazia a linha quebrar. Aqui ele atravessa a
            // largura inteira, que é o que a frase precisa.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppStatusIndicator(
                    label = CliSessionsLabels.healthShort(status.health, language),
                    tone = statusTone
                )
                // Ponto e palavra, como o veredito ao lado: a marca não pode ser
                // só cor. Fica na segunda linha, e não numa sétima coluna — o
                // orçamento de largura das seis colunas não comporta mais uma, e
                // a faixa de legendas não admite linha quebrada.
                if (stalledForMillis != null) {
                    AppStatusIndicator(
                        label = CliSessionsLabels.stalledLabel(stalledForMillis, language),
                        tone = AppTone.WARNING,
                        modifier = Modifier.testTag(cliSessionStalledTag(session.sessionId))
                    )
                }
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
        }

        // Fora do fluxo de colunas, como nas listas de time e de presença: lá
        // dentro a ação é o último item e o primeiro a sair numa janela estreita.
        if (onRemove != null) {
            AppIconButton(
                contentDescription = TeamUsageLabels.removeSession(language),
                onClick = onRemove,
                tone = AppButtonTone.DANGER,
                modifier = if (removeButtonTag != null) {
                    Modifier.testTag(removeButtonTag)
                } else {
                    Modifier
                }
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        } else if (hasActionColumn) {
            // Quadrado, não só largura: sem reservar a altura a linha sem botão
            // sairia mais baixa que as vizinhas.
            Spacer(modifier = Modifier.size(SESSION_ACTION_SLOT))
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
            AppButton(
                label = CliSessionsLabels.back(language),
                onClick = onCloseDetail,
                tone = AppButtonTone.GHOST
            )
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
            is CliSessionDetailUiState.Loading -> AppLoadingState(CliSessionsLabels.loading(language))
            is CliSessionDetailUiState.Error -> AppErrorState(detail.message)
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
        AppProgressTrack(fraction = analytics.cacheHitRate.toFloat(), tone = AppTone.OK)
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
        AppDataSurface(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
            contentPadding = 14.dp,
            verticalArrangement = Arrangement.Top
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

    // Vira o aviso do sistema: barra de severidade de 2dp, título e descrição.
    // O veredito é o título, e o número que o gerou entra na descrição junto com
    // o conselho — antes eram três textos concorrendo na mesma linha.
    AppBanner(
        title = CliSessionsLabels.healthTitle(health, language),
        tone = healthTone(health),
        description = CliSessionsLabels.healthReason(
            saturationLabel = analytics.contextSaturation?.let { value -> formatPercent(value) },
            nextCostLabel = formatMicrosUsd(analytics.nextInteractionCostMicros),
            language = language
        ),
        detail = CliSessionsLabels.healthAdvice(health, language),
        modifier = Modifier.fillMaxWidth()
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SessionMetadataCard(summary: CliSessionSummary, language: AppLanguage) {
    AppDataSurface(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = 14.dp,
        verticalArrangement = Arrangement.Top
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
/**
 * Cor do resumo de vereditos: a do pior estado presente.
 *
 * `null` quando não há aviso nenhum — aí o texto também não existe, e devolver
 * uma cor faria o chamador achar que há algo a pintar.
 */
@Composable
internal fun healthTallyColor(tally: CliSessionHealthTally): Color? {
    if (!tally.hasWarnings) {
        return null
    }
    return if (tally.saturated > 0) {
        healthColor(CliSessionHealth.SATURATED, AppAccents.current)
    } else {
        healthColor(CliSessionHealth.ATTENTION, AppAccents.current)
    }
}

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

/**
 * O mesmo veredito como severidade do sistema.
 *
 * Convive com [healthColor] em vez de substituí-lo: aquele ainda serve a quem
 * precisa da cor crua para pintar traço de gráfico, e este entrega o par
 * ponto + palavra que a lista e o detalhe usam.
 */
internal fun healthTone(health: CliSessionHealth): AppTone {
    return when (health) {
        CliSessionHealth.HEALTHY -> AppTone.OK
        CliSessionHealth.ATTENTION -> AppTone.WARNING
        CliSessionHealth.SATURATED -> AppTone.CRITICAL
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
    AppDataSurfaceFlush(
        modifier = Modifier.fillMaxWidth(),
        header = {
            AppSectionHeader(
                title = title,
                markerColor = accent,
                trailing = {
                    if (help.isNotEmpty()) {
                        HelpDot(terms = help, language = language)
                    }
                    if (trailing != null) {
                        Text(
                            text = trailing,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(AppSpacing.md)) {
            content()
        }
    }
}

/**
 * O bloco de métrica da tela de sessões.
 *
 * O desenho é [AppMetricBlock], que é a primitiva compartilhada; aqui ficam só
 * as duas coisas que pertencem a esta tela: a largura fixa das fileiras de
 * métrica do detalhe e o `?` do glossário.
 */
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
    AppMetricBlock(
        label = label,
        value = value,
        modifier = Modifier.width(METRIC_BLOCK_WIDTH),
        footer = footer,
        footerColor = accent,
        labelTrailing = help?.let { term ->
            { HelpDot(terms = listOf(term), language = language) }
        }
    )
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
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = valueColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

// `internal`, e não `private`, porque a tela de time reaproveita estes blocos:
// duas listas com a mesma anatomia não podem ter duas implementações de célula.
//
// O valor é `label*` — mono — e não `body*`. A escala divide as duas famílias por
// papel, não por tamanho: `body*` é sans e existe para texto corrido, e estas são
// as células de valor de duas listas tabulares. Número em fonte proporcional não
// alinha coluna, que é a razão de a mono estar aqui.
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
            style = MaterialTheme.typography.labelMedium,
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
                .clip(AppShapes.extraSmall)
                .border(AppBorderWidth, MaterialTheme.colorScheme.outlineVariant, AppShapes.extraSmall),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "?",
                style = MaterialTheme.typography.labelSmall,
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
    AppDataSurfaceFlush(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
        header = {
            AppSectionHeader(
                title = CliSessionsLabels.glossaryTitle(language),
                trailing = {
                    Text(
                        text = if (expanded) "▾" else "▸",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
        }
    ) {
        if (!expanded) {
            return@AppDataSurfaceFlush
        }

        // Cada termo é uma linha do painel: título em mono, explicação em sans.
        // O glossário é o único lugar da tela com texto de duas ou três linhas
        // seguidas, e monoespaçada em texto corrido é ~8% mais larga e mais
        // lenta de ler.
        for (term in CliSessionsGlossary.readingOrder) {
            val entry = CliSessionsGlossary.entry(term, language)
            AppDataRow(showDivider = term != CliSessionsGlossary.readingOrder.last()) {
                Column {
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = entry.explanation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
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
            // Segmento chapado: o gradiente vertical dava a cada faixa dois tons
            // da mesma cor, e quatro faixas lado a lado viravam oito.
            Box(
                modifier = Modifier
                    .weight(weight)
                    .fillMaxHeight()
                    .background(color)
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

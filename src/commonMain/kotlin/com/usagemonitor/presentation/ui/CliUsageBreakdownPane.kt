package com.usagemonitor.presentation.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AccountCreditUsage
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CliUsageBreakdown
import com.usagemonitor.domain.entity.MonthlyBudgetStatus
import com.usagemonitor.presentation.ui.components.AppLoadingState
import com.usagemonitor.presentation.ui.components.AppErrorState
import com.usagemonitor.presentation.ui.components.AppEmptyState
import com.usagemonitor.presentation.ui.components.AppButton
import com.usagemonitor.presentation.ui.components.AppButtonTone
import com.usagemonitor.presentation.ui.components.AppIconButton
import com.usagemonitor.presentation.ui.components.AppMetricBlock
import com.usagemonitor.presentation.ui.components.AppSegment
import com.usagemonitor.presentation.ui.components.AppSegmentedControl
import com.usagemonitor.presentation.ui.components.AppTab
import com.usagemonitor.presentation.ui.components.AppTabs
import com.usagemonitor.presentation.ui.components.AppTextField
import com.usagemonitor.presentation.ui.theme.AppSpacing

const val BREAKDOWN_SCROLLBAR_TAG = "breakdownScrollbar"
const val BREAKDOWN_PANE_TAG = "breakdownPane"
const val BREAKDOWN_AXIS_TABS_TAG = "breakdownAxisTabs"
const val BREAKDOWN_AXIS_TAB_TAG_PREFIX = "breakdownAxisTab:"
const val BREAKDOWN_FILTER_TAG = "breakdownFilter"
const val BREAKDOWN_FILTER_CLEAR_TAG = "breakdownFilterClear"
const val BREAKDOWN_SORT_TAG_PREFIX = "breakdownSort:"
const val BREAKDOWN_SORT_DIRECTION_TAG = "breakdownSortDirection"
const val BREAKDOWN_PAGER_TAG = "breakdownPager"
const val BREAKDOWN_PAGE_SUMMARY_TAG = "breakdownPageSummary"
const val BREAKDOWN_PREVIOUS_PAGE_TAG = "breakdownPreviousPage"
const val BREAKDOWN_NEXT_PAGE_TAG = "breakdownNextPage"
const val BREAKDOWN_PAGE_SIZE_TAG_PREFIX = "breakdownPageSize:"

/**
 * Blocos de métrica dos totais.
 *
 * O custo deixou de vir emendado à palavra ("Custo estimado: US$ 9,50") e virou
 * valor de um bloco com rótulo próprio, então não há mais um texto único que
 * prove o número: a âncora é o bloco.
 */
const val BREAKDOWN_TOTAL_COST_TAG = "breakdownTotalCost"
const val BREAKDOWN_BURN_RATE_TAG = "breakdownBurnRate"

/** Largura fixa: num `FlowRow` um campo elástico empurraria os chips de ordem. */
private val FILTER_FIELD_WIDTH = 220.dp

// Larguras das colunas da tabela, num lugar só: a faixa de legendas e as linhas
// têm de cair no mesmo x, e dois conjuntos de literais seriam dois números que
// precisam concordar.
//
// O somatório não é livre. A janela abre em 960dp e sobram ~892 dentro do painel;
// com o vão de 12dp entre colunas, as fixas somam 524 e o rótulo — que é o eixo,
// e o único texto de comprimento imprevisível — leva o resto por peso. Se a soma
// passar da largura o `Row` corta a última coluna, e é por isso que só o rótulo é
// elástico.
internal val AXIS_COLUMN_MIN_WIDTH = 140.dp
internal val SESSIONS_COLUMN_WIDTH = 70.dp
internal val TURNS_COLUMN_WIDTH = 70.dp
internal val TOKENS_COLUMN_WIDTH = 140.dp
internal val COST_COLUMN_WIDTH = 100.dp
internal val ACTIVE_TIME_COLUMN_WIDTH = 84.dp
internal val SHARE_COLUMN_WIDTH = 100.dp
internal val CALLS_COLUMN_WIDTH = 90.dp

/**
 * Resumo do consumo da janela por projeto, branch e modelo.
 *
 * Stateless: recebe o resumo já calculado. As três seções descrevem os **mesmos**
 * turnos por eixos diferentes — a tela diz isso em texto, porque somar linhas de
 * seções diferentes contaria o mesmo gasto duas vezes.
 */
@Composable
internal fun CliUsageBreakdownPane(
    breakdown: CliUsageBreakdown?,
    errorMessage: String?,
    language: AppLanguage,
    budget: MonthlyBudgetStatus? = null,
    accountCredits: AccountCreditUsage? = null,
    /**
     * Frase que diz de onde os números vêm, no topo do card de totais.
     *
     * `null` no resumo da própria máquina, onde a aba está ao lado da lista que
     * originou os dados; o modal do time a usa para separar o resumo da lista de
     * integrantes, que descreve os mesmos turnos.
     */
    hint: String? = null,
    modifier: Modifier = Modifier
) {
    if (breakdown == null) {
        // Aqui os dois estados moravam na mesma frase, por causa do `?:`. Eles são
        // coisas diferentes: "ainda não chegou" é carregando, "não deu para ler" é
        // erro — e o design system dá um desenho a cada um.
        if (errorMessage == null) {
            AppLoadingState(CliSessionsLabels.loading(language))
        } else {
            AppErrorState(errorMessage)
        }
        return
    }

    if (breakdown.isEmpty) {
        AppEmptyState(BreakdownLabels.empty(language))
        return
    }

    val axes = availableBreakdownAxes(breakdown)
    if (axes.isEmpty()) {
        AppEmptyState(BreakdownLabels.empty(language))
        return
    }

    // Estado de tela pura: o filtro, a ordem e a página não mudam o que é lido do
    // índice nem do servidor, então não vão para o ViewModel — ali só moram as
    // escolhas que a carga precisa conhecer. O `remember` sobrevive às emissões
    // do laço ao vivo porque a pane não sai da composição entre elas.
    var axis by remember(axes) { mutableStateOf(axes.first()) }
    var query by remember(axis) { mutableStateOf("") }
    var sort by remember(axis) { mutableStateOf(BreakdownSort.SHARE) }
    var descending by remember(axis) { mutableStateOf(true) }
    var pageSize by remember { mutableStateOf(BREAKDOWN_DEFAULT_PAGE_SIZE) }

    // A página volta ao começo sozinha quando o recorte muda: continuar na
    // página 4 de uma lista que acabou de virar outra mostraria vazio.
    var pageIndex by remember(axis, query, sort, descending, pageSize) { mutableStateOf(0) }

    // Os totais entram na área rolável, e não acima dela: presos no topo eles
    // somam ~200dp de altura fixa e, numa janela baixa, empurram a lista para
    // fora da tela. Fica preso só o cromo pequeno: abas, filtro, ordem e página.
    val summary: androidx.compose.foundation.lazy.LazyListScope.() -> Unit = {
        item(key = "totals") {
            BreakdownTotals(breakdown = breakdown, hint = hint, language = language)
        }
        if (budget != null || accountCredits != null) {
            item(key = "budget") {
                BudgetPanel(budget = budget, accountCredits = accountCredits, language = language)
            }
        }
        if (errorMessage != null) {
            item(key = "stale") {
                // O resumo anterior continua na tela: a mensagem diz que a última
                // leitura falhou, não que não há dados.
                NoticeText(BreakdownLabels.staleNotice(errorMessage, language), MaterialTheme.colorScheme.error)
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize().testTag(BREAKDOWN_PANE_TAG),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
    ) {
        BreakdownAxisTabs(
            axes = axes,
            selected = axis,
            language = language,
            onSelect = { selected -> axis = selected }
        )

        // A grade de atividade não é lista: não há o que filtrar, ordenar nem
        // paginar nela, e oferecer os controles desligados seria pior que
        // escondê-los.
        if (axis == BreakdownAxis.ACTIVITY) {
            BreakdownList(
                isEmpty = false,
                query = "",
                language = language,
                modifier = Modifier.weight(1f)
            ) {
                summary()
                item(key = "activity") {
                    ActivityPanel(breakdown = breakdown, language = language)
                }
            }
            return@Column
        }

        // A página é calculada antes dos controles porque o paginador mora dentro
        // deles: filtro, ordem e página numa faixa só, presa no topo. No rodapé
        // ele saía da tela junto com o fim da lista, que é onde ele serve.
        val page: BreakdownPage<*>
        val rows: androidx.compose.foundation.lazy.LazyListScope.() -> Unit

        if (axis == BreakdownAxis.TOOL) {
            val toolPage = pageOfTools(
                tools = breakdown.byTool,
                query = query,
                sort = sort,
                descending = descending,
                pageIndex = pageIndex,
                pageSize = pageSize
            )
            page = toolPage
            rows = {
                item(key = "toolNotice") {
                    // Sem este aviso a aba seria lida como rateio de gasto: um
                    // turno que chama duas ferramentas gastou tokens uma vez só.
                    NoticeText(
                        BreakdownLabels.toolNotice(language),
                        MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item(key = "toolTable") {
                    val peak = breakdown.byTool.maxOf { tool -> tool.callCount }
                    ToolTable(page = toolPage, peak = peak, language = language)
                }
            }
        } else {
            val unknownLabel = BreakdownLabels.unknownLabel(axis, language)
            val bucketPage = pageOfBuckets(
                buckets = bucketsOf(breakdown, axis),
                query = query,
                sort = sort,
                descending = descending,
                pageIndex = pageIndex,
                pageSize = pageSize,
                unknownLabel = unknownLabel
            )
            page = bucketPage
            rows = {
                // Um item só, e não um por linha: a tabela inteira mora dentro de
                // uma superfície de dados, e a página tem no máximo 100 linhas —
                // o `LazyColumn` desta pane existe para a coluna de totais mais a
                // tabela, não para as linhas dela.
                item(key = "bucketTable") {
                    BucketTable(
                        page = bucketPage,
                        axis = axis,
                        totals = breakdown.totals,
                        unknownLabel = unknownLabel,
                        language = language
                    )
                }
            }
        }

        BreakdownControls(
            axis = axis,
            query = query,
            sort = sort,
            descending = descending,
            page = page,
            pageSize = pageSize,
            language = language,
            onQueryChange = { text -> query = text },
            onSortChange = { selected -> sort = selected },
            onToggleDirection = { descending = !descending },
            onPageChange = { index -> pageIndex = index },
            onPageSizeChange = { size -> pageSize = size }
        )

        BreakdownList(
            isEmpty = page.items.isEmpty(),
            query = query,
            language = language,
            modifier = Modifier.weight(1f),
            header = summary
        ) {
            rows()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BreakdownAxisTabs(
    axes: List<BreakdownAxis>,
    selected: BreakdownAxis,
    language: AppLanguage,
    onSelect: (BreakdownAxis) -> Unit
) {
    // Aba, não pílula: o eixo troca **o que** a pane mostra. Filtro, ordem e
    // tamanho de página, logo abaixo, escolhem parâmetros do mesmo conteúdo e
    // por isso são segmentados — desenhá-los igual foi o que fez a tela ter três
    // fileiras de pílulas idênticas com funções diferentes.
    AppTabs(
        tabs = axes.map { entry ->
            AppTab(
                label = BreakdownLabels.axisTab(entry, language),
                testTag = "$BREAKDOWN_AXIS_TAB_TAG_PREFIX${entry.name}"
            )
        },
        selectedIndex = axes.indexOf(selected).coerceAtLeast(0),
        onSelect = { index -> onSelect(axes[index]) },
        modifier = Modifier.fillMaxWidth().testTag(BREAKDOWN_AXIS_TABS_TAG)
    )
}

/**
 * Filtro, ordem e página numa faixa só, presa no topo.
 *
 * O paginador morava no rodapé da pane. Ali ele é a primeira coisa a sair da
 * tela numa janela baixa — justamente quando a lista é longa e ele serve para
 * alguma coisa. Agora os três controles do mesmo conteúdo ficam juntos, acima da
 * área que rola, como o protótipo desenha.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BreakdownControls(
    axis: BreakdownAxis,
    query: String,
    sort: BreakdownSort,
    descending: Boolean,
    page: BreakdownPage<*>,
    pageSize: Int,
    language: AppLanguage,
    onQueryChange: (String) -> Unit,
    onSortChange: (BreakdownSort) -> Unit,
    onToggleDirection: () -> Unit,
    onPageChange: (Int) -> Unit,
    onPageSizeChange: (Int) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().testTag(BREAKDOWN_PAGER_TAG),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        // O alinhamento vai no arranjo: o `FlowRow` desta versão não tem
        // `verticalAlignment`, e sem ele os chips subiriam para o topo do campo.
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
    ) {
        AppTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = BreakdownLabels.filterPlaceholder(language),
            modifier = Modifier.width(FILTER_FIELD_WIDTH).testTag(BREAKDOWN_FILTER_TAG)
        )

        // O botão de limpar sai de dentro do campo e passa a ficar ao lado dele:
        // um campo de 28dp de altura não tem onde acomodar dois ícones sem
        // espremer o texto que a pessoa está digitando.
        if (query.isNotEmpty()) {
            AppIconButton(
                contentDescription = BreakdownLabels.clearFilter(language),
                onClick = { onQueryChange("") },
                modifier = Modifier.testTag(BREAKDOWN_FILTER_CLEAR_TAG)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text(
            text = BreakdownLabels.sortLabel(language),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        AppSegmentedControl(
            options = BreakdownSort.entries.map { entry ->
                AppSegment(
                    label = BreakdownLabels.sortOption(entry, axis, language),
                    testTag = "$BREAKDOWN_SORT_TAG_PREFIX${entry.name}"
                )
            },
            selectedIndex = BreakdownSort.entries.indexOf(sort),
            onSelect = { index -> onSortChange(BreakdownSort.entries[index]) }
        )

        // Um botão que inverte, e não dois chips de "crescente/decrescente": a
        // direção é uma propriedade da ordem escolhida, não uma quarta ordem.
        AppIconButton(
            contentDescription = BreakdownLabels.sortDirection(descending, language),
            onClick = onToggleDirection,
            modifier = Modifier.testTag(BREAKDOWN_SORT_DIRECTION_TAG)
        ) {
            Icon(
                imageVector = if (descending) Icons.Rounded.ArrowDownward else Icons.Rounded.ArrowUpward,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Botão de texto e não de ícone, como no protótipo: uma seta sozinha
        // entre um campo de filtro e um segmentado de ordem não diz de que ela é
        // a seta. O `‹` e o `›` ficam no rótulo, que é onde já cabem.
        AppButton(
            label = "‹ ${BreakdownLabels.previousPage(language)}",
            onClick = { onPageChange(page.pageIndex - 1) },
            enabled = page.hasPrevious,
            tone = AppButtonTone.GHOST,
            modifier = Modifier.testTag(BREAKDOWN_PREVIOUS_PAGE_TAG)
        )

        Text(
            text = BreakdownLabels.pageSummary(page, language),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(BREAKDOWN_PAGE_SUMMARY_TAG)
        )

        AppButton(
            label = "${BreakdownLabels.nextPage(language)} ›",
            onClick = { onPageChange(page.pageIndex + 1) },
            enabled = page.hasNext,
            tone = AppButtonTone.GHOST,
            modifier = Modifier.testTag(BREAKDOWN_NEXT_PAGE_TAG)
        )

        Text(
            text = BreakdownLabels.pageSizeLabel(language),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        AppSegmentedControl(
            options = BREAKDOWN_PAGE_SIZES.map { size ->
                AppSegment(
                    label = size.toString(),
                    testTag = "$BREAKDOWN_PAGE_SIZE_TAG_PREFIX$size"
                )
            },
            selectedIndex = BREAKDOWN_PAGE_SIZES.indexOf(pageSize).coerceAtLeast(0),
            onSelect = { index -> onPageSizeChange(BREAKDOWN_PAGE_SIZES[index]) }
        )
    }
}

/**
 * A área rolável: os cards de resumo em [header] e as linhas da página.
 *
 * O [header] entra mesmo quando a página está vazia — um filtro sem resultado
 * não pode apagar os totais da janela, que continuam verdadeiros.
 */
@Composable
private fun BreakdownList(
    isEmpty: Boolean,
    query: String,
    language: AppLanguage,
    modifier: Modifier = Modifier,
    header: (androidx.compose.foundation.lazy.LazyListScope.() -> Unit)? = null,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    Box(modifier = modifier.fillMaxWidth()) {
        val listState = rememberLazyListState()

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(end = SCROLLBAR_GUTTER),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            header?.invoke(this)

            if (isEmpty) {
                item(key = "noMatches") {
                    Text(
                        text = if (query.isBlank()) {
                            BreakdownLabels.empty(language)
                        } else {
                            BreakdownLabels.noMatches(query.trim(), language)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                content()
            }
        }

        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(listState),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .testTag(BREAKDOWN_SCROLLBAR_TAG)
        )
    }
}

/**
 * Os três totais da janela, em blocos de métrica de largura igual.
 *
 * Eram dois painéis empilhados com o título em azul e em verde. O acento é
 * identidade de fonte — marcador de seção e linha de gráfico —, e como cor de
 * valor ele sugeria categorias que estes números não têm: os três medem a mesma
 * janela.
 *
 * As qualificações longas ficam **fora** dos blocos. Dentro, um rodapé de quatro
 * medidas mede três vezes a largura do bloco vizinho e a fileira perde o
 * alinhamento que a grade de métricas existe para dar.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BreakdownTotals(
    breakdown: CliUsageBreakdown,
    hint: String?,
    language: AppLanguage
) {
    val totals = breakdown.totals
    val burnRate = breakdown.burnRate

    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            AppMetricBlock(
                label = BreakdownLabels.estimatedCostLabel(language),
                value = BreakdownLabels.bucketCost(totals),
                modifier = Modifier.width(METRIC_BLOCK_WIDTH).testTag(BREAKDOWN_TOTAL_COST_TAG)
            )

            AppMetricBlock(
                label = BreakdownLabels.cacheSavingsLabel(language),
                value = formatMicrosUsdShort(totals.cacheSavingsMicros),
                modifier = Modifier.width(METRIC_BLOCK_WIDTH)
            )

            AppMetricBlock(
                label = BreakdownLabels.burnRateTitle(language),
                value = burnRate
                    ?.let { rate -> BreakdownLabels.burnRateCostPerHour(rate.costMicrosPerHour) }
                    ?: "—",
                // Rodapé curto, o mesmo do protótipo: ele qualifica o valor sem
                // medir mais que o bloco.
                footer = if (burnRate != null) BreakdownLabels.burnRateFooter(language) else null,
                modifier = Modifier.width(METRIC_BLOCK_WIDTH).testTag(BREAKDOWN_BURN_RATE_TAG)
            )
        }

        NoticeText(
            BreakdownLabels.totalSubtitle(totals, language),
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        NoticeText(
            BreakdownLabels.cacheSavings(
                savingsMicros = totals.cacheSavingsMicros,
                share = breakdown.cacheSavingsShare,
                hitRate = totals.cacheHitRate,
                language = language
            ),
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!totals.isCostComplete) {
            NoticeText(
                BreakdownLabels.unpricedNotice(totals.unpricedTurnCount, language),
                MaterialTheme.colorScheme.error
            )
        }
        if (burnRate == null) {
            NoticeText(
                BreakdownLabels.burnRateUnavailable(language),
                MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            NoticeText(
                BreakdownLabels.burnRateTokensPerHour(burnRate.tokensPerHour, language) + " " +
                    BreakdownLabels.burnRateElapsed(burnRate.elapsedMillis, language),
                MaterialTheme.colorScheme.onSurfaceVariant
            )
            val projected = burnRate.projectedCostMicros
            if (projected != null) {
                NoticeText(
                    BreakdownLabels.burnRateProjection(projected, language),
                    MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        NoticeText(BreakdownLabels.axisNotice(language), MaterialTheme.colorScheme.onSurfaceVariant)
        if (hint != null) {
            NoticeText(hint, MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

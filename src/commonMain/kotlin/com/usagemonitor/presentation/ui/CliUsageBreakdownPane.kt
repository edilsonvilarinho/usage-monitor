package com.usagemonitor.presentation.ui

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AccountCreditUsage
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CliToolUsage
import com.usagemonitor.domain.entity.CliUsageBreakdown
import com.usagemonitor.domain.entity.CliUsageBucket
import com.usagemonitor.domain.entity.MonthlyBudgetStatus
import com.usagemonitor.presentation.ui.components.ActivityHeatmapGrid
import com.usagemonitor.presentation.ui.components.AppIconButton
import com.usagemonitor.presentation.ui.components.AppSegment
import com.usagemonitor.presentation.ui.components.AppSegmentedControl
import com.usagemonitor.presentation.ui.components.AppTab
import com.usagemonitor.presentation.ui.components.AppTabs
import com.usagemonitor.presentation.ui.components.AppTextField
import com.usagemonitor.presentation.ui.components.DepthSurface
import com.usagemonitor.presentation.ui.theme.AppShapes
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

/** Altura da barra de fatia; fina o bastante para não competir com o número. */
private val SHARE_BAR_HEIGHT = 4.dp

/** Largura fixa: num `FlowRow` um campo elástico empurraria os chips de ordem. */
private val FILTER_FIELD_WIDTH = 220.dp

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
        CenteredMessage(errorMessage ?: CliSessionsLabels.loading(language))
        return
    }

    if (breakdown.isEmpty) {
        CenteredMessage(BreakdownLabels.empty(language))
        return
    }

    val axes = availableBreakdownAxes(breakdown)
    if (axes.isEmpty()) {
        CenteredMessage(BreakdownLabels.empty(language))
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

    // Os cards de resumo entram na área rolável, e não acima dela: presos no topo
    // eles somam ~200dp de altura fixa e, numa janela baixa, empurram o paginador
    // para fora da tela — o controle deixaria de existir justamente onde a lista
    // é longa. Fica preso só o cromo pequeno: abas, filtro e paginador.
    val summary: androidx.compose.foundation.lazy.LazyListScope.() -> Unit = {
        item(key = "totals") {
            BreakdownTotalsCard(breakdown = breakdown, hint = hint, language = language)
        }
        item(key = "burn") {
            BurnRateCard(breakdown = breakdown, language = language)
        }
        if (budget != null || accountCredits != null) {
            item(key = "budget") {
                BudgetCard(budget = budget, accountCredits = accountCredits, language = language)
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
        verticalArrangement = Arrangement.spacedBy(12.dp)
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
                    ActivityCard(breakdown = breakdown, language = language)
                }
            }
            return@Column
        }

        BreakdownControls(
            axis = axis,
            query = query,
            sort = sort,
            descending = descending,
            language = language,
            onQueryChange = { text -> query = text },
            onSortChange = { selected -> sort = selected },
            onToggleDirection = { descending = !descending }
        )

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
                val peak = breakdown.byTool.maxOf { tool -> tool.callCount }
                items(count = toolPage.items.size, key = { index -> "tool-$index" }) { index ->
                    ToolRow(tool = toolPage.items[index], peak = peak, language = language)
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
            val accent = axisAccent(axis)
            rows = {
                // A chave é a posição, não o rótulo. Nos eixos por projeto, branch
                // e modelo o rótulo é a chave da agregação e seria único; no eixo
                // por integrante ele é o apelido, que duas pessoas podem repetir —
                // e chave repetida num `LazyColumn` não é um item mal desenhado, é
                // uma exceção que derruba a janela. A posição serve como identidade
                // porque a ordem é total e determinística.
                items(count = bucketPage.items.size, key = { index -> "bucket-$index" }) { index ->
                    BreakdownRow(
                        bucket = bucketPage.items[index],
                        totals = breakdown.totals,
                        unknownLabel = unknownLabel,
                        accent = accent,
                        language = language
                    )
                }
            }
        }

        BreakdownList(
            isEmpty = page.items.isEmpty(),
            query = query,
            language = language,
            modifier = Modifier.weight(1f),
            header = summary
        ) {
            rows()
        }

        BreakdownPager(
            page = page,
            pageSize = pageSize,
            language = language,
            onPageChange = { index -> pageIndex = index },
            onPageSizeChange = { size -> pageSize = size }
        )
    }
}

/** Uma cor por eixo, como as seções empilhadas já tinham. */
@Composable
private fun axisAccent(axis: BreakdownAxis): Color {
    return when (axis) {
        BreakdownAxis.MEMBER -> CACHE_WRITE_COLOR
        BreakdownAxis.PROJECT -> CACHE_READ_COLOR
        BreakdownAxis.MODEL -> INPUT_COLOR
        BreakdownAxis.BRANCH -> OUTPUT_COLOR
        BreakdownAxis.TOOL -> CACHE_WRITE_COLOR
        BreakdownAxis.ACTIVITY -> CACHE_READ_COLOR
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BreakdownControls(
    axis: BreakdownAxis,
    query: String,
    sort: BreakdownSort,
    descending: Boolean,
    language: AppLanguage,
    onQueryChange: (String) -> Unit,
    onSortChange: (BreakdownSort) -> Unit,
    onToggleDirection: () -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BreakdownPager(
    page: BreakdownPage<*>,
    pageSize: Int,
    language: AppLanguage,
    onPageChange: (Int) -> Unit,
    onPageSizeChange: (Int) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().testTag(BREAKDOWN_PAGER_TAG),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
    ) {
        AppIconButton(
            contentDescription = BreakdownLabels.previousPage(language),
            onClick = { onPageChange(page.pageIndex - 1) },
            enabled = page.hasPrevious,
            modifier = Modifier.testTag(BREAKDOWN_PREVIOUS_PAGE_TAG)
        ) {
            Icon(
                imageVector = Icons.Rounded.ChevronLeft,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = BreakdownLabels.pageSummary(page, language),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(BREAKDOWN_PAGE_SUMMARY_TAG)
        )

        AppIconButton(
            contentDescription = BreakdownLabels.nextPage(language),
            onClick = { onPageChange(page.pageIndex + 1) },
            enabled = page.hasNext,
            modifier = Modifier.testTag(BREAKDOWN_NEXT_PAGE_TAG)
        ) {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

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

@Composable
private fun BreakdownTotalsCard(
    breakdown: CliUsageBreakdown,
    hint: String?,
    language: AppLanguage
) {
    val totals = breakdown.totals

    DepthSurface(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = AppSpacing.md
    ) {
        Text(
            text = BreakdownLabels.totalCost(totals, language),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = INPUT_COLOR
        )
        Text(
            text = BreakdownLabels.totalSubtitle(totals, language),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = BreakdownLabels.cacheSavings(
                savingsMicros = totals.cacheSavingsMicros,
                share = breakdown.cacheSavingsShare,
                hitRate = totals.cacheHitRate,
                language = language
            ),
            style = MaterialTheme.typography.labelSmall,
            color = CACHE_READ_COLOR
        )
        if (!totals.isCostComplete) {
            Text(
                text = BreakdownLabels.unpricedNotice(totals.unpricedTurnCount, language),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        Text(
            text = BreakdownLabels.axisNotice(language),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (hint != null) {
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BudgetCard(
    budget: MonthlyBudgetStatus?,
    accountCredits: AccountCreditUsage?,
    language: AppLanguage
) {
    DepthSurface(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = AppSpacing.md
    ) {
        Text(
            text = BreakdownLabels.budgetTitle(language),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = INPUT_COLOR
        )

        if (budget != null) {
            Text(
                text = BreakdownLabels.budgetValue(
                    spentMicros = budget.spentMicros,
                    limitMicros = budget.limitMicros,
                    isComplete = budget.isSpendComplete,
                    language = language
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (budget.isExceeded) MaterialTheme.colorScheme.error else INPUT_COLOR
            )
            ShareBar(
                share = budget.share,
                accent = if (budget.isExceeded) MaterialTheme.colorScheme.error else INPUT_COLOR
            )
            Text(
                text = BreakdownLabels.budgetProjection(budget.projectedMicros, budget.willExceed, language),
                style = MaterialTheme.typography.labelSmall,
                color = if (budget.willExceed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Text(
                text = BreakdownLabels.budgetScopeNotice(language),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (accountCredits != null) {
            // Linha própria e moeda explícita: somar isto ao valor acima daria um
            // número inventado quando a conta não é em USD.
            Text(
                text = BreakdownLabels.accountCredits(
                    usedMinorUnits = accountCredits.usedMinorUnits,
                    limitMinorUnits = accountCredits.limitMinorUnits,
                    currencyCode = accountCredits.currencyCode,
                    language = language
                ),
                style = MaterialTheme.typography.labelSmall,
                color = CACHE_READ_COLOR
            )
        }
    }
}

@Composable
private fun BurnRateCard(breakdown: CliUsageBreakdown, language: AppLanguage) {
    val burnRate = breakdown.burnRate

    DepthSurface(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = AppSpacing.md
    ) {
        Text(
            text = BreakdownLabels.burnRateTitle(language),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = OUTPUT_COLOR
        )

        if (burnRate == null) {
            Text(
                text = BreakdownLabels.burnRateUnavailable(language),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@DepthSurface
        }

        Text(
            text = BreakdownLabels.burnRateValue(
                costMicrosPerHour = burnRate.costMicrosPerHour,
                tokensPerHour = burnRate.tokensPerHour,
                language = language
            ),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        if (burnRate.projectedCostMicros != null) {
            Text(
                text = BreakdownLabels.burnRateProjection(burnRate.projectedCostMicros, language),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = BreakdownLabels.burnRateElapsed(burnRate.elapsedMillis, language),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Uma ferramenta, no mesmo formato de card das linhas de eixo.
 *
 * [peak] é a ferramenta mais chamada da janela **inteira**, não da página: a
 * barra responde "qual domina", e renormalizá-la por página faria a primeira
 * linha de toda página parecer o pico.
 */
@Composable
private fun ToolRow(tool: CliToolUsage, peak: Int, language: AppLanguage) {
    DepthSurface(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = AppSpacing.md
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = tool.toolName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = tool.callCount.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = CACHE_WRITE_COLOR
            )
        }
        // Fatia contra a ferramenta mais chamada, não contra o total: a pergunta
        // é qual domina, e todas somariam 100% de qualquer jeito.
        ShareBar(
            share = if (peak <= 0) 0.0 else tool.callCount.toDouble() / peak.toDouble(),
            accent = CACHE_WRITE_COLOR
        )
        Text(
            text = BreakdownLabels.toolSubtitle(tool.callCount, tool.turnCount, language),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ActivityCard(breakdown: CliUsageBreakdown, language: AppLanguage) {
    DepthSurface(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = AppSpacing.md
    ) {
        Text(
            text = BreakdownLabels.activityTitle(language),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = CACHE_READ_COLOR
        )
        ActivityHeatmapGrid(
            heatmap = breakdown.heatmap,
            accent = CACHE_READ_COLOR,
            language = language,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = BreakdownLabels.activityNotice(language),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BreakdownRow(
    bucket: CliUsageBucket,
    totals: CliUsageBucket,
    unknownLabel: String,
    accent: Color,
    language: AppLanguage
) {
    val share = bucket.costShareOf(totals)

    DepthSurface(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = AppSpacing.md
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = bucket.label ?: unknownLabel,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = BreakdownLabels.bucketCost(bucket),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = INPUT_COLOR
            )
            Text(
                text = formatPercent(share),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        ShareBar(share = share, accent = accent)

        Text(
            text = BreakdownLabels.bucketSubtitle(bucket, language),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Barra de fatia desenhada com dois `Box`, sem animação.
 *
 * Uma animação numa lista que o laço ao vivo republica de cinco em cinco
 * segundos travaria o `waitForIdle` dos testes de componente — mesma razão pela
 * qual o ponto da tela de presença não pisca.
 */
@Composable
private fun ShareBar(share: Double, accent: Color) {
    val fraction = share.coerceIn(0.0, 1.0).toFloat()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(SHARE_BAR_HEIGHT)
            .clip(AppShapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (fraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .clip(AppShapes.small)
                    .background(accent)
            )
        }
    }
}

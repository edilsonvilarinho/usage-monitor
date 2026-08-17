package com.usagemonitor.presentation.ui

import com.usagemonitor.domain.entity.CliToolUsage
import com.usagemonitor.domain.entity.CliUsageBreakdown
import com.usagemonitor.domain.entity.CliUsageBucket

/**
 * Qual recorte o resumo mostra.
 *
 * Enum novo, e não valor a mais em `CliSessionsView` ou `TeamUsageView`: aquelas
 * escolhem a tela, esta escolhe o eixo dentro de uma delas. Um enum só teria de
 * carregar combinações que não existem — "Sessões · Por branch" não é nada.
 *
 * `ACTIVITY` não é um eixo de consumo como os outros: é a grade por hora, e a
 * tela a trata à parte justamente por isso (não tem lista, filtro nem página).
 */
enum class BreakdownAxis { MEMBER, PROJECT, MODEL, BRANCH, TOOL, ACTIVITY }

/**
 * Por qual coluna a lista do eixo é ordenada.
 *
 * Três valores e não seis porque as duas formas de lista — baldes de consumo e
 * chamadas de ferramenta — respondem à mesma pergunta com números diferentes.
 * [SHARE] é a fatia que a linha representa (custo no balde, chamadas na
 * ferramenta), que é a ordem que a tela abre.
 */
enum class BreakdownSort { SHARE, VOLUME, NAME }

/**
 * Quantas linhas cabem numa página.
 *
 * Lista fixa em vez de campo livre: são atalhos, e um campo numérico convidaria
 * a digitar 3 e paginar de três em três.
 */
val BREAKDOWN_PAGE_SIZES = listOf(5, 10, 50, 100)

/** Dez linhas cabem na janela sem rolagem e ainda mostram a cauda do consumo. */
const val BREAKDOWN_DEFAULT_PAGE_SIZE = 10

/**
 * Uma fatia da lista já filtrada e ordenada, com o que o rodapé precisa saber.
 *
 * [pageIndex] é o **efetivo**, já preso ao intervalo válido: a lista encolhe a
 * cada tique do laço ao vivo, e uma página que deixou de existir mostraria vazio
 * em vez do fim dos dados.
 */
data class BreakdownPage<T>(
    val items: List<T> = emptyList(),
    val pageIndex: Int = 0,
    val pageCount: Int = 1,
    /**
     * Posição do primeiro item na lista filtrada, base zero.
     *
     * Sai daqui pronto porque a última página é mais curta que as outras:
     * multiplicar o índice pelo tamanho **da página exibida** erraria o começo
     * dela, e é esse número que o rodapé mostra.
     */
    val fromIndex: Int = 0,
    /** Linhas do eixo antes do filtro; é o que diz se o filtro escondeu algo. */
    val totalCount: Int = 0,
    /** Linhas que sobraram do filtro. */
    val filteredCount: Int = 0
) {
    val isFiltered: Boolean
        get() = filteredCount != totalCount

    val hasPrevious: Boolean
        get() = pageIndex > 0

    val hasNext: Boolean
        get() = pageIndex < pageCount - 1
}

/**
 * Eixos que têm o que mostrar, na ordem em que as abas aparecem.
 *
 * Aba vazia não entra: no modal do time não há ferramenta nem grade de atividade,
 * e uma aba que abre em "nenhum dado" é um clique desperdiçado. `MEMBER` vem
 * primeiro quando existe — no time a pergunta é quem gastou, e os outros três
 * respondem em quê.
 */
fun availableBreakdownAxes(breakdown: CliUsageBreakdown): List<BreakdownAxis> {
    val axes = mutableListOf<BreakdownAxis>()
    if (breakdown.byMember.isNotEmpty()) {
        axes += BreakdownAxis.MEMBER
    }
    if (breakdown.byProject.isNotEmpty()) {
        axes += BreakdownAxis.PROJECT
    }
    if (breakdown.byModel.isNotEmpty()) {
        axes += BreakdownAxis.MODEL
    }
    if (breakdown.byBranch.isNotEmpty()) {
        axes += BreakdownAxis.BRANCH
    }
    if (breakdown.byTool.isNotEmpty()) {
        axes += BreakdownAxis.TOOL
    }
    if (!breakdown.heatmap.isEmpty) {
        axes += BreakdownAxis.ACTIVITY
    }
    return axes
}

/** Baldes do eixo pedido; vazio para os eixos que não são lista de baldes. */
fun bucketsOf(breakdown: CliUsageBreakdown, axis: BreakdownAxis): List<CliUsageBucket> {
    return when (axis) {
        BreakdownAxis.MEMBER -> breakdown.byMember
        BreakdownAxis.PROJECT -> breakdown.byProject
        BreakdownAxis.MODEL -> breakdown.byModel
        BreakdownAxis.BRANCH -> breakdown.byBranch
        BreakdownAxis.TOOL, BreakdownAxis.ACTIVITY -> emptyList()
    }
}

/**
 * Filtra, ordena e pagina os baldes de um eixo.
 *
 * [unknownLabel] entra porque a linha sem rótulo aparece na tela com esse texto:
 * filtrar pelo rótulo cru esconderia a linha "Sem branch" de quem digita "sem".
 */
fun pageOfBuckets(
    buckets: List<CliUsageBucket>,
    query: String,
    sort: BreakdownSort,
    descending: Boolean,
    pageIndex: Int,
    pageSize: Int,
    unknownLabel: String
): BreakdownPage<CliUsageBucket> {
    val filtered = buckets.filter { bucket ->
        matchesQuery(bucket.label ?: unknownLabel, query)
    }

    val ascending = bucketComparator(sort, unknownLabel)
    val sorted = filtered.sortedWith(if (descending) ascending.reversed() else ascending)

    return paginate(sorted, buckets.size, pageIndex, pageSize)
}

/** Mesma operação para as ferramentas, que não são baldes de consumo. */
fun pageOfTools(
    tools: List<CliToolUsage>,
    query: String,
    sort: BreakdownSort,
    descending: Boolean,
    pageIndex: Int,
    pageSize: Int
): BreakdownPage<CliToolUsage> {
    val filtered = tools.filter { tool -> matchesQuery(tool.toolName, query) }

    val ascending: Comparator<CliToolUsage> = when (sort) {
        BreakdownSort.SHARE -> compareBy<CliToolUsage> { tool -> tool.callCount }
            .thenBy { tool -> tool.toolName }
        BreakdownSort.VOLUME -> compareBy<CliToolUsage> { tool -> tool.turnCount }
            .thenBy { tool -> tool.toolName }
        BreakdownSort.NAME -> compareBy { tool -> tool.toolName.lowercase() }
    }

    val sorted = filtered.sortedWith(if (descending) ascending.reversed() else ascending)

    return paginate(sorted, tools.size, pageIndex, pageSize)
}

/**
 * Ordem crescente por coluna, sempre desempatada pelo rótulo.
 *
 * O desempate não é estético: sem ele duas linhas de mesmo custo poderiam trocar
 * de lugar entre leituras, e o laço ao vivo republica a lista de cinco em cinco
 * segundos. `sortedWith` é estável, então a ordem de entrada — já total, vinda de
 * `rankedByCost` — resolve o que sobrar.
 */
private fun bucketComparator(sort: BreakdownSort, unknownLabel: String): Comparator<CliUsageBucket> {
    return when (sort) {
        BreakdownSort.SHARE -> compareBy<CliUsageBucket> { bucket -> bucket.costMicros }
            .thenBy { bucket -> (bucket.label ?: unknownLabel).lowercase() }
        BreakdownSort.VOLUME -> compareBy<CliUsageBucket> { bucket -> bucket.totalTokens }
            .thenBy { bucket -> (bucket.label ?: unknownLabel).lowercase() }
        BreakdownSort.NAME -> compareBy { bucket -> (bucket.label ?: unknownLabel).lowercase() }
    }
}

/**
 * Corta a página pedida, prendendo o índice ao intervalo que existe.
 *
 * Uma lista vazia continua tendo uma página: "página 1 de 0" não é uma frase.
 */
private fun <T> paginate(
    sorted: List<T>,
    totalCount: Int,
    pageIndex: Int,
    pageSize: Int
): BreakdownPage<T> {
    val safeSize = pageSize.coerceAtLeast(1)
    val pageCount = if (sorted.isEmpty()) 1 else (sorted.size + safeSize - 1) / safeSize
    val safeIndex = pageIndex.coerceIn(0, pageCount - 1)
    val from = safeIndex * safeSize
    val to = minOf(from + safeSize, sorted.size)

    return BreakdownPage(
        items = if (from >= sorted.size) emptyList() else sorted.subList(from, to),
        pageIndex = safeIndex,
        pageCount = pageCount,
        fromIndex = from,
        totalCount = totalCount,
        filteredCount = sorted.size
    )
}

/**
 * Casamento por trecho, sem diferenciar maiúsculas.
 *
 * Trecho e não prefixo: os rótulos são caminhos de projeto e nomes de modelo
 * (`claude-opus-5`), onde o que identifica costuma estar no meio.
 */
private fun matchesQuery(label: String, query: String): Boolean {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) {
        return true
    }
    return label.contains(trimmed, ignoreCase = true)
}

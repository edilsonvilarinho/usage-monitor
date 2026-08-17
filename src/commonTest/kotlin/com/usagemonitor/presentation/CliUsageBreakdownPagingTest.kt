package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.CliActivityHeatmap
import com.usagemonitor.domain.entity.CliActivityCell
import com.usagemonitor.domain.entity.CliToolUsage
import com.usagemonitor.domain.entity.CliUsageBreakdown
import com.usagemonitor.domain.entity.CliUsageBucket
import com.usagemonitor.presentation.ui.BREAKDOWN_PAGE_SIZES
import com.usagemonitor.presentation.ui.BreakdownAxis
import com.usagemonitor.presentation.ui.BreakdownSort
import com.usagemonitor.presentation.ui.availableBreakdownAxes
import com.usagemonitor.presentation.ui.bucketsOf
import com.usagemonitor.presentation.ui.pageOfBuckets
import com.usagemonitor.presentation.ui.pageOfTools
import kotlinx.datetime.DayOfWeek
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val UNKNOWN = "Sem branch"

private fun bucket(
    label: String?,
    costMicros: Long = 0L,
    tokens: Long = 0L
) = CliUsageBucket(
    label = label,
    costMicros = costMicros,
    inputTokens = tokens,
    sessionCount = 1,
    turnCount = 1
)

private fun page(
    buckets: List<CliUsageBucket>,
    query: String = "",
    sort: BreakdownSort = BreakdownSort.SHARE,
    descending: Boolean = true,
    pageIndex: Int = 0,
    pageSize: Int = 10
) = pageOfBuckets(
    buckets = buckets,
    query = query,
    sort = sort,
    descending = descending,
    pageIndex = pageIndex,
    pageSize = pageSize,
    unknownLabel = UNKNOWN
)

private fun labelsOf(buckets: List<CliUsageBucket>) = buckets.map { it.label }

class CliUsageBreakdownPagingTest {

    // ------------------------------------------------------------------------
    // Abas disponíveis
    // ------------------------------------------------------------------------

    @Test
    fun `so entram as abas que tem dado`() {
        val breakdown = CliUsageBreakdown(
            byProject = listOf(bucket("alpha")),
            byModel = listOf(bucket("opus"))
        )

        // Aba que abre em "nenhum dado" é um clique desperdiçado — e no modal do
        // time ferramenta e atividade nunca existem.
        assertEquals(
            listOf(BreakdownAxis.PROJECT, BreakdownAxis.MODEL),
            availableBreakdownAxes(breakdown)
        )
    }

    @Test
    fun `o eixo por integrante vem primeiro quando existe`() {
        val breakdown = CliUsageBreakdown(
            byProject = listOf(bucket("alpha")),
            byMember = listOf(bucket("edilson")),
            byTool = listOf(CliToolUsage("Read", callCount = 1)),
            heatmap = CliActivityHeatmap(cells = listOf(CliActivityCell(DayOfWeek.MONDAY, 9, costMicros = 1L)))
        )

        // No time a pergunta é quem gastou; os outros eixos respondem em quê.
        assertEquals(
            listOf(
                BreakdownAxis.MEMBER,
                BreakdownAxis.PROJECT,
                BreakdownAxis.TOOL,
                BreakdownAxis.ACTIVITY
            ),
            availableBreakdownAxes(breakdown)
        )
    }

    @Test
    fun `ferramenta e atividade nao sao listas de balde`() {
        val breakdown = CliUsageBreakdown(byTool = listOf(CliToolUsage("Read")))

        assertTrue(bucketsOf(breakdown, BreakdownAxis.TOOL).isEmpty())
        assertTrue(bucketsOf(breakdown, BreakdownAxis.ACTIVITY).isEmpty())
    }

    // ------------------------------------------------------------------------
    // Filtro
    // ------------------------------------------------------------------------

    @Test
    fun `o filtro casa por trecho e ignora maiusculas`() {
        val buckets = listOf(bucket("usage-monitor"), bucket("coletor-android"), bucket("docs"))

        // Trecho e não prefixo: o que identifica um caminho costuma estar no meio.
        assertEquals(listOf("usage-monitor"), labelsOf(page(buckets, query = "MONITOR").items))
        assertEquals(listOf("coletor-android"), labelsOf(page(buckets, query = "android").items))
    }

    @Test
    fun `filtro em branco nao esconde nada`() {
        val buckets = listOf(bucket("a"), bucket("b"))

        val result = page(buckets, query = "   ")

        assertEquals(2, result.items.size)
        assertFalse(result.isFiltered)
    }

    @Test
    fun `o filtro casa com o texto que a linha sem rotulo mostra`() {
        val buckets = listOf(bucket(null), bucket("main"))

        // Filtrar pelo rótulo cru esconderia a linha que a tela chama "Sem branch".
        val result = page(buckets, query = "sem branch")

        assertEquals(1, result.items.size)
        assertEquals(null, result.items.single().label)
    }

    @Test
    fun `o rodape sabe quantas linhas o filtro escondeu`() {
        val buckets = listOf(bucket("alpha"), bucket("beta"), bucket("gamma"))

        val result = page(buckets, query = "a")

        assertEquals(3, result.totalCount)
        assertEquals(3, result.filteredCount)
        assertFalse(result.isFiltered)

        val narrow = page(buckets, query = "alp")
        assertEquals(3, narrow.totalCount)
        assertEquals(1, narrow.filteredCount)
        assertTrue(narrow.isFiltered)
    }

    // ------------------------------------------------------------------------
    // Ordenação
    // ------------------------------------------------------------------------

    @Test
    fun `ordena por custo nos dois sentidos`() {
        val buckets = listOf(
            bucket("barato", costMicros = 10L),
            bucket("caro", costMicros = 900L),
            bucket("medio", costMicros = 100L)
        )

        assertEquals(listOf("caro", "medio", "barato"), labelsOf(page(buckets).items))
        assertEquals(
            listOf("barato", "medio", "caro"),
            labelsOf(page(buckets, descending = false).items)
        )
    }

    @Test
    fun `ordena por tokens e por nome`() {
        val buckets = listOf(
            bucket("zulu", costMicros = 900L, tokens = 1L),
            bucket("alpha", costMicros = 10L, tokens = 500L)
        )

        assertEquals(
            listOf("alpha", "zulu"),
            labelsOf(page(buckets, sort = BreakdownSort.VOLUME).items)
        )
        assertEquals(
            listOf("alpha", "zulu"),
            labelsOf(page(buckets, sort = BreakdownSort.NAME, descending = false).items)
        )
        assertEquals(
            listOf("zulu", "alpha"),
            labelsOf(page(buckets, sort = BreakdownSort.NAME, descending = true).items)
        )
    }

    /**
     * Duas leituras iguais têm de produzir listas iguais, ou o `StateFlow`
     * reemite e a tela recompõe a cada tique do laço ao vivo.
     */
    @Test
    fun `empate no custo é desempatado pelo rotulo`() {
        val buckets = listOf(
            bucket("zulu", costMicros = 100L),
            bucket("alpha", costMicros = 100L),
            bucket("mike", costMicros = 100L)
        )

        assertEquals(listOf("zulu", "mike", "alpha"), labelsOf(page(buckets).items))
        assertEquals(
            labelsOf(page(buckets).items),
            labelsOf(page(buckets.reversed()).items)
        )
    }

    // ------------------------------------------------------------------------
    // Paginação
    // ------------------------------------------------------------------------

    @Test
    fun `pagina corta e conta as paginas com sobra`() {
        val buckets = (1..12).map { index -> bucket("p$index", costMicros = index.toLong()) }

        val first = page(buckets, pageSize = 5)
        assertEquals(5, first.items.size)
        assertEquals(3, first.pageCount)
        assertEquals(0, first.fromIndex)
        assertFalse(first.hasPrevious)
        assertTrue(first.hasNext)

        val last = page(buckets, pageIndex = 2, pageSize = 5)
        assertEquals(2, last.items.size)
        assertEquals(10, last.fromIndex)
        assertTrue(last.hasPrevious)
        assertFalse(last.hasNext)
    }

    /** A lista encolhe a cada tique; a página que sumiu mostraria vazio. */
    @Test
    fun `pagina fora do intervalo cai na ultima que existe`() {
        val buckets = (1..3).map { index -> bucket("p$index") }

        val result = page(buckets, pageIndex = 9, pageSize = 5)

        assertEquals(0, result.pageIndex)
        assertEquals(3, result.items.size)
    }

    @Test
    fun `indice negativo cai na primeira pagina`() {
        val result = page(listOf(bucket("a")), pageIndex = -3)

        assertEquals(0, result.pageIndex)
    }

    /** "Página 1 de 0" não é uma frase. */
    @Test
    fun `lista vazia continua tendo uma pagina`() {
        val result = page(emptyList())

        assertTrue(result.items.isEmpty())
        assertEquals(1, result.pageCount)
        assertFalse(result.hasNext)
        assertFalse(result.hasPrevious)
    }

    @Test
    fun `filtro sem resultado nao quebra a paginacao`() {
        val result = page(listOf(bucket("alpha")), query = "zzz", pageIndex = 4)

        assertTrue(result.items.isEmpty())
        assertEquals(0, result.filteredCount)
        assertEquals(1, result.totalCount)
        assertEquals(0, result.pageIndex)
    }

    @Test
    fun `os tamanhos de pagina oferecidos sao os combinados`() {
        assertEquals(listOf(5, 10, 50, 100), BREAKDOWN_PAGE_SIZES)
    }

    // ------------------------------------------------------------------------
    // Ferramentas
    // ------------------------------------------------------------------------

    @Test
    fun `ferramentas filtram ordenam e paginam pelas proprias colunas`() {
        val tools = listOf(
            CliToolUsage("Read", callCount = 90, turnCount = 10),
            CliToolUsage("Bash", callCount = 40, turnCount = 30),
            CliToolUsage("Edit", callCount = 10, turnCount = 5)
        )

        val byCalls = pageOfTools(tools, "", BreakdownSort.SHARE, true, 0, 10)
        assertEquals(listOf("Read", "Bash", "Edit"), byCalls.items.map { it.toolName })

        // "Turnos" e não "tokens": a ferramenta não carrega custo.
        val byTurns = pageOfTools(tools, "", BreakdownSort.VOLUME, true, 0, 10)
        assertEquals(listOf("Bash", "Read", "Edit"), byTurns.items.map { it.toolName })

        val filtered = pageOfTools(tools, "ba", BreakdownSort.SHARE, true, 0, 10)
        assertEquals(listOf("Bash"), filtered.items.map { it.toolName })
        assertEquals(3, filtered.totalCount)

        val second = pageOfTools(tools, "", BreakdownSort.SHARE, true, 1, 2)
        assertEquals(listOf("Edit"), second.items.map { it.toolName })
        assertEquals(2, second.pageCount)
    }
}

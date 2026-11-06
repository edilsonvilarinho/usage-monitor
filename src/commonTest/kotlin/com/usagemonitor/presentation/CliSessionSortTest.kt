package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.CliSessionSummary
import com.usagemonitor.domain.entity.CodexCliSessionSummary
import com.usagemonitor.domain.entity.OutputThroughput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.time.Instant

class CliSessionSortTest {

    private fun claude(
        id: String,
        lastTs: String = "2026-11-06T12:00:00Z",
        inputTokens: Long = 0L,
        cacheReadTokens: Long = 0L,
        cacheWrite5mTokens: Long = 0L,
        costMicros: Long = 0L,
        activeMillis: Long? = 0L,
        throughput: OutputThroughput? = null
    ): CliSessionSummary = CliSessionSummary(
        sessionId = id,
        filePath = "/tmp/$id.jsonl",
        firstTs = Instant.parse("2026-11-06T08:00:00Z"),
        lastTs = Instant.parse(lastTs),
        inputTokens = inputTokens,
        cacheReadTokens = cacheReadTokens,
        cacheWrite5mTokens = cacheWrite5mTokens,
        costMicros = costMicros,
        activeMillis = activeMillis,
        throughput = throughput
    )

    private fun codex(
        id: String,
        lastTs: String = "2026-11-06T12:00:00Z",
        totalTokens: Long = 0L,
        inputTokens: Long = 0L,
        cachedInputTokens: Long = 0L,
        responseCount: Int = 0,
        throughput: OutputThroughput? = null
    ): CodexCliSessionSummary = CodexCliSessionSummary(
        sessionId = id,
        filePath = "/tmp/$id.jsonl",
        firstTs = Instant.parse("2026-11-06T08:00:00Z"),
        lastTs = Instant.parse(lastTs),
        totalTokens = totalTokens,
        inputTokens = inputTokens,
        cachedInputTokens = cachedInputTokens,
        responseCount = responseCount,
        throughput = throughput
    )

    private fun <T> assertColumnOrder(
        column: CliSessionSortColumn,
        build: (id: String, value: Long) -> T,
        sort: (List<T>, CliSessionSort?) -> List<T>,
        idOf: (T) -> String
    ) {
        val sessions = listOf(build("p", 100L), build("q", 300L), build("r", 200L))
        val descending = sort(sessions, CliSessionSort(column, descending = true)).map(idOf)
        val ascending = sort(sessions, CliSessionSort(column, descending = false)).map(idOf)
        assertEquals(listOf("q", "r", "p"), descending, "coluna $column, maior primeiro")
        assertEquals(listOf("p", "r", "q"), ascending, "coluna $column, menor primeiro")
    }

    @Test
    fun `clique repetido percorre maior, menor e depois padrao`() {
        val first = nextCliSessionSort(null, CliSessionSortColumn.TOKENS)
        assertEquals(CliSessionSort(CliSessionSortColumn.TOKENS, descending = true), first)
        val second = nextCliSessionSort(first, CliSessionSortColumn.TOKENS)
        assertEquals(CliSessionSort(CliSessionSortColumn.TOKENS, descending = false), second)
        assertNull(nextCliSessionSort(second, CliSessionSortColumn.TOKENS))
    }

    @Test
    fun `clicar outra coluna comeca em maior primeiro`() {
        val current = CliSessionSort(CliSessionSortColumn.TOKENS, descending = false)
        assertEquals(
            CliSessionSort(CliSessionSortColumn.COST, descending = true),
            nextCliSessionSort(current, CliSessionSortColumn.COST)
        )
    }

    @Test
    fun `valor nao medido fica no fim nas duas direcoes`() {
        val sessions = listOf(
            claude("a", activeMillis = null),
            claude("b", activeMillis = 300L),
            claude("c", activeMillis = 100L),
            claude("d", activeMillis = null)
        )
        val descending = sortCliSessions(sessions, CliSessionSort(CliSessionSortColumn.ACTIVE_TIME, descending = true))
        val ascending = sortCliSessions(sessions, CliSessionSort(CliSessionSortColumn.ACTIVE_TIME, descending = false))
        assertEquals(listOf("b", "c", "a", "d"), descending.map { it.sessionId }, "maior primeiro")
        assertEquals(listOf("c", "b", "a", "d"), ascending.map { it.sessionId }, "menor primeiro")
    }

    @Test
    fun `empate de valor desempata por lastTs decrescente e depois sessionId crescente`() {
        val sessions = listOf(
            claude("b", lastTs = "2026-11-06T09:00:00Z", costMicros = 500L),
            claude("c", lastTs = "2026-11-06T11:00:00Z", costMicros = 500L),
            claude("a", lastTs = "2026-11-06T09:00:00Z", costMicros = 500L),
            claude("x", lastTs = "2026-11-06T11:00:00Z", costMicros = 500L)
        )
        val expected = listOf("c", "x", "a", "b")
        val sort = CliSessionSort(CliSessionSortColumn.COST, descending = true)
        assertEquals(expected, sortCliSessions(sessions, sort).map { it.sessionId }, "ordem da entrada")
        assertEquals(expected, sortCliSessions(sessions.reversed(), sort).map { it.sessionId }, "entrada invertida")
    }

    @Test
    fun `sem ordem ativa devolve a mesma lista sem reordenar`() {
        val sessions = listOf(claude("b", costMicros = 1L), claude("a", costMicros = 9L))
        val codexSessions = listOf(codex("b", totalTokens = 1L), codex("a", totalTokens = 9L))
        assertSame(sessions, sortCliSessions(sessions, null))
        assertSame(codexSessions, sortCodexCliSessions(codexSessions, null))
    }

    @Test
    fun `cada coluna ordena as sessoes Anthropic pelo valor exibido`() {
        assertColumnOrder(
            CliSessionSortColumn.TOKENS,
            { id, v -> claude(id, inputTokens = v) },
            ::sortCliSessions,
            CliSessionSummary::sessionId
        )
        assertColumnOrder(
            CliSessionSortColumn.CACHE,
            { id, v -> claude(id, cacheReadTokens = v, cacheWrite5mTokens = 1_000L) },
            ::sortCliSessions,
            CliSessionSummary::sessionId
        )
        assertColumnOrder(
            CliSessionSortColumn.COST,
            { id, v -> claude(id, costMicros = v) },
            ::sortCliSessions,
            CliSessionSummary::sessionId
        )
        assertColumnOrder(
            CliSessionSortColumn.ACTIVE_TIME,
            { id, v -> claude(id, activeMillis = v) },
            ::sortCliSessions,
            CliSessionSummary::sessionId
        )
        assertColumnOrder(
            CliSessionSortColumn.THROUGHPUT,
            { id, v -> claude(id, throughput = OutputThroughput(outputTokens = v, generationMillis = 1_000L)) },
            ::sortCliSessions,
            CliSessionSummary::sessionId
        )
    }

    @Test
    fun `cada coluna ordena as sessoes Codex pelo valor exibido`() {
        assertColumnOrder(
            CliSessionSortColumn.TOKENS,
            { id, v -> codex(id, totalTokens = v) },
            ::sortCodexCliSessions,
            CodexCliSessionSummary::sessionId
        )
        assertColumnOrder(
            CliSessionSortColumn.CACHE,
            { id, v -> codex(id, inputTokens = 1_000L, cachedInputTokens = v) },
            ::sortCodexCliSessions,
            CodexCliSessionSummary::sessionId
        )
        assertColumnOrder(
            CliSessionSortColumn.RESPONSES,
            { id, v -> codex(id, responseCount = v.toInt()) },
            ::sortCodexCliSessions,
            CodexCliSessionSummary::sessionId
        )
        assertColumnOrder(
            CliSessionSortColumn.THROUGHPUT,
            { id, v -> codex(id, throughput = OutputThroughput(outputTokens = v, generationMillis = 1_000L)) },
            ::sortCodexCliSessions,
            CodexCliSessionSummary::sessionId
        )
    }

    @Test
    fun `coluna sem valor no tipo cai no desempate por lastTs e sessionId`() {
        val claudeSessions = listOf(
            claude("b", lastTs = "2026-11-06T09:00:00Z"),
            claude("a", lastTs = "2026-11-06T09:00:00Z"),
            claude("c", lastTs = "2026-11-06T11:00:00Z")
        )
        assertEquals(
            listOf("c", "a", "b"),
            sortCliSessions(claudeSessions, CliSessionSort(CliSessionSortColumn.RESPONSES)).map { it.sessionId },
            "Anthropic sem coluna Responses"
        )
        val codexSessions = listOf(
            codex("b", lastTs = "2026-11-06T09:00:00Z"),
            codex("a", lastTs = "2026-11-06T09:00:00Z"),
            codex("c", lastTs = "2026-11-06T11:00:00Z")
        )
        assertEquals(
            listOf("c", "a", "b"),
            sortCodexCliSessions(codexSessions, CliSessionSort(CliSessionSortColumn.COST)).map { it.sessionId },
            "Codex sem coluna Custo"
        )
        assertEquals(
            listOf("c", "a", "b"),
            sortCodexCliSessions(codexSessions, CliSessionSort(CliSessionSortColumn.ACTIVE_TIME)).map { it.sessionId },
            "Codex sem coluna Tempo ativo"
        )
    }

    @Test
    fun `razao de cache do Codex e a exibida e nula sem entrada medida`() {
        assertEquals(0.25, codexCacheRateOf(codex("a", inputTokens = 400L, cachedInputTokens = 100L)))
        assertNull(codexCacheRateOf(codex("b", inputTokens = 0L)))
    }
}

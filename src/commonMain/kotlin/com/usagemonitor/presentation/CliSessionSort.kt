package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.CliSessionSummary
import com.usagemonitor.domain.entity.CodexCliSessionSummary
import kotlin.time.Instant

/** Coluna ordenável das listas de sessões CLI (#537). Os valores são os exibidos na linha. */
enum class CliSessionSortColumn { TOKENS, CACHE, COST, ACTIVE_TIME, RESPONSES, THROUGHPUT }

/** Ordem ativa de uma lista de sessões. `null` no estado da tela é o padrão da consulta. */
data class CliSessionSort(val column: CliSessionSortColumn, val descending: Boolean = true)

/**
 * Próximo estado do clique no cabeçalho: maior primeiro → menor primeiro → padrão.
 * Clicar outra coluna começa em maior primeiro.
 */
fun nextCliSessionSort(current: CliSessionSort?, column: CliSessionSortColumn): CliSessionSort? {
    if (current == null || current.column != column) return CliSessionSort(column, descending = true)
    return if (current.descending) CliSessionSort(column, descending = false) else null
}

/**
 * Ordena as sessões Anthropic pela coluna. Padrão (`null`) devolve a lista como veio, sem reordenar.
 * Valor não medido (`null`) fica sempre no fim.
 */
fun sortCliSessions(sessions: List<CliSessionSummary>, sort: CliSessionSort?): List<CliSessionSummary> {
    if (sort == null) return sessions
    return orderCliSessions(
        items = sessions,
        descending = sort.descending,
        valueOf = { session -> anthropicValueOf(session, sort.column) },
        lastTsOf = { session -> session.lastTs },
        sessionIdOf = { session -> session.sessionId }
    )
}

/** Mesma regra de [sortCliSessions], para as sessões Codex. */
fun sortCodexCliSessions(
    sessions: List<CodexCliSessionSummary>,
    sort: CliSessionSort?
): List<CodexCliSessionSummary> {
    if (sort == null) return sessions
    return orderCliSessions(
        items = sessions,
        descending = sort.descending,
        valueOf = { session -> codexValueOf(session, sort.column) },
        lastTsOf = { session -> session.lastTs },
        sessionIdOf = { session -> session.sessionId }
    )
}

/**
 * Razão de cache do Codex, a mesma que a coluna exibe (`cachedInputTokens / inputTokens`).
 * `null` sem entrada medida, que a tela mostra como "— %".
 */
fun codexCacheRateOf(session: CodexCliSessionSummary): Double? {
    if (session.inputTokens <= 0L) return null
    return session.cachedInputTokens.toDouble() / session.inputTokens.toDouble()
}

private fun anthropicValueOf(session: CliSessionSummary, column: CliSessionSortColumn): Double? {
    return when (column) {
        CliSessionSortColumn.TOKENS -> session.totalTokens.toDouble()
        CliSessionSortColumn.CACHE -> session.cacheHitRate
        CliSessionSortColumn.COST -> session.costMicros.toDouble()
        CliSessionSortColumn.ACTIVE_TIME -> session.activeMillis?.toDouble()
        CliSessionSortColumn.RESPONSES -> null
        CliSessionSortColumn.THROUGHPUT -> session.throughput?.tokensPerSecond
    }
}

/** Colunas sem valor no Codex (custo, tempo ativo) ficam `null` e caem no desempate. */
private fun codexValueOf(session: CodexCliSessionSummary, column: CliSessionSortColumn): Double? {
    return when (column) {
        CliSessionSortColumn.TOKENS -> session.totalTokens.toDouble()
        CliSessionSortColumn.CACHE -> codexCacheRateOf(session)
        CliSessionSortColumn.COST -> null
        CliSessionSortColumn.ACTIVE_TIME -> null
        CliSessionSortColumn.RESPONSES -> session.responseCount.toDouble()
        CliSessionSortColumn.THROUGHPUT -> session.throughput?.tokensPerSecond
    }
}

/**
 * Núcleo da ordem: valor (`null` no fim nas duas direções), depois `lastTs` decrescente, depois
 * `sessionId` crescente. Ordem total, para a lista não recompor a cada tique.
 */
private fun <T> orderCliSessions(
    items: List<T>,
    descending: Boolean,
    valueOf: (T) -> Double?,
    lastTsOf: (T) -> Instant,
    sessionIdOf: (T) -> String
): List<T> {
    val byValue = Comparator<T> { a, b ->
        val valueA = valueOf(a)
        val valueB = valueOf(b)
        when {
            valueA == null && valueB == null -> 0
            valueA == null -> 1
            valueB == null -> -1
            descending -> valueB.compareTo(valueA)
            else -> valueA.compareTo(valueB)
        }
    }
    return items.sortedWith(
        byValue
            .thenByDescending { item -> lastTsOf(item) }
            .thenBy { item -> sessionIdOf(item) }
    )
}

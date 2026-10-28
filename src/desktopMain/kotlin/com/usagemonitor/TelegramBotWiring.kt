package com.usagemonitor

import com.usagemonitor.domain.entity.HistoryRange
import com.usagemonitor.domain.entity.TelegramAccountHistory
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.SourceHealthObservation
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.viewmodel.UiApiError
import com.usagemonitor.domain.entity.snapshotLabel
import kotlin.time.Instant
import com.usagemonitor.domain.entity.ComparisonRow
import com.usagemonitor.domain.entity.displayName
import com.usagemonitor.domain.entity.TeamMemberPresence
import com.usagemonitor.domain.usecase.GetUsageHistoryUseCase
import com.usagemonitor.domain.usecase.TeamPresenceResult
import com.usagemonitor.presentation.viewmodel.UiState
import com.usagemonitor.presentation.ui.TelegramChart
import com.usagemonitor.presentation.ui.buildTelegramChart
import kotlin.time.Clock

/**
 * A presença do time (#408, `/time`) nas mesmas contas do semáforo dos cards
 * (`buildSessionPulseTargets`: integração ligada e perfil participando). Uma pessoa em
 * duas contas aparece uma vez. Todas as leituras falhando é falha; algumas, não.
 */
internal fun teamPresenceReader(
    presence: suspend (accountKey: String) -> Result<TeamPresenceResult>,
    accountKeys: () -> List<String>
): suspend () -> Result<List<TeamMemberPresence>>? {
    return reader@{
        val keys = accountKeys().distinct()
        if (keys.isEmpty()) return@reader null
        val results = keys.map { key -> presence(key) }
        val failure = results.firstOrNull { result -> result.isFailure }?.exceptionOrNull()
        if (failure != null && results.all { result -> result.isFailure }) return@reader Result.failure(failure)
        Result.success(results.flatMap { result -> result.getOrNull()?.entries.orEmpty() }.distinctBy { entry -> entry.memberKey })
    }
}

/**
 * Fonte quebrada (#408, AA18): uma observação por conta da última coleta. A falha leva a
 * mensagem que a tela mostra (já saneada) e a hora dos números mantidos na tela; o 429 entra
 * como falha — a fonte de fato parou de atualizar, e a mensagem diz que é limite.
 */
internal fun sourceHealthOf(state: UiState): List<SourceHealthObservation>? {
    val data: List<ApiUsageStats>
    val errors: List<UiApiError>
    when (state) {
        is UiState.Success -> {
            data = state.data
            errors = state.errors
        }
        is UiState.Error -> {
            data = emptyList()
            errors = state.errors
        }
        UiState.NoApisEnabled -> return emptyList()
        UiState.Loading -> return null
    }
    val failingKeys = errors.map { error -> error.target.storageKey }.toSet()
    val failing = errors.map { error ->
        SourceHealthObservation(
            key = error.target.storageKey,
            label = error.targetLabel?.takeIf { label -> label.isNotBlank() } ?: error.source.displayName(),
            failure = error.message,
            lastReadingAt = data.firstOrNull { stats -> stats.targetKey == error.target }?.fetchedAt
        )
    }
    val healthy = data.filter { stats -> stats.targetKey.storageKey !in failingKeys }
        .map { stats -> SourceHealthObservation(stats.targetKey.storageKey, stats.snapshotLabel, failure = null) }
    return failing + healthy
}

/** A comparação entre modelos (#386) desde um instante, com as cotas da última coleta. */
internal fun comparisonReader(
    buildComparison: suspend (sinceEpochMillis: Long, stats: List<ApiUsageStats>) -> Result<List<ComparisonRow>>,
    stats: () -> List<ApiUsageStats>
): suspend (Long) -> List<ComparisonRow>? = { since ->
    buildComparison(since, stats()).getOrNull()
}

/**
 * O histórico de cada conta da última coleta, pela mesma chave que o painel usa
 * para a projeção (`accountContext.key`). Conta cujo SQLite falha fica fora.
 */
internal fun accountHistoryReader(
    history: GetUsageHistoryUseCase,
    stats: () -> List<ApiUsageStats>,
    clock: Clock = Clock.System
): suspend (HistoryRange) -> List<TelegramAccountHistory> = { range ->
    val now = clock.now()
    stats().mapNotNull { item ->
        runCatching { TelegramAccountHistory(item.snapshotLabel, history(item.source, range, item.accountContext?.key, now)) }.getOrNull()
    }
}

/**
 * Histórico das fontes ligadas para o `/grafico` (#398, Y6). Uma série por conta:
 * sem `listAccounts`, a fonte tem uma conta só e vai sem chave. O nome é o da fonte,
 * numerado quando há mais de uma conta — nunca o e-mail da conta.
 */
internal suspend fun telegramChartOf(
    history: GetUsageHistoryUseCase,
    enabledSources: Set<ApiSource>,
    language: AppLanguage,
    range: HistoryRange,
    now: Instant
): TelegramChart? {
    val reports = enabledSources.sortedBy { source -> source.ordinal }.flatMap { source ->
        val accounts = runCatching { history.listAccounts(source) }.getOrDefault(emptyList())
        if (accounts.isEmpty()) {
            listOfNotNull(runCatching { source.displayName() to history(source, range, null, now) }.getOrNull())
        } else {
            accounts.mapIndexedNotNull { index, account ->
                val name = if (accounts.size > 1) "${source.displayName()} ${index + 1}" else source.displayName()
                runCatching { name to history(source, range, account.key, now) }.getOrNull()
            }
        }
    }
    return buildTelegramChart(reports, range, now, language).takeUnless { chart -> chart.isEmpty }
}

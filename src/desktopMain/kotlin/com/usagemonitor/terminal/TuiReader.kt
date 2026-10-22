package com.usagemonitor.terminal

import com.usagemonitor.data.datasource.LocalCollectorHealthDataSource
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CollectorHealth
import com.usagemonitor.presentation.terminal.TerminalGlyphs
import com.usagemonitor.domain.entity.HistoryRange
import com.usagemonitor.domain.entity.displayName
import com.usagemonitor.presentation.terminal.TerminalAccount
import com.usagemonitor.presentation.terminal.TuiAccount
import com.usagemonitor.presentation.terminal.TuiActiveSession
import com.usagemonitor.presentation.terminal.TuiData
import com.usagemonitor.presentation.terminal.TuiHistory
import com.usagemonitor.presentation.terminal.TuiHistoryRow
import com.usagemonitor.presentation.terminal.TuiHistoryState
import com.usagemonitor.presentation.terminal.TuiReadFailure
import com.usagemonitor.presentation.terminal.TuiSpark
import com.usagemonitor.presentation.terminal.TuiSparkline
import com.usagemonitor.presentation.terminal.TuiNotice
import com.usagemonitor.presentation.terminal.TuiSessionsLabels
import com.usagemonitor.presentation.terminal.TuiState
import com.usagemonitor.presentation.terminal.TuiTab
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * O que o modo interativo lê a cada releitura (#441). Só leitura: o mesmo cache e as mesmas
 * preferências dos comandos da #439, os bancos do app pelos [TuiStores] e a saúde que o
 * coletor grava (#440). Nenhuma requisição, nenhuma trava e nada gravado.
 */
internal class TuiReader(
    private val reader: TerminalReader = TerminalReader(),
    /** Os bancos do app em somente leitura; sem eles, as abas que dependem deles ficam vazias. */
    private val stores: TuiStores? = null,
    /** A aba 4; `null` sem os meios de ler o servidor do time. */
    private val team: TuiTeamReader? = null,
    private val health: LocalCollectorHealthDataSource = LocalCollectorHealthDataSource(),
    private val languageOverride: AppLanguage? = null,
    /** A aba 5, com os glifos do perfil (a máscara do segredo); `null` nos testes das outras abas. */
    private val settings: TuiSettingsReader? = null,
    private val glyphs: TerminalGlyphs = TerminalGlyphs.UNICODE
) {
    private val sessionsReader = stores?.let(::TuiSessionsReader)

    /** Cada aba lê o que é dela: a Visão geral não paga a lista de sessões, e o contrário. */
    fun load(state: TuiState): TuiData {
        // A TUI fica aberta enquanto o coletor grava: sem isto, preferência nova chega 30 s depois.
        reader.syncPreferences()
        val reading = reader.read(languageOverride)
        // A saúde só vale com coletor vivo: sem ele, o arquivo é de uma execução que já acabou.
        val collectorHealth = if (reading.collector != null) health.read() else null
        return TuiData(
            now = reading.now,
            collector = reading.collector,
            lastCollectedAt = reading.lastCollectedAt,
            teamConfigured = team?.available() == true,
            accounts = reading.accounts.map { account ->
                val overview = state.tab == TuiTab.OVERVIEW
                TuiAccount(
                    account = account,
                    failure = failureOf(account, collectorHealth),
                    activeSession = if (overview) activeSession(account, reading.now) else null,
                    last24h = if (overview) last24h(account, reading.now) else null
                )
            },
            sessions = if (state.tab == TuiTab.SESSIONS) sessionsReader?.load(state.sessions, reading.now) else null,
            history = if (state.tab == TuiTab.HISTORY) history(reading.accounts, state.history.range, reading.now) else null,
            team = if (state.tab == TuiTab.TEAM) team?.load(state.team, reading.now) else null,
            settings = if (state.tab == TuiTab.SETTINGS) settings?.load(reading.language, glyphs) else null
        )
    }

    /** `e` na aba 2. Sem os bancos, não há o que exportar. */
    fun exportSessions(state: TuiState): TuiNotice? =
        sessionsReader?.export(state.sessions, reader.read(languageOverride).now, TuiSessionsLabels(reader.language(languageOverride)))

    /**
     * A falha da última coleta desta conta. Pela chave do alvo quando o coletor a grava; o
     * coletor anterior à #441 só grava o rótulo, montado como em `collectorHealthOf`.
     */
    private fun failureOf(account: TerminalAccount, health: CollectorHealth?): TuiReadFailure? {
        val sources = health?.sources ?: return null
        val label = listOfNotNull(account.source.displayName(), account.profile).joinToString(" ")
        val entry = sources.firstOrNull { source -> source.target == account.storageKey }
            ?: sources.firstOrNull { source -> source.target == null && source.source == account.source && source.label == label }
            ?: return null
        if (entry.ok) {
            return null
        }
        val retryAt = entry.retryAtEpochMillis ?: health.nextCollectionAtEpochMillis
        return TuiReadFailure(reason = entry.failure, attemptedAt = null, retryAt = retryAt?.let(Instant::fromEpochMilliseconds))
    }

    /**
     * A sessão mais recente da conta, se teve atividade nos últimos [ACTIVE_WINDOW]. Só as
     * fontes com índice de sessões: o Claude Code por perfil e o Codex padrão. Custo da sessão
     * inteira, recalculado pelo índice; o Codex não tem tarifa, e o custo fica `null`.
     */
    private fun activeSession(account: TerminalAccount, now: Instant): TuiActiveSession? {
        val stores = stores ?: return null
        val recentSince = now - ACTIVE_WINDOW
        return when {
            account.source == ApiSource.ANTHROPIC && account.targetProfileId != null -> {
                val sessions = runBlocking { stores.cliSessions(account.targetProfileId, sinceEpochMillis = null) }.getOrNull() ?: return null
                val session = sessions.firstOrNull { item -> item.lastTs >= recentSince } ?: return TuiActiveSession.NONE
                TuiActiveSession(
                    project = session.projectName,
                    model = session.primaryModel,
                    durationMillis = session.activeMillis ?: (session.lastTs - session.firstTs).inWholeMilliseconds,
                    costMicros = session.costMicros,
                    costIncomplete = !session.isCostComplete,
                    throughput = session.throughput
                )
            }
            account.source == ApiSource.CODEX && account.targetProfileId == null -> {
                val sessions = runBlocking { stores.codexSessions(sinceEpochMillis = null) }.getOrNull() ?: return null
                val session = sessions.firstOrNull { item -> item.lastTs >= recentSince } ?: return TuiActiveSession.NONE
                TuiActiveSession(
                    project = session.projectName,
                    model = session.primaryModel,
                    durationMillis = (session.lastTs - session.firstTs).inWholeMilliseconds,
                    costMicros = null,
                    costIncomplete = false,
                    throughput = session.throughput
                )
            }
            else -> null
        }
    }

    /**
     * A aba 3: uma trilha por cota com percentual, na ordem do painel, pelo mesmo caso de uso
     * do Histórico do app. Uma consulta por conta; conta sem histórico fica com a trilha vazia.
     */
    private fun history(accounts: List<TerminalAccount>, range: HistoryRange, now: Instant): TuiHistory? {
        val stores = stores ?: return null
        val start = range.windowStart(now)
        val rows = accounts.flatMap { account ->
            val quotas = account.quotas.filter { quota -> quota.percent != null }
            if (quotas.isEmpty()) {
                return@flatMap emptyList()
            }
            val report = runCatching { runBlocking { stores.getUsageHistory(account.source, range, account.accountKey, now) } }.getOrNull()
            quotas.map { quota ->
                val series = report?.series?.firstOrNull { item -> item.quotaLabel == quota.label && item.periodType == quota.periodType }
                TuiHistoryRow(account, quota, series?.let { found -> TuiSparkline.build(found.points, start, now, TuiHistoryState.cellCount(range)) })
            }
        }
        return TuiHistory(range, rows)
    }

    /** As últimas 24 h da primeira cota com percentual, em células de meia hora. */
    private fun last24h(account: TerminalAccount, now: Instant): TuiSpark? {
        val stores = stores ?: return null
        val quota = account.quotas.firstOrNull { item -> item.percent != null } ?: return null
        val report = runCatching { runBlocking { stores.getUsageHistory(account.source, HistoryRange.LAST_24_HOURS, account.accountKey, now) } }.getOrNull() ?: return null
        val series = report.series.firstOrNull { item -> item.quotaLabel == quota.label && item.periodType == quota.periodType } ?: return null
        return TuiSparkline.build(series.points, now - DAY, now, cellCount = HALF_HOURS_PER_DAY)
    }

    private companion object {
        val ACTIVE_WINDOW: Duration = 10.minutes
        val DAY: Duration = 24.hours
        const val HALF_HOURS_PER_DAY = 48
    }
}

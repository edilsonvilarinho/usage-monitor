package com.usagemonitor.terminal

import com.usagemonitor.data.datasource.LocalCollectorHealthDataSource
import com.usagemonitor.data.datasource.LocalTeamSettingsDataSource
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CollectorHealth
import com.usagemonitor.domain.entity.HistoryRange
import com.usagemonitor.domain.entity.displayName
import com.usagemonitor.presentation.terminal.TerminalAccount
import com.usagemonitor.presentation.terminal.TuiAccount
import com.usagemonitor.presentation.terminal.TuiActiveSession
import com.usagemonitor.presentation.terminal.TuiData
import com.usagemonitor.presentation.terminal.TuiReadFailure
import com.usagemonitor.presentation.terminal.TuiSpark
import com.usagemonitor.presentation.terminal.TuiSparkline
import com.usagemonitor.presentation.terminal.TuiState
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
    private val teamSettings: LocalTeamSettingsDataSource = LocalTeamSettingsDataSource(),
    private val health: LocalCollectorHealthDataSource = LocalCollectorHealthDataSource(),
    private val languageOverride: AppLanguage? = null
) {
    @Suppress("UNUSED_PARAMETER")
    fun load(state: TuiState): TuiData {
        val reading = reader.read(languageOverride)
        val team = teamSettings.peek()
        // A saúde só vale com coletor vivo: sem ele, o arquivo é de uma execução que já acabou.
        val collectorHealth = if (reading.collector != null) health.read() else null
        return TuiData(
            now = reading.now,
            collector = reading.collector,
            lastCollectedAt = reading.lastCollectedAt,
            teamConfigured = team != null && team.enabled && team.normalizedServerUrl.isNotEmpty() && team.apiKey.isNotBlank(),
            accounts = reading.accounts.map { account ->
                TuiAccount(
                    account = account,
                    failure = failureOf(account, collectorHealth),
                    activeSession = activeSession(account, reading.now),
                    last24h = last24h(account, reading.now)
                )
            }
        )
    }

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

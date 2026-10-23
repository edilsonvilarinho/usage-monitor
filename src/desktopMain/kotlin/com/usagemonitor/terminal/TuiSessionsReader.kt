package com.usagemonitor.terminal

import com.usagemonitor.data.export.DefaultUsageExportEncoder
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.CliSessionSummary
import com.usagemonitor.domain.entity.CodexCliSessionSummary
import com.usagemonitor.domain.entity.ModelContextWindowTable
import com.usagemonitor.domain.entity.UsageExportFormat
import com.usagemonitor.presentation.terminal.TuiNotice
import com.usagemonitor.presentation.terminal.TuiSession
import com.usagemonitor.presentation.terminal.TuiSessionPeriod
import com.usagemonitor.presentation.terminal.TuiSessions
import com.usagemonitor.presentation.terminal.TuiSessionsLabels
import com.usagemonitor.presentation.terminal.TuiSessionsState
import com.usagemonitor.presentation.terminal.TuiTurn
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.io.File
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * A aba 2 sobre os bancos do app (#441): as sessões do Claude Code (todas as contas) e as do
 * Codex no corte pedido, e os turnos da aberta. Somente leitura, pelos [TuiStores].
 */
internal class TuiSessionsReader(
    private val stores: TuiStores,
    /** Onde o CSV vai: a pasta de onde a TUI foi aberta, como qualquer comando de terminal. */
    private val exportDirectory: File = File(System.getProperty("user.dir").orEmpty())
) {
    fun load(state: TuiSessionsState, now: Instant): TuiSessions {
        val since = periodStart(state.period, now).toEpochMilliseconds()
        val claude = runBlocking { stores.cliSessions(profileId = null, sinceEpochMillis = since) }
        val codex = runBlocking { stores.codexSessions(sinceEpochMillis = since) }
        // Uma das duas basta: o índice do Codex ausente não esconde as sessões do Claude Code.
        if (claude.isFailure && codex.isFailure) {
            return TuiSessions(state.period, emptyList(), failure = claude.exceptionOrNull()?.message ?: "?")
        }
        val sessions = (claude.getOrNull().orEmpty().map(::fromClaude) + codex.getOrNull().orEmpty().map(::fromCodex))
            .sortedWith(compareByDescending<TuiSession> { session -> session.lastAt }.thenBy { session -> session.id })
        val turns = if (state.turnsOpen) sessions.getOrNull(state.selected.coerceIn(0, (sessions.size - 1).coerceAtLeast(0)))?.let(::turnsOf) else null
        return TuiSessions(state.period, sessions, turns)
    }

    /**
     * `e`: as sessões do Claude Code do corte no CSV do app (`UsageExporter`), as mesmas
     * colunas da exportação da janela de Sessões. O Codex não entra: a exportação do app é
     * só do índice do Claude Code. Só metadados de uso, nunca prompt nem resposta.
     */
    fun export(state: TuiSessionsState, now: Instant, labels: TuiSessionsLabels): TuiNotice {
        val since = periodStart(state.period, now).toEpochMilliseconds()
        val sessions = runBlocking { stores.cliSessions(profileId = null, sinceEpochMillis = since) }
            .getOrElse { error -> return TuiNotice(labels.exportFailed(error.message ?: "?"), ok = false) }
        if (sessions.isEmpty()) {
            return TuiNotice(labels.nothingToExport, ok = false)
        }
        val local = now.toLocalDateTime(SAO_PAULO)
        val stamp = "%04d%02d%02d-%02d%02d".format(local.year, local.monthNumber, local.dayOfMonth, local.hour, local.minute)
        val period = when (state.period) {
            TuiSessionPeriod.TODAY -> "today"
            TuiSessionPeriod.LAST_7_DAYS -> "7d"
            TuiSessionPeriod.LAST_30_DAYS -> "30d"
        }
        val file = File(exportDirectory, "usage-monitor-sessions-$period-$stamp.${UsageExportFormat.CSV.extension}")
        return runCatching { file.writeText(DefaultUsageExportEncoder.encodeSessions(sessions, UsageExportFormat.CSV)) }
            .map { TuiNotice(labels.exported(file.name, sessions.size), ok = true) }
            .getOrElse { error -> TuiNotice(labels.exportFailed(error.message ?: "?"), ok = false) }
    }

    private fun fromClaude(session: CliSessionSummary): TuiSession =
        TuiSession(
            id = session.sessionId,
            source = ApiSource.ANTHROPIC,
            project = session.projectName,
            // O que mais pesou na fatia, que é o que a linha de modelos mostra primeiro (#445).
            model = session.modelMix.leadingModel ?: session.primaryModel,
            startedAt = session.firstTs,
            lastAt = session.lastTs,
            durationMillis = session.activeMillis ?: (session.lastTs - session.firstTs).inWholeMilliseconds,
            turnCount = session.turnCount,
            inputTokens = session.inputTokens,
            outputTokens = session.outputTokens,
            cacheReadTokens = session.cacheReadTokens,
            cacheWriteTokens = session.cacheWriteTokens,
            costMicros = session.costMicros,
            costIncomplete = !session.isCostComplete,
            throughput = session.throughput,
            contextTokens = session.liveContextTokens.takeIf { tokens -> tokens > 0L },
            contextWindow = ModelContextWindowTable.forModel(session.liveContextModel ?: session.primaryModel),
            modelMix = session.modelMix
        )

    /** O Codex não tem tarifa no app: custo `null`, nunca zero. */
    private fun fromCodex(session: CodexCliSessionSummary): TuiSession =
        TuiSession(
            id = session.sessionId,
            source = ApiSource.CODEX,
            project = session.projectName,
            model = session.modelMix.leadingModel ?: session.primaryModel,
            startedAt = session.firstTs,
            lastAt = session.lastTs,
            durationMillis = (session.lastTs - session.firstTs).inWholeMilliseconds,
            turnCount = session.turnCount,
            inputTokens = (session.inputTokens - session.cachedInputTokens).coerceAtLeast(0L),
            outputTokens = session.outputTokens,
            cacheReadTokens = session.cachedInputTokens,
            cacheWriteTokens = session.cacheWriteInputTokens,
            costMicros = null,
            costIncomplete = false,
            throughput = session.throughput,
            modelMix = session.modelMix
        )

    private fun turnsOf(session: TuiSession): List<TuiTurn> =
        when (session.source) {
            ApiSource.CODEX -> runBlocking { stores.getCodexSessionDetail(session.id) }.getOrNull()?.turns.orEmpty().map { turn ->
                TuiTurn(turn.ts, turn.model, turn.usage.billableInputTokens, turn.usage.outputTokens, turn.usage.cachedInputTokens, costMicros = null)
            }
            else -> runBlocking { stores.getCliSessionDetail(session.id) }.getOrNull()?.detail?.turns.orEmpty().map { turn ->
                TuiTurn(turn.ts, turn.model, turn.inputTokens + turn.cacheWriteTokens, turn.outputTokens, turn.cacheReadTokens, turn.costMicros)
            }
        }.sortedByDescending { turn -> turn.at }

    private companion object {
        val SAO_PAULO: TimeZone = TimeZone.of("America/Sao_Paulo")

        fun periodStart(period: TuiSessionPeriod, now: Instant): Instant =
            when (period) {
                TuiSessionPeriod.TODAY -> startOfTodayBrt(now)
                TuiSessionPeriod.LAST_7_DAYS -> now - 7.days
                TuiSessionPeriod.LAST_30_DAYS -> now - 30.days
            }
    }
}

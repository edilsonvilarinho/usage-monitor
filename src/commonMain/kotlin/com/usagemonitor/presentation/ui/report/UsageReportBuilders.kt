package com.usagemonitor.presentation.ui.report

import com.usagemonitor.domain.entity.ACTIVITY_TIME_ZONE_ID
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CliSessionRange
import com.usagemonitor.domain.entity.CliSessionSummary
import com.usagemonitor.domain.entity.CliToolUsage
import com.usagemonitor.domain.entity.CliUsageBucket
import com.usagemonitor.presentation.ui.BreakdownAxis
import com.usagemonitor.presentation.ui.BreakdownLabels
import com.usagemonitor.presentation.ui.CliSessionsLabels
import com.usagemonitor.presentation.ui.formatActiveTime
import com.usagemonitor.presentation.ui.formatInstant
import com.usagemonitor.presentation.ui.formatMicrosUsd
import com.usagemonitor.presentation.ui.formatPercent
import com.usagemonitor.presentation.ui.formatQuantity
import com.usagemonitor.presentation.ui.shortSessionId
import com.usagemonitor.presentation.viewmodel.CliSessionsUiState
import com.usagemonitor.presentation.viewmodel.TeamUsageUiState
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Relatório da tela de Sessões CLI, no recorte que está na tela.
 *
 * Função pura: recebe o estado, devolve o documento. Nenhuma leitura nova —
 * exportar um recorte diferente do exibido seria surpresa, a mesma regra que a
 * exportação CSV/JSON já segue.
 */
fun reportForCliSessions(
    state: CliSessionsUiState.Success,
    language: AppLanguage,
    now: Instant,
    timeZone: TimeZone = TimeZone.of(ACTIVITY_TIME_ZONE_ID)
): UsageReportDocument {
    val breakdown = state.breakdown
    val sections = mutableListOf<UsageReportSection>()
    val footnotes = mutableListOf<String>()

    sections += UsageReportSection.KeyValues(
        heading = ReportLabels.totals(language),
        entries = buildList {
            add(UsageReportEntry(ReportLabels.sessions(language), state.sessions.size.toString()))
            add(UsageReportEntry(ReportLabels.turns(language), state.sessions.sumOf { it.turnCount }.toString()))
            add(UsageReportEntry(CliSessionsLabels.columnTokens(language), formatQuantity(state.totalTokens)))
            add(
                UsageReportEntry(
                    CliSessionsLabels.columnCost(language),
                    costCell(state.totalCostMicros, state.isTotalCostComplete)
                )
            )
            val activeMillis = state.totalActiveMillis
            if (activeMillis != null && activeMillis > 0L) {
                add(UsageReportEntry(CliSessionsLabels.activeTime(language), formatActiveTime(activeMillis)))
            }
            if (breakdown != null) {
                add(
                    UsageReportEntry(
                        ReportLabels.cacheSavings(language),
                        formatMicrosUsd(breakdown.totals.cacheSavingsMicros)
                    )
                )
                val burnRate = breakdown.burnRate
                if (burnRate != null) {
                    add(
                        UsageReportEntry(
                            ReportLabels.burnRate(language),
                            "${formatMicrosUsd(burnRate.costMicrosPerHour)}/h"
                        )
                    )
                }
            }
        }
    )

    if (breakdown != null) {
        sections += bucketTable(BreakdownAxis.PROJECT, breakdown.byProject, language)
        sections += bucketTable(BreakdownAxis.BRANCH, breakdown.byBranch, language)
        sections += bucketTable(BreakdownAxis.MODEL, breakdown.byModel, language)

        if (breakdown.byTool.isNotEmpty()) {
            sections += toolTable(breakdown.byTool, language)
        }
        if (!breakdown.heatmap.isEmpty) {
            sections += UsageReportSection.Grid(
                heading = BreakdownLabels.axisTab(BreakdownAxis.ACTIVITY, language),
                heatmap = breakdown.heatmap,
                note = ReportLabels.activityNote(language)
            )
        }
        footnotes += BreakdownLabels.axisNotice(language)
    }

    sections += sessionTable(state.sessions, language)

    if (!state.isTotalCostComplete) {
        footnotes += CliSessionsLabels.estimatedCostNotice(language)
    }
    footnotes += ReportLabels.activeTimeNote(language)

    return UsageReportDocument(
        title = ReportLabels.cliTitle(language),
        subtitle = subtitleOf(
            scopeLabel = state.profileLabel ?: ReportLabels.allAccounts(language),
            range = state.range,
            language = language,
            now = now,
            timeZone = timeZone
        ),
        sections = sections,
        period = periodOf(
            range = state.range,
            rangeStartsAt = state.rangeStartsAt,
            earliestActivityAt = state.sessions.minOfOrNull { session -> session.firstTs },
            language = language,
            now = now,
            timeZone = timeZone
        ),
        footnotes = footnotes
    ).sanitized()
}

/**
 * Relatório do modal de Sessões do time.
 *
 * Sem grade de atividade e sem ferramentas: o servidor agrega por sessão e
 * modelo, nunca por hora nem por ferramenta, e a aba de resumo do time já pula
 * essas seções pelo mesmo motivo. Seção vazia num relatório é pior que seção
 * ausente — sugere que não houve atividade.
 */
fun reportForTeam(
    state: TeamUsageUiState.Success,
    language: AppLanguage,
    now: Instant,
    timeZone: TimeZone = TimeZone.of(ACTIVITY_TIME_ZONE_ID)
): UsageReportDocument {
    val breakdown = state.breakdown
    val sections = mutableListOf<UsageReportSection>()
    val footnotes = mutableListOf<String>()

    val totalActiveMillis = state.members.mapNotNull { member -> member.totalActiveMillis }
        .takeIf { measured -> measured.isNotEmpty() }
        ?.sum()

    sections += UsageReportSection.KeyValues(
        heading = ReportLabels.totals(language),
        entries = buildList {
            add(UsageReportEntry(ReportLabels.members(language), state.activeMemberCount.toString()))
            add(UsageReportEntry(ReportLabels.sessions(language), state.sessionCount.toString()))
            add(UsageReportEntry(CliSessionsLabels.columnTokens(language), formatQuantity(state.totalTokens)))
            add(
                UsageReportEntry(
                    CliSessionsLabels.columnCost(language),
                    costCell(state.totalCostMicros, state.isTotalCostComplete)
                )
            )
            if (totalActiveMillis != null && totalActiveMillis > 0L) {
                add(UsageReportEntry(CliSessionsLabels.activeTime(language), formatActiveTime(totalActiveMillis)))
            }
        }
    )

    sections += UsageReportSection.Table(
        heading = BreakdownLabels.axisTab(BreakdownAxis.MEMBER, language),
        columns = listOf(
            UsageReportColumn(ReportLabels.member(language), weight = 2.2f),
            UsageReportColumn(ReportLabels.machine(language), weight = 1.6f),
            UsageReportColumn(ReportLabels.sessions(language), weight = 0.8f, alignEnd = true),
            UsageReportColumn(CliSessionsLabels.columnTokens(language), weight = 1.2f, alignEnd = true),
            UsageReportColumn(CliSessionsLabels.columnCost(language), weight = 1.1f, alignEnd = true),
            UsageReportColumn(CliSessionsLabels.activeTime(language), weight = 0.9f, alignEnd = true)
        ),
        rows = state.members.sortedByActiveTimeDescending { member -> member.totalActiveMillis }.map { member ->
            listOf(
                member.alias,
                member.machineLabel,
                member.sessionCount.toString(),
                formatQuantity(member.totalTokens),
                costCell(member.totalCostMicros, member.isCostComplete),
                activeCell(member.totalActiveMillis)
            )
        }
    )

    if (breakdown != null) {
        sections += bucketTable(BreakdownAxis.PROJECT, breakdown.byProject, language)
        sections += bucketTable(BreakdownAxis.BRANCH, breakdown.byBranch, language)
        sections += bucketTable(BreakdownAxis.MODEL, breakdown.byModel, language)
        footnotes += BreakdownLabels.axisNotice(language)
    }

    // Uma sessão por linha, com a pessoa ao lado: no time o identificador da
    // sessão sozinho não diz de quem ela é, e o relatório não tem o recolhimento
    // por integrante que a tela usa para responder isso.
    sections += UsageReportSection.Table(
        heading = ReportLabels.sessionsHeading(language),
        columns = listOf(
            UsageReportColumn(ReportLabels.member(language), weight = 1.6f),
            UsageReportColumn(ReportLabels.session(language), weight = 1.2f),
            UsageReportColumn(ReportLabels.project(language), weight = 1.6f),
            UsageReportColumn(ReportLabels.model(language), weight = 1.8f),
            UsageReportColumn(ReportLabels.turns(language), weight = 0.7f, alignEnd = true),
            UsageReportColumn(CliSessionsLabels.columnTokens(language), weight = 1.1f, alignEnd = true),
            UsageReportColumn(CliSessionsLabels.columnCost(language), weight = 1f, alignEnd = true),
            UsageReportColumn(CliSessionsLabels.activeTime(language), weight = 0.8f, alignEnd = true)
        ),
        rows = state.members
            .flatMap { member -> member.sessions.map { session -> member.alias to session } }
            .sortedByActiveTimeDescending { (_, session) -> session.activeMillis }
            .map { (memberAlias, session) ->
                listOf(
                    memberAlias,
                    shortSessionId(session.sessionId),
                    session.projectName ?: "-",
                    session.primaryModel ?: "-",
                    session.turnCount.toString(),
                    formatQuantity(session.totalTokens),
                    costCell(session.costMicros, session.isCostComplete),
                    activeCell(session.activeMillis)
                )
            }
    )

    if (!state.isTotalCostComplete) {
        footnotes += CliSessionsLabels.estimatedCostNotice(language)
    }
    if (totalActiveMillis == null) {
        footnotes += ReportLabels.activeTimeUnavailable(language)
    } else {
        footnotes += ReportLabels.activeTimeNote(language)
    }
    // A visão global mistura contas que resetam a quota em horas diferentes: sem
    // este aviso o recorte de 5h seria lido como a janela de alguém.
    if (state.isAdminOverview && state.range != CliSessionRange.ALL) {
        footnotes += ReportLabels.slidingWindowNote(language)
    }

    return UsageReportDocument(
        title = ReportLabels.teamTitle(language),
        subtitle = subtitleOf(
            scopeLabel = state.accountLabel ?: ReportLabels.allAccounts(language),
            range = state.range,
            language = language,
            now = now,
            timeZone = timeZone
        ),
        sections = sections,
        period = periodOf(
            range = state.range,
            rangeStartsAt = state.rangeStartsAt,
            earliestActivityAt = state.members
                .asSequence()
                .flatMap { member -> member.sessions.asSequence() }
                .minOfOrNull { session -> session.firstTs },
            language = language,
            now = now,
            timeZone = timeZone
        ),
        footnotes = footnotes
    ).sanitized()
}

/**
 * Tabela de um eixo do resumo.
 *
 * A coluna de hora só existe onde o balde a tem: no eixo de modelo ela seria uma
 * coluna inteira de traços, e coluna vazia ocupa espaço para não dizer nada.
 */
private fun bucketTable(
    axis: BreakdownAxis,
    buckets: List<CliUsageBucket>,
    language: AppLanguage
): UsageReportSection.Table {
    val hasActiveTime = buckets.any { bucket -> bucket.activeMillis != null }
    val unknownLabel = BreakdownLabels.unknownLabel(axis, language)

    val columns = buildList {
        add(UsageReportColumn(BreakdownLabels.axisTab(axis, language), weight = 2.4f))
        add(UsageReportColumn(ReportLabels.sessions(language), weight = 0.8f, alignEnd = true))
        add(UsageReportColumn(ReportLabels.turns(language), weight = 0.8f, alignEnd = true))
        add(UsageReportColumn(CliSessionsLabels.columnTokens(language), weight = 1.2f, alignEnd = true))
        add(UsageReportColumn(CliSessionsLabels.columnCost(language), weight = 1.1f, alignEnd = true))
        add(UsageReportColumn(CliSessionsLabels.columnCache(language), weight = 0.8f, alignEnd = true))
        if (hasActiveTime) {
            add(UsageReportColumn(CliSessionsLabels.activeTime(language), weight = 0.9f, alignEnd = true))
        }
    }

    val rankedBuckets = if (hasActiveTime) {
        buckets.sortedByActiveTimeDescending { bucket -> bucket.activeMillis }
    } else {
        buckets
    }
    val rows = rankedBuckets.map { bucket ->
        buildList {
            add(bucket.label ?: unknownLabel)
            add(bucket.sessionCount.toString())
            add(bucket.turnCount.toString())
            add(formatQuantity(bucket.totalTokens))
            add(costCell(bucket.costMicros, bucket.isCostComplete))
            add(formatPercent(bucket.cacheHitRate))
            if (hasActiveTime) {
                add(activeCell(bucket.activeMillis))
            }
        }
    }

    return UsageReportSection.Table(heading = BreakdownLabels.axisTab(axis, language), columns = columns, rows = rows)
}

private fun toolTable(tools: List<CliToolUsage>, language: AppLanguage): UsageReportSection.Table {
    return UsageReportSection.Table(
        heading = BreakdownLabels.axisTab(BreakdownAxis.TOOL, language),
        columns = listOf(
            UsageReportColumn(BreakdownLabels.axisTab(BreakdownAxis.TOOL, language), weight = 2.4f),
            UsageReportColumn(ReportLabels.calls(language), weight = 1f, alignEnd = true),
            UsageReportColumn(ReportLabels.turns(language), weight = 1f, alignEnd = true)
        ),
        rows = tools.map { tool ->
            listOf(tool.toolName, tool.callCount.toString(), tool.turnCount.toString())
        },
        // O mesmo aviso da aba: sem ele a tabela seria lida como rateio de gasto.
        note = BreakdownLabels.toolNotice(language)
    )
}

private fun sessionTable(
    sessions: List<CliSessionSummary>,
    language: AppLanguage
): UsageReportSection.Table {
    return UsageReportSection.Table(
        heading = ReportLabels.sessionsHeading(language),
        columns = listOf(
            UsageReportColumn(ReportLabels.session(language), weight = 1.1f),
            UsageReportColumn(ReportLabels.project(language), weight = 1.5f),
            UsageReportColumn(ReportLabels.branch(language), weight = 1.3f),
            UsageReportColumn(ReportLabels.model(language), weight = 1.7f),
            UsageReportColumn(ReportLabels.turns(language), weight = 0.7f, alignEnd = true),
            UsageReportColumn(CliSessionsLabels.columnTokens(language), weight = 1.4f, alignEnd = true),
            UsageReportColumn(CliSessionsLabels.columnCost(language), weight = 1f, alignEnd = true),
            UsageReportColumn(CliSessionsLabels.activeTime(language), weight = 0.9f, alignEnd = true),
            UsageReportColumn(ReportLabels.lastTurn(language), weight = 1.4f)
        ),
        rows = sessions.sortedByActiveTimeDescending { session -> session.activeMillis }.map { session ->
            listOf(
                shortSessionId(session.sessionId),
                session.projectName ?: "-",
                session.gitBranch ?: "-",
                session.primaryModel ?: "-",
                session.turnCount.toString(),
                formatQuantity(session.totalTokens),
                costCell(session.costMicros, session.isCostComplete),
                activeCell(session.activeMillis),
                formatInstant(session.lastTs)
            )
        }
    )
}

/**
 * Ordem exclusiva do PDF: maior tempo ativo primeiro, zero depois e medição
 * ausente no fim. `sortedWith` é estável, então empates preservam o ranking que
 * a tela já recebeu (custo, tokens ou último turno, conforme a origem).
 */
private fun <T> List<T>.sortedByActiveTimeDescending(activeMillisOf: (T) -> Long?): List<T> {
    return sortedWith(compareByDescending<T> { item -> activeMillisOf(item) ?: Long.MIN_VALUE })
}

/** Custo incompleto sai com `+`, como na tela: é piso, não total. */
private fun costCell(micros: Long, isComplete: Boolean): String {
    val formatted = formatMicrosUsd(micros)
    return if (isComplete) formatted else "$formatted+"
}

/** Hora nula ou zerada vira traço: "0min" seria lido como sessão instantânea. */
private fun activeCell(millis: Long?): String {
    if (millis == null || millis <= 0L) {
        return "-"
    }
    return formatActiveTime(millis)
}

private fun subtitleOf(
    scopeLabel: String,
    range: CliSessionRange,
    language: AppLanguage,
    now: Instant,
    timeZone: TimeZone
): String {
    val rangeLabel = CliSessionsLabels.rangeLabel(range, language)
    return "$scopeLabel · $rangeLabel · ${ReportLabels.generatedAt(language)} ${formatTimestamp(now, timeZone)}"
}

/** Faixa absoluta efetivamente representada pelo relatório. */
private fun periodOf(
    range: CliSessionRange,
    rangeStartsAt: Instant?,
    earliestActivityAt: Instant?,
    language: AppLanguage,
    now: Instant,
    timeZone: TimeZone
): String {
    val start = if (range == CliSessionRange.ALL) {
        earliestActivityAt
    } else {
        rangeStartsAt ?: range.durationMillis?.let { durationMillis ->
            Instant.fromEpochMilliseconds(now.toEpochMilliseconds() - durationMillis)
        }
    }
    val endLabel = formatTimestamp(now, timeZone)
    if (start == null) {
        return ReportLabels.allHistoryUntil(endLabel, language)
    }
    return ReportLabels.period(
        start = formatTimestamp(start, timeZone, includeZone = false),
        end = endLabel,
        language = language
    )
}

/** `2026-08-17 15:42 BRT` — data absoluta, que é o que um relatório arquivado exige. */
private fun formatTimestamp(instant: Instant, timeZone: TimeZone, includeZone: Boolean = true): String {
    val local = instant.toLocalDateTime(timeZone)
    val month = local.monthNumber.toString().padStart(2, '0')
    val day = local.dayOfMonth.toString().padStart(2, '0')
    val hour = local.hour.toString().padStart(2, '0')
    val minute = local.minute.toString().padStart(2, '0')
    val zone = if (includeZone) " BRT" else ""
    return "${local.year}-$month-$day $hour:$minute$zone"
}

/**
 * Rótulos que só o relatório usa.
 *
 * O que já existe na tela vem de `CliSessionsLabels` e `BreakdownLabels`: o PDF
 * tem de mostrar os mesmos nomes, ou dois vocabulários descreveriam o mesmo
 * número.
 */
internal object ReportLabels {

    private fun pick(language: AppLanguage, pt: String, en: String): String {
        return if (language == AppLanguage.PT) pt else en
    }

    fun cliTitle(language: AppLanguage) =
        pick(language, "Relatório de uso — Sessões CLI", "Usage report — CLI sessions")

    fun teamTitle(language: AppLanguage) =
        pick(language, "Relatório de uso — Sessões do time", "Usage report — Team sessions")

    fun totals(language: AppLanguage) = pick(language, "Totais", "Totals")

    fun sessions(language: AppLanguage) = pick(language, "Sessões", "Sessions")

    fun sessionsHeading(language: AppLanguage) = pick(language, "Sessões", "Sessions")

    fun session(language: AppLanguage) = pick(language, "Sessão", "Session")

    fun turns(language: AppLanguage) = pick(language, "Turnos", "Turns")

    fun calls(language: AppLanguage) = pick(language, "Chamadas", "Calls")

    fun project(language: AppLanguage) = pick(language, "Projeto", "Project")

    fun branch(language: AppLanguage) = pick(language, "Branch", "Branch")

    fun model(language: AppLanguage) = pick(language, "Modelo", "Model")

    fun member(language: AppLanguage) = pick(language, "Integrante", "Member")

    fun members(language: AppLanguage) = pick(language, "Integrantes ativos", "Active members")

    fun machine(language: AppLanguage) = pick(language, "Máquina", "Machine")

    fun lastTurn(language: AppLanguage) = pick(language, "Último turno", "Last turn")

    fun cacheSavings(language: AppLanguage) = pick(language, "Economia do cache", "Cache savings")

    fun burnRate(language: AppLanguage) = pick(language, "Ritmo de queima", "Burn rate")

    fun allAccounts(language: AppLanguage) = pick(language, "Todas as contas", "All accounts")

    fun generatedAt(language: AppLanguage) = pick(language, "gerado em", "generated at")

    fun period(start: String, end: String, language: AppLanguage) = pick(
        language,
        "Período considerado: $start a $end",
        "Period considered: $start to $end"
    )

    fun allHistoryUntil(end: String, language: AppLanguage) = pick(
        language,
        "Período considerado: todo o histórico disponível até $end",
        "Period considered: all available history until $end"
    )

    fun activityNote(language: AppLanguage) = pick(
        language,
        "Intensidade pelo custo, relativa ao pico desta janela. Hora local (BRT).",
        "Intensity by cost, relative to this window's peak. Local time (BRT)."
    )

    fun activeTimeNote(language: AppLanguage) = pick(
        language,
        "Tempo ativo não é duração: soma só os intervalos entre turnos seguidos menores que cinco " +
            "minutos, e ignora os turnos de subagente.",
        "Active time is not duration: it sums only the gaps between consecutive turns shorter than " +
            "five minutes, and ignores subagent turns."
    )

    fun activeTimeUnavailable(language: AppLanguage) = pick(
        language,
        "Tempo ativo não medido: o servidor de time é anterior à versão 0.7.0.",
        "Active time not measured: the team server predates version 0.7.0."
    )

    fun slidingWindowNote(language: AppLanguage) = pick(
        language,
        "Visão de todas as contas: cada conta reseta a quota numa hora diferente, então o recorte " +
            "aqui é deslizante e não corresponde à janela de nenhuma delas.",
        "All-accounts view: each account resets its quota at a different hour, so this window slides " +
            "and matches none of them."
    )

    fun page(current: Int, total: Int, language: AppLanguage) =
        pick(language, "Página $current de $total", "Page $current of $total")

    fun continued(language: AppLanguage) = pick(language, "continuação", "continued")
}

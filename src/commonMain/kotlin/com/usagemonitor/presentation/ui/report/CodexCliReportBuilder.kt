package com.usagemonitor.presentation.ui.report

import com.usagemonitor.domain.entity.ACTIVITY_TIME_ZONE_ID
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CodexCliSessionSummary
import com.usagemonitor.domain.entity.combinedThroughput
import com.usagemonitor.presentation.ui.CliSessionsLabels
import com.usagemonitor.presentation.ui.CodexSessionBucket
import com.usagemonitor.presentation.ui.codexBucketsBy
import com.usagemonitor.presentation.ui.formatPercent
import com.usagemonitor.presentation.ui.formatQuantity
import com.usagemonitor.presentation.ui.formatThroughput
import com.usagemonitor.presentation.viewmodel.CodexCliSessionRange
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

/**
 * Relatório PDF do modal de Sessões do Codex (#384), no recorte da tela — o
 * mesmo desenho de [reportForCliSessions]: função pura, nenhuma leitura nova.
 *
 * Sem custo: o Codex não tem tarifa na tabela de preços, e custo zero seria
 * afirmar o que não se sabe.
 */
fun reportForCodexCliSessions(
    sessions: List<CodexCliSessionSummary>,
    range: CodexCliSessionRange,
    language: AppLanguage,
    now: Instant,
    timeZone: TimeZone = TimeZone.of(ACTIVITY_TIME_ZONE_ID)
): UsageReportDocument {
    val pt = language == AppLanguage.PT
    val throughput = sessions.map { session -> session.throughput }.combinedThroughput()
    val sections = mutableListOf<UsageReportSection>()

    sections += UsageReportSection.KeyValues(
        heading = ReportLabels.totals(language),
        entries = listOf(
            UsageReportEntry(ReportLabels.sessions(language), sessions.size.toString()),
            UsageReportEntry(if (pt) "Respostas" else "Responses", sessions.sumOf { it.responseCount }.toString()),
            UsageReportEntry(CliSessionsLabels.columnTokens(language), formatQuantity(sessions.sumOf { it.totalTokens })),
            UsageReportEntry(CliSessionsLabels.throughput(language), formatThroughput(throughput))
        )
    )
    sections += bucketSection(if (pt) "Por projeto" else "By project", ReportLabels.project(language), codexBucketsBy(sessions) { it.projectName }, language)
    sections += bucketSection(
        if (pt) "Por modelo principal da sessão" else "By session primary model",
        ReportLabels.model(language),
        codexBucketsBy(sessions) { it.primaryModel },
        language
    )
    sections += UsageReportSection.Table(
        heading = ReportLabels.sessionsHeading(language),
        columns = listOf(
            UsageReportColumn(ReportLabels.session(language), weight = 1.4f),
            UsageReportColumn(ReportLabels.project(language), weight = 1.6f),
            UsageReportColumn(ReportLabels.model(language), weight = 1.5f),
            UsageReportColumn(if (pt) "Respostas" else "Responses", weight = 0.9f, alignEnd = true),
            UsageReportColumn(CliSessionsLabels.columnTokens(language), weight = 1.1f, alignEnd = true),
            UsageReportColumn(CliSessionsLabels.throughput(language), weight = 1f, alignEnd = true)
        ),
        rows = sessions.map { session ->
            listOf(
                session.sessionId.take(SESSION_ID_CHARS),
                session.projectName ?: "-",
                session.primaryModel ?: "-",
                session.responseCount.toString(),
                formatQuantity(session.totalTokens),
                formatThroughput(session.throughput)
            )
        }
    )

    return UsageReportDocument(
        title = if (pt) "Sessões do Codex" else "Codex sessions",
        subtitle = "Codex · ${rangeLabel(range, language)} · ${ReportLabels.generatedAt(language)} ${formatTimestamp(now, timeZone)}",
        sections = sections,
        period = sessions.minOfOrNull { it.firstTs }?.let { start ->
            ReportLabels.period(
                start = formatTimestamp(start, timeZone, includeZone = false),
                end = formatTimestamp(now, timeZone),
                language = language
            )
        },
        footnotes = listOf(
            if (pt) {
                "Vazão: tokens de saída do pedido ao registro de uso, ponta a ponta. Custo não estimado: o Codex não tem tarifa na tabela de preços."
            } else {
                "Throughput: output tokens from request to usage record, end to end. Cost not estimated: Codex has no rate in the pricing table."
            }
        )
    ).sanitized()
}

private fun bucketSection(
    heading: String,
    axisLabel: String,
    buckets: List<CodexSessionBucket>,
    language: AppLanguage
): UsageReportSection {
    val pt = language == AppLanguage.PT
    return UsageReportSection.Table(
        heading = heading,
        columns = listOf(
            UsageReportColumn(axisLabel, weight = 2f),
            UsageReportColumn(ReportLabels.sessions(language), weight = 0.8f, alignEnd = true),
            UsageReportColumn(if (pt) "Respostas" else "Responses", weight = 0.9f, alignEnd = true),
            UsageReportColumn(CliSessionsLabels.columnTokens(language), weight = 1.1f, alignEnd = true),
            UsageReportColumn(CliSessionsLabels.columnCache(language), weight = 0.8f, alignEnd = true),
            UsageReportColumn(CliSessionsLabels.throughput(language), weight = 1f, alignEnd = true)
        ),
        rows = buckets.map { bucket ->
            listOf(
                bucket.label,
                bucket.sessionCount.toString(),
                bucket.responseCount.toString(),
                formatQuantity(bucket.totalTokens),
                formatPercent(bucket.cacheRate),
                formatThroughput(bucket.throughput)
            )
        }
    )
}

private fun rangeLabel(range: CodexCliSessionRange, language: AppLanguage): String {
    val pt = language == AppLanguage.PT
    return when (range) {
        CodexCliSessionRange.LAST_5H -> if (pt) "últimas 5h" else "last 5h"
        CodexCliSessionRange.LAST_7D -> if (pt) "últimos 7 dias" else "last 7 days"
        CodexCliSessionRange.ALL -> if (pt) "todo o histórico" else "all history"
    }
}

private const val SESSION_ID_CHARS = 8

package com.usagemonitor.presentation.ui

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import com.usagemonitor.data.export.UsageExportFormat
import com.usagemonitor.data.export.UsageExporter
import com.usagemonitor.domain.entity.ACTIVITY_TIME_ZONE_ID
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CliSessionRange
import com.usagemonitor.domain.entity.CliSessionSummary
import com.usagemonitor.domain.entity.CliUsageBreakdown
import com.usagemonitor.presentation.ui.report.UsageReportDocument

/**
 * O que gravar, já pronto, e o nome sugerido no diálogo de arquivo.
 *
 * [Text] cobre CSV e JSON; [Report] carrega o documento do relatório e **não** o
 * PDF. Quem transforma um no outro é o desktop: o gerador de PDF é JVM-only, e
 * trazê-lo para cá levaria uma dependência de biblioteca para dentro do
 * `commonMain` só para atravessar uma fronteira.
 */
sealed interface UsageExportPayload {
    data class Text(val content: String) : UsageExportPayload
    data class Report(val document: UsageReportDocument) : UsageExportPayload
}

data class UsageExportRequest(
    val suggestedFileName: String,
    val payload: UsageExportPayload
)

/**
 * Monta o conteúdo da exportação.
 *
 * Função pura: quem grava é a camada desktop, que recebe isto pronto. É o que
 * permite testar o formato sem tocar em disco nem abrir diálogo.
 */
fun exportRequestForSessions(
    sessions: List<CliSessionSummary>,
    range: CliSessionRange,
    format: UsageExportFormat,
    now: Instant,
    timeZone: TimeZone = TimeZone.of(ACTIVITY_TIME_ZONE_ID)
): UsageExportRequest {
    return UsageExportRequest(
        suggestedFileName = exportFileName("sessions", range, format, now, timeZone),
        payload = UsageExportPayload.Text(UsageExporter.exportSessions(sessions, format))
    )
}

fun exportRequestForBreakdown(
    breakdown: CliUsageBreakdown,
    range: CliSessionRange,
    format: UsageExportFormat,
    now: Instant,
    timeZone: TimeZone = TimeZone.of(ACTIVITY_TIME_ZONE_ID)
): UsageExportRequest {
    return UsageExportRequest(
        suggestedFileName = exportFileName("breakdown", range, format, now, timeZone),
        payload = UsageExportPayload.Text(UsageExporter.exportBreakdown(breakdown, format))
    )
}

/**
 * Relatório PDF do recorte que está na tela.
 *
 * Irmão de [exportRequestForSessions], e não um valor a mais em
 * [UsageExportFormat]: os `when` exaustivos de `UsageExporter` são sobre formato
 * de texto, e um `PDF` ali obrigaria um ramo impossível em cada um deles.
 */
fun reportRequest(
    document: UsageReportDocument,
    range: CliSessionRange,
    now: Instant,
    timeZone: TimeZone = TimeZone.of(ACTIVITY_TIME_ZONE_ID)
): UsageExportRequest {
    return UsageExportRequest(
        suggestedFileName = reportFileName(range, now, timeZone),
        payload = UsageExportPayload.Report(document)
    )
}

/** `usage-monitor-report-5h-2026-08-17.pdf`, no mesmo padrão de [exportFileName]. */
internal fun reportFileName(
    range: CliSessionRange,
    now: Instant,
    timeZone: TimeZone
): String {
    val local = now.toLocalDateTime(timeZone).date
    val month = local.monthNumber.toString().padStart(2, '0')
    val day = local.dayOfMonth.toString().padStart(2, '0')
    return "usage-monitor-report-${rangeSlug(range)}-${local.year}-$month-$day.pdf"
}

/**
 * Nome como `usage-monitor-sessions-5h-2026-08-16.csv`.
 *
 * A janela entra no nome porque dois arquivos exportados no mesmo dia com
 * recortes diferentes seriam indistinguíveis na pasta de destino.
 */
internal fun exportFileName(
    scope: String,
    range: CliSessionRange,
    format: UsageExportFormat,
    now: Instant,
    timeZone: TimeZone
): String {
    val local = now.toLocalDateTime(timeZone).date
    val month = local.monthNumber.toString().padStart(2, '0')
    val day = local.dayOfMonth.toString().padStart(2, '0')
    return "usage-monitor-$scope-${rangeSlug(range)}-${local.year}-$month-$day.${format.extension}"
}

private fun rangeSlug(range: CliSessionRange): String {
    return when (range) {
        CliSessionRange.LAST_5H -> "5h"
        CliSessionRange.LAST_7D -> "7d"
        CliSessionRange.LAST_30D -> "30d"
        CliSessionRange.ALL -> "total"
    }
}

/** Traduz o resultado da exportação; o estado carrega só o fato. */
internal fun exportOutcomeMessage(
    outcome: com.usagemonitor.presentation.viewmodel.CliExportOutcome,
    language: AppLanguage
): String {
    return when (outcome) {
        is com.usagemonitor.presentation.viewmodel.CliExportOutcome.Saved ->
            ExportLabels.exportSaved(outcome.path, language)
        is com.usagemonitor.presentation.viewmodel.CliExportOutcome.Failed ->
            ExportLabels.exportFailed(outcome.message, language)
    }
}

internal object ExportLabels {

    fun exportCsv(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Exportar CSV" else "Export CSV"
    }

    fun exportJson(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Exportar JSON" else "Export JSON"
    }

    fun exportPdf(language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Relatório PDF" else "PDF report"
    }

    fun exportSaved(path: String, language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Exportado para $path" else "Exported to $path"
    }

    fun exportFailed(message: String, language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Falha ao exportar: $message" else "Export failed: $message"
    }
}

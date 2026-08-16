package com.usagemonitor.presentation.ui

import com.usagemonitor.data.export.UsageExportFormat
import com.usagemonitor.data.export.UsageExporter
import com.usagemonitor.domain.entity.ACTIVITY_TIME_ZONE_ID
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CliSessionRange
import com.usagemonitor.domain.entity.CliSessionSummary
import com.usagemonitor.domain.entity.CliUsageBreakdown
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Conteúdo pronto para gravar e o nome sugerido no diálogo de arquivo. */
data class UsageExportRequest(
    val suggestedFileName: String,
    val content: String
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
        content = UsageExporter.exportSessions(sessions, format)
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
        content = UsageExporter.exportBreakdown(breakdown, format)
    )
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

    fun exportSaved(path: String, language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Exportado para $path" else "Exported to $path"
    }

    fun exportFailed(message: String, language: AppLanguage): String {
        return if (language == AppLanguage.PT) "Falha ao exportar: $message" else "Export failed: $message"
    }
}

package com.usagemonitor.presentation.terminal

import com.usagemonitor.domain.entity.AlertDestination
import com.usagemonitor.domain.entity.AlertLogEntry
import com.usagemonitor.domain.entity.AlertLogKind
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.localized
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** Os textos do `alerts` (#440, 5.2 do protótipo). */
internal class TerminalAlertsLabels(val language: AppLanguage) {
    val headerTime: String get() = localized(language, "HORA", "TIME")
    val headerKind: String get() = localized(language, "TIPO", "KIND")
    val headerAlert: String get() = localized(language, "AVISO", "ALERT")
    val headerTo: String get() = localized(language, "DESTINO", "SENT TO")

    fun kind(kind: AlertLogKind): String =
        when (kind) {
            AlertLogKind.CRITICAL -> localized(language, "crítico", "critical")
            AlertLogKind.WARNING -> localized(language, "atenção", "warning")
            AlertLogKind.RESET -> localized(language, "reinício", "reset")
            AlertLogKind.REMINDER -> localized(language, "lembrete", "reminder")
            AlertLogKind.SESSION -> localized(language, "sessão", "session")
            AlertLogKind.SPIKE -> localized(language, "consumo", "usage")
        }

    fun destination(destination: AlertDestination): String =
        when (destination) {
            AlertDestination.TRAY -> localized(language, "bandeja", "tray")
            AlertDestination.TELEGRAM -> "telegram"
        }

    val onlyHere: String get() = localized(language, "só no registro", "log only")
    val noneToday: String get() = localized(language, "Nenhum aviso hoje.", "No alert today.")
    val noneInRange: String get() = localized(language, "Nenhum aviso no período.", "No alert in the period.")
    val noTrayNote: String get() = localized(language, "Sem tela não há bandeja: os avisos vão para o Telegram e ficam neste registro.", "Without a display there is no tray: alerts go to Telegram and stay in this log.")
    val quietNote: String get() = localized(language, "O silêncio adia o aviso, e ele entra aqui quando sai.", "Quiet hours delay an alert, and it shows up here when it goes out.")
}

/** `usage-monitor alerts`: o que foi avisado, quando e para onde, do mais novo para o mais velho. */
internal object TerminalAlertsView {
    private const val GAP = "  "
    private val SAO_PAULO = TimeZone.of("America/Sao_Paulo")

    fun render(entries: List<AlertLogEntry>, now: Instant, todayOnly: Boolean, labels: TerminalAlertsLabels, profile: TerminalProfile): TerminalOutput {
        val notes = listOf(
            terminalLine(TerminalSpan(" " + labels.noTrayNote, TerminalColor.Muted)),
            terminalLine(TerminalSpan(" " + labels.quietNote, TerminalColor.Muted))
        )
        if (entries.isEmpty()) {
            return TerminalOutput(listOf(terminalLine(TerminalSpan(if (todayOnly) labels.noneToday else labels.noneInRange))) + TerminalLine.EMPTY + notes, 0)
        }
        val today = now.toLocalDateTime(SAO_PAULO).date
        val rows = entries.sortedByDescending { entry -> entry.atEpochMillis }.map { entry ->
            val at = Instant.fromEpochMilliseconds(entry.atEpochMillis).toLocalDateTime(SAO_PAULO)
            val clock = at.hour.toString().padStart(2, '0') + ":" + at.minute.toString().padStart(2, '0')
            val day = at.dayOfMonth.toString().padStart(2, '0') + "/" + at.monthNumber.toString().padStart(2, '0')
            val time = if (at.date == today) clock else localized(labels.language, "$day $clock", at.monthNumber.toString().padStart(2, '0') + "/" + at.dayOfMonth.toString().padStart(2, '0') + " $clock")
            val alert = entry.percent?.let { percent -> "${entry.title} $percent%" } ?: entry.title
            val to = entry.destinations.joinToString(", ", transform = labels::destination).ifEmpty { labels.onlyHere }
            Row(time, entry.kind, alert, to, entry.destinations.isEmpty())
        }
        val timeWidth = maxOf(labels.headerTime.length, rows.maxOf { row -> row.time.length })
        val kindWidth = maxOf(labels.headerKind.length, AlertLogKind.entries.maxOf { kind -> labels.kind(kind).length })
        val alertWidth = maxOf(labels.headerAlert.length, rows.maxOf { row -> row.alert.length })
        val header = " " + listOf(labels.headerTime.padEnd(timeWidth), labels.headerKind.padEnd(kindWidth), labels.headerAlert.padEnd(alertWidth), labels.headerTo).joinToString(GAP)
        val lines = mutableListOf(terminalLine(TerminalSpan(header, TerminalColor.Muted, bold = true)))
        for (row in rows) {
            lines += terminalLine(
                TerminalSpan(" " + row.time.padEnd(timeWidth) + GAP),
                TerminalSpan(labels.kind(row.kind).padEnd(kindWidth), colorOf(row.kind)),
                TerminalSpan(GAP + row.alert.padEnd(alertWidth) + GAP),
                TerminalSpan(row.to, if (row.onlyHere) TerminalColor.Muted else null)
            )
        }
        return TerminalOutput(lines + TerminalLine.EMPTY + notes, 0)
    }

    private fun colorOf(kind: AlertLogKind): TerminalColor =
        when (kind) {
            AlertLogKind.CRITICAL -> TerminalColor.Crit
            AlertLogKind.WARNING, AlertLogKind.SESSION, AlertLogKind.SPIKE -> TerminalColor.Warn
            AlertLogKind.RESET -> TerminalColor.Ok
            AlertLogKind.REMINDER -> TerminalColor.Muted
        }

    private data class Row(val time: String, val kind: AlertLogKind, val alert: String, val to: String, val onlyHere: Boolean)
}

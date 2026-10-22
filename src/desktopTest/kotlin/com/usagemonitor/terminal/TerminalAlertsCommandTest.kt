package com.usagemonitor.terminal

import com.usagemonitor.CommandLineRequest
import com.usagemonitor.data.datasource.LocalAlertLogDataSource
import com.usagemonitor.domain.entity.AlertDestination
import com.usagemonitor.domain.entity.AlertLogEntry
import com.usagemonitor.domain.entity.AlertLogKind
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.parseCommandLine
import com.usagemonitor.presentation.terminal.TerminalAlertsLabels
import com.usagemonitor.presentation.terminal.TerminalAlertsView
import com.usagemonitor.presentation.terminal.TerminalPalette
import com.usagemonitor.presentation.terminal.TerminalProfile
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** `usage-monitor alerts` e o registro que o coletor grava (#440, 5.2). */
class TerminalAlertsCommandTest {

    private val now = Instant.parse("2026-10-22T17:23:00Z")
    private val plain = TerminalProfile(TerminalPalette.NONE, unicode = true)

    private fun at(text: String) = Instant.parse(text).toEpochMilliseconds()

    @Test
    fun `alerts parses today and a time back`() {
        assertEquals(CommandLineRequest.Terminal(TerminalCommand.Alerts()), parseCommandLine(arrayOf("alerts")))
        assertEquals(CommandLineRequest.Terminal(TerminalCommand.Alerts()), parseCommandLine(arrayOf("alerts", "--today")))
        assertEquals(CommandLineRequest.Terminal(TerminalCommand.Alerts(7.days)), parseCommandLine(arrayOf("alerts", "--since", "7d")))
        assertTrue(parseCommandLine(arrayOf("alerts", "--since", "ontem")) is CommandLineRequest.Invalid)
    }

    @Test
    fun `the log keeps what went out, skips a broken line and reads from a moment on`() {
        val file = File(Files.createTempDirectory("usage-monitor-alerts").toFile(), "alerts.jsonl")
        val log = LocalAlertLogDataSource(file)
        log.append(AlertLogEntry(at("2026-10-21T12:00:00Z"), AlertLogKind.RESET, "Claude Max · Claude 7d"))
        file.appendText("isto não é json\n")
        log.append(AlertLogEntry(at("2026-10-22T16:51:00Z"), AlertLogKind.CRITICAL, "Claude Team · Claude 5h", 91, listOf(AlertDestination.TELEGRAM)))

        val today = log.read(at("2026-10-22T03:00:00Z"))

        assertEquals(1, today.size)
        assertEquals(listOf(AlertDestination.TELEGRAM), today.single().destinations)
        assertEquals(2, log.read(0L).size)
    }

    @Test
    fun `the table shows the newest first, with the kind word and where it went`() {
        val entries = listOf(
            AlertLogEntry(at("2026-10-22T12:00:00Z"), AlertLogKind.RESET, "Claude Max · Claude 7d", destinations = listOf(AlertDestination.TELEGRAM)),
            AlertLogEntry(at("2026-10-22T16:51:00Z"), AlertLogKind.CRITICAL, "Claude Team · Claude 5h", 91, listOf(AlertDestination.TELEGRAM)),
            AlertLogEntry(at("2026-10-22T14:02:00Z"), AlertLogKind.WARNING, "Cursor · Cursor Included", 72)
        )

        val lines = TerminalAlertsView.render(entries, now, todayOnly = true, TerminalAlertsLabels(AppLanguage.PT), plain).lines.map { line -> line.plainText.trimEnd() }

        // A coluna do aviso tem a largura do maior aviso ("Cursor · Cursor Included 72%", 28).
        assertEquals(" HORA   TIPO      " + "AVISO".padEnd(28) + "  DESTINO", lines[0])
        assertEquals(" 13:51  crítico   " + "Claude Team · Claude 5h 91%".padEnd(28) + "  telegram", lines[1])
        assertEquals(" 11:02  atenção   " + "Cursor · Cursor Included 72%" + "  só no registro", lines[2])
        assertEquals(" 09:00  reinício  " + "Claude Max · Claude 7d".padEnd(28) + "  telegram", lines[3])
    }

    @Test
    fun `no alert today says so`() {
        val lines = TerminalAlertsView.render(emptyList(), now, todayOnly = true, TerminalAlertsLabels(AppLanguage.PT), plain).lines
        assertEquals("Nenhum aviso hoje.", lines.first().plainText)
    }
}

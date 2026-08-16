package com.usagemonitor.data

import com.usagemonitor.data.export.UsageExportFormat
import com.usagemonitor.data.export.UsageExporter
import com.usagemonitor.domain.entity.CliSessionSummary
import com.usagemonitor.domain.entity.CliSessionTurn
import com.usagemonitor.domain.entity.CliUsageGroupRow
import com.usagemonitor.domain.entity.toUsageBreakdown
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val FIRST_TS = Instant.parse("2026-08-01T10:00:00Z")
private val LAST_TS = Instant.parse("2026-08-01T11:00:00Z")

class UsageExporterTest {

    @Test
    fun `the sessions csv starts with the header and one line per session`() {
        val csv = UsageExporter.exportSessions(
            listOf(summary("a"), summary("b")),
            UsageExportFormat.CSV
        )

        val lines = csv.trim().lines()
        assertEquals(3, lines.size)
        assertTrue(lines.first().startsWith("session_id,project,git_branch"))
        assertTrue(lines[1].startsWith("a,"))
    }

    /**
     * Nome de projeto e branch são texto livre: sem escape, uma vírgula
     * deslocaria todas as colunas seguintes da linha.
     */
    @Test
    fun `a comma in a free-text field is quoted`() {
        val csv = UsageExporter.exportSessions(
            listOf(summary("a", cwd = "/home/dev/proj, v2")),
            UsageExportFormat.CSV
        )

        assertTrue(csv.contains("\"proj, v2\""))
    }

    @Test
    fun `an embedded quote is doubled`() {
        val csv = UsageExporter.exportSessions(
            listOf(summary("a", cwd = "/home/dev/pro\"j")),
            UsageExportFormat.CSV
        )

        assertTrue(csv.contains("\"pro\"\"j\""))
    }

    /** Zero afirmaria que o turno não custou nada; a coluna fica vazia. */
    @Test
    fun `an unpriced turn exports an empty cost`() {
        val csv = UsageExporter.exportTurns(
            listOf(turn(model = "modelo-inexistente")),
            UsageExportFormat.CSV
        )

        val row = csv.trim().lines()[1]
        assertTrue(row.endsWith(","))
    }

    @Test
    fun `a priced turn exports its cost`() {
        val csv = UsageExporter.exportTurns(
            listOf(turn(model = "claude-opus-5", outputTokens = 1_000_000L)),
            UsageExportFormat.CSV
        )

        val row = csv.trim().lines()[1]
        assertFalse(row.endsWith(","))
    }

    @Test
    fun `the breakdown csv carries the axis as a column`() {
        val breakdown = listOf(
            CliUsageGroupRow(
                sessionId = "a",
                cwd = "/home/dev/alpha",
                gitBranch = "main",
                model = "claude-opus-5",
                turnCount = 1,
                inputTokens = 1_000_000L
            )
        ).toUsageBreakdown()

        val csv = UsageExporter.exportBreakdown(breakdown, UsageExportFormat.CSV)
        val lines = csv.trim().lines()

        assertTrue(lines.first().startsWith("axis,label,"))
        assertTrue(lines.any { line -> line.startsWith("project,alpha,") })
        assertTrue(lines.any { line -> line.startsWith("model,claude-opus-5,") })
        assertTrue(lines.any { line -> line.startsWith("branch,main,") })
        assertTrue(lines.any { line -> line.startsWith("total,,") })
    }

    @Test
    fun `the sessions json carries the snake case keys`() {
        val json = UsageExporter.exportSessions(listOf(summary("a")), UsageExportFormat.JSON)

        assertTrue(json.contains("\"session_id\""))
        assertTrue(json.contains("\"cache_read_tokens\""))
        assertTrue(json.contains("\"cost_is_complete\""))
    }

    /** Nada de conteúdo de prompt ou resposta sai daqui — só metadados de uso. */
    @Test
    fun `the export carries no prompt or response content`() {
        val json = UsageExporter.exportTurns(listOf(turn()), UsageExportFormat.JSON)

        assertFalse(json.contains("content"))
        assertFalse(json.contains("text"))
    }
}

private fun summary(
    sessionId: String,
    cwd: String = "/home/dev/alpha"
): CliSessionSummary {
    return CliSessionSummary(
        sessionId = sessionId,
        filePath = "/tmp/$sessionId.jsonl",
        cwd = cwd,
        gitBranch = "main",
        firstTs = FIRST_TS,
        lastTs = LAST_TS,
        primaryModel = "claude-opus-5",
        turnCount = 2,
        outputTokens = 1_000L,
        costMicros = 1_230_000L
    )
}

private fun turn(
    model: String? = "claude-opus-5",
    outputTokens: Long = 0L
): CliSessionTurn {
    return CliSessionTurn(
        sessionId = "a",
        seq = 1,
        messageId = "msg-1",
        ts = FIRST_TS,
        model = model,
        outputTokens = outputTokens
    )
}

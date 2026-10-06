package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CodexCliSessionSummary
import com.usagemonitor.domain.entity.OutputThroughput
import com.usagemonitor.presentation.ui.codexBucketsBy
import com.usagemonitor.presentation.ui.codexReportRequest
import com.usagemonitor.presentation.ui.UsageExportPayload
import com.usagemonitor.presentation.ui.report.UsageReportSection
import com.usagemonitor.presentation.ui.report.reportForCodexCliSessions
import com.usagemonitor.presentation.viewmodel.CodexCliSessionRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

private val NOW = Instant.parse("2026-10-06T15:00:00Z")

private fun session(id: String, project: String, model: String, tokens: Long, throughput: OutputThroughput?) =
    CodexCliSessionSummary(
        sessionId = id,
        filePath = "/tmp/$id.jsonl",
        cwd = "C:/work/$project",
        firstTs = NOW,
        lastTs = NOW,
        primaryModel = model,
        responseCount = 3,
        totalTokens = tokens,
        throughput = throughput
    )

class CodexCliReportTest {

    private val sessions = listOf(
        session("01a0fee1aaaa", "usage-monitor", "gpt-5.6-luna", 3_000L, OutputThroughput(320L, 10_000L)),
        session("01a08196bbbb", "usage-monitor", "gpt-6.1-sol", 1_000L, OutputThroughput(30L, 1_000L)),
        session("01a07777cccc", "coletor", "gpt-5.6-luna", 500L, null)
    )

    @Test
    fun `buckets add tokens and combine throughput as total over total`() {
        val byProject = codexBucketsBy(sessions) { it.projectName }

        assertEquals(listOf("usage-monitor", "coletor"), byProject.map { it.label })
        assertEquals(4_000L, byProject.first().totalTokens)
        assertEquals(OutputThroughput(350L, 11_000L), byProject.first().throughput)
    }

    @Test
    fun `report lists totals, both axes and every session without cost`() {
        val document = reportForCodexCliSessions(sessions, CodexCliSessionRange.LAST_5H, AppLanguage.PT, NOW)

        val totals = assertIs<UsageReportSection.KeyValues>(document.sections.first())
        assertTrue(totals.entries.any { it.label == "Vazão" && it.value == "32 tok/s" })
        val sessionTable = document.sections.filterIsInstance<UsageReportSection.Table>().last()
        assertEquals(3, sessionTable.rows.size)
        assertTrue(document.footnotes.single().contains("Custo não estimado"))
    }

    @Test
    fun `pdf request carries the app language`() {
        val document = reportForCodexCliSessions(sessions, CodexCliSessionRange.LAST_7D, AppLanguage.EN, NOW)
        val request = codexReportRequest(document, CodexCliSessionRange.LAST_7D, NOW, AppLanguage.EN)

        assertEquals("usage-monitor-codex-report-7d-2026-10-06.pdf", request.suggestedFileName)
        assertEquals(AppLanguage.EN, assertIs<UsageExportPayload.Report>(request.payload).language)
    }
}

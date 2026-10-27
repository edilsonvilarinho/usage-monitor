package com.usagemonitor.presentation

import com.usagemonitor.data.export.DefaultUsageExportEncoder
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CliSessionRange
import com.usagemonitor.domain.entity.CliUsageBreakdown
import com.usagemonitor.domain.entity.UsageExportFormat
import com.usagemonitor.presentation.ui.ExportLabels
import com.usagemonitor.presentation.ui.UsageExportPayload
import com.usagemonitor.presentation.ui.codexReportRequest
import com.usagemonitor.presentation.ui.comparisonReportRequest
import com.usagemonitor.presentation.ui.exportOutcomeMessage
import com.usagemonitor.presentation.ui.exportRequestForBreakdown
import com.usagemonitor.presentation.ui.exportRequestForCodexCliSessions
import com.usagemonitor.presentation.ui.exportRequestForDashboard
import com.usagemonitor.presentation.ui.report.UsageReportDocument
import com.usagemonitor.presentation.viewmodel.CliExportOutcome
import com.usagemonitor.presentation.viewmodel.CodexCliSessionRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant

/**
 * Nome sugerido de cada exportação. A data é a do dia em BRT: 01:30 UTC de 28/10
 * ainda é 27/10 em São Paulo, e o arquivo tem de dizer o dia que a pessoa viveu.
 */
class UsageExportRequestsTest {

    private val now = Instant.parse("2026-10-28T01:30:00Z")
    private val document = UsageReportDocument(title = "t", subtitle = "s", sections = emptyList())

    @Test
    fun `breakdown export names range, date and format`() {
        val request = exportRequestForBreakdown(
            DefaultUsageExportEncoder, CliUsageBreakdown(), CliSessionRange.LAST_30D, UsageExportFormat.JSON, now
        )

        assertEquals("usage-monitor-breakdown-30d-2026-10-27.json", request.suggestedFileName)
        assertIs<UsageExportPayload.Text>(request.payload)
    }

    @Test
    fun `codex exports carry each range slug`() {
        val names = CodexCliSessionRange.entries.map { range ->
            exportRequestForCodexCliSessions(DefaultUsageExportEncoder, emptyList(), range, UsageExportFormat.CSV, now)
                .suggestedFileName
        }
        assertEquals(
            listOf(
                "usage-monitor-codex-cli-5h-2026-10-27.csv",
                "usage-monitor-codex-cli-7d-2026-10-27.csv",
                "usage-monitor-codex-cli-total-2026-10-27.csv"
            ),
            names
        )
        assertEquals(
            listOf(
                "usage-monitor-codex-report-5h-2026-10-27.pdf",
                "usage-monitor-codex-report-7d-2026-10-27.pdf",
                "usage-monitor-codex-report-total-2026-10-27.pdf"
            ),
            CodexCliSessionRange.entries.map { range -> codexReportRequest(document, range, now, AppLanguage.PT).suggestedFileName }
        )
    }

    @Test
    fun `comparison report keeps the language for the pdf`() {
        val request = comparisonReportRequest(document, CliSessionRange.LAST_7D, now, AppLanguage.EN)

        assertEquals("usage-monitor-comparison-7d-2026-10-27.pdf", request.suggestedFileName)
        assertEquals(UsageExportPayload.Report(document, AppLanguage.EN), request.payload)
        assertEquals(
            "usage-monitor-comparison-total-2026-10-27.pdf",
            comparisonReportRequest(document, CliSessionRange.ALL, now, AppLanguage.EN).suggestedFileName
        )
    }

    @Test
    fun `dashboard snapshot is a dated csv`() {
        val request = exportRequestForDashboard(DefaultUsageExportEncoder, emptyList(), now)

        assertEquals("usage-monitor-dashboard-2026-10-27.csv", request.suggestedFileName)
        assertIs<UsageExportPayload.Text>(request.payload)
    }

    @Test
    fun `outcome messages in both languages`() {
        assertEquals("Exportado para C:/a.csv", exportOutcomeMessage(CliExportOutcome.Saved("C:/a.csv"), AppLanguage.PT))
        assertEquals("Exported to C:/a.csv", exportOutcomeMessage(CliExportOutcome.Saved("C:/a.csv"), AppLanguage.EN))
        assertEquals("Falha ao exportar: disco", exportOutcomeMessage(CliExportOutcome.Failed("disco"), AppLanguage.PT))
        assertEquals("Export failed: disco", exportOutcomeMessage(CliExportOutcome.Failed("disco"), AppLanguage.EN))
        assertEquals("Export CSV", ExportLabels.exportCsv(AppLanguage.EN))
        assertEquals("Exportar JSON", ExportLabels.exportJson(AppLanguage.PT))
        assertEquals("PDF report", ExportLabels.exportPdf(AppLanguage.EN))
    }
}

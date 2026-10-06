package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.*
import com.usagemonitor.presentation.ui.UsageExportPayload
import com.usagemonitor.presentation.ui.report.*
import com.usagemonitor.presentation.viewmodel.*
import kotlin.time.Instant
import kotlin.test.*

class HistoryReportBuilderTest {
    private val captured = Instant.parse("2026-10-06T15:00:00Z")
    private fun series(label: String, period: PeriodType, unit: UsageUnit = UsageUnit.PERCENTAGE): UsageHistorySeries = UsageHistorySeries(
        quotaLabel = label, periodType = period, unit = unit,
        points = listOf(UsageHistoryPoint(captured, 41, if (unit == UsageUnit.TOKENS) 0 else 100, 0, 0, captured)),
        currentDisplayUsed = 41, currentDisplayTotal = if (unit == UsageUnit.TOKENS) 0 else 100, deltaDisplayUsed = 12,
        averageDisplayConsumptionPerHour = 2.0, currentPeriodEndAt = captured, forecast = UsageForecast.ResetsBeforeExhaustion, riskSummary = null
    )
    private fun state(series: List<UsageHistorySeries>, source: ApiSource = ApiSource.ANTHROPIC, quota: HistoryQuotaView = HistoryQuotaView.BOTH, range: HistoryRange = HistoryRange.LAST_7_DAYS): HistoryUiState.Success {
        val account = UsageAccountContext(UsageAccountKey(source, "account-id", "workspace-id"), "synthetic@example.com", "Uma identificação de workspace longa que precisa permanecer completa no relatório")
        return HistoryUiState.Success(listOf(source), source, range, ApiUsageHistoryReport(source, range, captured, series, account, if (range == HistoryRange.TOTAL) null else range.windowStart(captured), captured), selectedAccount = account, selectedQuotaView = quota)
    }
    @Test fun `PDF respects selected quota and query time instead of generation time`() {
        val snapshot = state(listOf(series("Claude 5h", PeriodType.INTERVAL), series("Claude 7d", PeriodType.WEEKLY)), quota = HistoryQuotaView.WEEKLY)
        val document = reportForHistory(snapshot, AppLanguage.PT, Instant.parse("2026-10-08T15:00:00Z"))
        assertTrue(document.sections.any { it.heading == "Claude 7d" })
        assertFalse(document.sections.any { it.heading == "Claude 5h" })
        assertTrue(document.period!!.contains("06/10"))
        assertFalse(document.period!!.contains("08/10"))
        val context = assertIs<UsageReportSection.Paragraphs>(document.sections.first())
        assertTrue(context.paragraphs.any { it.contains(snapshot.selectedAccount!!.workspaceName!!) })
    }
    @Test fun `PDF exports every quota window including those outside eight visible rows`() {
        val base = series("Claude 5h", PeriodType.INTERVAL)
        val windows = (0..11).map { QuotaWindowSummary(captured, captured, captured, it, null, 1.0, null, false) }
        val document = reportForHistory(state(listOf(base.copy(windows = windows))), AppLanguage.PT, captured)
        val table = document.sections.filterIsInstance<UsageReportSection.Table>().single { it.heading.startsWith("Janelas") }
        assertEquals(12, table.rows.size)
        assertEquals("11 %", table.rows.first()[2])
        assertEquals("0 %", table.rows.last()[2])
        assertEquals("—", table.rows.first()[3])
    }
    @Test fun `reported Codex quota does not invent forecast or consumption pace`() {
        val document = reportForHistory(state(listOf(series("Reportada", PeriodType.REPORTED)), source = ApiSource.CODEX), AppLanguage.PT, captured)
        val data = document.sections.filterIsInstance<UsageReportSection.Paragraphs>().flatMap { it.paragraphs }.joinToString()
        assertFalse(data.contains("Previsão:"))
        assertFalse(data.contains("Média por hora:"))
        assertTrue(data.contains("Variação observada:"))
    }
    @Test fun `local token data retains its unit without quotas or forecast`() {
        val snapshot = state(listOf(series("modelo 5h", PeriodType.INTERVAL, UsageUnit.TOKENS), series("modelo 7d", PeriodType.WEEKLY, UsageUnit.TOKENS)), source = ApiSource.GEMINI)
        val document = reportForHistory(snapshot, AppLanguage.EN, captured)
        val data = document.sections.filterIsInstance<UsageReportSection.Paragraphs>().flatMap { it.paragraphs }.joinToString()
        assertTrue(data.contains("Tokens in last 5h: 41 tok"))
        assertFalse(data.contains("Requests"))
        assertFalse(data.contains("Forecast:"))
        val readings = document.sections.filterIsInstance<UsageReportSection.Table>().filter { it.heading.startsWith("Readings") }
        assertEquals(listOf("Readings · modelo 5h", "Readings · modelo 7d"), readings.map { it.heading })
        assertTrue(readings.all { it.rows.single()[2] == "tok" })
    }
    @Test fun `total range explains sampling and file name does not expose email`() {
        val snapshot = state(listOf(series("Claude 5h", PeriodType.INTERVAL)), range = HistoryRange.TOTAL)
        val request = historyReportRequest(snapshot, AppLanguage.PT, captured)
        assertFalse(request.suggestedFileName.contains("@"))
        assertEquals("usage-monitor-history-anthropic-total-2026-10-06.pdf", request.suggestedFileName)
        val payload = assertIs<UsageExportPayload.Report>(request.payload)
        assertEquals(AppLanguage.PT, payload.language)
        val document = payload.document
        assertEquals("Todo o histórico disponível", document.period)
        assertTrue(document.footnotes.any { it.contains("720") })
    }
}

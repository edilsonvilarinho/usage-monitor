package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.*
import com.usagemonitor.domain.repository.UsageHistoryRepository
import com.usagemonitor.domain.usecase.GetUsageHistoryUseCase
import com.usagemonitor.presentation.ui.UsageExportRequest
import com.usagemonitor.presentation.viewmodel.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.test.*
import kotlin.time.Instant

class HistoryExportTest {
    private val at = Instant.parse("2026-10-06T15:00:00Z")
    private fun report(range: HistoryRange) = ApiUsageHistoryReport(ApiSource.ANTHROPIC, range, at, listOf(
        UsageHistorySeries("Claude 5h", PeriodType.INTERVAL, UsageUnit.PERCENTAGE, emptyList(), 68, 100, 10, 2.0, at, UsageForecast.ResetsBeforeExhaustion, null)
    ), rangeStartsAt = range.windowStart(at), rangeEndsAt = at)
    private inner class Repository : UsageHistoryRepository {
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun recordSnapshot(stats: ApiUsageStats, capturedAt: Instant) = Unit
        override suspend fun listAccounts(source: ApiSource) = emptyList<UsageAccountContext>()
        override suspend fun getHistoryReport(source: ApiSource, range: HistoryRange, now: Instant): ApiUsageHistoryReport { gate?.await(); return report(range) }
        override suspend fun getHistoryReport(source: ApiSource, accountKey: UsageAccountKey?, range: HistoryRange, now: Instant) = getHistoryReport(source, range, now)
    }
    private suspend fun ready(vm: HistoryViewModel): HistoryUiState.Success = withTimeout(5000) {
        vm.uiState.filterIsInstance<HistoryUiState.Success>().first { !it.isRefreshing && !it.isExporting }
    }
    @Test fun `duplicate export is blocked and cancellation of destination is silent`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val started = CompletableDeferred<UsageExportRequest>()
        var calls = 0
        val vm = HistoryViewModel(GetUsageHistoryUseCase(Repository()), MutableStateFlow(setOf(ApiSource.ANTHROPIC)), exportWriter = UsageExportWriter { calls++; started.complete(it); gate.await(); null })
        try {
            ready(vm)
            vm.exportReport(AppLanguage.PT)
            withTimeout(5000) { started.await() }
            vm.exportReport(AppLanguage.PT)
            assertEquals(1, calls)
            assertTrue((vm.uiState.value as HistoryUiState.Success).isExporting)
            gate.complete(Unit)
            assertNull(ready(vm).exportOutcome)
        } finally { vm.onDestroy() }
    }
    @Test fun `export is unavailable while old report is being refreshed`(): Unit = runBlocking {
        val repo = Repository()
        var calls = 0
        val vm = HistoryViewModel(GetUsageHistoryUseCase(repo), MutableStateFlow(setOf(ApiSource.ANTHROPIC)), exportWriter = UsageExportWriter { calls++; "/report.pdf" })
        try {
            ready(vm)
            repo.gate = CompletableDeferred()
            vm.selectRange(HistoryRange.LAST_7_DAYS)
            assertTrue((vm.uiState.value as HistoryUiState.Success).isRefreshing)
            vm.exportReport(AppLanguage.PT)
            assertEquals(0, calls)
            repo.gate!!.complete(Unit)
            ready(vm)
        } finally { vm.onDestroy() }
    }
    @Test fun `writer success and failure publish useful outcomes`() = runBlocking {
        for (failed in listOf(false, true)) {
            val vm = HistoryViewModel(GetUsageHistoryUseCase(Repository()), MutableStateFlow(setOf(ApiSource.ANTHROPIC)), exportWriter = UsageExportWriter { if (failed) error("Disco sem espaço") else "/report.pdf" })
            try {
                ready(vm); vm.exportReport(AppLanguage.PT)
                val outcome = withTimeout(5000) { vm.uiState.filterIsInstance<HistoryUiState.Success>().first { it.exportOutcome != null }.exportOutcome }
                if (failed) assertEquals("Disco sem espaço", assertIs<CliExportOutcome.Failed>(outcome).message)
                else assertEquals("/report.pdf", assertIs<CliExportOutcome.Saved>(outcome).path)
            } finally { vm.onDestroy() }
        }
    }
    @Test fun `changing range cancels pending export without publishing result into new range`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val vm = HistoryViewModel(GetUsageHistoryUseCase(Repository()), MutableStateFlow(setOf(ApiSource.ANTHROPIC)), exportWriter = UsageExportWriter {
            started.complete(Unit)
            try { awaitCancellation() } finally { cancelled.complete(Unit) }
        })
        try {
            ready(vm); vm.exportReport(AppLanguage.PT)
            withTimeout(5000) { started.await() }
            vm.selectRange(HistoryRange.LAST_7_DAYS)
            withTimeout(5000) { cancelled.await() }
            val current = ready(vm)
            assertEquals(HistoryRange.LAST_7_DAYS, current.selectedRange)
            assertNull(current.exportOutcome)
        } finally { vm.onDestroy() }
    }
}

package com.usagemonitor.ui

import androidx.compose.ui.test.*
import com.usagemonitor.domain.entity.*
import com.usagemonitor.domain.repository.UsageHistoryRepository
import com.usagemonitor.domain.usecase.GetUsageHistoryUseCase
import com.usagemonitor.presentation.ui.*
import com.usagemonitor.presentation.viewmodel.*
import com.usagemonitor.screenshots.issue383HistoryReports
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class)
class HistoryAdequacyTest {
    @Test fun `expanded window readings remain complete in narrow modal`() = runDesktopComposeUiTest(width = 320, height = 840) {
        val vm = viewModel(UsageExportWriter { null })
        try {
            setContent { ScreenTestTheme(isDark = true) { HistoryScreen(vm, AppLanguage.PT, {}, showSourceSelector = false) } }
            waitUntil(timeoutMillis = 5000) { vm.uiState.value is HistoryUiState.Success }
            onNodeWithText("▸ Janelas e distribuição horária").performScrollTo().performClick()
            val window = (vm.uiState.value as HistoryUiState.Success).report.series.first().windows.last()
            val value = onNodeWithText(windowStartLabel(window, AppLanguage.PT)).performScrollTo().assertIsDisplayed()
            val layouts = mutableListOf<TextLayoutResult>()
            value.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertFalse(layouts.single().hasVisualOverflow)
        } finally { vm.onDestroy() }
    }
    private fun viewModel(writer: UsageExportWriter? = null): HistoryViewModel {
        val report = issue383HistoryReports().first()
        val repository = object : UsageHistoryRepository {
            override suspend fun recordSnapshot(stats: ApiUsageStats, capturedAt: Instant) = Unit
            override suspend fun listAccounts(source: ApiSource) = listOfNotNull(report.accountContext)
            override suspend fun getHistoryReport(source: ApiSource, range: HistoryRange, now: Instant) = report.copy(range = range)
            override suspend fun getHistoryReport(source: ApiSource, accountKey: UsageAccountKey?, range: HistoryRange, now: Instant) = report.copy(range = range)
        }
        return HistoryViewModel(GetUsageHistoryUseCase(repository), MutableStateFlow(setOf(ApiSource.ANTHROPIC)), exportWriter = writer)
    }
    @Test fun `narrow modal keeps range and PDF controls visible while report scrolls`() = runDesktopComposeUiTest(width = 320, height = 840) {
        val vm = viewModel(UsageExportWriter { null })
        try {
            setContent { ScreenTestTheme(isDark = true) { HistoryScreen(vm, AppLanguage.PT, {}, showSourceSelector = false) } }
            waitUntil(timeoutMillis = 5000) { vm.uiState.value is HistoryUiState.Success }
            onNodeWithTag(HISTORY_RANGE_MENU_TAG).performClick()
            onNodeWithTag(historyRangeChipTag(HistoryRange.TOTAL)).performClick()
            waitUntil(timeoutMillis = 5000) { (vm.uiState.value as? HistoryUiState.Success)?.isRefreshing == false }
            onNodeWithTag(HISTORY_RANGE_MENU_TAG).assertIsDisplayed()
            onNodeWithTag(HISTORY_EXPORT_PDF_TAG).assertIsDisplayed().assertIsEnabled()
            onNodeWithText("Cota semanal atual").performScrollTo().assertIsDisplayed()
            onNodeWithTag(HISTORY_RANGE_MENU_TAG).assertIsDisplayed()
            onNodeWithTag(HISTORY_EXPORT_PDF_TAG).assertIsDisplayed()
        } finally { vm.onDestroy() }
    }
    @Test fun `PDF action reaches writer and cancelling does not show a saved message`() = runDesktopComposeUiTest(height = 1200) {
        var calls = 0
        val vm = viewModel(UsageExportWriter { calls++; null })
        try {
            setContent { ScreenTestTheme(isDark = true) { HistoryScreen(vm, AppLanguage.PT, {}, showSourceSelector = false) } }
            waitUntil(timeoutMillis = 5000) { vm.uiState.value is HistoryUiState.Success }
            onNodeWithTag(HISTORY_EXPORT_PDF_TAG).performClick()
            waitUntil(timeoutMillis = 5000) { calls == 1 && !(vm.uiState.value as HistoryUiState.Success).isExporting }
            onNodeWithTag("historyExportOutcome").assertDoesNotExist()
            assertEquals(1, calls)
        } finally { vm.onDestroy() }
    }
    @Test fun `quota details can be collapsed while chart remains accessible`() = runDesktopComposeUiTest(height = 1200) {
        val vm = viewModel()
        try {
            setContent { ScreenTestTheme(isDark = false) { HistoryScreen(vm, AppLanguage.PT, {}, showSourceSelector = false) } }
            waitUntil(timeoutMillis = 5000) { vm.uiState.value is HistoryUiState.Success }
            onNodeWithText("▾ Resumo das cotas").performClick()
            onNodeWithText("Cota intervalar atual").assertDoesNotExist()
            onNodeWithText("Claude").assertIsDisplayed()
            onNodeWithText("▸ Resumo das cotas").performClick()
            onNodeWithText("Cota intervalar atual").assertIsDisplayed()
        } finally { vm.onDestroy() }
    }
}

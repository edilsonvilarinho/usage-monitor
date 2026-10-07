package com.usagemonitor.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.graphics.Color
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.*
import com.usagemonitor.presentation.viewmodel.HistoryUiState
import com.usagemonitor.screenshots.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class HistoryLayoutRegressionTest {
    @Test
    fun `eight window list items occupy distinct vertical positions`() = runDesktopComposeUiTest(width = 1030, height = 900) {
        val series = issue383ManyWindowsSeries()
        setContent { ScreenTestTheme(isDark = true) { HistoryWindowAnalysisPanel(series, Color.Cyan, AppLanguage.PT) } }
        val rows = windowRowsNewestFirst(series.windows).map { window ->
            onNodeWithTag(historyWindowItemTag(window)).fetchSemanticsNode().boundsInRoot
        }
        assertEquals(HISTORY_WINDOW_ROW_LIMIT, rows.size)
        rows.zipWithNext().forEach { (previous, next) -> assertTrue(previous.bottom <= next.top, "Linhas sobrepostas: $previous e $next") }
    }

    @Test
    fun `window list opens the current window and switches the detail on click`() = runDesktopComposeUiTest(width = 1030, height = 900) {
        val series = issue383ManyWindowsSeries()
        val newestFirst = windowRowsNewestFirst(series.windows)
        setContent { ScreenTestTheme(isDark = true) { HistoryWindowAnalysisPanel(series, Color.Cyan, AppLanguage.PT) } }
        onNodeWithTag(historyWindowItemTag(newestFirst.first())).assertIsSelected()
        onNodeWithTag(HISTORY_WINDOW_DETAIL_TAG).onChildren().filterToOne(hasText(windowStartLabel(newestFirst.first(), AppLanguage.PT))).assertExists()

        onNodeWithTag(historyWindowItemTag(newestFirst[2])).performClick()

        onNodeWithTag(historyWindowItemTag(newestFirst[2])).assertIsSelected()
        onNodeWithTag(historyWindowItemTag(newestFirst.first())).assertIsNotSelected()
        onNodeWithTag(HISTORY_WINDOW_DETAIL_TAG).onChildren().filterToOne(hasText(windowStartLabel(newestFirst[2], AppLanguage.PT))).assertExists()
    }

    @Test
    fun `narrow window panel turns the list into a menu`() = runDesktopComposeUiTest(width = 360, height = 900) {
        val series = issue383ManyWindowsSeries()
        val newestFirst = windowRowsNewestFirst(series.windows)
        setContent { ScreenTestTheme(isDark = true) { HistoryWindowAnalysisPanel(series, Color.Cyan, AppLanguage.PT) } }
        onNodeWithTag(historyWindowItemTag(newestFirst[1])).assertDoesNotExist()
        onNodeWithTag(HISTORY_WINDOW_MENU_TAG).performClick()
        onNodeWithTag(historyWindowItemTag(newestFirst[1])).performClick()
        onNodeWithTag(HISTORY_WINDOW_MENU_TAG).assertContentDescriptionEquals("Janela: ${windowStartLabel(newestFirst[1], AppLanguage.PT)}")
    }

    @Test
    fun `long account list does not consume report area in short window`() = runDesktopComposeUiTest(width = 1030, height = 560) {
        val accounts = issue383LongHistoryAccounts(ApiSource.ANTHROPIC)
        val report = issue383HistoryReports().first().copy(accountContext = accounts.first())
        val vm = fixedHistoryViewModel(report, accounts)
        try {
            setContent { ScreenTestTheme(isDark = true) { HistoryScreen(vm, AppLanguage.PT, {}, focusedSource = report.source, showSourceSelector = false) } }
            val controls = onNodeWithTag(HISTORY_CONTROLS_TAG).getUnclippedBoundsInRoot()
            assertTrue((controls.bottom - controls.top).value <= 60f, "Barra fixa excessiva: $controls")
            accounts.forEach { onNodeWithTag(historyAccountChipTag(it)).assertDoesNotExist() }
            onNodeWithText("Resumo das cotas", substring = true).assertIsDisplayed()
            onNodeWithTag(HISTORY_ACCOUNT_MENU_TAG).performClick()
            onNodeWithTag(historyAccountChipTag(accounts.first())).assertIsSelected()
            onNodeWithTag(historyAccountChipTag(accounts.last())).performClick()
            waitUntil(timeoutMillis = 5000) { (vm.uiState.value as? HistoryUiState.Success)?.selectedAccount == accounts.last() }
            onNodeWithTag(HISTORY_ACCOUNT_MENU_TAG).assertContentDescriptionEquals("Conta: ${accounts.last().displayLabel}")
        } finally { vm.onDestroy() }
    }

    @Test
    fun `narrow account popup wraps full labels at elevated scale`() = runDesktopComposeUiTest(width = 320, height = 700) {
        val accounts = issue383LongHistoryAccounts(ApiSource.ANTHROPIC)
        val vm = fixedHistoryViewModel(issue383HistoryReports().first().copy(accountContext = accounts.first()), accounts)
        try {
            setContent { ScreenTestTheme(isDark = false, uiScalePercent = 125) { HistoryScreen(vm, AppLanguage.PT, {}, showSourceSelector = false) } }
            onNodeWithTag(HISTORY_ACCOUNT_MENU_TAG).performClick()
            val label = onNodeWithText(accounts.first().displayLabel, useUnmergedTree = true)
            val layouts = mutableListOf<TextLayoutResult>()
            label.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertFalse(layouts.single().hasVisualOverflow, "Nome da conta truncado no menu")
            onNodeWithTag(historyAccountChipTag(accounts.last())).performScrollTo().performClick()
            waitUntil(timeoutMillis = 5000) { (vm.uiState.value as? HistoryUiState.Success)?.selectedAccount == accounts.last() }
        } finally { vm.onDestroy() }
    }
}

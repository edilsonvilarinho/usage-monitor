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
    fun `hovering an hourly bar names the hour and its share`() = runDesktopComposeUiTest(width = 1030, height = 900) {
        val hours = com.usagemonitor.domain.entity.QuotaHourlyDistribution(List(24) { hour -> when (hour) { 16 -> 31.0; 17 -> 53.0; 19 -> 16.0; else -> 0.0 } })
        val base = issue383ManyWindowsSeries()
        val series = base.copy(windows = base.windows.map { it.copy(hourlyDistribution = hours) })
        setContent { ScreenTestTheme(isDark = true) { HistoryWindowAnalysisPanel(series, Color.Cyan, AppLanguage.PT) } }
        onNodeWithTag(HISTORY_HOURLY_TOOLTIP_TAG).assertDoesNotExist()

        val chart = onNodeWithTag(HISTORY_HOURLY_CHART_TAG)
        val width = chart.fetchSemanticsNode().size.width.toFloat()
        chart.performMouseInput { moveTo(androidx.compose.ui.geometry.Offset(width * 17.5f / 24f, 20f)) }

        onNodeWithTag(HISTORY_HOURLY_TOOLTIP_TAG).assertExists()
        onNodeWithText("17h–18h BRT · 53% do consumo").assertExists()
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

    /** 5h com uma leitura no início e outra no pico de cada janela sintética, para a diagonal (#463). */
    private fun paceSeries(label: String = "Claude 5h", periodType: com.usagemonitor.domain.entity.PeriodType = com.usagemonitor.domain.entity.PeriodType.INTERVAL): com.usagemonitor.domain.entity.UsageHistorySeries {
        val base = issue383ManyWindowsSeries()
        val points = base.windows.flatMap { window ->
            listOf(
                com.usagemonitor.domain.entity.UsageHistoryPoint(window.firstObservedAt, 0L, 100L, 0L, 0L, window.resetsAt),
                com.usagemonitor.domain.entity.UsageHistoryPoint(window.lastObservedAt, window.peakPercent.toLong(), 100L, 0L, 0L, window.resetsAt)
            )
        }
        return base.copy(quotaLabel = label, periodType = periodType, points = points)
    }

    @Test
    fun `pace panel draws the windows on the diagonal and switches the chosen one by the menu`() = runDesktopComposeUiTest(width = 1030, height = 900) {
        val series = paceSeries()
        val newestFirst = windowRowsNewestFirst(series.windows)
        setContent { ScreenTestTheme(isDark = true) { HistoryPaceWindowsPanel(series, Color.Cyan, AppLanguage.PT) } }
        onNodeWithTag(PACE_DIAGONAL_TAG).assertIsDisplayed()
        onNodeWithTag(HISTORY_WINDOW_MENU_TAG).assertContentDescriptionEquals("Janela: ${windowStartLabel(newestFirst.first(), AppLanguage.PT)}")
        onNodeWithText(paceWindowFacts(newestFirst.first(), AppLanguage.PT)).assertExists()

        onNodeWithTag(HISTORY_WINDOW_MENU_TAG).performClick()
        onNodeWithTag(historyWindowItemTag(newestFirst[2])).performClick()

        onNodeWithTag(HISTORY_WINDOW_MENU_TAG).assertContentDescriptionEquals("Janela: ${windowStartLabel(newestFirst[2], AppLanguage.PT)}")
        onNodeWithText(paceWindowFacts(newestFirst[2], AppLanguage.PT)).assertExists()
        onNodeWithTag(PACE_DIAGONAL_TAG).assertContentDescriptionContains(windowStartLabel(newestFirst[2], AppLanguage.PT), substring = true)
    }

    @Test
    fun `5h and 7d windows sit side by side on a wide window and stack on a narrow one`() {
        val fiveHour = paceSeries()
        val weekly = paceSeries("Claude 7d", com.usagemonitor.domain.entity.PeriodType.WEEKLY)
        fun bounds(width: Int): Pair<androidx.compose.ui.unit.DpRect, androidx.compose.ui.unit.DpRect> {
            var result: Pair<androidx.compose.ui.unit.DpRect, androidx.compose.ui.unit.DpRect>? = null
            runDesktopComposeUiTest(width = width, height = 2400) {
                setContent { ScreenTestTheme(isDark = true) { HistoryPaceWindowsRow(listOf(fiveHour, weekly), { Color.Cyan }, AppLanguage.PT) } }
                result = onNodeWithTag(HISTORY_PACE_PANEL_TAG_PREFIX + "Claude 5h").getUnclippedBoundsInRoot() to
                    onNodeWithTag(HISTORY_PACE_PANEL_TAG_PREFIX + "Claude 7d").getUnclippedBoundsInRoot()
            }
            return result!!
        }
        val (wideFive, wideWeek) = bounds(1030)
        assertEquals(wideFive.top, wideWeek.top, "Lado a lado: mesma altura de topo")
        assertTrue(wideFive.right <= wideWeek.left, "Lado a lado: 5h à esquerda da 7d")
        val (narrowFive, narrowWeek) = bounds(600)
        assertTrue(narrowFive.bottom <= narrowWeek.top, "Empilhadas: 7d abaixo da 5h")
    }
}

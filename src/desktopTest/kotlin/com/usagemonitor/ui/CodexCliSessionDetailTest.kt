package com.usagemonitor.ui

import androidx.compose.ui.test.*
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.CLI_TURN_CHART_GRID_TAG
import com.usagemonitor.presentation.ui.CODEX_TURN_ROW_TAG_PREFIX
import com.usagemonitor.presentation.ui.CodexCliSessionDetailPane
import com.usagemonitor.presentation.ui.formatQuantity
import com.usagemonitor.screenshots.codexDetailFixture
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class CodexCliSessionDetailTest {
    private val titles = listOf("Contexto por resposta", "Cache por resposta", "Saída por resposta", "Vazão (tok/s)")

    @Test
    fun `detail shows the four turn charts in two columns`() = runDesktopComposeUiTest(width = 1060, height = 1400) {
        setContent { ScreenTestTheme(isDark = true) { CodexCliSessionDetailPane(codexDetailFixture(), AppLanguage.PT, {}) } }
        onNodeWithTag(CLI_TURN_CHART_GRID_TAG).assertExists()
        val bounds = titles.map { title -> onAllNodesWithText(title).onFirst().fetchSemanticsNode().boundsInRoot }
        // Contexto e Cache na mesma linha; Saída e Vazão na de baixo.
        assertTrue(bounds[0].top == bounds[1].top && bounds[0].right <= bounds[1].left, "1ª linha: $bounds")
        assertTrue(bounds[2].top == bounds[3].top && bounds[2].top > bounds[0].bottom, "2ª linha: $bounds")
    }

    @Test
    fun `narrow detail stacks the charts in one column`() = runDesktopComposeUiTest(width = 420, height = 2000) {
        setContent { ScreenTestTheme(isDark = true) { CodexCliSessionDetailPane(codexDetailFixture(12), AppLanguage.PT, {}) } }
        val tops = titles.map { title -> onAllNodesWithText(title).onFirst().fetchSemanticsNode().boundsInRoot.top }
        tops.zipWithNext().forEach { (previous, next) -> assertTrue(previous < next, "Gráficos fora de uma coluna: $tops") }
    }

    @Test
    fun `turn table formats tokens and drops the response id`() = runDesktopComposeUiTest(width = 1060, height = 1400) {
        setContent { ScreenTestTheme(isDark = true) { CodexCliSessionDetailPane(codexDetailFixture(12), AppLanguage.PT, {}) } }
        val first = onNodeWithTag("${CODEX_TURN_ROW_TAG_PREFIX}0", useUnmergedTree = true)
        first.performScrollTo()
        onNode(hasText(formatQuantity(35_789L)) and hasAnyAncestor(hasTestTag("${CODEX_TURN_ROW_TAG_PREFIX}0")), useUnmergedTree = true).assertExists()
        // Resposta 0 não tem vazão medida (múltiplo de 17): "—", nunca "0 tok/s".
        onNode(hasText("—") and hasAnyAncestor(hasTestTag("${CODEX_TURN_ROW_TAG_PREFIX}0")), useUnmergedTree = true).assertExists()
        onAllNodesWithText("resp_", substring = true, useUnmergedTree = true).assertCountEquals(0)
    }
}

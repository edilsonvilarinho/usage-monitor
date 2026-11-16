package com.usagemonitor.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.CLI_TURN_CHART_GRID_TAG
import com.usagemonitor.presentation.ui.CODEX_TURN_ROW_TAG_PREFIX
import com.usagemonitor.presentation.ui.CodexCliSessionDetailPane
import com.usagemonitor.presentation.ui.SESSION_MODEL_ROW_TAG_PREFIX
import com.usagemonitor.presentation.ui.SESSION_MODEL_TABLE_TAG
import com.usagemonitor.presentation.ui.formatQuantity
import com.usagemonitor.screenshots.codexDetailFixture
import kotlin.test.Test
import kotlin.test.assertEquals
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

    @Test
    fun `detail splits the session by model on a token basis without cost`() = runDesktopComposeUiTest(width = 1060, height = 1400) {
        setContent { ScreenTestTheme(isDark = true) { CodexCliSessionDetailPane(codexDetailFixture(), AppLanguage.PT, {}) } }
        onNodeWithTag(SESSION_MODEL_TABLE_TAG).performScrollTo().assertIsDisplayed()
        // Sem tarifa do Codex no app: a fatia é dos tokens, e não há coluna de custo (#445).
        onNodeWithText("2 modelos · participação nos tokens, sem tarifa").assertExists()
        onNodeWithText("% dos tokens").assertExists()
        onNodeWithTag("${SESSION_MODEL_ROW_TAG_PREFIX}gpt-6.1-sol").assertExists()
        onNodeWithTag("${SESSION_MODEL_ROW_TAG_PREFIX}gpt-6.1-sol-mini").assertExists()
        onAllNodesWithText("Custo").assertCountEquals(0)
        // Sem tarifa não há base de custo: a coluna de participação no uso some junto (#535).
        onAllNodesWithText("% de uso").assertCountEquals(0)
    }

    @Test
    fun `long session shows only the 200 newest response rows`() = runDesktopComposeUiTest(width = 1060, height = 1400) {
        setContent { ScreenTestTheme(isDark = true) { CodexCliSessionDetailPane(codexDetailFixture(4317), AppLanguage.PT, {}) } }
        val rows = turnRowCount()
        assertTrue(rows <= 200, "linhas de turno montadas: $rows")
        onNodeWithTag("${CODEX_TURN_ROW_TAG_PREFIX}4316", useUnmergedTree = true).assertExists()
        onAllNodesWithTag("${CODEX_TURN_ROW_TAG_PREFIX}0", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun `show earlier button adds 200 responses per click`() = runDesktopComposeUiTest(width = 1060, height = 1400) {
        setContent { ScreenTestTheme(isDark = true) { CodexCliSessionDetailPane(codexDetailFixture(4317), AppLanguage.PT, {}) } }
        onNodeWithText("Mostrar 200 anteriores (restam 4117)").performClick()
        waitForIdle()
        assertEquals(400, turnRowCount(), "linhas após um clique")
        onNodeWithText("Mostrar 200 anteriores (restam 3917)").assertExists()
    }

    @Test
    fun `english show earlier button counts the responses left`() = runDesktopComposeUiTest(width = 1060, height = 1400) {
        setContent { ScreenTestTheme(isDark = true) { CodexCliSessionDetailPane(codexDetailFixture(4317), AppLanguage.EN, {}) } }
        onNodeWithText("Show 200 earlier (4117 left)").assertExists()
    }

    @Test
    fun `page resets when the session changes and survives a reload of the same session`() = runDesktopComposeUiTest(width = 1060, height = 1400) {
        val base = codexDetailFixture(4317)
        var detail by mutableStateOf(base)
        setContent { ScreenTestTheme(isDark = true) { CodexCliSessionDetailPane(detail, AppLanguage.PT, {}) } }
        onNodeWithText("Mostrar 200 anteriores (restam 4117)").performClick()
        waitForIdle()
        assertEquals(400, turnRowCount(), "após clique")
        // Recarga de 5 s da mesma sessão: objeto novo, mesmo sessionId; a página não volta.
        detail = base.copy(turns = base.turns.toList())
        waitForIdle()
        assertEquals(400, turnRowCount(), "após recarga da mesma sessão")
        // Outra sessão: a página volta às 200 mais recentes.
        detail = base.copy(summary = base.summary.copy(sessionId = "outra-sessao"))
        waitForIdle()
        assertEquals(200, turnRowCount(), "após troca de sessão")
    }

    private fun ComposeUiTest.turnRowCount(): Int = onAllNodes(
        SemanticsMatcher("linha de turno do Codex") { node ->
            node.config.contains(SemanticsProperties.TestTag) && node.config[SemanticsProperties.TestTag].startsWith(CODEX_TURN_ROW_TAG_PREFIX)
        },
        useUnmergedTree = true
    ).fetchSemanticsNodes().size
}

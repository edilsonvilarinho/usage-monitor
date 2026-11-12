package com.usagemonitor.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CliActivityCell
import com.usagemonitor.domain.entity.CliActivityHeatmap
import com.usagemonitor.domain.entity.CliBurnRate
import com.usagemonitor.domain.entity.CliToolUsage
import com.usagemonitor.domain.entity.CliUsageBreakdown
import com.usagemonitor.domain.entity.CliUsageBucket
import com.usagemonitor.presentation.ui.components.ACTIVITY_HEATMAP_TAG
import com.usagemonitor.presentation.ui.BREAKDOWN_AXIS_TAB_TAG_PREFIX
import com.usagemonitor.presentation.ui.BREAKDOWN_SORT_TAG_PREFIX
import com.usagemonitor.presentation.ui.BreakdownLabels
import com.usagemonitor.presentation.ui.BreakdownSort
import com.usagemonitor.presentation.ui.CliUsageBreakdownPane
import com.usagemonitor.presentation.ui.formatActiveTime
import kotlinx.datetime.DayOfWeek
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Abas de ferramenta e de atividade, estados sem dado e os avisos do card de
 * totais. A cena é alta porque a pane empilha totais e tabela numa lista só e
 * o que fica fora da cena não é composto.
 */
@OptIn(ExperimentalTestApi::class)
class CliUsageBreakdownPaneTest {

    @Test
    fun `without a breakdown it loads, and with an error it says so`() = runDesktopComposeUiTest(width = 1_000, height = 600) {
        val error = mutableStateOf<String?>(null)
        setContent { ScreenTestTheme { CliUsageBreakdownPane(breakdown = null, errorMessage = error.value, language = AppLanguage.PT) } }

        onAllNodesWithText("índice indisponível").assertCountEquals(0)
        error.value = "índice indisponível"
        waitForIdle()

        onNodeWithText("índice indisponível").assertExists()
    }

    @Test
    fun `empty breakdown shows the empty state`() = runDesktopComposeUiTest(width = 1_000, height = 600) {
        setContent {
            ScreenTestTheme { CliUsageBreakdownPane(breakdown = CliUsageBreakdown(), errorMessage = null, language = AppLanguage.EN) }
        }

        onNodeWithText("No turns in this window.").assertExists()
    }

    @Test
    fun `tool tab lists calls and turns with the call-count notice`() = runDesktopComposeUiTest(width = 1_200, height = SCENE_HEIGHT) {
        setContent { ScreenTestTheme { CliUsageBreakdownPane(breakdown(), errorMessage = null, language = AppLanguage.PT) } }

        onNodeWithTag("${BREAKDOWN_AXIS_TAB_TAG_PREFIX}TOOL").performClick()
        waitForIdle()

        onNodeWithText("Bash").assertExists()
        onNodeWithText("Read").assertExists()
        // Ordem e coluna dizem "Chamadas": na ferramenta não existe custo.
        onAllNodesWithText("Chamadas").assertCountEquals(2)
        onAllNodesWithText("Custo").assertCountEquals(0)
        onNodeWithText(BreakdownLabels.toolNotice(AppLanguage.PT)).assertExists()
        // O líder do eixo é o pico: 100% da barra.
        onNodeWithText("100%").assertExists()
        onNodeWithText("25%").assertExists()
    }

    @Test
    fun `activity tab draws the heatmap without list controls`() = runDesktopComposeUiTest(width = 1_200, height = SCENE_HEIGHT) {
        setContent { ScreenTestTheme { CliUsageBreakdownPane(breakdown(), errorMessage = null, language = AppLanguage.EN) } }

        onNodeWithTag("${BREAKDOWN_AXIS_TAB_TAG_PREFIX}ACTIVITY").performClick()
        waitForIdle()

        onNodeWithTag(ACTIVITY_HEATMAP_TAG).assertExists()
        onNodeWithText("Activity by hour").assertExists()
        onNodeWithText(BreakdownLabels.activityNotice(AppLanguage.EN)).assertExists()
        // Grade não é lista: sem filtro.
        onAllNodesWithText("Filter").assertCountEquals(0)
    }

    @Test
    fun `totals warn about unpriced turns, project the window and keep stale numbers`() =
        runDesktopComposeUiTest(width = 1_200, height = SCENE_HEIGHT) {
            setContent {
                ScreenTestTheme { CliUsageBreakdownPane(breakdown(), errorMessage = "leitura falhou", language = AppLanguage.PT) }
            }

            onNodeWithText(BreakdownLabels.unpricedNotice(2, AppLanguage.PT)).assertExists()
            onNodeWithText(BreakdownLabels.burnRateProjection(9_000_000L, AppLanguage.PT)).assertExists()
            onNodeWithText(BreakdownLabels.staleNotice("leitura falhou", AppLanguage.PT)).assertExists()
        }

    @Test
    fun `member axis shows active time only where it was measured`() = runDesktopComposeUiTest(width = 1_200, height = SCENE_HEIGHT) {
        setContent { ScreenTestTheme { CliUsageBreakdownPane(breakdown(), errorMessage = null, language = AppLanguage.PT) } }

        onNodeWithTag("${BREAKDOWN_AXIS_TAB_TAG_PREFIX}MEMBER").performClick()
        waitForIdle()

        onNodeWithText(formatActiveTime(5_400_000L)).assertExists()
        onNodeWithText("ana").assertExists()
        onNodeWithText("bia").assertExists()
    }

    @Test
    fun `codex member axis takes the agent name and maps the main thread row`() = runDesktopComposeUiTest(width = 1_200, height = SCENE_HEIGHT) {
        setContent {
            ScreenTestTheme {
                CliUsageBreakdownPane(
                    breakdown(),
                    errorMessage = null,
                    language = AppLanguage.PT,
                    memberAxisLabel = "Agente",
                    memberRowLabel = { label -> if (label == "ana") "Sessão principal" else label }
                )
            }
        }

        onNodeWithTag("${BREAKDOWN_AXIS_TAB_TAG_PREFIX}MEMBER").performClick()
        waitForIdle()

        assertShown("Agente")
        assertShown("Sessão principal")
        assertAbsent("Integrante")
    }

    @Test
    fun `unpriced mode drops the cost cards and shows responses`() = runDesktopComposeUiTest(width = 1_200, height = SCENE_HEIGHT) {
        setContent {
            ScreenTestTheme {
                CliUsageBreakdownPane(breakdown(), errorMessage = null, language = AppLanguage.PT, unpricedMode = true)
            }
        }

        assertAbsent("Custo")
        assertShown("Respostas")
    }

    @Test
    fun `changing the sort order brings the list back to the top after scrolling down`() =
        runDesktopComposeUiTest(width = 1_200, height = 700) {
            setContent { ScreenTestTheme { CliUsageBreakdownPane(manyProjects(), errorMessage = null, language = AppLanguage.PT) } }
            // Página de 50 linhas: com 40 projetos a lista cabe na cena e não há o que rolar.
            onNodeWithText("50").performClick()
            waitForIdle()

            onRoot().performMouseInput { moveTo(center); repeat(200) { scroll(1f) } }
            waitForIdle()
            // Pré-condição: a linha que vai ao topo com a nova ordem já saiu da janela.
            onNodeWithText("proj39").assertIsNotDisplayed()

            onNodeWithTag("$BREAKDOWN_SORT_TAG_PREFIX${BreakdownSort.NAME.name}").performClick()
            waitForIdle()

            onNodeWithText("proj39").assertIsDisplayed()
        }

    private fun manyProjects(): CliUsageBreakdown {
        val buckets = (0 until 40).map { index ->
            CliUsageBucket(label = "proj%02d".format(index), turnCount = index + 1, costMicros = 1_000_000L)
        }
        return CliUsageBreakdown(byProject = buckets, totals = buckets.last())
    }

    private fun ComposeUiTest.assertShown(text: String) {
        val nodes = onAllNodesWithText(text).fetchSemanticsNodes().size
        assertTrue(nodes >= 1, "Falta \"$text\" na pane: achei $nodes nó(s)")
    }

    private fun ComposeUiTest.assertAbsent(text: String) {
        val nodes = onAllNodesWithText(text).fetchSemanticsNodes().size
        assertTrue(nodes == 0, "Esperava 0 nó(s) com \"$text\", achei $nodes")
    }

    private fun breakdown(): CliUsageBreakdown {
        val bucket = CliUsageBucket(
            label = "usage-monitor",
            turnCount = 10,
            sessionCount = 2,
            inputTokens = 5_000L,
            cacheReadTokens = 1_000L,
            costMicros = 4_000_000L,
            unpricedTurnCount = 2
        )
        return CliUsageBreakdown(
            byProject = listOf(bucket),
            byModel = listOf(bucket.copy(label = "claude-sonnet-5")),
            byMember = listOf(
                bucket.copy(label = "ana", activeMillis = 5_400_000L),
                bucket.copy(label = "bia", activeMillis = 0L, costMicros = 1_000_000L)
            ),
            totals = bucket,
            byTool = listOf(
                CliToolUsage(toolName = "Bash", callCount = 8, turnCount = 5),
                CliToolUsage(toolName = "Read", callCount = 2, turnCount = 2)
            ),
            heatmap = CliActivityHeatmap(
                cells = listOf(
                    CliActivityCell(DayOfWeek.MONDAY, 9, turnCount = 4, totalTokens = 3_000L, costMicros = 2_000_000L),
                    CliActivityCell(DayOfWeek.FRIDAY, 18, turnCount = 1, totalTokens = 500L, costMicros = 100_000L)
                )
            ),
            burnRate = CliBurnRate(
                elapsedMillis = 90L * 60_000L,
                tokensPerHour = 4_000.0,
                costMicrosPerHour = 2_000_000L,
                projectedCostMicros = 9_000_000L
            )
        )
    }

    private companion object {
        const val SCENE_HEIGHT = 1_600
    }
}

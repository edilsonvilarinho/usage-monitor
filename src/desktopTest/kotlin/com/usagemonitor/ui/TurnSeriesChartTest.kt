package com.usagemonitor.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.usagemonitor.presentation.ui.components.TurnSeries
import com.usagemonitor.presentation.ui.components.TurnSeriesChart
import kotlin.test.Test

/** Tooltip por hover, marcador de compactação e estado sem dado do gráfico por resposta. */
@OptIn(ExperimentalTestApi::class)
class TurnSeriesChartTest {

    private val series = listOf(
        TurnSeries(label = "Contexto", values = listOf(100L, 300L, 50L, 80L), color = Color.Cyan),
        TurnSeries(label = "Cache", values = listOf(10L, 20L, 30L, null), color = Color.Green)
    )

    private val plot = hasContentDescription("Contexto: 4 pontos", substring = true)

    /** O primeiro evento dentro do nó chega como `Enter`; o `Move` que o gráfico escuta vem depois. */
    private fun androidx.compose.ui.test.ComposeUiTest.hoverPlot(fraction: Float) {
        onNode(plot).performMouseInput {
            moveTo(percentOffset(fraction, 0.5f))
            moveBy(androidx.compose.ui.geometry.Offset(0.5f, 0f))
        }
        waitForIdle()
    }

    @Test
    fun `hover shows every series value at the pointed response and leaves on exit`() =
        runDesktopComposeUiTest(width = 800, height = 400) {
            setContent { ScreenTestTheme { TurnSeriesChart(series = series, valueFormatter = { "v$it" }) } }

            onAllNodesWithText("#1").assertCountEquals(0)
            hoverPlot(0.01f)
            waitForIdle()

            onNodeWithText("#1").assertExists()
            onNodeWithText("v100").assertExists()
            onNodeWithText("v10").assertExists()

            // Último ponto: o cache não foi medido ali, e "—" não é zero.
            hoverPlot(0.99f)
            waitForIdle()
            onNodeWithText("#4").assertExists()
            onNodeWithText("—").assertExists()

            onNode(plot).performMouseInput { exit() }
            waitForIdle()
            onAllNodesWithText("#4").assertCountEquals(0)
        }

    @Test
    fun `hover on a drop names the compaction`() = runDesktopComposeUiTest(width = 800, height = 400) {
        setContent { ScreenTestTheme { TurnSeriesChart(series = series, highlightDrops = true) } }

        hoverPlot(0.67f)
        waitForIdle()

        onNodeWithText("#3 · compactação").assertExists()
    }

    @Test
    fun `stacked chart keeps the same tooltip`() = runDesktopComposeUiTest(width = 800, height = 400) {
        setContent { ScreenTestTheme { TurnSeriesChart(series = series, stacked = true) } }

        hoverPlot(0.34f)
        waitForIdle()

        onNodeWithText("#2").assertExists()
        onNodeWithText("300").assertExists()
    }

    @Test
    fun `no measured series shows the empty label`() = runDesktopComposeUiTest(width = 800, height = 400) {
        setContent {
            ScreenTestTheme {
                TurnSeriesChart(
                    series = listOf(TurnSeries(label = "Contexto", values = emptyList(), color = Color.Cyan)),
                    emptyLabel = "sem respostas"
                )
            }
        }

        onNodeWithText("sem respostas").assertExists()
    }
}

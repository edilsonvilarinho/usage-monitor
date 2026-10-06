package com.usagemonitor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CliSessionRange
import com.usagemonitor.domain.entity.ComparisonRow
import com.usagemonitor.domain.entity.OutputThroughput
import com.usagemonitor.presentation.ui.COMPARISON_TABLE_TAG
import com.usagemonitor.presentation.ui.ComparisonContent
import com.usagemonitor.presentation.viewmodel.ComparisonUiState
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.time.Instant

private val READ_AT = Instant.parse("2026-10-06T14:42:00Z")

private val ROWS = listOf(
    ComparisonRow(ApiSource.ANTHROPIC, "claude-opus-5-5", 112, 6_320_000, 9_100_000, 0, 0.96, OutputThroughput(94_000, 1_000_000), 68),
    ComparisonRow(ApiSource.ANTHROPIC, "claude-sonnet-5", 61, 2_540_000, 2_310_000, 0, 0.95, OutputThroughput(118_000, 1_000_000), 68),
    ComparisonRow(ApiSource.MINIMAX, "MiniMax", null, null, null, 0, null, null, 22),
    ComparisonRow(ApiSource.CODEX, "gpt-5.6-luna", 73, 3_100_000, null, 0, 0.88, OutputThroughput(33_000, 1_000_000), 41)
)

@OptIn(ExperimentalTestApi::class)
class ComparisonScreenTest {

    @Test
    fun `heat map writes every value and says what is not measured`() = runDesktopComposeUiTest {
        setContent {
            ScreenTestTheme {
                Box(Modifier.width(1000.dp).height(620.dp)) {
                    ComparisonContent(
                        state = ComparisonUiState.Success(ROWS, CliSessionRange.LAST_7D, READ_AT),
                        language = AppLanguage.PT,
                        onSelectRange = {},
                        onExportReport = {},
                        onRetry = {}
                    )
                }
            }
        }

        onNodeWithTag(COMPARISON_TABLE_TAG).assertIsDisplayed()
        onNodeWithText("94 tok/s").assertIsDisplayed()
        onNodeWithText("sem tarifa").assertIsDisplayed()
        onNodeWithText("22%").assertIsDisplayed()

        // Evidência visual do mapa de calor para a revisão do plano (#386).
        val output = File("build/issue386-screenshots").also { it.mkdirs() }
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(output, "comparison-heatmap.png"))
    }
}

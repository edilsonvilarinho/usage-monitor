package com.usagemonitor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.usagemonitor.presentation.ui.components.AppChartKey
import com.usagemonitor.presentation.ui.components.AppChartKeyItem
import com.usagemonitor.presentation.ui.components.AppChartKeyStyle
import com.usagemonitor.presentation.ui.components.appChartKeySampleTag
import kotlin.test.Test

/** Legenda de gráfico (#610, A02): uma amostra por estilo do traço e o rótulo em texto. */
@OptIn(ExperimentalTestApi::class)
class AppChartKeyTest {

    /** Cada estilo desenha a amostra com a tag dele, e o rótulo entra como texto, não só no Canvas. */
    @Test
    fun `every style draws its sample and the label is text`() = runDesktopComposeUiTest(width = 600, height = 400) {
        val items = listOf(
            AppChartKeyItem("seu uso até agora", AppChartKeyStyle.SOLID, Color.White),
            AppChartKeyItem("linha segura: 25 %/h", AppChartKeyStyle.DASHED, Color.White),
            AppChartKeyItem("agora (linha pontilhada)", AppChartKeyStyle.DOTTED, Color.White),
            AppChartKeyItem("1 janela anterior", AppChartKeyStyle.FADED, Color.White),
            AppChartKeyItem("acima da linha: esgota antes", AppChartKeyStyle.ZONE, Color.White),
        )
        setContent {
            ScreenTestTheme {
                Box(Modifier.size(500.dp, 300.dp)) {
                    AppChartKey(items)
                }
            }
        }
        onNodeWithText("seu uso até agora").assertIsDisplayed()
        onNodeWithText("linha segura: 25 %/h").assertIsDisplayed()
        onNodeWithText("agora (linha pontilhada)").assertIsDisplayed()
        onNodeWithText("1 janela anterior").assertIsDisplayed()
        onAllNodesWithText("acima da linha: esgota antes").assertCountEquals(1)
        AppChartKeyStyle.entries.forEach { style ->
            onNodeWithTag(appChartKeySampleTag(style)).assertIsDisplayed()
        }
    }
}

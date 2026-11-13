package com.usagemonitor.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.usagemonitor.presentation.ui.components.AppStatusPill
import com.usagemonitor.presentation.ui.components.AppTone
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/** Contorno da pílula (A04, #590): só com `outlined`, desenhado na borda de cima. */
@OptIn(ExperimentalTestApi::class)
class AppStatusPillOutlineTest {

    @Test
    fun `pilula com outlined desenha contorno na borda de cima`() = runDesktopComposeUiTest {
        setContent {
            ScreenTestTheme(isDark = false) {
                Column {
                    AppStatusPill(label = "OK", tone = AppTone.OK, modifier = Modifier.testTag(PLAIN_TAG), outlined = false)
                    AppStatusPill(label = "OK", tone = AppTone.OK, modifier = Modifier.testTag(OUTLINED_TAG), outlined = true)
                }
            }
        }
        waitForIdle()
        val plain = onNodeWithTag(PLAIN_TAG).captureToImage().toPixelMap()
        val outlined = onNodeWithTag(OUTLINED_TAG).captureToImage().toPixelMap()
        val x = plain.width / 2
        val plainTop = plain[x, 0]
        val outlinedTop = outlined[x, 0]
        assertTrue(
            distance(plainTop, outlinedTop) > 0.05f,
            "topo sem contorno: pixel com outlined $outlinedTop igual ao sem outlined $plainTop",
        )
    }

    private fun distance(a: Color, b: Color): Float =
        abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue)

    private companion object {
        const val PLAIN_TAG = "pill-plain"
        const val OUTLINED_TAG = "pill-outlined"
    }
}

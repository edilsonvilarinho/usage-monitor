package com.usagemonitor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.usagemonitor.presentation.ui.components.AppGargantuaRing
import com.usagemonitor.presentation.ui.components.AppRingArc
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.theme.AppMotionPolicy
import com.usagemonitor.presentation.ui.theme.AppTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/** #592 A05: com movimento reduzido o arco de execução do anel continua desenhado (campo para, arco não). */
@OptIn(ExperimentalTestApi::class)
class GargantuaActivityArcTest {
    @Test
    fun `movimento reduzido continua desenhando o arco de execucao`() = runDesktopComposeUiTest {
        var active by mutableStateOf(false)
        setContent {
            AppTheme(isDark = true, motion = AppMotionPolicy.Reduced) {
                Box(Modifier.testTag(ARC_FRAME_TAG).background(Color.Black).padding(8.dp)) {
                    AppGargantuaRing(
                        arcs = listOf(AppRingArc(0.5f, AppTone.WARNING)),
                        description = "Conta · 5h 50%",
                        active = active
                    )
                }
            }
        }
        val idle = onNodeWithTag(ARC_FRAME_TAG).captureToImage().toPixelMap()
        active = true
        waitForIdle()
        val running = onNodeWithTag(ARC_FRAME_TAG).captureToImage().toPixelMap()
        assertTrue(differs(idle, running), "arco de execução sumiu com movimento reduzido")
    }

    private fun differs(first: PixelMap, second: PixelMap): Boolean {
        for (y in 0 until first.height) {
            for (x in 0 until first.width) {
                if (first[x, y] != second[x, y]) return true
            }
        }
        return false
    }
}

private const val ARC_FRAME_TAG = "gargantuaActivityArcFrame"

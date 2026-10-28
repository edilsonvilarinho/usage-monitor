package com.usagemonitor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.presentation.ui.components.AppProviderMark
import com.usagemonitor.presentation.ui.theme.AppMotionPolicy
import com.usagemonitor.presentation.ui.theme.AppTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * AK1 (#517): a marca deixa de ser silhueta chapada. A do OpenCode é um
 * retângulo vazado (`M4 2h16v20H4z`, viewBox 24), então a 48dp a borda de cima
 * fica na linha 4 e a de baixo na 43, no meio da largura.
 */
@OptIn(ExperimentalTestApi::class)
class AppProviderMarkReliefScreenTest {

    @Test
    fun `a borda de cima da marca acende e a de baixo cai na sombra`() = runDesktopComposeUiTest {
        setContent {
            AppTheme(isDark = true, motion = AppMotionPolicy.Static) {
                Box(Modifier.testTag(FRAME).background(Color.Black)) {
                    AppProviderMark(source = ApiSource.OPENCODE, tint = Color(0xFF808080), size = 48.dp)
                }
            }
        }
        val map = onNodeWithTag(FRAME).captureToImage().toPixelMap()
        val scale = map.width / 48f
        val top = map[(24 * scale).toInt(), (4 * scale).toInt()]
        val bottom = map[(24 * scale).toInt(), (43 * scale).toInt()]
        val topLight = (top.red + top.green + top.blue) / 3f
        val bottomLight = (bottom.red + bottom.green + bottom.blue) / 3f
        assertTrue(topLight > bottomLight + 0.3f, "borda de cima $topLight deveria passar a de baixo $bottomLight em 0,3")
    }

    private companion object {
        const val FRAME = "provider-mark-frame"
    }
}

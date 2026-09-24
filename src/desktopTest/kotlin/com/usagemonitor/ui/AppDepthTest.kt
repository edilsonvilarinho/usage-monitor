package com.usagemonitor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.usagemonitor.presentation.ui.components.AppDataSurface
import com.usagemonitor.presentation.ui.theme.AppTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A profundidade é de pintura, e só bitmap a mede — pela mesma razão do teste da
 * barra na issue #83: `boundsInRoot` é idêntico com e sem sombra.
 */
@OptIn(ExperimentalTestApi::class)
class AppDepthTest {

    /** No claro a sombra do painel escurece o fundo logo abaixo dele. */
    @Test
    fun `o painel projeta sombra abaixo dele no tema claro`() {
        val pixels = renderPanel(isDark = false)
        val below = pixels[PANEL_CENTER_X, PANEL_BOTTOM + 3].luminance()
        val farBackground = pixels[PANEL_CENTER_X, SCENE_SIZE - 2].luminance()
        assertTrue(below < farBackground, "Sem sombra: abaixo=$below fundo=$farBackground")
    }

    /**
     * No escuro a sombra quase não aparece, e o volume vem da luz: o topo interno
     * do painel é mais claro que o meio dele.
     */
    @Test
    fun `o painel recebe brilho no topo no tema escuro`() {
        val pixels = renderPanel(isDark = true)
        val nearTop = pixels[PANEL_CENTER_X, PANEL_TOP + 6].luminance()
        val middle = pixels[PANEL_CENTER_X, (PANEL_TOP + PANEL_BOTTOM) / 2].luminance()
        assertTrue(nearTop > middle, "Sem brilho: topo=$nearTop meio=$middle")
    }

    private fun renderPanel(isDark: Boolean): PixelMap {
        lateinit var pixels: PixelMap
        runDesktopComposeUiTest {
            setContent {
                AppTheme(isDark = isDark) {
                    Box(
                        modifier = Modifier
                            .size(SCENE_SIZE.dp)
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        AppDataSurface(
                            modifier = Modifier
                                .padding(start = PANEL_LEFT.dp, top = PANEL_TOP.dp)
                                .width(PANEL_WIDTH.dp)
                                .height((PANEL_BOTTOM - PANEL_TOP).dp)
                        ) {}
                    }
                }
            }
            pixels = onRoot().captureToImage().toPixelMap()
        }
        return pixels
    }

    private companion object {
        const val SCENE_SIZE = 200
        const val PANEL_LEFT = 40
        const val PANEL_WIDTH = 120
        const val PANEL_TOP = 40
        const val PANEL_BOTTOM = 140
        const val PANEL_CENTER_X = PANEL_LEFT + PANEL_WIDTH / 2
    }
}

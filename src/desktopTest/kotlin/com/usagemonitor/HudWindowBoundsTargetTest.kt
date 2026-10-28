package com.usagemonitor

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import java.awt.Dimension
import java.awt.Point
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** O que a janela AWT da HUD recebe como tamanho e posição (#426). Função pura, sem janela. */
class HudWindowBoundsTargetTest {

    @Test
    fun `a floating HUD asks the window for its rounded size`() {
        assertEquals(Dimension(120, 38), hudWindowTargetSize(floating = true, size = DpSize(120.4.dp, 37.6.dp)))
    }

    @Test
    fun `a window that is not floating gets no size`() {
        assertNull(hudWindowTargetSize(floating = false, size = DpSize(120.dp, 38.dp)))
    }

    @Test
    fun `a size with no area is not applied`() {
        assertNull(hudWindowTargetSize(floating = true, size = DpSize(0.dp, 38.dp)))
        assertNull(hudWindowTargetSize(floating = true, size = DpSize(120.dp, 0.4.dp)))
    }

    @Test
    fun `an absolute position is rounded to the nearest pixel`() {
        assertEquals(Point(10, 21), hudWindowTargetLocation(floating = true, position = WindowPosition(10.4.dp, 20.6.dp)))
    }

    @Test
    fun `negative coordinates round half toward positive infinity`() {
        assertEquals(Point(-6, -2), hudWindowTargetLocation(floating = true, position = WindowPosition(-5.6.dp, -2.5.dp)))
    }

    @Test
    fun `a relative position is never asked of the window`() {
        assertNull(hudWindowTargetLocation(floating = true, position = WindowPosition.PlatformDefault))
    }

    @Test
    fun `a window that is not floating gets no location`() {
        assertNull(hudWindowTargetLocation(floating = false, position = WindowPosition(10.dp, 20.dp)))
    }
}

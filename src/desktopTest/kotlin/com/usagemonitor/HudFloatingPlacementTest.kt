package com.usagemonitor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A posição da HUD solta (issue #619, A05). A janela é prendida na área de trabalho e
 * nunca encosta numa borda por conta própria: sem alvo de encaixe, a distância à borda
 * não muda a posição. Sem centro salvo, nasce no centro do notch.
 */
class HudFloatingPlacementTest {

    /** Área de trabalho de um monitor 1920x1080 com origem em (0, 0). */
    private val area = HudPxRect(xPx = 0, yPx = 0, widthPx = 1920, heightPx = 1080)

    private val notch = HudFloatCenter(xPx = 960, yPx = 200)

    @Test
    fun `a center whose window fits inside the work area is kept`() {
        val bounds = hudFloatingBounds(HudFloatCenter(960, 540), notch, 400, 300, area)

        assertEquals(HudPxRect(xPx = 760, yPx = 390, widthPx = 400, heightPx = 300), bounds)
    }

    /** Esquerda: x = 50 - 200 = -150 passa da borda; prende em x = 0. */
    @Test
    fun `the window is clamped at the left edge`() {
        val bounds = hudFloatingBounds(HudFloatCenter(50, 540), notch, 400, 300, area)

        assertEquals(0, bounds.xPx)
        assertEquals(390, bounds.yPx)
    }

    /** Direita: x + largura = 2100 passa de 1920; prende em x = 1920 - 400 = 1520. */
    @Test
    fun `the window is clamped at the right edge`() {
        val bounds = hudFloatingBounds(HudFloatCenter(1900, 540), notch, 400, 300, area)

        assertEquals(1520, bounds.xPx)
        assertEquals(390, bounds.yPx)
    }

    /** Topo: y = 20 - 150 = -130 passa da borda; prende em y = 0. */
    @Test
    fun `the window is clamped at the top edge`() {
        val bounds = hudFloatingBounds(HudFloatCenter(960, 20), notch, 400, 300, area)

        assertEquals(760, bounds.xPx)
        assertEquals(0, bounds.yPx)
    }

    /** Base: y + altura = 1220 passa de 1080; prende em y = 1080 - 300 = 780. */
    @Test
    fun `the window is clamped at the bottom edge`() {
        val bounds = hudFloatingBounds(HudFloatCenter(960, 1070), notch, 400, 300, area)

        assertEquals(760, bounds.xPx)
        assertEquals(780, bounds.yPx)
    }

    /** Área com origem fora de zero (monitor à direita): o prendimento usa a origem da área. */
    @Test
    fun `the clamp follows the origin of a work area that is not at zero`() {
        val second = HudPxRect(xPx = 1920, yPx = 0, widthPx = 1920, heightPx = 1080)

        val bounds = hudFloatingBounds(HudFloatCenter(1950, 540), notch, 400, 300, second)

        assertEquals(1920, bounds.xPx)
    }

    /** A 20 px da borda esquerda a janela não é puxada para x = 0: a posição fica como foi solta. */
    @Test
    fun `a window 20 px from the left edge does not snap to it`() {
        val bounds = hudFloatingBounds(HudFloatCenter(220, 540), notch, 400, 300, area)

        assertEquals(20, bounds.xPx)
    }

    /** A 20 px da borda inferior, o mesmo: nada de encaixe. */
    @Test
    fun `a window 20 px from the bottom edge does not snap to it`() {
        val bounds = hudFloatingBounds(HudFloatCenter(960, 1060 - 150), notch, 400, 300, area)

        assertEquals(760, bounds.yPx)
    }

    /** Sem centro salvo, a janela nasce centrada no notch atual. */
    @Test
    fun `a null center starts at the notch center`() {
        val bounds = hudFloatingBounds(null, notch, 400, 300, area)

        assertEquals(HudPxRect(xPx = 760, yPx = 50, widthPx = 400, heightPx = 300), bounds)
        assertEquals(notch.xPx, bounds.xPx + bounds.widthPx / 2)
    }

    /** Janela maior que a área: fica no canto superior esquerdo da área, nunca passa dele. */
    @Test
    fun `a window larger than the work area sticks to its top left corner`() {
        val bounds = hudFloatingBounds(HudFloatCenter(960, 540), notch, 2000, 1200, area)

        assertEquals(HudPxRect(xPx = 0, yPx = 0, widthPx = 2000, heightPx = 1200), bounds)
    }

    /** Só Windows: a HUD solta é medida no Windows (#340); os outros sistemas não mostram o botão. */
    @Test
    fun `floating mode is supported only on Windows`() {
        assertTrue(hudFloatingSupported(AutoStartManager.Platform.WINDOWS))
        assertFalse(hudFloatingSupported(AutoStartManager.Platform.LINUX))
        assertFalse(hudFloatingSupported(AutoStartManager.Platform.MACOS))
        assertFalse(hudFloatingSupported(AutoStartManager.Platform.OTHER))
    }
}

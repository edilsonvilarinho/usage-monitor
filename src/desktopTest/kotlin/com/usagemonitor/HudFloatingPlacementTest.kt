package com.usagemonitor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A posição da HUD solta (issue #619, A05; A08e2). O que é prendido na área é a âncora do modo:
 * o buraco (minimizado) ou o campo (expandido), pelo raio dela. A janela inteira não é prendida
 * e pode passar da borda. Sem centro salvo, nasce no centro do notch.
 */
class HudFloatingPlacementTest {

    /** Área de trabalho de um monitor 1920x1080 com origem em (0, 0). */
    private val area = HudPxRect(xPx = 0, yPx = 0, widthPx = 1920, heightPx = 1080)

    private val notch = HudFloatCenter(xPx = 960, yPx = 200)

    /** Raio do buraco minimizado (diâmetro 60). */
    private val holeRadius = 30

    /** Raio do campo expandido (diâmetro 200). */
    private val fieldRadius = 100

    @Test
    fun `a center whose anchor fits inside the work area is kept`() {
        val bounds = hudFloatingBounds(HudFloatCenter(960, 540), notch, 400, 300, area, holeRadius)

        assertEquals(HudPxRect(xPx = 760, yPx = 390, widthPx = 400, heightPx = 300), bounds)
    }

    /** Buraco no canto superior esquerdo: o centro vai a (30, 30) e o buraco encosta nas duas bordas. */
    @Test
    fun `a hole at the top left corner touches both edges`() {
        val bounds = hudFloatingBounds(HudFloatCenter(0, 0), notch, 400, 300, area, holeRadius)

        assertEquals(HudPxRect(xPx = -170, yPx = -120, widthPx = 400, heightPx = 300), bounds)
    }

    /** Buraco no canto inferior direito: o centro vai a (1890, 1050); a janela passa das bordas. */
    @Test
    fun `a hole at the bottom right corner touches both edges`() {
        val bounds = hudFloatingBounds(HudFloatCenter(1920, 1080), notch, 400, 300, area, holeRadius)

        assertEquals(HudPxRect(xPx = 1690, yPx = 900, widthPx = 400, heightPx = 300), bounds)
    }

    /** Campo expandido no canto: o centro vai a (100, 100), e o campo inteiro fica dentro da área. */
    @Test
    fun `an expanded field at the corner moves its center inside by its radius`() {
        val bounds = hudFloatingBounds(HudFloatCenter(0, 0), notch, 400, 300, area, fieldRadius)

        assertEquals(HudPxRect(xPx = -100, yPx = -50, widthPx = 400, heightPx = 300), bounds)
    }

    /** Buraco a 20 px da borda esquerda (centro em x = 50, raio 30): o centro não muda. */
    @Test
    fun `a hole 20 px from the left edge does not move`() {
        val bounds = hudFloatingBounds(HudFloatCenter(50, 540), notch, 400, 300, area, holeRadius)

        assertEquals(-150, bounds.xPx)
    }

    /** Sem centro salvo, a janela nasce centrada no notch atual. */
    @Test
    fun `a null center starts at the notch center`() {
        val bounds = hudFloatingBounds(null, notch, 400, 300, area, holeRadius)

        assertEquals(HudPxRect(xPx = 760, yPx = 50, widthPx = 400, heightPx = 300), bounds)
        assertEquals(notch.xPx, bounds.xPx + bounds.widthPx / 2)
    }

    /** Área com origem fora de zero (monitor à direita): o buraco é prendido a partir da origem dela. */
    @Test
    fun `the clamp follows the origin of a work area that is not at zero`() {
        val second = HudPxRect(xPx = 1920, yPx = 0, widthPx = 1920, heightPx = 1080)

        val bounds = hudFloatingBounds(HudFloatCenter(1900, 540), notch, 400, 300, second, holeRadius)

        // Centro prendido em 1920 + 30 = 1950; a janela começa em 1950 - 200 = 1750.
        assertEquals(1750, bounds.xPx)
    }

    /** Âncora maior que a área: sem intervalo válido, o centro vai à origem mais o raio (limite inferior). */
    @Test
    fun `an anchor larger than the work area sticks to its top left corner`() {
        val bounds = hudFloatingBounds(HudFloatCenter(960, 540), notch, 400, 300, area, anchorRadiusPx = 1200)

        assertEquals(HudPxRect(xPx = 1000, yPx = 1050, widthPx = 400, heightPx = 300), bounds)
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

package com.usagemonitor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A posição da HUD solta (issue #619, A05; A08e2; #625). A âncora (o buraco ou o campo, pelo raio)
 * é presa na área útil, e a janela também é presa na área útil pela origem: com a janela perto da
 * borda ela fica deslocada do centro pedido e o buraco anda dentro dela. Sem centro salvo, nasce no
 * centro do notch.
 */
class HudFloatingPlacementTest {

    /** Área de trabalho de um monitor 1920x1080 com origem em (0, 0). */
    private val area = HudPxRect(xPx = 0, yPx = 0, widthPx = 1920, heightPx = 1080)

    private val notch = HudFloatCenter(xPx = 960, yPx = 200)

    /** Raio do buraco minimizado (diâmetro 60). */
    private val holeRadius = 30

    /** Raio do campo expandido (diâmetro 200). */
    private val fieldRadius = 100

    /** Centro do buraco dentro da janela, em px relativos à origem da janela. */
    private fun holeOf(placement: HudFloatingPlacementPx): HudFloatCenter =
        HudFloatCenter(placement.holeCenterXPx, placement.holeCenterYPx)

    @Test
    fun `a center whose anchor fits inside the work area is kept`() {
        val placement = hudFloatingBounds(HudFloatCenter(960, 540), notch, 400, 300, area, holeRadius)

        assertEquals(HudPxRect(xPx = 760, yPx = 390, widthPx = 400, heightPx = 300), placement.window)
        assertEquals(HudFloatCenter(200, 150), holeOf(placement))
    }

    /** Buraco no canto superior esquerdo: o centro vai a (30, 30) e a janela encosta na origem da área. */
    @Test
    fun `a hole at the top left corner touches both edges`() {
        val placement = hudFloatingBounds(HudFloatCenter(0, 0), notch, 400, 300, area, holeRadius)

        assertEquals(HudPxRect(xPx = 0, yPx = 0, widthPx = 400, heightPx = 300), placement.window)
        assertEquals(HudFloatCenter(30, 30), holeOf(placement))
    }

    /** Buraco no canto inferior direito: o centro vai a (1890, 1050); a janela encosta na borda final da área. */
    @Test
    fun `a hole at the bottom right corner touches both edges`() {
        val placement = hudFloatingBounds(HudFloatCenter(1920, 1080), notch, 400, 300, area, holeRadius)

        assertEquals(HudPxRect(xPx = 1520, yPx = 780, widthPx = 400, heightPx = 300), placement.window)
        assertEquals(HudFloatCenter(370, 270), holeOf(placement))
    }

    /** Campo expandido no canto: o centro vai a (100, 100) e a janela encosta na origem da área. */
    @Test
    fun `an expanded field at the corner moves its center inside by its radius`() {
        val placement = hudFloatingBounds(HudFloatCenter(0, 0), notch, 400, 300, area, fieldRadius)

        assertEquals(HudPxRect(xPx = 0, yPx = 0, widthPx = 400, heightPx = 300), placement.window)
        assertEquals(HudFloatCenter(100, 100), holeOf(placement))
    }

    /** Buraco a 20 px da borda esquerda (centro em x = 50, raio 30): a âncora não se move; a janela encosta na borda. */
    @Test
    fun `a hole 20 px from the left edge does not move`() {
        val placement = hudFloatingBounds(HudFloatCenter(50, 540), notch, 400, 300, area, holeRadius)

        assertEquals(HudPxRect(xPx = 0, yPx = 390, widthPx = 400, heightPx = 300), placement.window)
        assertEquals(50, holeOf(placement).xPx)
    }

    /** Sem centro salvo, a janela nasce centrada no notch atual. */
    @Test
    fun `a null center starts at the notch center`() {
        val placement = hudFloatingBounds(null, notch, 400, 300, area, holeRadius)

        assertEquals(HudPxRect(xPx = 760, yPx = 50, widthPx = 400, heightPx = 300), placement.window)
        assertEquals(notch.xPx, placement.window.xPx + placement.holeCenterXPx)
    }

    /** Área com origem fora de zero (monitor à direita): a janela é presa a partir da origem dela. */
    @Test
    fun `the clamp follows the origin of a work area that is not at zero`() {
        val second = HudPxRect(xPx = 1920, yPx = 0, widthPx = 1920, heightPx = 1080)

        val placement = hudFloatingBounds(HudFloatCenter(1900, 540), notch, 400, 300, second, holeRadius)

        // Âncora presa em 1920 + 30 = 1950; a janela ideal começaria em 1750, abaixo da origem 1920: presa em 1920.
        assertEquals(HudPxRect(xPx = 1920, yPx = 390, widthPx = 400, heightPx = 300), placement.window)
        assertEquals(30, placement.holeCenterXPx)
    }

    /** Âncora maior que a área: sem intervalo válido, o centro vai à origem mais o raio; a janela é presa na área. */
    @Test
    fun `an anchor larger than the work area sticks to its top left corner`() {
        val placement = hudFloatingBounds(HudFloatCenter(960, 540), notch, 400, 300, area, anchorRadiusPx = 1200)

        assertEquals(HudPxRect(xPx = 1000, yPx = 780, widthPx = 400, heightPx = 300), placement.window)
        assertEquals(HudFloatCenter(200, 420), holeOf(placement))
    }

    /**
     * Caso do log (#625): área com origem (0, 30), janela 778x546, raio 40, centro pedido no canto
     * superior direito. A âncora fica em (1880, 70); a janela é presa à direita e ao topo da área útil.
     */
    @Test
    fun `a hole pressed to the top right of a work area that starts at y 30 keeps the window inside it`() {
        val workArea = HudPxRect(xPx = 0, yPx = 30, widthPx = 1920, heightPx = 1050)

        val placement = hudFloatingBounds(HudFloatCenter(1880, 10), notch, 778, 546, workArea, 40)

        assertEquals(HudPxRect(xPx = 1142, yPx = 30, widthPx = 778, heightPx = 546), placement.window)
        assertEquals(HudFloatCenter(738, 40), holeOf(placement))
    }

    /** Canto inferior esquerdo da mesma área: a janela encosta na origem em x e na base da área em y. */
    @Test
    fun `a hole pressed to the bottom left keeps the window inside a work area that starts at y 30`() {
        val workArea = HudPxRect(xPx = 0, yPx = 30, widthPx = 1920, heightPx = 1050)

        val placement = hudFloatingBounds(HudFloatCenter(0, 1080), notch, 778, 546, workArea, 40)

        assertEquals(HudPxRect(xPx = 0, yPx = 534, widthPx = 778, heightPx = 546), placement.window)
        assertEquals(HudFloatCenter(40, 506), holeOf(placement))
    }

    /** Janela maior que a área: fica na origem da área (x e y), não na origem da tela. */
    @Test
    fun `a window larger than the work area stays at the work area origin`() {
        val workArea = HudPxRect(xPx = 100, yPx = 50, widthPx = 800, heightPx = 600)

        val placement = hudFloatingBounds(HudFloatCenter(400, 300), notch, 1000, 700, workArea, holeRadius)

        assertEquals(HudPxRect(xPx = 100, yPx = 50, widthPx = 1000, heightPx = 700), placement.window)
        assertEquals(HudFloatCenter(300, 250), holeOf(placement))
    }

    /** Windows (#340, medido) e Linux (#623, liberado sem medição); macOS e outros sistemas não mostram o botão. */
    @Test
    fun `floating mode is supported on Windows and Linux only`() {
        assertTrue(hudFloatingSupported(AutoStartManager.Platform.WINDOWS))
        assertTrue(hudFloatingSupported(AutoStartManager.Platform.LINUX))
        assertFalse(hudFloatingSupported(AutoStartManager.Platform.MACOS))
        assertFalse(hudFloatingSupported(AutoStartManager.Platform.OTHER))
    }
}

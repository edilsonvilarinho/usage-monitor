package com.usagemonitor

import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A decisão de mexer ou não em `window.shape`. "Não mexer" e "mexer passando
 * `null`" são coisas diferentes, e confundi-las já custou duas regressões: a
 * #340 (o `null` não limpa no X11) e a #426 (limpar o que nunca foi aplicado
 * recortava a pintura).
 */
class HudHitRegionTargetTest {

    private val notch = Rectangle(300, 100, 130, 330)

    @Test
    fun `a requested region is applied as it is`() {
        val target = hudHitRegionTarget(notch, hasApplied = false, clearWithFullBounds = false, 434, 541)

        assertTrue(target.touch)
        assertEquals(notch, target.shape)
    }

    /**
     * O caso da regressão da #426: Linux com alfa, onde o recorte nunca valeu.
     * Limpar aqui punha um retângulo do tamanho da janela na primeira
     * composição — antes de as contas chegarem e a janela crescer —, ele ficava
     * congelado naquele tamanho e cortava a pintura. A barra aparecia com o anel
     * de cima e nada embaixo.
     */
    @Test
    fun `a clip that was never applied is not cleared`() {
        val target = hudHitRegionTarget(null, hasApplied = false, clearWithFullBounds = true, 434, 541)

        assertFalse(target.touch)
        assertNull(target.shape)
    }

    /** Windows: o `null` limpa, e é o caminho medido. */
    @Test
    fun `an applied clip is cleared with null off X11`() {
        val target = hudHitRegionTarget(null, hasApplied = true, clearWithFullBounds = false, 434, 541)

        assertTrue(target.touch)
        assertNull(target.shape)
    }

    /** X11: o `null` não limpa (#340), então a limpeza é o retângulo da janela. */
    @Test
    fun `an applied clip is cleared with the full window on X11`() {
        val target = hudHitRegionTarget(null, hasApplied = true, clearWithFullBounds = true, 434, 541)

        assertTrue(target.touch)
        assertEquals(Rectangle(0, 0, 434, 541), target.shape)
    }

    /**
     * O retângulo de limpeza acompanha o tamanho: a janela da HUD cresce quando
     * as contas chegam, e um retângulo congelado no tamanho antigo cortaria o
     * que nasceu depois.
     */
    @Test
    fun `the clearing rectangle follows the window size`() {
        val small = hudHitRegionTarget(null, hasApplied = true, clearWithFullBounds = true, 200, 200)
        val grown = hudHitRegionTarget(null, hasApplied = true, clearWithFullBounds = true, 434, 541)

        assertEquals(Rectangle(0, 0, 200, 200), small.shape)
        assertEquals(Rectangle(0, 0, 434, 541), grown.shape)
    }

    /** Janela sem tamanho medido cai no `null`: melhor não recortar do que recortar em zero. */
    @Test
    fun `a window with no measured size falls back to null`() {
        assertNull(hudHitRegionTarget(null, hasApplied = true, clearWithFullBounds = true, 0, 541).shape)
        assertNull(hudHitRegionTarget(null, hasApplied = true, clearWithFullBounds = true, 434, 0).shape)
        assertTrue(hudHitRegionTarget(null, hasApplied = true, clearWithFullBounds = true, 0, 0).touch)
    }

    /**
     * O recorte em pixels da AWT nunca fica menor que o notch: a borda de cima e a
     * da esquerda descem (`floor`), a de baixo e a da direita sobem (`ceil`). Com
     * 125%, a esquerda 10 vira 12,5 e desce a 12; a direita 30 vira 37,5 e sobe a
     * 38: o pixel cortado ao meio entra.
     */
    @Test
    fun `the AWT rectangle covers every pixel the notch touches`() {
        val notch = DpRect(left = 10.dp, top = 10.dp, right = 30.dp, bottom = 20.dp)

        assertEquals(Rectangle(10, 10, 20, 10), notch.toAwtRectangle(1f))
        assertEquals(Rectangle(12, 12, 26, 13), notch.toAwtRectangle(1.25f))
    }
}

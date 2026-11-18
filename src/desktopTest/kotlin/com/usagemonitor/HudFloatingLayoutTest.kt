package com.usagemonitor

import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Geometria pura da janela flutuante da HUD (issue #619, A08a). Sem janela AWT: afirma
 * as posições que a janela vai ocupar. Medidas de referência: campo máximo 300, balão
 * 264x200, coluna de botões 32, vão 6 → janela de 878x300.
 */
class HudFloatingLayoutTest {

    private val maxField = 300.dp
    private val balloon = DpSize(264.dp, 200.dp)
    private val handle = 32.dp
    private val gap = 6.dp

    /** 264 + 6 + 300 + 6 + 32 + 6 + 264. */
    private val windowSize = DpSize(878.dp, 300.dp)

    @Test
    fun `window width is balloon, gap, field, gap, button column, gap and balloon`() {
        val size = hudFloatingWindowSize(maxField, balloon, handle, gap)

        assertEquals(DpSize(878.dp, 300.dp), size)
    }

    /** Balão mais alto que o campo máximo: a altura da janela é a do balão. */
    @Test
    fun `window height is the taller of the field and the balloon`() {
        val size = hudFloatingWindowSize(maxField, DpSize(264.dp, 400.dp), handle, gap)

        assertEquals(DpSize(878.dp, 400.dp), size)
    }

    /**
     * O tamanho é um só para minimizado, expandido e com balão aberto: toda parte de
     * todo estado tem de caber dentro dele.
     */
    @Test
    fun `every state fits inside the same fixed window size`() {
        for (expanded in listOf(false, true)) {
            for (balloonOpen in listOf(false, true)) {
                for (side in listOf(HudEdge.LEFT, HudEdge.RIGHT)) {
                    val parts = layout(expanded = expanded, balloonOpen = balloonOpen, side = side)
                    val label = "expanded=$expanded balloonOpen=$balloonOpen side=$side"

                    assertInside(parts.hitRect, windowSize, "hitRect $label")
                    parts.field?.let { assertInside(it, windowSize, "field $label") }
                    parts.buttons?.let { assertInside(it, windowSize, "buttons $label") }
                    parts.balloon?.let { assertInside(it, windowSize, "balloon $label") }
                }
            }
        }
    }

    /** Minimizado: só o buraco, centrado na janela (439, 150); campo, coluna e balão somem. */
    @Test
    fun `minimized keeps only the hole and the hit area is the hole`() {
        val parts = layout(expanded = false, balloonOpen = true, side = HudEdge.RIGHT)

        assertEquals(DpRect(399.dp, 110.dp, 479.dp, 190.dp), parts.hole)
        assertEquals(parts.hole, parts.hitRect)
        assertNull(parts.field)
        assertNull(parts.buttons)
        assertNull(parts.balloon)
    }

    /** Campo de 240 centrado: x 319..559. Coluna à direita do campo: x 565..597, centrada. */
    @Test
    fun `expanded without balloon places the field centered and the button column to its right`() {
        val parts = layout(expanded = true, balloonOpen = false, side = HudEdge.RIGHT)

        assertEquals(DpRect(319.dp, 30.dp, 559.dp, 270.dp), parts.field)
        assertEquals(DpRect(565.dp, 115.dp, 597.dp, 185.dp), parts.buttons)
        assertNull(parts.balloon)
    }

    /** Sem balão, o recorte cobre buraco, campo e coluna; a área do balão fica de fora. */
    @Test
    fun `expanded without balloon hit area covers field and buttons only`() {
        val parts = layout(expanded = true, balloonOpen = false, side = HudEdge.RIGHT)

        assertEquals(DpRect(319.dp, 30.dp, 597.dp, 270.dp), parts.hitRect)
    }

    /** Balão à esquerda da janela (side RIGHT): x 0..264, centrado na vertical. */
    @Test
    fun `expanded with balloon on the left covers balloon, field and buttons`() {
        val parts = layout(expanded = true, balloonOpen = true, side = HudEdge.RIGHT)

        assertEquals(DpRect(0.dp, 50.dp, 264.dp, 250.dp), parts.balloon)
        assertEquals(DpRect(0.dp, 30.dp, 597.dp, 270.dp), parts.hitRect)
    }

    /** Balão à direita da janela (side LEFT): x 614..878. */
    @Test
    fun `expanded with balloon on the right sits in the right slot`() {
        val parts = layout(expanded = true, balloonOpen = true, side = HudEdge.LEFT)

        assertEquals(DpRect(614.dp, 50.dp, 878.dp, 250.dp), parts.balloon)
        assertEquals(DpRect(319.dp, 30.dp, 878.dp, 270.dp), parts.hitRect)
    }

    /** Centro na metade esquerda da área (800 de 1920): mais espaço à direita, balão para a direita. */
    @Test
    fun `center on the left half opens the balloon to the right`() {
        assertEquals(HudEdge.LEFT, hudFloatingBalloonSide(800, PRIMARY_AREA))
    }

    /** Centro na metade direita (1500 de 1920): balão para a esquerda, RIGHT. */
    @Test
    fun `center on the right half opens the balloon to the left`() {
        assertEquals(HudEdge.RIGHT, hudFloatingBalloonSide(1500, PRIMARY_AREA))
    }

    /** Empate exato no meio: sem lado com mais espaço, o balão abre para a direita (LEFT). */
    @Test
    fun `center exactly on the middle opens the balloon to the right`() {
        assertEquals(HudEdge.LEFT, hudFloatingBalloonSide(960, PRIMARY_AREA))
    }

    /** Segundo monitor com origem em x = 1920: a metade é 2880, não 960. */
    @Test
    fun `balloon side uses the work area origin on a second monitor`() {
        val secondArea = HudPxRect(xPx = 1920, yPx = 0, widthPx = 1920, heightPx = 1080)

        assertEquals(HudEdge.LEFT, hudFloatingBalloonSide(2000, secondArea))
        assertEquals(HudEdge.RIGHT, hudFloatingBalloonSide(3700, secondArea))
    }

    private fun layout(expanded: Boolean, balloonOpen: Boolean, side: HudEdge): HudFloatingParts =
        hudFloatingParts(
            windowSize = windowSize,
            expanded = expanded,
            fieldDiameter = 240.dp,
            holeDiameter = 80.dp,
            balloonOpen = balloonOpen,
            balloonSide = side,
            balloonWidth = balloon.width,
            balloonHeight = balloon.height,
            handleSize = handle,
            gap = gap
        )

    /** Parte vazia não conta como visível: retângulo de área zero falha. */
    private fun assertInside(rect: DpRect, size: DpSize, label: String) {
        assertTrue(
            rect.right - rect.left > 0.dp && rect.bottom - rect.top > 0.dp,
            "$label sem área: $rect"
        )
        assertTrue(
            rect.left >= 0.dp && rect.top >= 0.dp && rect.right <= size.width && rect.bottom <= size.height,
            "$label fora da janela $size: $rect"
        )
    }

    private companion object {
        /** Monitor 1920x1080 com origem em (0, 0). */
        val PRIMARY_AREA = HudPxRect(xPx = 0, yPx = 0, widthPx = 1920, heightPx = 1080)
    }
}

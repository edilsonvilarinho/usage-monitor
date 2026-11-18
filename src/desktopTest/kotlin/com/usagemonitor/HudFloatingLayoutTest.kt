package com.usagemonitor

import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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

    /** Janela do modo coluna: 80 + 4x6 + 2x60 + 2x264 de largura; altura = dobro da coluna mais as alças dos dois extremos (792). */
    @Test
    fun `column window is wide enough for both sides and tall enough for the slide`() {
        val size = hudFloatingColumnWindowSize(COLUMN, balloon, 80.dp, 32.dp, gap)

        assertEquals(COLUMN_WINDOW, size)
    }

    /** Coluna curta com balão alto: a altura da janela é a do balão, não o dobro da coluna. */
    @Test
    fun `column window height is the balloon when it is taller than twice the column`() {
        val size = hudFloatingColumnWindowSize(DpSize(60.dp, 100.dp), DpSize(264.dp, 400.dp), 80.dp, 32.dp, gap)

        assertEquals(DpSize(752.dp, 400.dp), size)
    }

    /**
     * Coluna curta (60x100) com balão de 200: a janela ficou com 200 de altura e, com a coluna
     * encostada na área (buraco em y 46, topo; em y 1040, base), as alças saíam pela borda
     * (y -22 no run). Com a janela dimensionada pelas alças, cabem nos dois extremos.
     */
    @Test
    fun `buttons stay inside the window when the column is slid to the top or to the bottom of the area`() {
        val window = hudFloatingColumnWindowSize(SHORT_COLUMN, balloon, 80.dp, 32.dp, gap)

        for (holeY in listOf(46, 1040)) {
            val parts = columnLayout(holeCenterYPx = holeY, side = HudEdge.LEFT, window = window, columnSize = SHORT_COLUMN)
            val buttons = assertNotNull(parts.buttons, "alças ausentes y=$holeY")

            assertInside(buttons, window, "buttons y=$holeY")
        }
    }

    /** Minimizado: só o buraco no centro da janela (336..416, 280..360); coluna e balão somem. */
    @Test
    fun `column mode minimized keeps only the hole and the hit area is the hole`() {
        val parts = columnLayout(expanded = false, holeCenterYPx = 540, side = HudEdge.LEFT, balloonOpen = true)

        assertEquals(DpRect(336.dp, 356.dp, 416.dp, 436.dp), parts.hole)
        assertEquals(parts.hole, parts.hitRect)
        assertNull(parts.column)
        assertNull(parts.balloon)
    }

    /** Buraco no meio da área (y 540): coluna centrada no Y do buraco, à direita (lado LEFT). */
    @Test
    fun `column is centered on the hole when the hole is in the middle of the work area`() {
        val parts = columnLayout(holeCenterYPx = 540, side = HudEdge.LEFT)

        assertEquals(DpRect(422.dp, 236.dp, 482.dp, 556.dp), parts.column)
        assertEquals(DpRect(336.dp, 356.dp, 416.dp, 436.dp), parts.hole)
    }

    /**
     * Canto superior direito (centro x 1800, y 40): o lado com mais espaço é o esquerdo
     * (RIGHT). A coluna fica à esquerda do buraco e desce até a área (top 280, não 160).
     * O buraco não se move.
     */
    @Test
    fun `column goes left of the hole and slides down at the top right corner`() {
        assertEquals(HudEdge.RIGHT, hudFloatingBalloonSide(1800, PRIMARY_AREA))

        val parts = columnLayout(holeCenterYPx = 40, side = HudEdge.RIGHT)

        assertEquals(DpRect(270.dp, 356.dp, 330.dp, 676.dp), parts.column)
        assertEquals(DpRect(336.dp, 356.dp, 416.dp, 436.dp), parts.hole)
    }

    /**
     * Canto inferior esquerdo (centro x 120, y 1040): o lado com mais espaço é o direito
     * (LEFT). A coluna fica à direita do buraco e sobe até a área (top 40, não 160).
     */
    @Test
    fun `column goes right of the hole and slides up at the bottom left corner`() {
        assertEquals(HudEdge.LEFT, hudFloatingBalloonSide(120, PRIMARY_AREA))

        val parts = columnLayout(holeCenterYPx = 1040, side = HudEdge.LEFT)

        assertEquals(DpRect(422.dp, 116.dp, 482.dp, 436.dp), parts.column)
    }

    /** Buraco na borda superior da área: a coluna encosta na metade de baixo da janela (top 396). */
    @Test
    fun `hole on the top edge of the work area pushes the column to the lower half of the window`() {
        val parts = columnLayout(holeCenterYPx = 0, side = HudEdge.LEFT)

        assertEquals(396.dp, parts.column?.top)
        assertEquals(DpRect(422.dp, 396.dp, 482.dp, 716.dp), parts.column)
    }

    /** Qualquer Y do buraco, dos dois lados: a coluna nunca sai da janela. */
    @Test
    fun `column stays inside the window for any hole height on both sides`() {
        for (y in listOf(-1000, -300, 0, 40, 540, 1040, 1080, 2000)) {
            for (side in listOf(HudEdge.LEFT, HudEdge.RIGHT)) {
                val column = columnLayout(holeCenterYPx = y, side = side).column
                val label = "y=$y side=$side"

                assertNotNull(column, "coluna ausente $label")
                assertInside(column, COLUMN_WINDOW, "column $label")
            }
        }
    }

    /** Engrenagem e encaixar logo abaixo da coluna, centrados no X dela, quando cabem (A11c-2, passo 1). */
    @Test
    fun `buttons sit right below the column centered on its x when they fit`() {
        val parts = columnLayout(holeCenterYPx = 540, side = HudEdge.LEFT)

        assertEquals(DpRect(436.dp, 562.dp, 468.dp, 632.dp), parts.buttons)
    }

    /** Sem espaço embaixo dentro da janela: a coluna de botões sobe para logo acima da coluna. */
    @Test
    fun `buttons move above the column when there is no room below`() {
        val parts = columnLayout(holeCenterYPx = -100, side = HudEdge.RIGHT)

        assertEquals(DpRect(284.dp, 396.dp, 316.dp, 466.dp), parts.buttons)
    }

    /** Minimizado: nem coluna nem botões. */
    @Test
    fun `buttons are absent while minimized`() {
        val parts = columnLayout(expanded = false, holeCenterYPx = 540, side = HudEdge.LEFT)

        assertNull(parts.buttons)
    }

    /** Expandido sem balão: recorte = buraco ∪ coluna ∪ botões (336..482 x 160..556). */
    @Test
    fun `expanded column without balloon hit area is the hole united with the column and buttons`() {
        val parts = columnLayout(holeCenterYPx = 540, side = HudEdge.LEFT)

        assertEquals(DpRect(336.dp, 236.dp, 482.dp, 632.dp), parts.hitRect)
        assertNull(parts.balloon)
    }

    /** Balão além da coluna (lado LEFT): x 488..752, centrado na vertical da janela (220..420). */
    @Test
    fun `balloon sits beyond the column and the hit area includes it`() {
        val parts = columnLayout(holeCenterYPx = 540, balloonOpen = true, side = HudEdge.LEFT)

        assertEquals(DpRect(488.dp, 296.dp, 752.dp, 496.dp), parts.balloon)
        assertEquals(DpRect(336.dp, 236.dp, 752.dp, 632.dp), parts.hitRect)
    }

    /** Todo estado, em todo Y de teste, cabe na janela fixa. */
    @Test
    fun `every column state fits inside the same fixed window size`() {
        for (y in listOf(40, 540, 1040)) {
            for (expanded in listOf(false, true)) {
                for (balloonOpen in listOf(false, true)) {
                    for (side in listOf(HudEdge.LEFT, HudEdge.RIGHT)) {
                        val parts = columnLayout(expanded, y, side, balloonOpen)
                        val label = "y=$y expanded=$expanded balloonOpen=$balloonOpen side=$side"

                        assertInside(parts.hitRect, COLUMN_WINDOW, "hitRect $label")
                        parts.column?.let { assertInside(it, COLUMN_WINDOW, "column $label") }
                        parts.balloon?.let { assertInside(it, COLUMN_WINDOW, "balloon $label") }
                    }
                }
            }
        }
    }

    /** Balão alinhado ao anel em foco e preso dentro da janela (A11c, adendo do balão). */
    @Test
    fun `balao fica centrado no anel em foco e preso dentro da janela`() {
        val window = 200.dp
        val balloon = 100.dp
        assertEquals(50.dp, hudFloatingBalloonTop(ringCenterY = 100.dp, balloonHeight = balloon, windowHeight = window))
        assertEquals(0.dp, hudFloatingBalloonTop(ringCenterY = 10.dp, balloonHeight = balloon, windowHeight = window))
        assertEquals(100.dp, hudFloatingBalloonTop(ringCenterY = 190.dp, balloonHeight = balloon, windowHeight = window))
    }

    private fun columnLayout(
        expanded: Boolean = true,
        holeCenterYPx: Int,
        side: HudEdge,
        balloonOpen: Boolean = false,
        window: DpSize = COLUMN_WINDOW,
        columnSize: DpSize = COLUMN
    ): HudFloatingColumnParts =
        hudFloatingColumnParts(
            windowSize = window,
            expanded = expanded,
            columnSize = columnSize,
            holeDiameter = 80.dp,
            holeCenterYPx = holeCenterYPx,
            workArea = PRIMARY_AREA,
            balloonOpen = balloonOpen,
            balloonSide = side,
            balloonSize = balloon,
            handleSize = handle,
            gap = gap
        )

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

        /** Coluna de anéis de referência: 60 de largura, 320 de altura (8 contas, por exemplo). */
        val COLUMN = DpSize(60.dp, 320.dp)

        /** Coluna curta de referência: 60 de largura, 100 de altura (2 contas). */
        val SHORT_COLUMN = DpSize(60.dp, 100.dp)

        /** Janela do modo coluna: 752 (buraco 80, dois vãos de 6 por lado, coluna 60, balão 264) x 792. */
        val COLUMN_WINDOW = DpSize(752.dp, 792.dp)
    }
}

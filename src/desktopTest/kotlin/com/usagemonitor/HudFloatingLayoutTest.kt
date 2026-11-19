package com.usagemonitor

import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Geometria pura da janela flutuante da HUD, modo coluna (issue #619). Sem janela AWT:
 * afirma as posições que a janela vai ocupar.
 */
class HudFloatingLayoutTest {

    private val balloon = DpSize(264.dp, 200.dp)
    private val handle = 32.dp
    private val gap = 6.dp

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

    /**
     * Coluna curta com balão alto: a altura da janela é o dobro do balão (A11l), para que um balão
     * inteiro caiba para cima e para baixo do buraco, que fica no centro.
     */
    @Test
    fun `column window height is twice the balloon when the balloon is taller than the column`() {
        val size = hudFloatingColumnWindowSize(DpSize(60.dp, 100.dp), DpSize(264.dp, 400.dp), 80.dp, 32.dp, gap)

        assertEquals(DpSize(752.dp, 800.dp), size)
    }

    /**
     * A11l: só o buraco (coluna sem anéis) com balão de 600 dp. A janela tem de caber um balão inteiro
     * acima e abaixo do centro, onde o buraco fica; antes a altura era a do balão, e o balão saía dela.
     */
    @Test
    fun `window holds a whole tall balloon above and below the hole when only the hole is left`() {
        val window = hudFloatingColumnWindowSize(DpSize(60.dp, 0.dp), DpSize(264.dp, 600.dp), 80.dp, 32.dp, gap)

        assertTrue(window.height >= 1200.dp, "altura da janela ${window.height} menor que 2 x balão de 600 dp")
    }

    /**
     * A11l: buraco 10 dp abaixo do topo da área útil e balão de 600 dp preso ao topo da área (A11k).
     * A base do balão tem de cair dentro da janela; senão a fileira de ações sai cortada.
     */
    @Test
    fun `balloon base stays inside the window when the hole is 10 dp below the top of the work area`() {
        val tallBalloon = DpSize(264.dp, 600.dp)
        val window = hudFloatingColumnWindowSize(DpSize(60.dp, 0.dp), tallBalloon, 80.dp, 32.dp, gap)
        val holeCenterY = window.height / 2f
        val areaTop = holeCenterY - 10.dp
        val top = hudFloatingBalloonTop(
            ringCenterY = holeCenterY,
            balloonHeight = tallBalloon.height,
            windowHeight = window.height,
            areaTop = areaTop,
            areaBottom = window.height
        )
        val bottom = top + tallBalloon.height

        assertTrue(bottom <= window.height, "base do balão $bottom fora da janela ${window.height} (topo $top, área a partir de $areaTop)")
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

    /** A03 (#623): com a pilha embaixo da coluna, o centro da engrenagem é o meio da alça de cima (562 + 16). */
    @Test
    fun `gear center is the middle of the first handle when the stack sits below the column`() {
        val parts = columnLayout(holeCenterYPx = 540, side = HudEdge.LEFT)
        val buttons = assertNotNull(parts.buttons, "alças ausentes")

        assertEquals(578.dp, hudFloatingGearCenterY(buttons, handle))
    }

    /** A03 (#623): com a pilha em cima da coluna, o centro da engrenagem é o meio da alça de cima (396 + 16). */
    @Test
    fun `gear center is the middle of the first handle when the stack sits above the column`() {
        val parts = columnLayout(holeCenterYPx = -100, side = HudEdge.RIGHT)
        val buttons = assertNotNull(parts.buttons, "alças ausentes")

        assertEquals(412.dp, hudFloatingGearCenterY(buttons, handle))
    }

    /** A03 (#623): o balão da engrenagem contém o centro dela, com a pilha embaixo da coluna. */
    @Test
    fun `balloon top keeps the gear center inside the balloon when the stack sits below the column`() {
        val parts = columnLayout(holeCenterYPx = 540, side = HudEdge.LEFT)
        val gearY = hudFloatingGearCenterY(assertNotNull(parts.buttons, "alças ausentes"), handle)
        val top = hudFloatingBalloonTop(gearY, balloon.height, COLUMN_WINDOW.height, 0.dp, COLUMN_WINDOW.height)

        assertTrue(gearY >= top && gearY <= top + balloon.height, "centro da engrenagem $gearY fora do balão $top..${top + balloon.height}")
    }

    /** A03 (#623): o balão da engrenagem contém o centro dela, com a pilha em cima da coluna. */
    @Test
    fun `balloon top keeps the gear center inside the balloon when the stack sits above the column`() {
        val parts = columnLayout(holeCenterYPx = -100, side = HudEdge.RIGHT)
        val gearY = hudFloatingGearCenterY(assertNotNull(parts.buttons, "alças ausentes"), handle)
        val top = hudFloatingBalloonTop(gearY, balloon.height, COLUMN_WINDOW.height, 0.dp, COLUMN_WINDOW.height)

        assertTrue(gearY >= top && gearY <= top + balloon.height, "centro da engrenagem $gearY fora do balão $top..${top + balloon.height}")
    }

    /**
     * Buraco a 20 px da base da área (y 1060): a coluna desce até a base e a pilha de alças fica
     * acima dela, tudo dentro da área útil (A11h). Embaixo, a engrenagem sairia pela barra de tarefas.
     */
    @Test
    fun `buttons stay inside the work area above the column when the hole is 20 px above its bottom`() {
        val parts = columnLayout(holeCenterYPx = 1060, side = HudEdge.LEFT)
        val column = assertNotNull(parts.column, "coluna ausente")
        val buttons = assertNotNull(parts.buttons, "alças ausentes")

        assertInsideArea(column, holeCenterYPx = 1060, label = "column y=1060")
        assertInsideArea(buttons, holeCenterYPx = 1060, label = "buttons y=1060")
        assertTrue(buttons.bottom <= column.top, "alças fora de cima da coluna: buttons=$buttons column=$column")
    }

    /** Buraco a 20 px do topo da área (y 20): alças abaixo da coluna, tudo dentro da área útil (A11h). */
    @Test
    fun `buttons stay inside the work area below the column when the hole is 20 px below its top`() {
        val parts = columnLayout(holeCenterYPx = 20, side = HudEdge.LEFT)
        val column = assertNotNull(parts.column, "coluna ausente")
        val buttons = assertNotNull(parts.buttons, "alças ausentes")

        assertInsideArea(column, holeCenterYPx = 20, label = "column y=20")
        assertInsideArea(buttons, holeCenterYPx = 20, label = "buttons y=20")
        assertTrue(buttons.top >= column.bottom, "alças fora de baixo da coluna: buttons=$buttons column=$column")
    }

    /** Buraco no meio da área (y 540): alças abaixo da coluna, tudo dentro da área útil (A11h). */
    @Test
    fun `buttons stay inside the work area below the column when the hole is in the middle`() {
        val parts = columnLayout(holeCenterYPx = 540, side = HudEdge.LEFT)
        val column = assertNotNull(parts.column, "coluna ausente")
        val buttons = assertNotNull(parts.buttons, "alças ausentes")

        assertInsideArea(column, holeCenterYPx = 540, label = "column y=540")
        assertInsideArea(buttons, holeCenterYPx = 540, label = "buttons y=540")
        assertTrue(buttons.top >= column.bottom, "alças fora de baixo da coluna: buttons=$buttons column=$column")
    }

    /**
     * Área útil de 400 px com o buraco em y 196 (área em 200..600 na janela): a coluna sozinha cabe,
     * mas com a pilha embaixo ela passa da base. O deslize considera o bloco inteiro (A11h).
     */
    @Test
    fun `the whole column and button stack slides into a short work area`() {
        val area = HudPxRect(xPx = 0, yPx = 0, widthPx = 1920, heightPx = 400)
        val parts = columnLayout(holeCenterYPx = 196, side = HudEdge.LEFT, workArea = area)
        val column = assertNotNull(parts.column, "coluna ausente")
        val buttons = assertNotNull(parts.buttons, "alças ausentes")

        assertInsideArea(column, holeCenterYPx = 196, label = "column", workArea = area)
        assertInsideArea(buttons, holeCenterYPx = 196, label = "buttons", workArea = area)
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

    /**
     * Coluna com retângulo fixo, minimizado ou não (A11e): a íris revela dentro do mesmo retângulo,
     * em vez de a coluna nascer ou sumir de uma vez.
     */
    @Test
    fun `column slot is the same rectangle minimized as expanded`() {
        val expanded = columnLayout(expanded = true, holeCenterYPx = 540, side = HudEdge.LEFT)
        val minimized = columnLayout(expanded = false, holeCenterYPx = 540, side = HudEdge.LEFT)

        assertEquals(DpRect(422.dp, 236.dp, 482.dp, 556.dp), minimized.columnSlot)
        assertEquals(expanded.columnSlot, minimized.columnSlot)
    }

    /** Alças com retângulo fixo, minimizado ou não (A11e): a animação de entrada e saída fica no lugar. */
    @Test
    fun `buttons slot is the same rectangle minimized as expanded`() {
        val expanded = columnLayout(expanded = true, holeCenterYPx = 540, side = HudEdge.LEFT)
        val minimized = columnLayout(expanded = false, holeCenterYPx = 540, side = HudEdge.LEFT)

        assertEquals(DpRect(436.dp, 562.dp, 468.dp, 632.dp), minimized.buttonsSlot)
        assertEquals(expanded.buttonsSlot, minimized.buttonsSlot)
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
        assertEquals(50.dp, hudFloatingBalloonTop(ringCenterY = 100.dp, balloonHeight = balloon, windowHeight = window, areaTop = 0.dp, areaBottom = window))
        assertEquals(0.dp, hudFloatingBalloonTop(ringCenterY = 10.dp, balloonHeight = balloon, windowHeight = window, areaTop = 0.dp, areaBottom = window))
        assertEquals(100.dp, hudFloatingBalloonTop(ringCenterY = 190.dp, balloonHeight = balloon, windowHeight = window, areaTop = 0.dp, areaBottom = window))
    }

    /** A11k: o balão nunca sai da área útil da tela, nem pelo topo nem pela base. */
    @Test
    fun `balao nao sai da area util perto do topo da tela`() {
        // Janela de 200 dp com a área útil começando em 20 dp: anel a 10 dp do topo da área.
        val top = hudFloatingBalloonTop(ringCenterY = 30.dp, balloonHeight = 100.dp, windowHeight = 200.dp, areaTop = 20.dp, areaBottom = 200.dp)
        assertEquals(20.dp, top, "topo do balão deve encostar no topo da área útil")
    }

    @Test
    fun `balao nao sai da area util perto da base da tela`() {
        // Área útil termina em 180 dp: anel a 10 dp da base da área.
        val top = hudFloatingBalloonTop(ringCenterY = 170.dp, balloonHeight = 100.dp, windowHeight = 200.dp, areaTop = 0.dp, areaBottom = 180.dp)
        assertEquals(80.dp, top, "base do balão deve encostar na base da área útil")
    }

    /**
     * Buraco deslocado à direita (centro x 600 de uma janela de 752): o lado com mais espaço é o esquerdo
     * (RIGHT). A coluna fica à esquerda do buraco, e o balão tem a borda direita a `gap` da coluna (A02, #625).
     */
    @Test
    fun `hole shifted right keeps the column left of it and the balloon a gap away from the column`() {
        val parts = layoutAt(holeCenter = DpOffset(600.dp, 396.dp), holeCenterYPx = 540, side = HudEdge.RIGHT, balloonOpen = true)
        val column = assertNotNull(parts.column, "coluna ausente")
        val balloonRect = assertNotNull(parts.balloon, "balão ausente")

        assertEquals(DpRect(560.dp, 356.dp, 640.dp, 436.dp), parts.hole)
        assertEquals(DpRect(494.dp, 236.dp, 554.dp, 556.dp), column)
        assertEquals(column.left - 6.dp, balloonRect.right)
        assertEquals(DpRect(224.dp, 296.dp, 488.dp, 496.dp), balloonRect)
    }

    /**
     * Buraco deslocado à esquerda (centro x 150): o lado com mais espaço é o direito (LEFT). A coluna fica
     * à direita do buraco, e o balão tem a borda esquerda a `gap` da coluna (A02, #625).
     */
    @Test
    fun `hole shifted left keeps the column right of it and the balloon a gap away from the column`() {
        val parts = layoutAt(holeCenter = DpOffset(150.dp, 396.dp), holeCenterYPx = 540, side = HudEdge.LEFT, balloonOpen = true)
        val column = assertNotNull(parts.column, "coluna ausente")
        val balloonRect = assertNotNull(parts.balloon, "balão ausente")

        assertEquals(DpRect(110.dp, 356.dp, 190.dp, 436.dp), parts.hole)
        assertEquals(DpRect(196.dp, 236.dp, 256.dp, 556.dp), column)
        assertEquals(column.right + 6.dp, balloonRect.left)
        assertEquals(DpRect(262.dp, 296.dp, 526.dp, 496.dp), balloonRect)
    }

    /**
     * Buraco com centro a 60 dp do topo da janela, janela colada no topo da tela e área útil começando em
     * y 30 px: a coluna desce até a área (top 30, não -100), e a pilha de alças fica embaixo dela, tudo
     * dentro da área útil e da janela (A02, #625).
     */
    @Test
    fun `hole near the top of the window with the work area starting at 30 px keeps column and buttons inside`() {
        val area = HudPxRect(xPx = 0, yPx = 30, widthPx = 1920, heightPx = 1050)
        val holeCenter = DpOffset(376.dp, 60.dp)
        val parts = layoutAt(holeCenter = holeCenter, holeCenterYPx = 60, side = HudEdge.LEFT, workArea = area)
        val column = assertNotNull(parts.column, "coluna ausente")
        val buttons = assertNotNull(parts.buttons, "alças ausentes")

        assertEquals(DpRect(422.dp, 30.dp, 482.dp, 350.dp), column)
        assertEquals(DpRect(436.dp, 356.dp, 468.dp, 426.dp), buttons)
        assertInsideAreaAt(column, holeCenter, holeCenterYPx = 60, label = "column", workArea = area)
        assertInsideAreaAt(buttons, holeCenter, holeCenterYPx = 60, label = "buttons", workArea = area)
    }

    /** Layout do modo coluna com o centro do buraco pedido (A02, #625); os demais parâmetros são os da janela de referência. */
    private fun layoutAt(
        holeCenter: DpOffset,
        holeCenterYPx: Int,
        side: HudEdge,
        balloonOpen: Boolean = false,
        workArea: HudPxRect = PRIMARY_AREA
    ): HudFloatingColumnParts =
        hudFloatingColumnParts(
            windowSize = COLUMN_WINDOW,
            expanded = true,
            columnSize = COLUMN,
            holeDiameter = 80.dp,
            holeCenterYPx = holeCenterYPx,
            workArea = workArea,
            balloonOpen = balloonOpen,
            balloonSide = side,
            balloonSize = balloon,
            handleSize = handle,
            gap = gap,
            holeCenter = holeCenter
        )

    /** Como [assertInsideArea], com o buraco no centro pedido em vez do centro da janela. */
    private fun assertInsideAreaAt(
        rect: DpRect,
        holeCenter: DpOffset,
        holeCenterYPx: Int,
        label: String,
        workArea: HudPxRect
    ) {
        assertInside(rect, COLUMN_WINDOW, label)
        val windowTopPx = holeCenterYPx - holeCenter.y.value
        val areaTop = workArea.yPx - windowTopPx
        val areaBottom = workArea.yPx + workArea.heightPx - windowTopPx
        assertTrue(
            rect.top.value >= areaTop && rect.bottom.value <= areaBottom,
            "$label fora da área útil [$areaTop, $areaBottom]: $rect"
        )
    }

    private fun columnLayout(
        expanded: Boolean = true,
        holeCenterYPx: Int,
        side: HudEdge,
        balloonOpen: Boolean = false,
        window: DpSize = COLUMN_WINDOW,
        columnSize: DpSize = COLUMN,
        workArea: HudPxRect = PRIMARY_AREA
    ): HudFloatingColumnParts =
        hudFloatingColumnParts(
            windowSize = window,
            expanded = expanded,
            columnSize = columnSize,
            holeDiameter = 80.dp,
            holeCenterYPx = holeCenterYPx,
            workArea = workArea,
            balloonOpen = balloonOpen,
            balloonSide = side,
            balloonSize = balloon,
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

    /** Dentro da janela e da área útil; a área útil vem em px de tela e vira dp de janela, como no layout. */
    private fun assertInsideArea(
        rect: DpRect,
        holeCenterYPx: Int,
        label: String,
        window: DpSize = COLUMN_WINDOW,
        workArea: HudPxRect = PRIMARY_AREA
    ) {
        assertInside(rect, window, label)
        val windowTopPx = holeCenterYPx - window.height.value / 2f
        val areaTop = workArea.yPx - windowTopPx
        val areaBottom = workArea.yPx + workArea.heightPx - windowTopPx
        assertTrue(
            rect.top.value >= areaTop && rect.bottom.value <= areaBottom,
            "$label fora da área útil [$areaTop, $areaBottom]: $rect"
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

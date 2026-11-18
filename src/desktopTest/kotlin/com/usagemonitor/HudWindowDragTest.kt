package com.usagemonitor

import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.usagemonitor.presentation.ui.windowDragSink
import java.awt.Point
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * As duas contas do arrasto da HUD, que só viraram testáveis quando saíram das
 * lambdas da composição (#426). O monitor de exemplo é o secundário desta
 * máquina de medição: `421,0 2560x1440`, à direita e acima do primário.
 */
class HudWindowDragTest {

    private val secondary = ScreenWorkArea(x = 421.dp, y = 0.dp, size = DpSize(2560.dp, 1440.dp))
    private val size = DpSize(442.dp, 557.dp)

    @Test
    fun `the window follows the pointer by the delta of one tick`() {
        val moved = hudDragStep(
            previous = Point(1000, 500),
            current = Point(1030, 480),
            position = DpOffset(900.dp, 400.dp),
            size = size,
            workArea = secondary
        )

        assertEquals(DpOffset(930.dp, 380.dp), moved)
    }

    /**
     * Incremental, nunca acumulado: dois tiques iguais andam o dobro do primeiro.
     * Somar o total desde o início prenderia o notch na borda (#288).
     */
    @Test
    fun `two ticks move twice as far as one`() {
        val first = hudDragStep(Point(1000, 500), Point(1010, 500), DpOffset(900.dp, 400.dp), size, secondary)
        val second = hudDragStep(Point(1010, 500), Point(1020, 500), first, size, secondary)

        assertEquals(DpOffset(910.dp, 400.dp), first)
        assertEquals(DpOffset(920.dp, 400.dp), second)
    }

    /** Sem medida do ponteiro ou sem gesto começado, a janela não anda. */
    @Test
    fun `a missing measurement leaves the window where it is`() {
        assertNull(hudDragStep(null, Point(1, 1), DpOffset.Zero, size, secondary))
        assertNull(hudDragStep(Point(1, 1), null, DpOffset.Zero, size, secondary))
        assertNull(hudDragStep(Point(1, 1), Point(2, 2), null, size, secondary))
    }

    /** O arrasto para fora da tela é contido pela área, e não deixa a janela escapar. */
    @Test
    fun `the window is kept inside the monitor it is being dragged on`() {
        val moved = hudDragStep(
            previous = Point(0, 0),
            current = Point(9000, 9000),
            position = DpOffset(2000.dp, 800.dp),
            size = size,
            workArea = secondary
        )

        assertEquals(DpOffset((421 + 2560 - 442).dp, (1440 - 557).dp), moved)
    }

    /** Solto perto da direita, o notch encaixa na direita — pelo **centro** dele. */
    @Test
    fun `the notch snaps to the edge nearest its centre`() {
        val landing = hudDragLanding(
            position = DpOffset(2400.dp, 600.dp),
            size = size,
            fallbackArea = ScreenWorkArea.Unknown,
            target = ScreenInfo(":0.1", secondary, secondary)
        )

        assertEquals(HudEdge.RIGHT, landing.edge)
    }

    @Test
    fun `the notch snaps to the top when it is dropped near it`() {
        val landing = hudDragLanding(
            position = DpOffset(1500.dp, 10.dp),
            size = size,
            fallbackArea = ScreenWorkArea.Unknown,
            target = ScreenInfo(":0.1", secondary, secondary)
        )

        assertEquals(HudEdge.TOP, landing.edge)
    }

    /** Ponteiro fora de todos os monitores cai na área que quem chama já tinha. */
    @Test
    fun `without a monitor under the pointer the fallback area decides`() {
        val landing = hudDragLanding(
            position = DpOffset(2400.dp, 600.dp),
            size = size,
            fallbackArea = secondary,
            target = null
        )

        assertEquals(HudEdge.RIGHT, landing.edge)
    }

    @Test
    fun `the drag state follows the pointer tick by tick and returns where it was dropped`() {
        val pointer = ArrayDeque(listOf(Point(1000, 500), Point(1030, 480), Point(1050, 470)))
        val drag = HudDragState(readPointer = { pointer.removeFirst() })
        val messages = mutableListOf<String>()
        val previousSink = windowDragSink
        windowDragSink = { message -> messages += message }
        try {
            drag.begin(DpOffset(1200.dp, 0.dp))
            assertTrue(drag.dragging)
            assertEquals(DpOffset(1200.dp, 0.dp), drag.windowPosition)

            drag.moveTo(size, secondary)
            assertEquals(DpOffset(1230.dp, 0.dp), drag.windowPosition, "presa à tela, a janela não sobe além do topo")
            drag.moveTo(size, secondary)
            assertEquals(DpOffset(1250.dp, 0.dp), drag.windowPosition)

            val dropped = drag.finish()

            assertEquals(DpOffset(1250.dp, 0.dp), dropped)
            assertFalse(drag.dragging)
            assertNull(drag.windowPosition)
            // O arrasto visto de fora (#416): uma linha ao soltar, com a posição pedida.
            assertEquals(1, messages.size)
            assertTrue(messages.single().contains("pedido 1250,0"), messages.single())
        } finally {
            windowDragSink = previousSink
        }
    }

    /**
     * Modo flutuante (#619, A08f1): o buraco minimizado é a âncora. Arrastado além do canto
     * inferior direito, ele para encostado no canto e a janela passa da tela; prender a janela
     * inteira prenderia o buraco longe do canto, por causa da vaga dos balões.
     */
    @Test
    fun `with an anchor the hole stops at the corner while the window may pass the edge`() {
        val hole = DpRect(left = 189.dp, top = 246.dp, right = 253.dp, bottom = 310.dp)
        val moved = hudDragStep(
            previous = Point(0, 0),
            current = Point(9000, 9000),
            position = DpOffset(2000.dp, 800.dp),
            size = size,
            workArea = secondary,
            anchor = hole
        )

        // Âncora em (421 + 2560 - 64, 1440 - 64) = (2917, 1376): a janela fica em (2917 - 189, 1376 - 246).
        assertEquals(DpOffset(2728.dp, 1130.dp), moved)
    }

    /** Com o buraco dentro do monitor, o âncora não atrapalha: a janela anda o delta do tique. */
    @Test
    fun `with an anchor inside the monitor the window follows the pointer`() {
        val hole = DpRect(left = 189.dp, top = 246.dp, right = 253.dp, bottom = 310.dp)
        val moved = hudDragStep(Point(1000, 500), Point(1030, 480), DpOffset(900.dp, 400.dp), size, secondary, anchor = hole)

        assertEquals(DpOffset(930.dp, 380.dp), moved)
    }

    /** O buraco é centrado na janela, com o diâmetro dado (em dp de janela). */
    @Test
    fun `the floating hole anchor is centred in the window`() {
        val anchor = hudFloatingHoleAnchor(DpSize(442.dp, 557.dp), 64.dp)

        assertEquals(DpRect(189.dp, 246.5f.dp, 253.dp, 310.5f.dp), anchor)
    }

    /** `HudDragState.moveTo` repassa a âncora ao passo. */
    @Test
    fun `the drag state clamps the hole anchor when a tick goes past the corner`() {
        val pointer = ArrayDeque(listOf(Point(0, 0), Point(9000, 9000)))
        val drag = HudDragState(readPointer = { pointer.removeFirst() })

        drag.begin(DpOffset(2000.dp, 800.dp))
        drag.moveTo(size, secondary, DpRect(left = 189.dp, top = 246.dp, right = 253.dp, bottom = 310.dp))

        assertEquals(DpOffset(2728.dp, 1130.dp), drag.windowPosition)
    }

    @Test
    fun `a tick without a pointer reading keeps the window and the last pointer`() {
        val pointer = ArrayDeque(listOf<Point?>(Point(1000, 500), null, Point(1010, 520)))
        val drag = HudDragState(readPointer = { pointer.removeFirst() })

        drag.begin(DpOffset(1200.dp, 300.dp))
        drag.moveTo(size, secondary)
        assertEquals(DpOffset(1200.dp, 300.dp), drag.windowPosition)
        // O passo seguinte mede a partir do último ponteiro lido, não do que falhou.
        drag.moveTo(size, secondary)
        assertEquals(DpOffset(1210.dp, 320.dp), drag.windowPosition)
    }

    @Test
    fun `finishing a gesture that never began returns no position`() {
        val drag = HudDragState(readPointer = { Point(0, 0) })

        assertNull(drag.finish())
        assertFalse(drag.dragging)
    }
}

package com.usagemonitor

import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import java.awt.Point
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
}

package com.usagemonitor

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** O anel de uso da bandeja (issue #328), medido em pixel: é só pintura. */
class TrayRiskIconTest {

    private val side = 64

    private fun render(ringFraction: Float?): androidx.compose.ui.graphics.PixelMap {
        val bitmap = ImageBitmap(side, side)
        val painter = TrayRiskIconPainter(base = null, riskLevel = null, ringFraction = ringFraction)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(side.toFloat(), side.toFloat())) {
            with(painter) { draw(size) }
        }
        return bitmap.toPixelMap()
    }

    @Test
    fun `without ring the icon is untouched`() {
        val plain = render(null)
        assertTrue((0 until side).all { x -> plain[x, 1].alpha == 0f }, "Sem anel e sem base nada deveria ser pintado")
    }

    @Test
    fun `the arc covers the right half at fifty percent and not the left`() {
        val half = render(0.5f)
        val ringY = side / 2
        val rightEdge = side - 3
        val leftEdge = 2

        // Topo no sentido horário: 50% cobre a metade direita. As duas bordas têm
        // o trilho escuro; só a direita tem a cor do arco por cima dele.
        assertNotEquals(half[leftEdge, ringY], half[rightEdge, ringY])
        assertEquals(trayRingColor(0.5f), half[rightEdge, ringY])
    }

    @Test
    fun `ring color follows the tray alert thresholds`() {
        assertEquals(trayRingColor(0.95f), render(0.95f)[side - 3, side / 2])
        assertNotEquals(trayRingColor(0.5f), trayRingColor(0.8f))
        assertNotEquals(trayRingColor(0.8f), trayRingColor(0.95f))
    }

    @Test
    fun `painters with different fractions are not equal`() {
        assertNotEquals(TrayRiskIconPainter(null, null, 0.4f), TrayRiskIconPainter(null, null, 0.5f))
        assertEquals(TrayRiskIconPainter(null, null, 0.4f), TrayRiskIconPainter(null, null, 0.4f))
    }
}

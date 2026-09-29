package com.usagemonitor.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.usagemonitor.HudEdge
import com.usagemonitor.presentation.ui.hudBalloonPoint
import com.usagemonitor.presentation.ui.hudBalloonRevealRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** B3 · o recorte que desdobra o balão e o caminho do feixe, nas quatro bordas. */
class HudBalloonJetGeometryTest {
    private val width = 300f
    private val height = 200f

    @Test
    fun `desdobrado inteiro o recorte e a caixa toda`() {
        for (edge in HudEdge.entries) {
            assertEquals(Rect(0f, 0f, width, height), hudBalloonRevealRect(edge, width, height, 90f, 1f), "$edge")
        }
    }

    @Test
    fun `fechado o recorte e uma linha sobre o feixe`() {
        val horizontal = hudBalloonRevealRect(HudEdge.TOP, width, height, 90f, 0f)
        assertEquals(Rect(90f, 0f, 90f, height), horizontal)
        val vertical = hudBalloonRevealRect(HudEdge.RIGHT, width, height, 60f, 0f)
        assertEquals(Rect(0f, 60f, width, 60f), vertical)
    }

    @Test
    fun `desdobrando a linha do feixe fica sempre dentro do recorte`() {
        for (edge in HudEdge.entries) {
            for (step in 0..10) {
                val shown = hudBalloonRevealRect(edge, width, height, 70f, step / 10f)
                val line = if (edge.isHorizontal) shown.left..shown.right else shown.top..shown.bottom
                assertTrue(70f in line, "$edge em ${step / 10f}: $shown")
            }
        }
    }

    @Test
    fun `profundidade negativa sai da caixa em direcao ao notch`() {
        assertEquals(Offset(40f, -10f), hudBalloonPoint(HudEdge.TOP, width, height, 40f, -10f))
        assertEquals(Offset(40f, height + 10f), hudBalloonPoint(HudEdge.BOTTOM, width, height, 40f, -10f))
        assertEquals(Offset(-10f, 40f), hudBalloonPoint(HudEdge.LEFT, width, height, 40f, -10f))
        assertEquals(Offset(width + 10f, 40f), hudBalloonPoint(HudEdge.RIGHT, width, height, 40f, -10f))
    }
}

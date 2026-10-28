package com.usagemonitor.presentation

import com.usagemonitor.presentation.ui.PaceLabelBox
import com.usagemonitor.presentation.ui.placePacePointLabel
import kotlin.test.Test
import kotlin.test.assertEquals

/** Rótulo do ponto da diagonal do ritmo sem cruzar a legenda (#477), em px de um gráfico de 400 × 168. */
class PaceLabelPlacementTest {

    private val areaWidth = 400f
    private val areaHeight = 168f

    /** "acima da linha: esgota antes", no topo esquerdo — o lugar da captura da issue. */
    private val aboveCaption = PaceLabelBox(left = 6f, top = 3f, width = 204f, height = 16f)

    /** "abaixo: cabe", no canto de baixo à direita. */
    private val belowCaption = PaceLabelBox(left = 310f, top = 149f, width = 84f, height = 16f)

    private fun place(pointX: Float, pointY: Float, avoid: List<PaceLabelBox> = listOf(aboveCaption, belowCaption)) =
        placePacePointLabel(
            pointX = pointX,
            pointY = pointY,
            labelWidth = 150f,
            labelHeight = 16f,
            gap = 8f,
            pointRadius = 5.5f,
            areaWidth = areaWidth,
            areaHeight = areaHeight,
            avoid = avoid
        )

    @Test
    fun `a point in the middle keeps the label above and to its left`() {
        val box = place(pointX = 260f, pointY = 90f)

        assertEquals(PaceLabelBox(left = 102f, top = 66f, width = 150f, height = 16f), box)
    }

    @Test
    fun `a point at the top no longer lands on the above caption`() {
        // 100% com 87% do tempo: acima e abaixo à esquerda caem na faixa da legenda.
        val box = place(pointX = 348f, pointY = 0f)

        assertEquals(0f, box.overlapArea(aboveCaption), "Rótulo sobre a legenda: $box")
        assertEquals(0f, box.overlapArea(PaceLabelBox(342.5f, -5.5f, 11f, 11f)), "Rótulo sobre o ponto: $box")
        assertEquals(250f, box.left)
    }

    @Test
    fun `the label stays inside the plotting area`() {
        val box = place(pointX = 4f, pointY = 166f, avoid = emptyList())

        assertEquals(true, box.left >= 0f && box.right <= areaWidth, "Fora na horizontal: $box")
        assertEquals(true, box.top >= 0f && box.bottom <= areaHeight, "Fora na vertical: $box")
    }

    @Test
    fun `with no free slot the label takes the smallest overlap instead of disappearing`() {
        val everywhere = PaceLabelBox(left = 0f, top = 0f, width = areaWidth, height = 40f)
        val box = place(pointX = 200f, pointY = 20f, avoid = listOf(everywhere))

        val candidatesOverlap = box.overlapArea(everywhere)
        assertEquals(true, candidatesOverlap < 150f * 16f, "Escolheu a sobreposição total: $box")
    }
}

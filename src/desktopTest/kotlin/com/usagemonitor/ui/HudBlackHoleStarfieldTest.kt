package com.usagemonitor.ui

import androidx.compose.ui.geometry.Rect
import com.usagemonitor.HUD_BLACK_HOLE_CELL_SIZE
import com.usagemonitor.HUD_RING_SIZE
import com.usagemonitor.presentation.ui.components.hudStarfieldFrame
import com.usagemonitor.presentation.ui.components.hudStarfieldGrainFrame
import com.usagemonitor.presentation.ui.hudStarfieldWells
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * #611 A05: a célula do buraco entra no campo com os limites de 114 dp, mas o poço deve ser o anel central de 64 dp.
 * Com o poço da célula inteira (raio 57), nada aparece na faixa entre o anel + folga (32 + 3 px) e o raio 57 px.
 */
class HudBlackHoleStarfieldTest {
    private val cellPx = HUD_BLACK_HOLE_CELL_SIZE.value
    private val center = cellPx / 2f
    private val ringRadius = HUD_RING_SIZE.value / 2f

    /** Folga do poço, igual a `HUD_STARFIELD_WELL_CLEARANCE` (privada em `HudStarfield.kt`). */
    private val clearance = 3f

    /** Poço da célula do buraco como o campo o recebe: o Rect é a célula inteira, o raio é limitado ao anel de 64 dp. */
    private val cellWells = hudStarfieldWells(mapOf(0 to Rect(0f, 0f, cellPx, cellPx)), maxRadiusPx = ringRadius)
    private val sampleTimes = (0L until 240_000L step 1_375L).toList()

    private fun distance(x: Float, y: Float): Float {
        val dx = x - center
        val dy = y - center
        return sqrt(dx * dx + dy * dy)
    }

    @Test
    fun `estrelas visiveis da celula do buraco aparecem na faixa fora do anel e nunca dentro da folga`() {
        val visible = sampleTimes
            .flatMap { t -> hudStarfieldFrame(t, cellPx, cellPx, cellWells, emptyList()) }
            .filter { it.alpha > 0f }
            .map { distance(it.x, it.y) }
        val band = visible.count { it >= ringRadius + clearance && it <= cellPx / 2f }
        val inside = visible.count { it < ringRadius + clearance }
        assertTrue(band > 0, "estrelas visiveis na faixa 35 a 57 px da celula: $band (esperado > 0)")
        assertTrue(inside == 0, "estrelas visiveis dentro do anel + folga: $inside (esperado 0)")
    }

    @Test
    fun `graos visiveis da celula do buraco aparecem na faixa fora do anel e nunca dentro da folga`() {
        val visible = hudStarfieldGrainFrame(cellPx, cellPx, cellWells, emptyList())
            .filter { it.alpha > 0f }
            .map { distance(it.x, it.y) }
        val band = visible.count { it >= ringRadius + clearance && it <= cellPx / 2f }
        val inside = visible.count { it < ringRadius + clearance }
        assertTrue(band > 0, "graos visiveis na faixa 35 a 57 px da celula: $band (esperado > 0)")
        assertTrue(inside == 0, "graos visiveis dentro do anel + folga: $inside (esperado 0)")
    }
}

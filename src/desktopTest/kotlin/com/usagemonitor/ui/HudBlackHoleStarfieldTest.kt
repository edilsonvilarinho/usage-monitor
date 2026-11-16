package com.usagemonitor.ui

import com.usagemonitor.HUD_BLACK_HOLE_CELL_SIZE
import com.usagemonitor.presentation.ui.components.hudStarfieldFrame
import com.usagemonitor.presentation.ui.components.hudStarfieldGrainFrame
import com.usagemonitor.presentation.ui.components.HudStarfieldWell
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * #611 A05 / #612 C1: a célula do buraco tem 106 dp e o poço é o anel central de 40 dp (raio 20 dp).
 * Este arquivo testa só a geometria do campo estelar com esse poço (raio 20 px); a ligação do poço do buraco
 * no notch (índice do buraco recebe 20 dp, os anéis cheios seguem 32 dp) é coberta em `HudNotchTest`.
 */
class HudBlackHoleStarfieldTest {
    private val cellPx = HUD_BLACK_HOLE_CELL_SIZE.value
    private val center = cellPx / 2f

    /** Raio do anel central do buraco: 40 dp de diâmetro (#612, C1). */
    private val holeRingRadius = 20f

    /** Folga do poço, igual a `HUD_STARFIELD_WELL_CLEARANCE` (privada em `HudStarfield.kt`). */
    private val clearance = 3f

    /** Poço da célula do buraco: o anel central de 40 dp, centrado na célula (raio 20 dp), sem ativação. */
    private val cellWells = listOf(HudStarfieldWell(center, center, holeRingRadius))
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
        val band = visible.count { it >= holeRingRadius + clearance && it <= cellPx / 2f }
        val inside = visible.count { it < holeRingRadius + clearance }
        assertTrue(band > 0, "estrelas visiveis na faixa 23 a 48 px da celula: $band (esperado > 0)")
        assertTrue(inside == 0, "estrelas visiveis dentro do anel + folga: $inside (esperado 0)")
    }

    @Test
    fun `graos visiveis da celula do buraco aparecem na faixa fora do anel e nunca dentro da folga`() {
        val visible = hudStarfieldGrainFrame(cellPx, cellPx, cellWells, emptyList())
            .filter { it.alpha > 0f }
            .map { distance(it.x, it.y) }
        val band = visible.count { it >= holeRingRadius + clearance && it <= cellPx / 2f }
        val inside = visible.count { it < holeRingRadius + clearance }
        assertTrue(band > 0, "graos visiveis na faixa 23 a 48 px da celula: $band (esperado > 0)")
        assertTrue(inside == 0, "graos visiveis dentro do anel + folga: $inside (esperado 0)")
    }
}

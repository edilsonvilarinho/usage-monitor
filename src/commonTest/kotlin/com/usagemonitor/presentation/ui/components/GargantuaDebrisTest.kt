package com.usagemonitor.presentation.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** J7 · anel de detritos: a trilha de cota sem projeção. */
class GargantuaDebrisTest {
    private val field = gargantuaDebrisField

    @Test
    fun `os fragmentos cobrem a volta com vaos e sem se sobrepor`() {
        assertTrue(field.size >= 15, "poucos fragmentos: ${field.size}")
        field.zipWithNext().forEach { (current, next) ->
            assertTrue(current.startDegrees + current.sweepDegrees < next.startDegrees, "fragmentos encostados em $current")
        }
        val last = field.last()
        assertTrue(last.startDegrees < 360f)
        val covered = field.sumOf { fragment -> fragment.sweepDegrees.toDouble() }
        assertTrue(covered in 90.0..270.0, "os vãos devem ser visíveis: $covered° cobertos")
    }

    @Test
    fun `detrito fica dentro do tubo e parte dele e dourado`() {
        field.forEach { fragment ->
            assertTrue(fragment.radialOffset in -GARGANTUA_DEBRIS_MAX_OFFSET..GARGANTUA_DEBRIS_MAX_OFFSET)
            assertTrue(fragment.width in 0.2f..0.76f, "espessura fora do tubo: ${fragment.width}")
            assertTrue(fragment.alpha in 0.12f..0.32f, "detrito competindo com o dado: ${fragment.alpha}")
        }
        assertTrue(field.any { fragment -> fragment.gold })
        assertTrue(field.any { fragment -> !fragment.gold })
    }

    @Test
    fun `o anel e sempre o mesmo`() {
        assertEquals(field, gargantuaDebrisField)
        assertEquals(0f, gargantuaDebrisAngle(field.first(), 0f))
    }

    @Test
    fun `fragmento de dentro gira mais rapido que o de fora`() {
        assertEquals(GARGANTUA_DEBRIS_BASE_LAPS, gargantuaDebrisLaps(0f))
        assertTrue(gargantuaDebrisLaps(-GARGANTUA_DEBRIS_MAX_OFFSET) > gargantuaDebrisLaps(0f))
        assertTrue(gargantuaDebrisLaps(GARGANTUA_DEBRIS_MAX_OFFSET) < gargantuaDebrisLaps(0f))
        var previous = Int.MAX_VALUE
        for (step in -8..8) {
            val laps = gargantuaDebrisLaps(step * GARGANTUA_DEBRIS_MAX_OFFSET / 8)
            assertTrue(laps <= previous, "mais para fora ficou mais rápido em $step")
            previous = laps
        }
    }

    @Test
    fun `o laco fecha sem salto`() {
        field.forEach { fragment ->
            assertEquals(gargantuaDebrisAngle(fragment, 0f), gargantuaDebrisAngle(fragment, 1f), 0.01f)
            val angle = gargantuaDebrisAngle(fragment, 0.37f)
            assertTrue(angle >= 0f && angle < 360f)
        }
    }
}

package com.usagemonitor.presentation.ui.components

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** F10 · a cauda de íons do cometa de execução. */
class GargantuaIonTailTest {
    @Test
    fun `a ponta da cauda para exatamente no limite do halo`() {
        val radius = 24.5f
        val allowance = 2.4f
        val tilt = 12f
        val length = gargantuaIonTailLength(radius, allowance, tilt)
        // Cabeça em (radius, 0); para trás é -y (tangente oposta), para fora é +x.
        val angle = tilt * PI / 180
        val endX = radius + length * sin(angle)
        val endY = -length * cos(angle)
        assertEquals(radius + allowance, hypot(endX, endY).toFloat(), 0.001f)
    }

    @Test
    fun `a cauda na HUD tem comprimento que se ve`() {
        // Anel de 44dp, traço 2,5dp: órbita a ~24,5dp, traço da órbita 2dp.
        val length = gargantuaIonTailLength(radius = 24.5f, allowance = 2f * 1.2f, tiltDegrees = 12f)
        assertTrue(length > 5f, "cauda de $length dp")
    }

    @Test
    fun `sem folga nao ha cauda`() {
        assertEquals(0f, gargantuaIonTailLength(24f, 0f, 12f))
        assertEquals(0f, gargantuaIonTailLength(0f, 2f, 12f))
    }
}

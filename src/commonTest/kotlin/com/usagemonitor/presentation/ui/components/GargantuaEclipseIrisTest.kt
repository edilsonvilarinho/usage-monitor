package com.usagemonitor.presentation.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Z2 · a íris do eclipse que expande e recolhe a HUD (#400). */
class GargantuaEclipseIrisTest {
    @Test
    fun `expandir comeca na faixa e termina no notch inteiro sem fio`() {
        assertEquals(GargantuaIrisFrame.Closed, gargantuaIrisOpenFrame(0f))
        assertEquals(GargantuaIrisFrame.Open, gargantuaIrisOpenFrame(1f))
        assertTrue(gargantuaIrisOpenFrame(1f).settled)
    }

    @Test
    fun `recolher comeca no notch inteiro e termina na faixa sem fio`() {
        assertEquals(GargantuaIrisFrame.Open, gargantuaIrisCloseFrame(0f))
        assertEquals(GargantuaIrisFrame.Closed, gargantuaIrisCloseFrame(1f))
    }

    @Test
    fun `o disco so cresce ao expandir e so encolhe ao recolher, sem passar do alcance`() {
        var opening = -1f
        var closing = 2f
        for (step in 0..100) {
            val open = gargantuaIrisOpenFrame(step / 100f)
            val close = gargantuaIrisCloseFrame(step / 100f)
            assertTrue(open.radius >= opening, "expandir voltou em $step")
            assertTrue(close.radius <= closing, "recolher voltou em $step")
            assertTrue(open.radius in 0f..1f && close.radius in 0f..1f, "raio fora do alcance em $step")
            opening = open.radius
            closing = close.radius
        }
    }

    @Test
    fun `inverter no meio continua do mesmo raio, sem salto`() {
        for (step in 0..10) {
            val radius = step / 10f
            val open = gargantuaIrisOpenFrame(gargantuaIrisOpenProgressFor(radius)).radius
            val close = gargantuaIrisCloseFrame(gargantuaIrisCloseProgressFor(radius)).radius
            assertEquals(radius, open, 0.001f, "expandir a partir de $radius")
            assertEquals(radius, close, 0.001f, "recolher a partir de $radius")
        }
    }

    @Test
    fun `o fio so existe durante a transicao e acende no meio`() {
        val middle = gargantuaIrisOpenFrame(0.2f)
        assertTrue(middle.rimAlpha > 0.5f, "fio em ${middle.rimAlpha}")
        assertTrue(middle.rimAlpha <= IRIS_RIM_PEAK)
        assertTrue(gargantuaIrisCloseFrame(0.3f).rimAlpha > 0f)
        assertEquals(0f, GargantuaIrisFrame.Open.rimAlpha)
        assertEquals(0f, GargantuaIrisFrame.Closed.rimAlpha)
    }
}

package com.usagemonitor.presentation.ui.components

import com.usagemonitor.presentation.ui.theme.AppGargantuaTokens
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** M1 · horizonte de eventos: o corpo da HUD e o anel de fótons da borda. */
class GargantuaHorizonBodyTest {
    private val period = AppGargantuaTokens.horizonBreathMillis.toLong()

    @Test
    fun `a respiracao fica entre 85 e 100 por cento e nunca apaga`() {
        val samples = (0 until period step 50).map { millis -> gargantuaHorizonBreath(millis) }
        assertTrue(samples.all { breath -> breath in 0.85f..1f }, "fora da faixa: ${samples.minOrNull()}..${samples.maxOrNull()}")
        assertEquals(1f, samples.maxOrNull()!!, 0.001f)
        assertEquals(0.85f, samples.minOrNull()!!, 0.001f)
    }

    @Test
    fun `o laco fecha no periodo e o quadro zero e o meio`() {
        assertEquals(gargantuaHorizonBreath(0L), gargantuaHorizonBreath(period), 0.0001f)
        assertEquals(gargantuaHorizonBreath(1_234L), gargantuaHorizonBreath(1_234L + 3 * period), 0.0001f)
        assertEquals(0.925f, gargantuaHorizonBreath(0L), 0.0001f)
    }

    @Test
    fun `o doppler vai da brasa ao quente nos dois temas`() {
        for (dark in listOf(true, false)) {
            val (receding, middle, approaching) = gargantuaHorizonRimColors(dark, breath = 1f)
            assertTrue(receding.alpha < approaching.alpha, "o lado quente devia brilhar mais (dark=$dark)")
            assertTrue(middle.alpha in receding.alpha..approaching.alpha)
        }
        val (receding, _, approaching) = gargantuaHorizonRimColors(dark = true, breath = 1f)
        assertEquals(AppGargantuaTokens.ember.copy(alpha = receding.alpha), receding)
        assertEquals(AppGargantuaTokens.hot.copy(alpha = approaching.alpha), approaching)
    }

    @Test
    fun `a respiracao escala o brilho sem trocar a cor`() {
        val full = gargantuaHorizonRimColors(dark = true, breath = 1f)
        val dim = gargantuaHorizonRimColors(dark = true, breath = 0.85f)
        full.zip(dim).forEach { (bright, faint) ->
            assertEquals(bright.copy(alpha = 1f), faint.copy(alpha = 1f))
            assertEquals(bright.alpha * 0.85f, faint.alpha, 0.01f)
        }
        assertTrue(gargantuaHorizonInnerGlow(1f).alpha <= 0.08f, "o filete de dentro não pode competir com o dado")
    }
}

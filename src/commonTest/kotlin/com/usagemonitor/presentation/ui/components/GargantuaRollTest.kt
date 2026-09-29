package com.usagemonitor.presentation.ui.components

import com.usagemonitor.presentation.ui.theme.AppGargantuaTokens
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** D5 · horizonte de eventos: quais caracteres rolam e quando. */
class GargantuaRollTest {
    @Test
    fun `o rotulo fica parado e so os digitos que mudaram rolam`() {
        assertEquals(
            listOf(GargantuaRollGlyph(3, 3, 0), GargantuaRollGlyph(4, 4, 1)),
            gargantuaRollGlyphs("7d 56%", "7d 61%")
        )
        // 8% → 9% rola só um dígito; o "%" é o mesmo.
        assertEquals(listOf(GargantuaRollGlyph(3, 3, 0)), gargantuaRollGlyphs("7d 8%", "7d 9%"))
    }

    @Test
    fun `numero que ganha um digito compara pela direita como odometro`() {
        // "9%" → "12%": o 9 vira 2 e o 1 nasce sem antecessor.
        assertEquals(
            listOf(GargantuaRollGlyph(3, null, 0), GargantuaRollGlyph(4, 3, 1)),
            gargantuaRollGlyphs("7d 9%", "7d 12%")
        )
        // E o inverso: "12%" → "9%" rola só o que sobra no texto novo.
        assertEquals(listOf(GargantuaRollGlyph(3, 4, 0)), gargantuaRollGlyphs("7d 12%", "7d 9%"))
    }

    @Test
    fun `texto igual nao rola nada`() {
        assertEquals(emptyList(), gargantuaRollGlyphs("5h 0%", "5h 0%"))
        assertEquals(0, gargantuaRollTotalMillis(0))
    }

    @Test
    fun `cada digito rola em cascata e termina parado sem passar do lugar`() {
        assertEquals(AppGargantuaTokens.rollMillis + AppGargantuaTokens.rollStaggerMillis, gargantuaRollTotalMillis(2))
        assertEquals(0f, gargantuaRollProgress(0f, 1))
        assertEquals(0f, gargantuaRollProgress(AppGargantuaTokens.rollStaggerMillis.toFloat(), 1))
        assertEquals(1f, gargantuaRollProgress(gargantuaRollTotalMillis(2).toFloat(), 1))
        var previous = 0f
        for (step in 0..100) {
            val progress = gargantuaRollProgress(step * 6f, 0)
            assertTrue(progress in previous..1f, "o rolar voltou ou passou de 1 em $step: $progress")
            previous = progress
        }
    }
}

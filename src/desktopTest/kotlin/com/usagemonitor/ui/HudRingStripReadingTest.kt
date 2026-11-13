package com.usagemonitor.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.usagemonitor.presentation.ui.components.gargantuaDebrisAlpha
import com.usagemonitor.presentation.ui.hudReadingStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Leitura reforçada da HUD no claro (A04, #590): estilo do rótulo por tema, sem tela. */
class HudRingStripReadingTest {

    @Test
    fun `no claro o prefixo vai em onSurface a 78 por cento com peso Medium e o valor em SemiBold`() {
        val style = hudReadingStyle(surface = Color.White, onSurface = Color.Black, onSurfaceVariant = Color.Gray)
        assertEquals(Color.Black.copy(alpha = 0.78f), style.prefixColor)
        assertEquals(FontWeight.Medium, style.prefixWeight)
        assertEquals(FontWeight.SemiBold, style.valueWeight)
    }

    @Test
    fun `no escuro o rotulo segue como hoje, com onSurfaceVariant e peso herdado`() {
        val style = hudReadingStyle(surface = Color(0xFF111111), onSurface = Color.White, onSurfaceVariant = Color.Gray)
        assertEquals(Color.Gray, style.prefixColor)
        assertNull(style.prefixWeight)
        assertNull(style.valueWeight)
    }

    @Test
    fun `no claro o tracejado sobe por 1,6 e no escuro fica como esta`() {
        assertEquals(1.6f, hudReadingStyle(Color.White, Color.Black, Color.Gray).debrisAlphaScale)
        assertEquals(1f, hudReadingStyle(Color(0xFF111111), Color.White, Color.Gray).debrisAlphaScale)
    }

    @Test
    fun `o alfa do tracejado sobe no claro e nunca passa de 1`() {
        assertEquals(0.64f, gargantuaDebrisAlpha(0.4f, 1.6f), 1e-5f)
        assertEquals(1f, gargantuaDebrisAlpha(0.8f, 1.6f))
    }
}

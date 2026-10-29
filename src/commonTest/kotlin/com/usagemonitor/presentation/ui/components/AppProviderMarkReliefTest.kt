package com.usagemonitor.presentation.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppProviderMarkReliefTest {

    /** O volume vem da luz a −135° dos arcos de vidro: claro em cima, escuro embaixo, a mesma cor no meio. */
    @Test
    fun `o relevo clareia em cima e escurece embaixo a partir da cor da marca`() {
        for (tint in listOf(Color(0xFF4F8CFF), Color(0xFFF2EDED), Color(0xFF1565C0))) {
            val relief = providerMarkRelief(tint)
            assertTrue(relief.top.luminance() > tint.luminance(), "topo de $tint não clareou")
            assertTrue(relief.bottom.luminance() < tint.luminance(), "base de $tint não escureceu")
        }
    }

    /** A borda é 5% do tamanho e nunca some: a 14–16 px ela ainda tem 1 px. */
    @Test
    fun `a borda do relevo tem cinco por cento do tamanho e nunca menos de um pixel`() {
        assertEquals(1f, providerMarkReliefDepth(14f))
        assertEquals(1f, providerMarkReliefDepth(16f))
        assertEquals(3f, providerMarkReliefDepth(64f))
        assertEquals(5f, providerMarkReliefDepth(100f))
    }
}

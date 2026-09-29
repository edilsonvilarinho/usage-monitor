package com.usagemonitor.presentation.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** B3 · o jato relativístico que abre e fecha o balão da HUD. */
class GargantuaBalloonJetTest {
    @Test
    fun `a abertura comeca sem balao e termina parada`() {
        val first = gargantuaJetOpenFrame(0f)
        assertEquals(0f, first.unfold)
        assertEquals(0f, first.beamEnd)
        assertEquals(1f, first.beamAlpha)
        assertEquals(GargantuaJetFrame.Settled, gargantuaJetOpenFrame(1f))
    }

    @Test
    fun `o feixe cruza o balao antes de ele se desdobrar`() {
        val crossing = gargantuaJetOpenFrame(0.2f)
        assertEquals(0f, crossing.unfold)
        assertTrue(crossing.beamEnd > 0.8f, "feixe em ${crossing.beamEnd}")
    }

    @Test
    fun `o desdobrar so cresce e o feixe se apaga antes do fim`() {
        var previous = -1f
        for (step in 0..100) {
            val frame = gargantuaJetOpenFrame(step / 100f)
            assertTrue(frame.unfold >= previous, "desdobrar voltou em $step")
            previous = frame.unfold
        }
        assertTrue(gargantuaJetOpenFrame(0.99f).beamAlpha < 0.05f)
    }

    @Test
    fun `o fechamento termina sem balao e sem feixe`() {
        assertEquals(1f, gargantuaJetCloseFrame(0f).unfold)
        val last = gargantuaJetCloseFrame(1f)
        assertEquals(0f, last.unfold)
        assertEquals(0f, last.beamEnd)
        assertEquals(0f, last.beamAlpha)
    }

    @Test
    fun `fechando o feixe recolhe so depois de o balao dobrar`() {
        val folding = gargantuaJetCloseFrame(0.4f)
        assertTrue(folding.unfold in 0f..1f)
        assertEquals(1f, folding.beamEnd)
        val retracting = gargantuaJetCloseFrame(0.7f)
        assertTrue(retracting.unfold == 0f)
        assertTrue(retracting.beamEnd < 1f && retracting.beamAlpha > 0f)
    }
}

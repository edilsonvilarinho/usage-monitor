package com.usagemonitor.ui

import androidx.compose.ui.geometry.Rect
import com.usagemonitor.presentation.ui.HudStarActivityWindow
import com.usagemonitor.presentation.ui.hudStarActivityStep
import com.usagemonitor.presentation.ui.hudStarActivityWindowStep
import com.usagemonitor.presentation.ui.hudStarfieldWells
import kotlin.test.Test
import kotlin.test.assertEquals

/** #592 A05: transição pura da janela de atividade por anel e entrega dos poços com janela. */
class HudStarActivityWindowTest {
    private val open = HudStarActivityWindow(sinceMs = 1_000L, untilMs = null)
    private val closed = HudStarActivityWindow(sinceMs = 1_000L, untilMs = 5_000L)

    @Test
    fun `ligar abre janela nova no quadro atual`() {
        assertEquals(HudStarActivityWindow(5_000L, null), hudStarActivityWindowStep(null, running = true, t = 5_000L), "liga")
    }

    @Test
    fun `desligar fecha a janela aberta no quadro atual`() {
        assertEquals(HudStarActivityWindow(1_000L, 5_000L), hudStarActivityWindowStep(open, running = false, t = 5_000L), "desliga")
    }

    @Test
    fun `religar depois de desligar abre outra janela e nao reabre a antiga`() {
        assertEquals(HudStarActivityWindow(9_000L, null), hudStarActivityWindowStep(closed, running = true, t = 9_000L), "religa")
    }

    @Test
    fun `sem mudanca de estado a janela fica como estava`() {
        assertEquals(open, hudStarActivityWindowStep(open, running = true, t = 7_000L), "continua ligado")
        assertEquals(closed, hudStarActivityWindowStep(closed, running = false, t = 7_000L), "continua desligado")
        assertEquals(null, hudStarActivityWindowStep(null, running = false, t = 7_000L), "nunca rodou")
    }

    @Test
    fun `o passo por mapa trata cada anel por conta propria`() {
        val next = hudStarActivityStep(mapOf(0 to open), running = setOf(1), t = 4_000L)
        assertEquals(
            mapOf(0 to HudStarActivityWindow(1_000L, 4_000L), 1 to HudStarActivityWindow(4_000L, null)),
            next,
            "anel 0 desliga e anel 1 liga no mesmo quadro"
        )
    }

    @Test
    fun `os pocos levam a janela do anel de cada indice`() {
        val rings = mapOf(0 to Rect(0f, 0f, 40f, 40f), 1 to Rect(50f, 0f, 90f, 40f))
        val wells = hudStarfieldWells(rings, mapOf(1 to HudStarActivityWindow(4_000L, null)))
        assertEquals(4_000L, wells[1].activeSinceMs, "anel 1 em execução leva a janela")
        assertEquals(null as Long?, wells[0].activeSinceMs, "anel 0 ocioso não leva janela")
    }
}

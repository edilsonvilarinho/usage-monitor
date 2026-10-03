package com.usagemonitor.presentation.ui.components

import androidx.compose.ui.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** E9 · os filamentos de plasma que abrem e fecham todo modal. */
class GargantuaModalFilamentsTest {
    @Test
    fun `abrindo nenhuma linha aparece no comeco e todas terminam paradas`() {
        for (index in 0 until 12) {
            assertEquals(0f, modalFilamentOpenRowFrame(0f, index, 12).reveal)
            assertEquals(ModalFilamentRowFrame.Settled, modalFilamentOpenRowFrame(1f, index, 12))
        }
    }

    @Test
    fun `a revelacao de cada linha so cresce e nunca passa da largura`() {
        for (index in listOf(0, 5, 11)) {
            var previous = -1f
            for (step in 0..200) {
                val frame = modalFilamentOpenRowFrame(step / 200f, index, 12)
                assertTrue(frame.reveal >= previous, "linha $index voltou em $step")
                assertTrue(frame.reveal <= 1f, "linha $index passou da largura em $step")
                previous = frame.reveal
            }
        }
    }

    @Test
    fun `as linhas entram em ordem de leitura`() {
        val midway = 0.3f
        val reveals = (0 until 8).map { modalFilamentOpenRowFrame(midway, it, 8).reveal }
        for (index in 1 until reveals.size) {
            assertTrue(reveals[index] <= reveals[index - 1], "linha $index passou a de cima: $reveals")
        }
        assertTrue(reveals.first() > 0f && reveals.last() == 0f)
    }

    @Test
    fun `o filamento so existe com a linha em curso e se apaga antes do fim`() {
        val entering = modalFilamentOpenRowFrame(0.15f, 0, 4)
        assertTrue(entering.reveal in 0.01f..0.99f)
        assertEquals(1f, entering.filamentAlpha)
        assertEquals(0f, modalFilamentOpenRowFrame(0.15f, 3, 4).filamentAlpha, "linha ainda não começou")
        for (index in 0 until 30) {
            val late = modalFilamentOpenRowFrame(0.999f, index, 30)
            assertTrue(late.filamentAlpha < 0.05f, "filamento da linha $index ainda aceso: ${late.filamentAlpha}")
        }
    }

    @Test
    fun `fechando a ultima linha recolhe primeiro e todas terminam escondidas`() {
        val early = 0.2f
        assertTrue(modalFilamentCloseRowFrame(early, 5, 6).reveal < 1f)
        assertEquals(1f, modalFilamentCloseRowFrame(early, 0, 6).reveal)
        for (index in 0 until 6) {
            val last = modalFilamentCloseRowFrame(1f, index, 6)
            assertEquals(0f, last.reveal)
            assertEquals(0f, last.filamentAlpha)
        }
    }

    @Test
    fun `a moldura aparece rapido e so some no fim do fechamento`() {
        assertEquals(0f, modalFilamentWindowAlpha(ModalRevealPhase.OPENING, 0f))
        assertEquals(1f, modalFilamentWindowAlpha(ModalRevealPhase.OPENING, 0.15f))
        assertEquals(1f, modalFilamentWindowAlpha(ModalRevealPhase.CLOSING, 0.8f))
        assertEquals(0f, modalFilamentWindowAlpha(ModalRevealPhase.CLOSING, 1f))
        assertEquals(1f, modalFilamentWindowAlpha(ModalRevealPhase.SETTLED, 0f))
    }

    @Test
    fun `a ordem sai da posicao e nao de quando a linha chegou`() {
        val state = ModalRevealState(ModalRevealPhase.OPENING)
        val content = Any()
        val navigation = Any()
        val header = Any()
        state.place(navigation, Rect(0f, 40f, 120f, 60f))
        state.place(content, Rect(140f, 40f, 600f, 60f))
        state.place(header, Rect(0f, 0f, 600f, 30f))
        assertEquals(0, state.indexOf(header))
        assertEquals(1, state.indexOf(navigation))
        assertEquals(2, state.indexOf(content))

        state.remove(header)
        assertEquals(0, state.indexOf(navigation))
        assertEquals(2, state.indexOf(Any()), "linha sem caixa vai para o fim")
    }

    @Test
    fun `parado toda linha aparece inteira qualquer que seja o relogio`() {
        val state = ModalRevealState()
        val row = Any()
        state.place(row, Rect(0f, 0f, 100f, 20f))
        state.progress = 0f
        assertEquals(ModalFilamentRowFrame.Settled, state.frameOf(row))
        state.begin(ModalRevealPhase.OPENING)
        assertEquals(0f, state.frameOf(row).reveal)
        state.settle()
        assertEquals(ModalFilamentRowFrame.Settled, state.frameOf(row))
    }
}

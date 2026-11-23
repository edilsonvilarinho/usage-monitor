package com.usagemonitor.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A sequência de eventos que `SceneRecorder.drag` entrega à cena (#633): pressionar, segurar, mover por quadro, soltar. */
class SceneRecorderDragTest {

    private val from = Offset(10f, 20f)
    private val to = Offset(70f, 20f)

    private data class Sample(val type: PointerEventType, val position: Offset)

    private fun recordDrag(onPointer: (Offset) -> Unit = {}): Pair<List<Sample>, SceneRecorder> {
        val samples = mutableListOf<Sample>()
        val recorder = SceneRecorder(widthDp = 100, heightDp = 50, frameMillis = 10L, downsample = false)
        recorder.setContent {
            Box(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            samples += Sample(event.type, event.changes.first().position)
                        }
                    }
                }
            )
        }
        recorder.drag(from, to, holdMillis = 50L, moveMillis = 100L, onPointer = onPointer)
        return samples.filter { sample ->
            sample.type == PointerEventType.Press || sample.type == PointerEventType.Move || sample.type == PointerEventType.Release
        } to recorder
    }

    @Test
    fun `o arrasto pressiona em from, move um passo por quadro ate to e solta ali`() {
        val (events, recorder) = recordDrag()
        recorder.close()
        val scale = SceneRecorder.SCALE
        val press = events.indexOfFirst { sample -> sample.type == PointerEventType.Press }
        assertTrue(press >= 0, "esperava um Press, veio $events")
        assertEquals(from * scale.toFloat(), events[press].position, "o Press tem de ser em from")
        val moves = events.drop(press + 1).takeWhile { sample -> sample.type == PointerEventType.Move }
        assertEquals(10, moves.size, "100 ms a 10 ms por quadro: 10 movimentos depois do press, veio $events")
        assertEquals(to * scale.toFloat(), moves.last().position, "o último movimento chega em to")
        assertTrue(moves.zipWithNext().all { (a, b) -> b.position.x >= a.position.x }, "o x nunca recua")
        assertEquals(PointerEventType.Release, events.last().type, "termina soltando")
        assertEquals(to * scale.toFloat(), events.last().position, "solta em to")
    }

    @Test
    fun `durante a pressao longa nenhum movimento chega a cena e os quadros contam hold e movimento`() {
        val (events, recorder) = recordDrag()
        val frames = recorder.frames.size
        recorder.close()
        val press = events.indexOfFirst { sample -> sample.type == PointerEventType.Press }
        val firstMove = events.drop(press + 1).indexOfFirst { sample -> sample.type == PointerEventType.Move }
        assertEquals(0, firstMove, "o primeiro movimento vem logo depois do Press: a espera é de quadros, sem evento")
        // 50 ms de pressão (5 quadros) + 100 ms de movimento (10 quadros) + 1 quadro depois de soltar.
        assertEquals(16, frames)
    }

    @Test
    fun `o callback de ponteiro recebe a posicao em dp de cada passo`() {
        val seen = mutableListOf<Offset>()
        val (_, recorder) = recordDrag { position -> seen += position }
        recorder.close()
        assertEquals(from, seen.first(), "começa em from")
        assertEquals(to, seen.last(), "termina em to")
        assertEquals(11, seen.size, "from, mais um por passo de movimento")
    }
}

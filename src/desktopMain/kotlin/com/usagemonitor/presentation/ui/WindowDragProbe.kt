package com.usagemonitor.presentation.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import java.awt.MouseInfo
import java.awt.Point

/**
 * Para onde vai a linha de cada arrasto de janela (issue #416). O `main()` liga ao
 * `app.log`; função e não o log porque `presentation` não importa `data`. Sem
 * ligação (testes, capturas), a linha não vai a lugar nenhum.
 */
@Volatile
internal var windowDragSink: (String) -> Unit = {}

/**
 * Uma linha por arrasto: de onde a janela saiu, onde o app pediu que ela ficasse
 * e onde ela está ao soltar. Separa "o app não pediu" de "o sistema recusou" —
 * no Cosmic/XWayland o relato é que nada arrasta, e a hipótese é o compositor
 * ignorar a posição pedida pelo cliente.
 *
 * `null` = não medido (a leitura lançou). Curta: a mensagem passa pela fábrica da
 * trilha, que corta em 200 caracteres.
 */
internal fun windowDragMessage(kind: String, start: Point?, requested: Point?, actual: Point?): String {
    val outcome = when {
        start == null || actual == null -> "?"
        actual == start -> "não moveu"
        actual == requested -> "moveu"
        else -> "moveu diferente"
    }
    return "arrasto $kind: início ${pointText(start)} pedido ${pointText(requested)} " +
        "obtido ${pointText(actual)} ($outcome)"
}

private fun pointText(point: Point?): String = if (point == null) "null" else "${point.x},${point.y}"

internal fun pointerOnScreen(): Point? = runCatching { MouseInfo.getPointerInfo()?.location }.getOrNull()

/**
 * Observa o arrasto da barra de título sem participar dele: passe `Initial`, sem
 * consumir, para o `WindowDraggableArea` seguir movendo a janela como antes.
 *
 * O deslocamento é medido pelo ponteiro **na tela**, não pela posição local: onde
 * a janela acompanha o mouse, o ponteiro fica parado em relação a ela, e a
 * posição local nunca muda. Clique sem deslocamento ([DRAG_MIN_PIXELS]) não grava.
 */
internal fun Modifier.windowDragProbe(window: java.awt.Window, kind: String): Modifier =
    pointerInput(window) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val startWindow = runCatching { window.location }.getOrNull()
            val startPointer = pointerOnScreen()
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
            } while (event.changes.any { change -> change.pressed })
            val endPointer = pointerOnScreen()
            if (startWindow == null || startPointer == null || endPointer == null) {
                return@awaitEachGesture
            }
            val dx = endPointer.x - startPointer.x
            val dy = endPointer.y - startPointer.y
            if (dx * dx + dy * dy < DRAG_MIN_PIXELS * DRAG_MIN_PIXELS) {
                return@awaitEachGesture
            }
            val requested = Point(startWindow.x + dx, startWindow.y + dy)
            val actual = runCatching { window.location }.getOrNull()
            runCatching { windowDragSink(windowDragMessage(kind, startWindow, requested, actual)) }
        }
    }

private const val DRAG_MIN_PIXELS = 4

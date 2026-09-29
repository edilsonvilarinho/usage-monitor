package com.usagemonitor.presentation.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.semantics.onClick

/**
 * Um gesto só para as ações do notch: mover (só pela mão) e clicar num anel. O
 * que separa clique de arrasto é o limiar de deslocamento. O botão direito é
 * decidido no próprio `down` e engolido: não vira arrasto nem recoleta conta —
 * levava ao modo somente cards (#215), que saiu do app. Nenhuma coordenada sai daqui.
 *
 * Sem [draggable], passar do limiar só desiste do clique: o ponteiro que
 * escorregou não recoleta a conta, e o `move` não é consumido.
 */
@Composable
internal fun Modifier.hudPressGesture(
    draggable: Boolean = true,
    onDragStart: () -> Unit,
    onDragMove: () -> Unit,
    onDragEnd: () -> Unit,
    /** Recebe a posição do `down`, no nó do gesto: é por ela que o notch acha o anel. */
    onClick: (Offset) -> Unit
): Modifier {
    val currentDragStart by rememberUpdatedState(onDragStart)
    val currentDragMove by rememberUpdatedState(onDragMove)
    val currentDragEnd by rememberUpdatedState(onDragEnd)
    val currentClick by rememberUpdatedState(onClick)

    return pointerInput(Unit) {
        awaitEachGesture {
            // `awaitFirstDown` só reage ao botão primário do mouse; o laço abaixo
            // é o mesmo, sem esse filtro, para o direito chegar aqui.
            var down: PointerInputChange
            while (true) {
                val event = awaitPointerEvent()
                val candidate = event.changes.firstOrNull { it.changedToDownIgnoreConsumed() }
                if (candidate != null) {
                    down = candidate
                    break
                }
            }

            if (currentEvent.buttons.isSecondaryPressed) {
                down.consume()
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { candidate -> candidate.id == down.id }
                        ?: break
                    change.consume()
                    if (!change.pressed) break
                }
                return@awaitEachGesture
            }

            var travelled = 0f
            var dragging = false
            var slipped = false
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { candidate -> candidate.id == down.id }
                    ?: break
                if (!change.pressed) {
                    when {
                        dragging -> currentDragEnd()
                        !slipped -> currentClick(down.position)
                    }
                    break
                }
                travelled += change.positionChange().getDistance()
                if (!dragging && !slipped && travelled > viewConfiguration.touchSlop) {
                    if (draggable) {
                        dragging = true
                        currentDragStart()
                    } else {
                        slipped = true
                    }
                }
                if (dragging) {
                    change.consume()
                    currentDragMove()
                }
            }
        }
    }
}

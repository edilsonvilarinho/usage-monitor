package com.usagemonitor

import java.awt.EventQueue
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Callbacks de withFrameNanos podem continuar dentro da pintura do Skia.
 * Operações de janela ficam para a próxima passada da EDT, depois que essa pintura retorna.
 * Dispatchers.Main.immediate não estabelece essa fronteira quando já estamos na EDT.
 */
internal suspend fun awaitAwtEventTurn() {
    suspendCancellableCoroutine<Unit> { continuation ->
        EventQueue.invokeLater {
            if (continuation.isActive) continuation.resume(Unit)
        }
    }
}

/** A animação não suspende no callback de quadro; a operação nativa é enfileirada. */
internal fun postAwtWindowOperation(operation: () -> Unit) {
    EventQueue.invokeLater(operation)
}

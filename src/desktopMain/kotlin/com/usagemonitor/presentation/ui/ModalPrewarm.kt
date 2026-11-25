package com.usagemonitor.presentation.ui

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext

/** Cloak nativo: `true` quando o sistema aceitou o pedido. `fun interface` para o teste passar lambda. */
internal fun interface WindowCloak {
    fun set(cloaked: Boolean): Boolean
}

/** Estado do cloak de uma janela: `active` só vale o que o sistema confirmou. */
internal class ModalCloak(private val native: WindowCloak) {
    var active: Boolean = false
        private set

    fun cloak(): Boolean {
        active = native.set(true)
        return active
    }

    /** Sem cloak ativo não há o que desfazer; com cloak, falha de liberação fica ativa para nova tentativa. */
    fun release(): Boolean {
        if (!active) return true
        val accepted = native.set(false)
        if (accepted) active = false
        return accepted
    }
}

/**
 * Pré-aquecimento com a janela escondida pelo DWM: cloak antes de mostrar, e a
 * liberação só depois de esconder e esperar a janela sumir. Cancelamento ou falha
 * no meio ainda esconde e libera. Cloak recusado devolve `false` sem mostrar.
 */
internal suspend fun prewarmCloaked(
    cloak: ModalCloak,
    show: () -> Unit,
    awaitFrames: suspend () -> Unit,
    hide: suspend () -> Unit,
    awaitHidden: suspend () -> Unit
): Boolean {
    if (!cloak.cloak()) return false
    try {
        show()
        awaitFrames()
    } finally {
        withContext(NonCancellable) {
            hide()
            awaitHidden()
            cloak.release()
        }
    }
    return true
}

/** Sonda [condition] a cada [stepMillis] até ela valer ou estourar [timeoutMillis]. Devolve se valeu. */
internal suspend fun awaitUntil(timeoutMillis: Long, stepMillis: Long, condition: () -> Boolean): Boolean =
    withTimeoutOrNull(timeoutMillis) {
        while (!condition()) delay(stepMillis)
        true
    } ?: false

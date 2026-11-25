package com.usagemonitor.presentation.ui

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

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

/** Desfecho do pré-aquecimento: diz por que a janela não ficou pronta, para a trilha nomear o motivo. */
internal enum class PrewarmOutcome { REFUSED, DONE, STILL_VISIBLE, STILL_CLOAKED }

/** Tentativas de liberar o cloak antes de mostrar a janela mesmo assim. */
private const val RELEASE_ATTEMPTS = 3

/**
 * Mostra escondida (cloak antes de mostrar), espera quadros, esconde e libera. Esconder e liberar rodam
 * mesmo com cancelamento ou falha; só libera se a AWT confirmou que a janela saiu da tela.
 */
internal suspend fun prewarmCloaked(
    cloak: ModalCloak,
    show: () -> Unit,
    awaitFrames: suspend () -> Unit,
    hide: suspend () -> Unit,
    awaitHidden: suspend () -> Boolean
): PrewarmOutcome {
    if (!cloak.cloak()) return PrewarmOutcome.REFUSED
    var outcome = PrewarmOutcome.DONE
    try {
        show()
        awaitFrames()
    } finally {
        withContext(NonCancellable) {
            hide()
            outcome = settleAfterHide(cloak, awaitHidden)
        }
    }
    return outcome
}

/** Sem a janela fora da tela não há liberação; com ela fora, uma tentativa de liberar. */
private suspend fun settleAfterHide(cloak: ModalCloak, awaitHidden: suspend () -> Boolean): PrewarmOutcome {
    if (!awaitHidden()) return PrewarmOutcome.STILL_VISIBLE
    return if (cloak.release()) PrewarmOutcome.DONE else PrewarmOutcome.STILL_CLOAKED
}

/**
 * Libera o cloak e só então mostra: a janela nunca aparece coberta. Liberação recusada é tentada de novo
 * até [RELEASE_ATTEMPTS] vezes; se nenhuma passar, mostra mesmo assim e devolve false.
 */
internal fun showUncloaked(cloak: ModalCloak?, show: () -> Unit): Boolean {
    val released = cloak == null || releaseWithRetries(cloak)
    show()
    return released
}

private fun releaseWithRetries(cloak: ModalCloak): Boolean {
    repeat(RELEASE_ATTEMPTS) {
        if (cloak.release()) return true
    }
    return false
}

/** Linha da trilha por desfecho: o sucesso mantém a linha de antes; cada falha nomeia o motivo. */
internal fun modalPrewarmOutcomeBreadcrumb(diagnosticName: String, outcome: PrewarmOutcome, elapsedMillis: Long): String =
    when (outcome) {
        PrewarmOutcome.DONE -> modalPrewarmedBreadcrumb(diagnosticName, elapsedMillis)
        PrewarmOutcome.REFUSED ->
            "janela $diagnosticName não concluiu o pré-aquecimento: o DWM recusou o cloak antes de mostrar"
        PrewarmOutcome.STILL_VISIBLE ->
            "janela $diagnosticName não concluiu o pré-aquecimento: a janela ainda estava visível depois de esconder"
        PrewarmOutcome.STILL_CLOAKED ->
            "janela $diagnosticName não concluiu o pré-aquecimento: o DWM recusou a liberação do cloak"
    }

/** Linha da trilha quando a janela abriu mesmo com o cloak recusado nas tentativas de liberação. */
internal fun modalUncloakRefusedBreadcrumb(diagnosticName: String): String =
    "janela $diagnosticName abriu com o cloak ativo: o DWM recusou a liberação em $RELEASE_ATTEMPTS tentativas"

/** Sonda [condition] a cada [stepMillis] até ela valer ou estourar [timeoutMillis]. Devolve se valeu. */
internal suspend fun awaitUntil(timeoutMillis: Long, stepMillis: Long, condition: () -> Boolean): Boolean =
    withTimeoutOrNull(timeoutMillis) {
        while (!condition()) delay(stepMillis)
        true
    } ?: false

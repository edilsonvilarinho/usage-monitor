package com.usagemonitor.presentation.viewmodel

import com.usagemonitor.domain.entity.TeamConnectionFailure
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val UNKNOWN_ACTION_ERROR_MESSAGE = "erro desconhecido"

/**
 * Erro da última ação de time (remover, excluir), com a etapa em que a conexão
 * parou (issue #494).
 *
 * A mensagem continua publicada como antes — é o detalhe técnico e o que a tela
 * mostra quando a falha não é classificada; [failure] é o que a tela mostra
 * quando é. A etapa é escrita **antes** da mensagem: quem coleta a mensagem já
 * encontra a etapa da mesma falha.
 */
internal class TeamActionErrorState {
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _failure = MutableStateFlow<TeamConnectionFailure?>(null)
    val failure: StateFlow<TeamConnectionFailure?> = _failure.asStateFlow()

    fun fail(error: Throwable) {
        _failure.value = teamConnectionFailureOf(error)
        _message.value = error.message ?: UNKNOWN_ACTION_ERROR_MESSAGE
    }

    fun clear() {
        _message.value = null
        _failure.value = null
    }
}

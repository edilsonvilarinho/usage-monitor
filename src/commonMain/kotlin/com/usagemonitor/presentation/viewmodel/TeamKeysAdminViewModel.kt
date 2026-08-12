package com.usagemonitor.presentation.viewmodel

import com.usagemonitor.domain.entity.TeamKeyEntry
import com.usagemonitor.domain.usecase.CreateTeamKeyUseCase
import com.usagemonitor.domain.usecase.ListTeamKeysUseCase
import com.usagemonitor.domain.usecase.RegenerateTeamKeyUseCase
import com.usagemonitor.domain.usecase.RevokeTeamKeyUseCase
import com.usagemonitor.domain.usecase.UnclaimTeamKeyAccountUseCase
import com.usagemonitor.domain.usecase.UpdateTeamKeyUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val UNKNOWN_ERROR_MESSAGE = "erro desconhecido"

sealed interface TeamKeysUiState {

    data object Loading : TeamKeysUiState

    data class Error(val message: String) : TeamKeysUiState

    data class Success(
        val keys: List<TeamKeyEntry> = emptyList(),
        /**
         * Falha da última ação, fora da lista.
         *
         * Uma ação que falha não pode apagar a lista que o admin está lendo — o
         * estado do servidor não mudou, e esconder tudo transformaria um erro
         * pontual numa tela vazia.
         */
        val actionError: String? = null,
        /** Ação em andamento; a tela desabilita os botões enquanto isso. */
        val busy: Boolean = false
    ) : TeamKeysUiState
}

/**
 * Estado da tela de chaves de time.
 *
 * Sem laço ao vivo, ao contrário de [TeamUsageViewModel]: aqui não há nada
 * mudando sozinho no servidor. A lista recarrega depois de cada ação, que é a
 * única prova de que o servidor aceitou o que foi pedido.
 */
class TeamKeysAdminViewModel(
    private val listKeys: ListTeamKeysUseCase,
    private val createKey: CreateTeamKeyUseCase,
    private val updateKey: UpdateTeamKeyUseCase,
    private val regenerateKey: RegenerateTeamKeyUseCase,
    private val revokeKey: RevokeTeamKeyUseCase,
    private val unclaimAccount: UnclaimTeamKeyAccountUseCase,
    dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val viewModelScope = CoroutineScope(SupervisorJob() + dispatcher)

    private var loadJob: Job? = null

    private val _uiState = MutableStateFlow<TeamKeysUiState>(TeamKeysUiState.Loading)
    val uiState: StateFlow<TeamKeysUiState> = _uiState.asStateFlow()

    /** Carrega do zero. Chamado na abertura da janela. */
    fun open() {
        _uiState.value = TeamKeysUiState.Loading
        refresh()
    }

    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch { load() }
    }

    fun onDestroy() {
        loadJob?.cancel()
        viewModelScope.cancel()
    }

    fun create(label: String, maxAccounts: Int) {
        if (label.isBlank()) {
            return
        }
        runAction { createKey(label = label, maxAccounts = maxAccounts) }
    }

    fun rename(id: String, label: String) {
        if (label.isBlank()) {
            return
        }
        runAction { updateKey(id = id, label = label) }
    }

    fun setMaxAccounts(id: String, maxAccounts: Int) {
        runAction { updateKey(id = id, maxAccounts = maxAccounts) }
    }

    fun regenerate(id: String) {
        runAction { regenerateKey(id) }
    }

    fun revoke(id: String) {
        runAction { revokeKey(id) }
    }

    fun unclaim(id: String, accountKey: String) {
        runAction { unclaimAccount(id = id, accountKey = accountKey) }
    }

    fun clearActionError() {
        val current = _uiState.value
        if (current is TeamKeysUiState.Success) {
            _uiState.value = current.copy(actionError = null)
        }
    }

    /**
     * Executa e recarrega.
     *
     * Recarregar em vez de aplicar o retorno na lista em memória: a listagem
     * seguinte é a única prova de que o servidor gravou. A chave regerada, por
     * exemplo, muda de valor — e mostrar a antiga por engano faria o admin
     * distribuir uma chave que já não vale.
     */
    private fun runAction(block: suspend () -> Result<TeamKeyEntry>) {
        val current = _uiState.value as? TeamKeysUiState.Success
        if (current?.busy == true) {
            return
        }
        if (current != null) {
            _uiState.value = current.copy(busy = true, actionError = null)
        }

        viewModelScope.launch {
            val error = block().exceptionOrNull()
            if (error != null) {
                val latest = _uiState.value as? TeamKeysUiState.Success
                if (latest == null) {
                    _uiState.value = TeamKeysUiState.Error(error.message ?: UNKNOWN_ERROR_MESSAGE)
                } else {
                    _uiState.value = latest.copy(
                        busy = false,
                        actionError = error.message ?: UNKNOWN_ERROR_MESSAGE
                    )
                }
                return@launch
            }
            load()
        }
    }

    private suspend fun load() {
        listKeys().fold(
            onSuccess = { keys ->
                _uiState.value = TeamKeysUiState.Success(keys = keys)
            },
            onFailure = { error ->
                val message = error.message ?: UNKNOWN_ERROR_MESSAGE
                val current = _uiState.value as? TeamKeysUiState.Success
                // Com lista na tela a falha vira aviso: o admin continua vendo as
                // chaves que já leu em vez de perder tudo por uma intermitência.
                _uiState.value = if (current == null) {
                    TeamKeysUiState.Error(message)
                } else {
                    current.copy(busy = false, actionError = message)
                }
            }
        )
    }
}

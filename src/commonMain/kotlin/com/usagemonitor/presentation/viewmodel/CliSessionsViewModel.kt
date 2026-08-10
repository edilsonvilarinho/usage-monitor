package com.usagemonitor.presentation.viewmodel

import com.usagemonitor.domain.usecase.GetCliSessionDetailUseCase
import com.usagemonitor.domain.usecase.GetCliSessionsUseCase
import com.usagemonitor.domain.usecase.SetCliSessionHiddenUseCase
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

/**
 * Estado da janela de Sessões CLI.
 *
 * A indexação corre no datasource, em `Dispatchers.IO`; aqui só se orquestra o
 * carregamento e se mantém o estado da lista e do detalhe.
 */
class CliSessionsViewModel(
    private val getCliSessions: GetCliSessionsUseCase,
    private val getCliSessionDetail: GetCliSessionDetailUseCase,
    private val setCliSessionHidden: SetCliSessionHiddenUseCase,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    autoLoad: Boolean = true
) {
    private val viewModelScope = CoroutineScope(SupervisorJob() + dispatcher)

    private var loadJob: Job? = null
    private var detailJob: Job? = null

    private val _uiState = MutableStateFlow<CliSessionsUiState>(CliSessionsUiState.Loading)
    val uiState: StateFlow<CliSessionsUiState> = _uiState.asStateFlow()

    private var showHidden: Boolean = false
    private var profileId: String? = null
    private var profileLabel: String? = null

    init {
        if (autoLoad) {
            refresh()
        }
    }

    /**
     * Aponta a janela para uma conta Anthropic. Os transcripts não carregam
     * identidade, então a conta vem da raiz de onde o arquivo foi lido — cada
     * perfil tem seu próprio config dir do Claude Code.
     */
    fun openForProfile(profileId: String, profileLabel: String?) {
        this.profileId = profileId
        this.profileLabel = profileLabel
        closeDetail()
        refresh()
    }

    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            loadSessions()
        }
    }

    fun toggleShowHidden() {
        showHidden = !showHidden
        refresh()
    }

    fun openSession(sessionId: String) {
        val current = _uiState.value
        if (current !is CliSessionsUiState.Success) {
            return
        }

        _uiState.value = current.copy(detail = CliSessionDetailUiState.Loading(sessionId))

        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            val result = getCliSessionDetail(sessionId)
            val detailState = result.fold(
                onSuccess = { loaded ->
                    if (loaded == null) {
                        CliSessionDetailUiState.Error(sessionId, "Sessão não encontrada no índice.")
                    } else {
                        CliSessionDetailUiState.Ready(sessionId, loaded)
                    }
                },
                onFailure = { error ->
                    CliSessionDetailUiState.Error(sessionId, error.message ?: UNKNOWN_ERROR_MESSAGE)
                }
            )
            publishDetail(sessionId, detailState)
        }
    }

    fun closeDetail() {
        detailJob?.cancel()
        val current = _uiState.value
        if (current is CliSessionsUiState.Success) {
            _uiState.value = current.copy(detail = null)
        }
    }

    /** Oculta ou reexibe a sessão e recarrega a lista. O transcript não é tocado. */
    fun setSessionHidden(sessionId: String, hidden: Boolean) {
        viewModelScope.launch {
            setCliSessionHidden(sessionId, hidden)
                .onFailure { error ->
                    _uiState.value = CliSessionsUiState.Error(
                        message = error.message ?: UNKNOWN_ERROR_MESSAGE,
                        showHidden = showHidden,
                        profileLabel = profileLabel
                    )
                    return@launch
                }
            loadSessions()
        }
    }

    fun onDestroy() {
        loadJob?.cancel()
        detailJob?.cancel()
        viewModelScope.cancel()
    }

    private suspend fun loadSessions() {
        val previousDetail = (_uiState.value as? CliSessionsUiState.Success)?.detail

        getCliSessions(profileId = profileId, includeHidden = showHidden).fold(
            onSuccess = { result ->
                _uiState.value = CliSessionsUiState.Success(
                    sessions = result.sessions,
                    showHidden = showHidden,
                    profileLabel = profileLabel,
                    indexWarning = result.indexError?.message,
                    detail = previousDetail?.takeIf { detail ->
                        result.sessions.any { session -> session.sessionId == detail.sessionId }
                    }
                )
            },
            onFailure = { error ->
                _uiState.value = CliSessionsUiState.Error(
                    message = error.message ?: UNKNOWN_ERROR_MESSAGE,
                    showHidden = showHidden,
                    profileLabel = profileLabel
                )
            }
        )
    }

    /** Descarta o resultado se o usuário já voltou à lista ou abriu outra sessão. */
    private fun publishDetail(sessionId: String, detailState: CliSessionDetailUiState) {
        val current = _uiState.value
        if (current !is CliSessionsUiState.Success) {
            return
        }
        if (current.detail?.sessionId != sessionId) {
            return
        }
        _uiState.value = current.copy(detail = detailState)
    }
}

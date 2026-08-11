package com.usagemonitor.presentation.viewmodel

import com.usagemonitor.domain.entity.CliQuotaWindows
import com.usagemonitor.domain.entity.CliSessionRange
import com.usagemonitor.domain.usecase.GetCliSessionDetailUseCase
import com.usagemonitor.domain.usecase.GetCliSessionsUseCase
import com.usagemonitor.domain.usecase.SyncCliSessionIndexUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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
 *
 * [backgroundIndexIntervalMillis] liga a indexação periódica: o Claude Code
 * apaga transcripts antigos, então o índice não pode depender de o usuário abrir
 * a tela. `null` desliga o laço.
 */
class CliSessionsViewModel(
    private val getCliSessions: GetCliSessionsUseCase,
    private val getCliSessionDetail: GetCliSessionDetailUseCase,
    private val syncCliSessionIndex: SyncCliSessionIndexUseCase,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    autoLoad: Boolean = true,
    private val backgroundIndexIntervalMillis: Long? = null
) {
    private val viewModelScope = CoroutineScope(SupervisorJob() + dispatcher)

    private var loadJob: Job? = null
    private var detailJob: Job? = null

    private val _uiState = MutableStateFlow<CliSessionsUiState>(CliSessionsUiState.Loading)
    val uiState: StateFlow<CliSessionsUiState> = _uiState.asStateFlow()

    private var range: CliSessionRange = CliSessionRange.DEFAULT
    private var quotaWindows: CliQuotaWindows = CliQuotaWindows()
    private var profileId: String? = null
    private var profileLabel: String? = null

    init {
        if (autoLoad) {
            refresh()
        }
        if (backgroundIndexIntervalMillis != null) {
            startBackgroundIndexLoop(backgroundIndexIntervalMillis)
        }
    }

    /**
     * Aponta a janela para uma conta Anthropic. Os transcripts não carregam
     * identidade, então a conta vem da raiz de onde o arquivo foi lido — cada
     * perfil tem seu próprio config dir do Claude Code.
     */
    fun openForProfile(
        profileId: String,
        profileLabel: String?,
        quotaWindows: CliQuotaWindows = CliQuotaWindows()
    ) {
        this.profileId = profileId
        this.profileLabel = profileLabel
        this.quotaWindows = quotaWindows
        closeDetail()
        refresh()
    }

    /**
     * Atualiza os resets de quota vindos do dashboard.
     *
     * Só recarrega quando o corte realmente muda: o dashboard reemite o mesmo
     * `resets_at` a cada coleta, e recarregar em toda emissão faria a lista
     * piscar de dez em dez minutos.
     */
    fun setQuotaWindows(quotaWindows: CliQuotaWindows) {
        if (quotaWindows == this.quotaWindows) {
            return
        }
        this.quotaWindows = quotaWindows
        if (range == CliSessionRange.LAST_5H || range == CliSessionRange.LAST_7D) {
            refresh()
        }
    }

    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            loadSessions()
        }
    }

    /** Troca a janela temporal e recarrega a lista reagregada. */
    fun setRange(range: CliSessionRange) {
        this.range = range
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

    fun onDestroy() {
        loadJob?.cancel()
        detailJob?.cancel()
        viewModelScope.cancel()
    }

    /**
     * Indexa no arranque e a cada intervalo. A lista só é recarregada quando está
     * visível: sem janela aberta basta o índice estar em dia, e com o detalhe
     * aberto um recarregamento poderia arrancar da tela a sessão que saiu da
     * janela temporal enquanto o usuário a lia.
     */
    private fun startBackgroundIndexLoop(intervalMillis: Long) {
        viewModelScope.launch {
            syncCliSessionIndex()
            while (true) {
                delay(intervalMillis)
                syncCliSessionIndex()
                val current = _uiState.value
                if (current is CliSessionsUiState.Success && current.detail == null) {
                    loadSessions()
                }
            }
        }
    }

    private suspend fun loadSessions() {
        val previousDetail = (_uiState.value as? CliSessionsUiState.Success)?.detail

        getCliSessions(profileId = profileId, range = range, windows = quotaWindows).fold(
            onSuccess = { result ->
                _uiState.value = CliSessionsUiState.Success(
                    sessions = result.sessions,
                    range = range,
                    rangeEndsAt = result.window.endsAt,
                    rangeAnchored = result.window.isAnchored,
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
                    range = range,
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

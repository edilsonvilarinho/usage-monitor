package com.usagemonitor.presentation.viewmodel

import com.usagemonitor.domain.entity.CliQuotaWindows
import com.usagemonitor.domain.entity.CliSessionRange
import com.usagemonitor.domain.usecase.GetTeamUsageUseCase
import com.usagemonitor.domain.usecase.RemoveTeamMemberUseCase
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

private const val UNKNOWN_ERROR_MESSAGE = "erro desconhecido"

/**
 * Estado da janela de Sessões do time.
 *
 * Espelha [CliSessionsViewModel] de propósito: as duas telas têm a mesma
 * anatomia e a mesma promessa de tempo real, então o comportamento tem de ser o
 * mesmo. Só o laço ao vivo existe aqui — não há índice local para manter em dia,
 * quem empurra os dados é o `TeamSyncService`, que roda independente desta
 * janela.
 *
 * O [liveIntervalMillis] fecha o ciclo pelo lado da leitura; a latência real com
 * que uma máquina aparece para as outras é dominada pelo intervalo de envio
 * daquela máquina, não por este.
 */
class TeamUsageViewModel(
    private val getTeamUsage: GetTeamUsageUseCase,
    private val removeTeamMember: RemoveTeamMemberUseCase,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val liveIntervalMillis: Long = DEFAULT_LIVE_INTERVAL_MILLIS,
    private val clock: Clock = Clock.System
) {
    private val viewModelScope = CoroutineScope(SupervisorJob() + dispatcher)

    private var loadJob: Job? = null
    private var liveJob: Job? = null

    /** Evita empilhar consultas quando a rede está mais lenta que o intervalo. */
    private val loadMutex = Mutex()

    private val _uiState = MutableStateFlow<TeamUsageUiState>(TeamUsageUiState.Loading)
    val uiState: StateFlow<TeamUsageUiState> = _uiState.asStateFlow()

    /**
     * Erro da última tentativa de remoção, fora do [uiState].
     *
     * O laço ao vivo reescreve o `uiState` a cada 5s e apagaria a mensagem antes
     * de o usuário ler.
     */
    private val _removalError = MutableStateFlow<String?>(null)
    val removalError: StateFlow<String?> = _removalError.asStateFlow()

    private var range: CliSessionRange = CliSessionRange.DEFAULT
    private var quotaWindows: CliQuotaWindows = CliQuotaWindows()
    private var accountKey: String? = null
    private var accountLabel: String? = null

    /** Aponta a janela para uma conta Anthropic e liga o tempo real. */
    fun openForAccount(
        accountKey: String,
        accountLabel: String?,
        quotaWindows: CliQuotaWindows = CliQuotaWindows()
    ) {
        val accountChanged = this.accountKey != accountKey
        this.accountKey = accountKey
        this.accountLabel = accountLabel
        this.quotaWindows = quotaWindows

        // Trocar de conta zera a lista: manter os integrantes da conta anterior
        // na tela enquanto a nova carrega mostraria dados de outro time.
        if (accountChanged) {
            _uiState.value = TeamUsageUiState.Loading
        }

        refresh()
        startLiveLoop()
    }

    fun closeWindow() {
        liveJob?.cancel()
        liveJob = null
    }

    /**
     * Atualiza os resets de quota vindos do dashboard.
     *
     * Só recarrega quando o corte muda de fato: o dashboard reemite o mesmo
     * `resets_at` a cada coleta, e recarregar em toda emissão faria a lista
     * piscar de dez em dez minutos.
     */
    fun setQuotaWindows(quotaWindows: CliQuotaWindows) {
        if (quotaWindows == this.quotaWindows) {
            return
        }
        this.quotaWindows = quotaWindows
        if (range == CliSessionRange.LAST_5H) {
            refresh()
        }
    }

    fun setRange(range: CliSessionRange) {
        this.range = range
        refresh()
    }

    /** Abre ou fecha as sessões de um integrante. */
    fun toggleMember(deviceId: String) {
        val current = _uiState.value
        if (current !is TeamUsageUiState.Success) {
            return
        }

        val expanded = current.expandedDeviceIds
        _uiState.value = current.copy(
            expandedDeviceIds = if (deviceId in expanded) expanded - deviceId else expanded + deviceId
        )
    }

    /**
     * Apaga um integrante no servidor e recarrega a lista.
     *
     * Recarregar em vez de remover da lista em memória: a resposta seguinte é a
     * única prova de que o servidor apagou de fato. A falha vira aviso na tela e
     * a lista fica intacta — não dá para mostrar como removido o que continua lá.
     */
    fun removeMember(deviceId: String) {
        val targetAccountKey = accountKey ?: return

        viewModelScope.launch {
            val result = removeTeamMember(accountKey = targetAccountKey, deviceId = deviceId)
            val error = result.exceptionOrNull()
            if (error != null) {
                _removalError.value = error.message ?: UNKNOWN_ERROR_MESSAGE
                return@launch
            }
            _removalError.value = null
            loadTeam()
        }
    }

    fun clearRemovalError() {
        _removalError.value = null
    }

    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            loadTeam()
        }
    }

    fun onDestroy() {
        loadJob?.cancel()
        liveJob?.cancel()
        viewModelScope.cancel()
    }

    /**
     * Tempo real com a janela aberta.
     *
     * `loadTeam` nunca passa por `Loading` e o estado é um `data class`, então um
     * tique sem novidade produz um valor igual ao anterior — o `StateFlow` não
     * reemite e a tela não recompõe.
     */
    private fun startLiveLoop() {
        if (liveJob?.isActive == true) {
            return
        }
        liveJob = viewModelScope.launch {
            while (true) {
                delay(liveIntervalMillis)
                loadTeam()
            }
        }
    }

    private suspend fun loadTeam() {
        val targetAccountKey = accountKey ?: return

        loadMutex.withLock {
            val current = _uiState.value as? TeamUsageUiState.Success

            getTeamUsage(accountKey = targetAccountKey, range = range, windows = quotaWindows).fold(
                onSuccess = { result ->
                    val members = result.snapshot.members
                    val contentChanged = current == null || current.members != members

                    _uiState.value = TeamUsageUiState.Success(
                        members = members,
                        range = range,
                        rangeEndsAt = result.window.endsAt,
                        rangeAnchored = result.window.isAnchored,
                        accountLabel = accountLabel,
                        // Estado de UI, não do servidor: sem carregá-lo daqui os
                        // grupos abertos se fechariam sozinhos a cada tique.
                        // Integrantes que sumiram da resposta saem do conjunto.
                        expandedDeviceIds = current?.expandedDeviceIds
                            ?.filterTo(mutableSetOf()) { deviceId ->
                                members.any { member -> member.deviceId == deviceId }
                            }
                            ?: emptySet(),
                        // Carimbo só anda quando o conteúdo muda. Marcá-lo a cada
                        // tique quebraria a igualdade do estado e recomporia a tela.
                        lastChangedAt = if (contentChanged) clock.now() else current?.lastChangedAt
                    )
                },
                onFailure = { error ->
                    // Falha intermitente com dados na tela não apaga o que o
                    // usuário está lendo: o erro vira aviso e a lista fica.
                    if (current != null) {
                        return@fold
                    }
                    _uiState.value = TeamUsageUiState.Error(
                        message = error.message ?: UNKNOWN_ERROR_MESSAGE,
                        range = range,
                        accountLabel = accountLabel
                    )
                }
            )
        }
    }

    companion object {
        /** Mesma cadência do modal de sessões da máquina. */
        const val DEFAULT_LIVE_INTERVAL_MILLIS = 5_000L
    }
}

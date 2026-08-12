package com.usagemonitor.presentation.viewmodel

import com.usagemonitor.domain.entity.CliQuotaWindows
import com.usagemonitor.domain.entity.CliSessionDetail
import com.usagemonitor.domain.entity.CliSessionRange
import com.usagemonitor.domain.entity.CliSessionSummary
import com.usagemonitor.domain.usecase.CliSessionDetailResult
import com.usagemonitor.domain.usecase.ComputeCliSessionAnalyticsUseCase
import com.usagemonitor.domain.usecase.GetTeamSessionDetailUseCase
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

private const val SESSION_GONE_MESSAGE =
    "Sessão não encontrada no servidor de time."

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
    private val getTeamSessionDetail: GetTeamSessionDetailUseCase,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val liveIntervalMillis: Long = DEFAULT_LIVE_INTERVAL_MILLIS,
    private val clock: Clock = Clock.System,
    private val computeAnalytics: ComputeCliSessionAnalyticsUseCase = ComputeCliSessionAnalyticsUseCase()
) {
    private val viewModelScope = CoroutineScope(SupervisorJob() + dispatcher)

    private var loadJob: Job? = null
    private var detailJob: Job? = null
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
        closeDetail()
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

    /**
     * Abre o detalhe de uma sessão de outra máquina.
     *
     * O `deviceId` acompanha o `sessionId` porque a leitura no servidor é
     * escopada por `(conta, máquina)` — o id da sessão sozinho não identifica o
     * dono dela.
     */
    fun openSession(deviceId: String, sessionId: String) {
        val current = _uiState.value
        if (current !is TeamUsageUiState.Success) {
            return
        }
        val targetAccountKey = accountKey ?: return

        _uiState.value = current.copy(
            detail = TeamSessionDetailUiState.Loading(deviceId = deviceId, sessionId = sessionId)
        )

        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            val loaded = loadDetailState(
                accountKey = targetAccountKey,
                deviceId = deviceId,
                sessionId = sessionId
            )
            publishDetail(deviceId = deviceId, sessionId = sessionId, detailState = loaded)
        }
    }

    fun closeDetail() {
        detailJob?.cancel()
        val current = _uiState.value
        if (current is TeamUsageUiState.Success) {
            _uiState.value = current.copy(detail = null)
        }
    }

    /**
     * Abre ou fecha o bloco Avançado do detalhe.
     *
     * A escolha vale para a janela toda, não para a sessão aberta — mesmo
     * comportamento do modal da própria máquina.
     */
    fun toggleAdvanced() {
        val current = _uiState.value
        if (current is TeamUsageUiState.Success) {
            _uiState.value = current.copy(advancedExpanded = !current.advancedExpanded)
        }
    }

    /** Abre ou fecha o painel "Como ler esta tela". */
    fun toggleGlossary() {
        val current = _uiState.value
        if (current is TeamUsageUiState.Success) {
            _uiState.value = current.copy(glossaryExpanded = !current.glossaryExpanded)
        }
    }

    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            loadTeam()
        }
    }

    fun onDestroy() {
        loadJob?.cancel()
        detailJob?.cancel()
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
                reloadOpenDetail()
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
                        lastChangedAt = if (contentChanged) clock.now() else current?.lastChangedAt,
                        // O detalhe é sempre a sessão inteira, então o recorte da
                        // lista não se aplica a ele: sem carregá-lo daqui, o tique
                        // de 5s fecharia o painel na cara de quem está lendo.
                        detail = current?.detail,
                        advancedExpanded = current?.advancedExpanded ?: false,
                        glossaryExpanded = current?.glossaryExpanded ?: false
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

    /**
     * Recarrega o detalhe aberto no lugar. Nunca volta para `Loading` e mantém o
     * último resultado bom se a leitura falhar: no tempo real o usuário está
     * lendo a tela, não esperando por ela.
     */
    private suspend fun reloadOpenDetail() {
        val current = _uiState.value as? TeamUsageUiState.Success ?: return
        val open = current.detail as? TeamSessionDetailUiState.Ready ?: return
        val targetAccountKey = accountKey ?: return

        val reloaded = loadDetailState(
            accountKey = targetAccountKey,
            deviceId = open.deviceId,
            sessionId = open.sessionId
        )
        if (reloaded is TeamSessionDetailUiState.Ready) {
            publishDetail(deviceId = open.deviceId, sessionId = open.sessionId, detailState = reloaded)
        }
    }

    private suspend fun loadDetailState(
        accountKey: String,
        deviceId: String,
        sessionId: String
    ): TeamSessionDetailUiState {
        return getTeamSessionDetail(
            accountKey = accountKey,
            deviceId = deviceId,
            sessionId = sessionId
        ).fold(
            onSuccess = { loaded ->
                if (loaded == null) {
                    // Servidor sem a rota de detalhe, ou sessão fora da retenção.
                    aggregatedDetail(deviceId, sessionId)
                } else {
                    TeamSessionDetailUiState.Ready(
                        deviceId = deviceId,
                        sessionId = sessionId,
                        result = loaded
                    )
                }
            },
            onFailure = { error ->
                TeamSessionDetailUiState.Error(
                    deviceId = deviceId,
                    sessionId = sessionId,
                    message = error.message ?: UNKNOWN_ERROR_MESSAGE
                )
            }
        )
    }

    /**
     * Detalhe possível sem os turnos: só o que o agregado da lista já prova.
     *
     * É o que a tela mostra contra um servidor anterior à rota `/v1/session`.
     * Sem turno não há série nem distribuição de custo, e a tela deixa isso
     * explícito em vez de desenhar gráfico vazio. Só vira erro quando nem o
     * agregado existe — aí não há nada a apresentar.
     */
    private fun aggregatedDetail(deviceId: String, sessionId: String): TeamSessionDetailUiState {
        val summary = findSessionSummary(deviceId, sessionId)
            ?: return TeamSessionDetailUiState.Error(
                deviceId = deviceId,
                sessionId = sessionId,
                message = SESSION_GONE_MESSAGE
            )

        return TeamSessionDetailUiState.Ready(
            deviceId = deviceId,
            sessionId = sessionId,
            result = CliSessionDetailResult(
                detail = CliSessionDetail(summary = summary, turns = emptyList()),
                analytics = computeAnalytics.fromSummary(summary)
            ),
            turnsUnavailable = true
        )
    }

    private fun findSessionSummary(deviceId: String, sessionId: String): CliSessionSummary? {
        val current = _uiState.value as? TeamUsageUiState.Success ?: return null
        val member = current.members.firstOrNull { entry -> entry.deviceId == deviceId } ?: return null
        return member.sessions.firstOrNull { session -> session.sessionId == sessionId }
    }

    /** Descarta o resultado se o usuário já voltou à lista ou abriu outra sessão. */
    private fun publishDetail(
        deviceId: String,
        sessionId: String,
        detailState: TeamSessionDetailUiState
    ) {
        val current = _uiState.value
        if (current !is TeamUsageUiState.Success) {
            return
        }
        val open = current.detail
        if (open == null || open.deviceId != deviceId || open.sessionId != sessionId) {
            return
        }
        _uiState.value = current.copy(detail = detailState)
    }

    companion object {
        /** Mesma cadência do modal de sessões da máquina. */
        const val DEFAULT_LIVE_INTERVAL_MILLIS = 5_000L
    }
}

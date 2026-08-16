package com.usagemonitor.presentation.viewmodel

import com.usagemonitor.domain.entity.CliQuotaWindows
import com.usagemonitor.domain.entity.CliSessionRange
import com.usagemonitor.domain.usecase.GetCliSessionDetailUseCase
import com.usagemonitor.domain.usecase.GetCliSessionsUseCase
import com.usagemonitor.domain.usecase.GetCliUsageBreakdownUseCase
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

private const val UNKNOWN_ERROR_MESSAGE = "erro desconhecido"

/**
 * Estado da janela de Sessões CLI.
 *
 * A indexação corre no datasource, em `Dispatchers.IO`; aqui só se orquestra o
 * carregamento e se mantém o estado da lista e do detalhe.
 *
 * São dois laços independentes:
 *
 * - [backgroundIndexIntervalMillis] mantém o índice em dia com a janela fechada.
 *   O Claude Code apaga transcripts antigos, então o índice não pode depender de
 *   o usuário abrir a tela. Esse laço nunca toca no estado da UI. `null` desliga.
 * - [liveIntervalMillis] é o tempo real: enquanto a janela está aberta, reindexa
 *   e recarrega lista e detalhe. Começa em [openForProfile] e para em
 *   [closeWindow].
 */
class CliSessionsViewModel(
    private val getCliSessions: GetCliSessionsUseCase,
    private val getCliSessionDetail: GetCliSessionDetailUseCase,
    private val syncCliSessionIndex: SyncCliSessionIndexUseCase,
    /**
     * Resumo por eixo. `null` esconde a aba — instalação sem ele continua
     * funcionando, mesmo tratamento dos recursos opcionais do time.
     */
    private val getCliUsageBreakdown: GetCliUsageBreakdownUseCase? = null,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    autoLoad: Boolean = true,
    private val backgroundIndexIntervalMillis: Long? = null,
    private val liveIntervalMillis: Long = DEFAULT_LIVE_INTERVAL_MILLIS,
    private val clock: Clock = Clock.System
) {
    private val viewModelScope = CoroutineScope(SupervisorJob() + dispatcher)

    private var loadJob: Job? = null
    private var detailJob: Job? = null
    private var liveJob: Job? = null
    private var breakdownJob: Job? = null

    /**
     * O laço de background e o ao vivo podem cair no mesmo instante. A conexão do
     * datasource já serializa por dentro; o mutex evita empilhar uma segunda
     * varredura esperando pela primeira.
     */
    private val syncMutex = Mutex()

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
        startLiveLoop()
    }

    /** Janela fechada: o laço ao vivo para e o detalhe aberto é descartado. */
    fun closeWindow() {
        liveJob?.cancel()
        liveJob = null
        closeDetail()
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
            refreshBreakdownIfVisible()
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
        // O resumo descreve a mesma janela: deixá-lo para trás mostraria dois
        // recortes diferentes na mesma tela.
        refreshBreakdownIfVisible()
    }

    /**
     * Troca de aba.
     *
     * Ao abrir o resumo, carrega-o na hora: o laço ao vivo levaria segundos e a
     * aba abriria vazia. A escolha vale para a janela toda, como os blocos
     * recolhíveis do detalhe.
     */
    fun setView(view: CliSessionsView) {
        val current = _uiState.value
        if (current !is CliSessionsUiState.Success || current.view == view) {
            return
        }
        _uiState.value = current.copy(view = view)
        if (view == CliSessionsView.BREAKDOWN) {
            startBreakdownLoad()
        }
    }

    private fun refreshBreakdownIfVisible() {
        if ((_uiState.value as? CliSessionsUiState.Success)?.view == CliSessionsView.BREAKDOWN) {
            startBreakdownLoad()
        }
    }

    private fun startBreakdownLoad() {
        breakdownJob?.cancel()
        breakdownJob = viewModelScope.launch {
            loadBreakdown()
        }
    }

    fun openSession(sessionId: String) {
        val current = _uiState.value
        if (current !is CliSessionsUiState.Success) {
            return
        }

        _uiState.value = current.copy(detail = CliSessionDetailUiState.Loading(sessionId))

        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            publishDetail(sessionId, loadDetailState(sessionId))
        }
    }

    fun closeDetail() {
        detailJob?.cancel()
        val current = _uiState.value
        if (current is CliSessionsUiState.Success) {
            _uiState.value = current.copy(detail = null)
        }
    }

    /**
     * Abre ou fecha o bloco Avançado do detalhe.
     *
     * A escolha vale para a janela toda, não para a sessão aberta: quem quer ver
     * os gráficos de uma sessão quer vê-los na próxima também.
     */
    fun toggleAdvanced() {
        val current = _uiState.value
        if (current is CliSessionsUiState.Success) {
            _uiState.value = current.copy(advancedExpanded = !current.advancedExpanded)
        }
    }

    /** Abre ou fecha o painel "Como ler esta tela". */
    fun toggleGlossary() {
        val current = _uiState.value
        if (current is CliSessionsUiState.Success) {
            _uiState.value = current.copy(glossaryExpanded = !current.glossaryExpanded)
        }
    }

    fun onDestroy() {
        loadJob?.cancel()
        detailJob?.cancel()
        liveJob?.cancel()
        breakdownJob?.cancel()
        viewModelScope.cancel()
    }

    /**
     * Recarrega o resumo no lugar.
     *
     * Falha **mantém** o resumo anterior e publica só a mensagem: apagar os
     * números por causa de uma leitura ruim tiraria da tela o que o usuário está
     * lendo. `internal` para o teste dispensar o laço.
     */
    internal suspend fun loadBreakdown() {
        val useCase = getCliUsageBreakdown ?: return
        if (_uiState.value !is CliSessionsUiState.Success) {
            return
        }

        useCase(profileId = profileId, range = range, windows = quotaWindows).fold(
            onSuccess = { breakdown ->
                val latest = _uiState.value as? CliSessionsUiState.Success ?: return
                _uiState.value = latest.copy(breakdown = breakdown, breakdownError = null)
            },
            onFailure = { error ->
                val latest = _uiState.value as? CliSessionsUiState.Success ?: return
                _uiState.value = latest.copy(breakdownError = error.message ?: UNKNOWN_ERROR_MESSAGE)
            }
        )
    }

    /**
     * Indexa no arranque e a cada intervalo, sem tocar na UI. Existe para que o
     * índice sobreviva à retenção do Claude Code mesmo com a janela fechada.
     */
    private fun startBackgroundIndexLoop(intervalMillis: Long) {
        viewModelScope.launch {
            syncIndexOnce()
            while (true) {
                delay(intervalMillis)
                syncIndexOnce()
            }
        }
    }

    /**
     * Tempo real com a janela aberta: reindexa e reemite lista e detalhe.
     *
     * `loadSessions` nunca passa por `Loading` e o estado é um `data class`, então
     * um tique sem novidade produz um valor igual ao anterior — o `StateFlow` não
     * reemite e a tela não recompõe.
     */
    private fun startLiveLoop() {
        if (liveJob?.isActive == true) {
            return
        }
        liveJob = viewModelScope.launch {
            while (true) {
                delay(liveIntervalMillis)
                syncIndexOnce()
                loadSessions()
                reloadOpenDetail()
                // Só com a aba aberta: recalcular o resumo que ninguém está
                // vendo custaria um `GROUP BY` sobre a tabela de turnos a cada
                // cinco segundos.
                if ((_uiState.value as? CliSessionsUiState.Success)?.view == CliSessionsView.BREAKDOWN) {
                    loadBreakdown()
                }
            }
        }
    }

    private suspend fun syncIndexOnce() {
        syncMutex.withLock {
            syncCliSessionIndex()
        }
    }

    private suspend fun loadSessions() {
        val current = _uiState.value as? CliSessionsUiState.Success

        getCliSessions(profileId = profileId, range = range, windows = quotaWindows).fold(
            onSuccess = { result ->
                val contentChanged = current == null ||
                    current.sessions != result.sessions ||
                    current.indexWarning != result.indexError?.message

                _uiState.value = CliSessionsUiState.Success(
                    sessions = result.sessions,
                    range = range,
                    rangeEndsAt = result.window.endsAt,
                    rangeAnchored = result.window.isAnchored,
                    profileLabel = profileLabel,
                    indexWarning = result.indexError?.message,
                    // O detalhe é sempre a sessão inteira, então o recorte da lista
                    // não se aplica a ele: arrancá-lo da tela porque a sessão saiu
                    // da janela seria arrancar o usuário do que ele está lendo.
                    detail = current?.detail,
                    // Carimbo só anda quando o conteúdo muda. Marcar cada tique
                    // quebraria a igualdade do estado e recomporia a tela à toa.
                    lastChangedAt = if (contentChanged) clock.now() else current?.lastChangedAt,
                    // Estado da UI, não do índice: sem carregá-lo daqui os blocos
                    // recolhíveis se fechariam sozinhos a cada tique do laço ao vivo.
                    advancedExpanded = current?.advancedExpanded ?: false,
                    glossaryExpanded = current?.glossaryExpanded ?: false,
                    // Mesma razão: sem carregar a aba e o resumo daqui, a tela
                    // voltaria para a lista sozinha a cada tique.
                    view = current?.view ?: CliSessionsView.SESSIONS,
                    breakdown = current?.breakdown,
                    breakdownError = current?.breakdownError
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

    /**
     * Recarrega o detalhe aberto no lugar. Nunca volta para `Loading` e mantém o
     * último resultado bom se a leitura falhar: no tempo real o usuário está
     * lendo a tela, não esperando por ela.
     */
    private suspend fun reloadOpenDetail() {
        val current = _uiState.value as? CliSessionsUiState.Success ?: return
        val sessionId = (current.detail as? CliSessionDetailUiState.Ready)?.sessionId ?: return

        val reloaded = loadDetailState(sessionId)
        if (reloaded is CliSessionDetailUiState.Ready) {
            publishDetail(sessionId, reloaded)
        }
    }

    private suspend fun loadDetailState(sessionId: String): CliSessionDetailUiState {
        return getCliSessionDetail(sessionId).fold(
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

    companion object {
        /** Cadência do tempo real. Uma passada custa um `walk` de diretório e um `SELECT`. */
        const val DEFAULT_LIVE_INTERVAL_MILLIS = 5_000L
    }
}

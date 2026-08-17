package com.usagemonitor.presentation.viewmodel

import com.usagemonitor.data.export.UsageExportFormat
import com.usagemonitor.domain.entity.AccountCreditUsage
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CliQuotaWindows
import com.usagemonitor.domain.entity.CliSessionRange
import com.usagemonitor.domain.usecase.GetCliSessionDetailUseCase
import com.usagemonitor.domain.usecase.GetCliSessionsUseCase
import com.usagemonitor.domain.usecase.GetCliUsageBreakdownUseCase
import com.usagemonitor.domain.usecase.GetMonthlyBudgetStatusUseCase
import com.usagemonitor.domain.usecase.SyncCliSessionIndexUseCase
import com.usagemonitor.presentation.ui.UsageExportRequest
import com.usagemonitor.presentation.ui.exportRequestForBreakdown
import com.usagemonitor.presentation.ui.exportRequestForSessions
import com.usagemonitor.presentation.ui.report.reportForCliSessions
import com.usagemonitor.presentation.ui.reportRequest
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
    /** Destino da exportação. `null` = sem exportação, como nos recursos opcionais. */
    private val exportWriter: UsageExportWriter? = null,
    /** Orçamento mensal. `null` esconde o cartão. */
    private val getMonthlyBudgetStatus: GetMonthlyBudgetStatusUseCase? = null,
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
    private var exportJob: Job? = null
    private var budgetJob: Job? = null

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
    private var budgetLimitMicros: Long = 0L

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
        // O usuário clicou e espera outra resposta. Sem esta marca a tela segura
        // os números da janela anterior durante toda a varredura, sem nada
        // dizendo que eles ainda são os antigos.
        val current = _uiState.value
        if (current is CliSessionsUiState.Success && !current.isRefreshing) {
            _uiState.value = current.copy(isRefreshing = true)
        }
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
            startBudgetLoad()
        }
    }

    /**
     * Exporta o que a aba aberta mostra, na janela escolhida.
     *
     * Exportar um recorte diferente do que está na tela seria surpresa, então o
     * conteúdo sai do próprio estado — nenhuma leitura nova.
     */
    fun exportCurrentView(format: UsageExportFormat) {
        val writer = exportWriter ?: return
        val current = _uiState.value as? CliSessionsUiState.Success ?: return

        val request = when (current.view) {
            CliSessionsView.SESSIONS -> exportRequestForSessions(
                sessions = current.sessions,
                range = current.range,
                format = format,
                now = clock.now()
            )
            CliSessionsView.BREAKDOWN -> {
                val breakdown = current.breakdown ?: return
                exportRequestForBreakdown(
                    breakdown = breakdown,
                    range = current.range,
                    format = format,
                    now = clock.now()
                )
            }
        }

        publishExport(writer, request)
    }

    /**
     * Relatório PDF do recorte que está na tela.
     *
     * Ao contrário de [exportCurrentView], **carrega o resumo por eixo se ele
     * ainda não foi lido**: um relatório sem a seção de projetos e sem a grade de
     * atividade seria uma surpresa pior que a espera, e quem exporta da aba de
     * sessões não tem por que saber que a outra aba precisava ter sido aberta.
     */
    fun exportReport(language: AppLanguage) {
        val writer = exportWriter ?: return
        if (_uiState.value !is CliSessionsUiState.Success) {
            return
        }

        exportJob?.cancel()
        exportJob = viewModelScope.launch {
            if ((_uiState.value as? CliSessionsUiState.Success)?.breakdown == null) {
                loadBreakdown()
            }
            val current = _uiState.value as? CliSessionsUiState.Success ?: return@launch

            val now = clock.now()
            val request = reportRequest(
                document = reportForCliSessions(state = current, language = language, now = now),
                range = current.range,
                now = now
            )
            writeExport(writer, request)
        }
    }

    private fun publishExport(writer: UsageExportWriter, request: UsageExportRequest) {
        exportJob?.cancel()
        exportJob = viewModelScope.launch {
            writeExport(writer, request)
        }
    }

    private suspend fun writeExport(writer: UsageExportWriter, request: UsageExportRequest) {
        val outcome = runCatching { writer.write(request) }.fold(
            // Cancelar o diálogo devolve `null` e não publica resultado: não é
            // sucesso nem erro, e anunciá-lo seria ruído.
            onSuccess = { path -> path?.let { saved -> CliExportOutcome.Saved(saved) } },
            onFailure = { error -> CliExportOutcome.Failed(error.message ?: UNKNOWN_ERROR_MESSAGE) }
        ) ?: return

        val latest = _uiState.value as? CliSessionsUiState.Success ?: return
        _uiState.value = latest.copy(exportOutcome = outcome)
    }

    /**
     * Teto mensal em micros de USD. Zero desliga.
     *
     * Recarrega só quando o valor muda: as Configurações reemitem o mesmo teto a
     * cada recomposição e recalcular em toda emissão seria uma leitura do índice
     * por tecla digitada.
     */
    fun setBudgetLimitMicros(limitMicros: Long) {
        if (limitMicros == budgetLimitMicros) {
            return
        }
        budgetLimitMicros = limitMicros
        startBudgetLoad()
    }

    /**
     * Créditos de uso da conta, vindos do dashboard.
     *
     * Não são lidos aqui porque a origem é a API da Anthropic, e esta janela só
     * conhece o índice local. Chegam prontos, com a moeda da conta.
     */
    fun setAccountCredits(credits: AccountCreditUsage?) {
        val current = _uiState.value as? CliSessionsUiState.Success ?: return
        if (current.accountCredits == credits) {
            return
        }
        _uiState.value = current.copy(accountCredits = credits)
    }

    private fun startBudgetLoad() {
        val useCase = getMonthlyBudgetStatus ?: return
        budgetJob?.cancel()
        budgetJob = viewModelScope.launch {
            val status = useCase(profileId = profileId, limitMicros = budgetLimitMicros).getOrNull()
            val latest = _uiState.value as? CliSessionsUiState.Success ?: return@launch
            // Falha mantém o cartão anterior: o orçamento é referência, não alarme.
            if (status != null || budgetLimitMicros <= 0L) {
                _uiState.value = latest.copy(budget = status)
            }
        }
    }

    /**
     * `true` entre o pedido de recarga do resumo e a resposta dele.
     *
     * A lista e o resumo são duas leituras: sem este marcador, a que terminasse
     * primeiro apagaria o "Atualizando…" e o resumo da janela **anterior**
     * ficaria na tela parecendo a resposta nova.
     */
    private var breakdownReloadPending = false

    private fun refreshBreakdownIfVisible() {
        if ((_uiState.value as? CliSessionsUiState.Success)?.view == CliSessionsView.BREAKDOWN) {
            breakdownReloadPending = true
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
        exportJob?.cancel()
        budgetJob?.cancel()
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
                breakdownReloadPending = false
                val latest = _uiState.value as? CliSessionsUiState.Success ?: return
                _uiState.value = latest.copy(
                    breakdown = breakdown,
                    breakdownError = null,
                    isRefreshing = false
                )
            },
            onFailure = { error ->
                // O aviso sai mesmo na falha: ele diz "estou esperando", e a
                // espera acabou — o que resta é o erro, que tem linha própria.
                breakdownReloadPending = false
                val latest = _uiState.value as? CliSessionsUiState.Success ?: return
                _uiState.value = latest.copy(
                    breakdownError = error.message ?: UNKNOWN_ERROR_MESSAGE,
                    isRefreshing = false
                )
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
                    breakdownError = current?.breakdownError,
                    exportOutcome = current?.exportOutcome,
                    budget = current?.budget,
                    accountCredits = current?.accountCredits,
                    // A lista já chegou, mas o resumo descreve a mesma janela e
                    // pode estar a caminho: o aviso só sai quando os dois chegam.
                    isRefreshing = breakdownReloadPending
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

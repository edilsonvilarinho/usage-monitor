package com.usagemonitor.presentation.viewmodel

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CliQuotaWindows
import com.usagemonitor.domain.entity.CliRangeWindow
import com.usagemonitor.domain.entity.CliSessionDetail
import com.usagemonitor.domain.entity.CliSessionRange
import com.usagemonitor.domain.entity.CliSessionSummary
import com.usagemonitor.domain.entity.CliUsageBreakdown
import com.usagemonitor.domain.entity.TeamAccountUsage
import com.usagemonitor.domain.entity.TeamMemberUsage
import com.usagemonitor.domain.usecase.CliSessionDetailResult
import com.usagemonitor.domain.usecase.ComputeCliSessionAnalyticsUseCase
import com.usagemonitor.domain.usecase.GetAdminTeamSessionDetailUseCase
import com.usagemonitor.domain.usecase.GetAdminTeamOverviewUseCase
import com.usagemonitor.domain.usecase.GetTeamSessionDetailUseCase
import com.usagemonitor.domain.usecase.GetTeamUsageTrendUseCase
import com.usagemonitor.domain.usecase.GetTeamUsageUseCase
import com.usagemonitor.domain.usecase.RemoveAdminTeamMemberUseCase
import com.usagemonitor.domain.usecase.RemoveAdminTeamSessionUseCase
import com.usagemonitor.presentation.ui.report.reportForTeam
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
import kotlinx.datetime.Instant

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
    private val getTeamSessionDetail: GetTeamSessionDetailUseCase,
    /**
     * Leitura de todas as contas, para o administrador.
     *
     * `null` desliga a visão global — é o que acontece numa instalação sem
     * administração, onde a janela só existe por conta.
     */
    private val getAdminOverview: GetAdminTeamOverviewUseCase? = null,
    /** Detalhe global com token de admin; nunca pode cair na chave do time local. */
    private val getAdminTeamSessionDetail: GetAdminTeamSessionDetailUseCase? = null,
    /** Remoção global com token de admin; não pode reutilizar a chave de time. */
    private val removeAdminTeamMember: RemoveAdminTeamMemberUseCase? = null,
    /** Exclusão de sessão também exclusiva do token administrativo. */
    private val removeAdminTeamSession: RemoveAdminTeamSessionUseCase? = null,
    /**
     * Tendência diária. `null` esconde o gráfico — instalação sem ele continua
     * funcionando, mesmo tratamento dos demais recursos opcionais do time.
     */
    private val getTeamUsageTrend: GetTeamUsageTrendUseCase? = null,
    /**
     * Gravação do relatório. `null` desliga a exportação — mesmo tratamento dos
     * demais recursos opcionais do time, e o que permite ao teste de componente
     * não abrir diálogo de arquivo.
     */
    private val exportWriter: UsageExportWriter? = null,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val liveIntervalMillis: Long = DEFAULT_LIVE_INTERVAL_MILLIS,
    private val clock: Clock = Clock.System,
    private val computeAnalytics: ComputeCliSessionAnalyticsUseCase = ComputeCliSessionAnalyticsUseCase()
) {
    private val viewModelScope = CoroutineScope(SupervisorJob() + dispatcher)

    private var loadJob: Job? = null
    private var detailJob: Job? = null
    private var liveJob: Job? = null
    private var trendJob: Job? = null
    private var exportJob: Job? = null

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

    private val _sessionRemovalError = MutableStateFlow<String?>(null)
    val sessionRemovalError: StateFlow<String?> = _sessionRemovalError.asStateFlow()

    private var range: CliSessionRange = CliSessionRange.DEFAULT
    private var quotaWindows: CliQuotaWindows = CliQuotaWindows()
    private var accountKey: String? = null
    private var accountLabel: String? = null

    /** `true` enquanto a janela mostra todas as contas do servidor. */
    private var adminOverview: Boolean = false

    /** Aponta a janela para uma conta Anthropic e liga o tempo real. */
    fun openForAccount(
        accountKey: String,
        accountLabel: String?,
        quotaWindows: CliQuotaWindows = CliQuotaWindows()
    ) {
        val scopeChanged = this.accountKey != accountKey || adminOverview
        this.accountKey = accountKey
        this.accountLabel = accountLabel
        this.quotaWindows = quotaWindows
        this.adminOverview = false

        // Trocar de conta zera a lista: manter os integrantes da conta anterior
        // na tela enquanto a nova carrega mostraria dados de outro time.
        if (scopeChanged) {
            _uiState.value = TeamUsageUiState.Loading
        }

        refresh()
        startLiveLoop()
        startTrendLoad(accountKey)
    }

    /**
     * Aponta a janela para **todas** as contas do servidor e liga o tempo real.
     *
     * É o modo de quem administra: ele não participa necessariamente de nenhuma
     * das contas que administra, então a lista não pode depender de credencial de
     * conta. Sem [getAdminOverview] configurado a chamada não faz nada — a
     * instalação não tem administração.
     */
    fun openForAllAccounts() {
        if (getAdminOverview == null || getAdminTeamSessionDetail == null) {
            return
        }

        val scopeChanged = !adminOverview
        this.accountKey = null
        this.accountLabel = null
        // Sem âncora de reset: cada conta reseta numa hora, e escolher uma delas
        // daria um recorte que não corresponde a conta nenhuma.
        this.quotaWindows = CliQuotaWindows()
        this.adminOverview = true

        if (scopeChanged) {
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
        // Na visão global não há âncora a atualizar: aceitar a de uma conta faria
        // o recorte de 5h de todas elas seguir o reset de uma só.
        if (adminOverview || quotaWindows == this.quotaWindows) {
            return
        }
        this.quotaWindows = quotaWindows
        if (range == CliSessionRange.LAST_5H) {
            refresh()
        }
    }

    fun setRange(range: CliSessionRange) {
        this.range = range
        // O usuário clicou e espera outra resposta. Sem esta marca a tela segura
        // os números da janela anterior durante toda a ida ao servidor, sem nada
        // dizendo que eles ainda são os antigos.
        markRefreshing()
        refresh()
    }

    /**
     * Relatório PDF do recorte que está na tela.
     *
     * Não recarrega nada: no time o resumo por eixo vem na mesma resposta da
     * lista, então o estado já tem tudo — ao contrário da tela da máquina, onde
     * o resumo é uma segunda consulta ao índice.
     */
    fun exportReport(language: AppLanguage) {
        val writer = exportWriter ?: return
        val current = _uiState.value as? TeamUsageUiState.Success ?: return

        val now = clock.now()
        val request = reportRequest(
            document = reportForTeam(state = current, language = language, now = now),
            range = current.range,
            now = now
        )

        exportJob?.cancel()
        exportJob = viewModelScope.launch {
            val outcome = runCatching { writer.write(request) }.fold(
                // Cancelar o diálogo não é sucesso nem erro: nada é publicado.
                onSuccess = { path -> path?.let { saved -> CliExportOutcome.Saved(saved) } },
                onFailure = { error -> CliExportOutcome.Failed(error.message ?: UNKNOWN_ERROR_MESSAGE) }
            ) ?: return@launch

            val latest = _uiState.value as? TeamUsageUiState.Success ?: return@launch
            _uiState.value = latest.copy(exportOutcome = outcome)
        }
    }

    private fun markRefreshing() {
        val current = _uiState.value
        if (current is TeamUsageUiState.Success && !current.isRefreshing) {
            _uiState.value = current.copy(isRefreshing = true)
        }
    }

    /**
     * Troca a aba do modal.
     *
     * Sem recarregar: as três leituras já estão no estado — os integrantes e o
     * resumo vêm da mesma resposta, e a tendência foi lida uma vez na abertura.
     */
    fun setView(view: TeamUsageView) {
        val current = _uiState.value
        if (current !is TeamUsageUiState.Success) {
            return
        }
        _uiState.value = current.copy(view = view)
    }

    /** Abre ou fecha as sessões de um integrante, por `memberKey`. */
    fun toggleMember(memberKey: String) {
        val current = _uiState.value
        if (current !is TeamUsageUiState.Success) {
            return
        }

        val expanded = current.expandedMemberKeys
        _uiState.value = current.copy(
            expandedMemberKeys = if (memberKey in expanded) {
                expanded - memberKey
            } else {
                expanded + memberKey
            }
        )
    }

    /** Abre ou fecha os integrantes de uma conta, por `TeamAccountGroup.groupKey`. */
    fun toggleAccount(groupKey: String) {
        val current = _uiState.value
        if (current !is TeamUsageUiState.Success) {
            return
        }

        val expanded = current.expandedAccountKeys
        _uiState.value = current.copy(
            expandedAccountKeys = if (groupKey in expanded) {
                expanded - groupKey
            } else {
                expanded + groupKey
            }
        )
    }

    /**
     * Apaga um integrante no servidor e recarrega a lista.
     *
     * Recarregar em vez de remover da lista em memória: a resposta seguinte é a
     * única prova de que o servidor apagou de fato. A falha vira aviso na tela e
     * a lista fica intacta — não dá para mostrar como removido o que continua lá.
     */
    fun removeMember(memberKey: String) {
        if (!adminOverview) {
            return
        }
        val remover = removeAdminTeamMember ?: return
        val member = findMember(memberKey) ?: return
        val targetAccountKey = member.accountKey ?: return

        viewModelScope.launch {
            val result = remover(accountKey = targetAccountKey, deviceId = member.deviceId)
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
     * Apaga uma sessão somente na visão global administrativa.
     *
     * A sessão precisa continuar pertencendo ao integrante exibido. O servidor
     * repete essa validação e executa a remoção em transação.
     */
    fun removeSession(memberKey: String, sessionId: String) {
        if (!adminOverview) {
            return
        }
        val remover = removeAdminTeamSession ?: return
        val member = findMember(memberKey) ?: return
        val targetAccountKey = member.accountKey ?: return
        if (member.sessions.none { session -> session.sessionId == sessionId }) {
            return
        }

        viewModelScope.launch {
            val result = remover(
                accountKey = targetAccountKey,
                deviceId = member.deviceId,
                sessionId = sessionId
            )
            val error = result.exceptionOrNull()
            if (error != null) {
                _sessionRemovalError.value = error.message ?: UNKNOWN_ERROR_MESSAGE
                return@launch
            }
            _sessionRemovalError.value = null
            loadTeam()
        }
    }

    fun clearSessionRemovalError() {
        _sessionRemovalError.value = null
    }

    /**
     * Abre o detalhe de uma sessão de outra máquina.
     *
     * O `deviceId` acompanha o `sessionId` porque a leitura no servidor é
     * escopada por `(conta, máquina)` — o id da sessão sozinho não identifica o
     * dono dela.
     */
    fun openSession(memberKey: String, sessionId: String) {
        val current = _uiState.value
        if (current !is TeamUsageUiState.Success) {
            return
        }
        val member = findMember(memberKey) ?: return
        val targetAccountKey = member.accountKey ?: accountKey ?: return
        val deviceId = member.deviceId

        _uiState.value = current.copy(
            detail = TeamSessionDetailUiState.Loading(
                deviceId = deviceId,
                sessionId = sessionId,
                accountKey = member.accountKey
            )
        )

        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            val loaded = loadDetailState(
                accountKey = targetAccountKey,
                deviceId = deviceId,
                sessionId = sessionId,
                scopedAccountKey = member.accountKey,
                adminAccess = current.isAdminOverview
            )
            publishDetail(
                deviceId = deviceId,
                sessionId = sessionId,
                scopedAccountKey = member.accountKey,
                detailState = loaded
            )
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
        trendJob?.cancel()
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

    /** Membros já rotulados, a janela aplicada e o resumo, de um dos dois escopos. */
    private data class LoadedTeam(
        val members: List<TeamMemberUsage>,
        val window: CliRangeWindow,
        val breakdown: CliUsageBreakdown
    )

    /**
     * Lê o escopo ativo, ou `null` quando não há nenhum apontado.
     *
     * As duas leituras convergem para a mesma forma: a visão global achata as
     * contas numa lista só de integrantes, cada um carimbado com a conta de
     * origem. É o que permite a tela e o resto do ViewModel não saberem em qual
     * dos dois modos estão, exceto onde a diferença importa de fato.
     */
    private suspend fun fetchScope(): Result<LoadedTeam>? {
        if (adminOverview) {
            val overview = getAdminOverview ?: return null
            return overview(range = range).map { result ->
                LoadedTeam(
                    members = flattenAccounts(result.accounts),
                    window = result.window,
                    breakdown = result.breakdown
                )
            }
        }

        val targetAccountKey = accountKey ?: return null
        return getTeamUsage(
            accountKey = targetAccountKey,
            range = range,
            windows = quotaWindows
        ).map { result ->
            LoadedTeam(
                members = result.snapshot.members,
                window = result.window,
                breakdown = result.breakdown
            )
        }
    }

    /**
     * Junta os integrantes de todas as contas numa lista só.
     *
     * **A conta é a chave primária da ordem, e o consumo desce para dentro dela.**
     * Com o consumo no topo, a faixa de uma conta aparecia onde o integrante que
     * mais gastou a levasse: a mesma conta subia e descia a lista entre dois
     * tiques do laço ao vivo, e procurar uma pessoa exigia ler a tela inteira. O
     * rótulo é o e-mail que o administrador digitou ao emitir a chave e não muda
     * sozinho, então é ele que dá uma posição estável.
     *
     * Dentro da conta continua sendo quem mais consumiu primeiro — é a pergunta
     * que esta tela responde — e sem atividade no fim, em ordem alfabética.
     *
     * [TeamUsageUiState.Success.memberGroups] agrupa por ordem de primeira
     * aparição, então ordenar os integrantes assim já ordena as faixas de conta;
     * uma segunda ordenação lá seria um segundo dono da mesma decisão.
     *
     * A ordem é **total e determinística**, como a de `toTeamPresence`: duas
     * leituras iguais têm de produzir listas iguais, ou o `StateFlow` reemite e a
     * tela recompõe a cada 5s.
     */
    private fun flattenAccounts(accounts: List<TeamAccountUsage>): List<TeamMemberUsage> {
        return accounts
            .flatMap { account ->
                account.snapshot.members.map { member ->
                    member.copy(accountKey = account.accountKey, accountLabel = account.label)
                }
            }
            .sortedWith(
                // Conta sem rótulo emitido vai depois de todas as identificadas,
                // por um degrau próprio do comparador e não por uma sentinela de
                // texto: ela não tem e-mail para comparar, e abrir a lista com um
                // uuid cru seria pior que fechá-la com ele.
                compareBy<TeamMemberUsage> { member -> if (member.accountLabel == null) 1 else 0 }
                    .thenBy { member -> member.accountLabel?.lowercase().orEmpty() }
                    // Duas contas sem rótulo empatam acima; o uuid as separa e
                    // mantém a ordem total.
                    .thenBy { member -> member.accountKey.orEmpty() }
                    .thenByDescending { member -> member.totalTokens }
                    .thenBy { member -> member.alias.lowercase() }
            )
    }

    private fun findMember(memberKey: String): TeamMemberUsage? {
        val current = _uiState.value as? TeamUsageUiState.Success ?: return null
        return current.members.firstOrNull { member -> member.memberKey == memberKey }
    }

    /**
     * Carrega a tendência uma vez por abertura, fora do laço ao vivo.
     *
     * A série é de dias: recarregá-la de cinco em cinco segundos seria uma
     * consulta ao servidor por tique para redesenhar exatamente o mesmo gráfico.
     * Falha ou rota ausente deixam o gráfico de fora, sem erro na tela — o
     * consumo do time, que é a informação principal, continua.
     */
    private fun startTrendLoad(accountKey: String) {
        val useCase = getTeamUsageTrend ?: return
        trendJob?.cancel()
        trendJob = viewModelScope.launch {
            val trend = useCase(accountKey).getOrNull() ?: return@launch
            val current = _uiState.value as? TeamUsageUiState.Success ?: return@launch
            _uiState.value = current.copy(trend = trend)
        }
    }

    private suspend fun loadTeam() {
        loadMutex.withLock {
            val current = _uiState.value as? TeamUsageUiState.Success
            val overview = adminOverview

            (fetchScope() ?: return@withLock).fold(
                onSuccess = { result ->
                    val members = result.members
                    val contentChanged = current == null || current.members != members

                    _uiState.value = TeamUsageUiState.Success(
                        members = members,
                        range = range,
                        rangeStartsAt = result.window.cutoffMillis?.let(Instant::fromEpochMilliseconds),
                        rangeEndsAt = result.window.endsAt,
                        rangeAnchored = result.window.isAnchored,
                        accountLabel = accountLabel,
                        isAdminOverview = overview,
                        // Estado de UI, não do servidor: sem carregá-lo daqui os
                        // grupos abertos se fechariam sozinhos a cada tique.
                        // Integrantes que sumiram da resposta saem do conjunto.
                        expandedMemberKeys = current?.expandedMemberKeys
                            ?.filterTo(mutableSetOf()) { key ->
                                members.any { member -> member.memberKey == key }
                            }
                            ?: emptySet(),
                        // Mesmo tratamento das linhas abertas. Conta que sumiu da
                        // resposta sai do conjunto, senão ela reapareceria aberta
                        // se voltasse horas depois.
                        expandedAccountKeys = current?.expandedAccountKeys
                            ?.filterTo(mutableSetOf()) { key ->
                                members.any { member -> member.accountKey.orEmpty() == key }
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
                        glossaryExpanded = current?.glossaryExpanded ?: false,
                        trend = current?.trend,
                        // Mesmo tratamento do detalhe: sem carregá-la daqui, o
                        // tique de 5s devolveria à lista quem está lendo o resumo.
                        view = current?.view ?: TeamUsageView.MEMBERS,
                        exportOutcome = current?.exportOutcome,
                        breakdown = result.breakdown
                    )
                },
                onFailure = { error ->
                    // Falha intermitente com dados na tela não apaga o que o
                    // usuário está lendo: o erro vira aviso e a lista fica. Mas o
                    // "Atualizando…" tem de sair, senão a tela avisaria para sempre
                    // que está esperando uma resposta que já falhou.
                    if (current != null) {
                        if (current.isRefreshing) {
                            _uiState.value = current.copy(isRefreshing = false)
                        }
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
        val targetAccountKey = open.accountKey ?: accountKey ?: return

        val reloaded = loadDetailState(
            accountKey = targetAccountKey,
            deviceId = open.deviceId,
            sessionId = open.sessionId,
            scopedAccountKey = open.accountKey,
            adminAccess = current.isAdminOverview
        )
        if (reloaded is TeamSessionDetailUiState.Ready) {
            publishDetail(
                deviceId = open.deviceId,
                sessionId = open.sessionId,
                scopedAccountKey = open.accountKey,
                detailState = reloaded
            )
        }
    }

    private suspend fun loadDetailState(
        accountKey: String,
        deviceId: String,
        sessionId: String,
        scopedAccountKey: String?,
        adminAccess: Boolean
    ): TeamSessionDetailUiState {
        val result = if (adminAccess) {
            val adminReader = getAdminTeamSessionDetail
                ?: return TeamSessionDetailUiState.Error(
                    deviceId = deviceId,
                    sessionId = sessionId,
                    accountKey = scopedAccountKey,
                    message = "Leitura administrativa da sessão não configurada."
                )
            adminReader(
                accountKey = accountKey,
                deviceId = deviceId,
                sessionId = sessionId
            )
        } else {
            getTeamSessionDetail(
                accountKey = accountKey,
                deviceId = deviceId,
                sessionId = sessionId
            )
        }

        return result.fold(
            onSuccess = { loaded ->
                if (loaded == null) {
                    // Servidor sem a rota de detalhe, ou sessão fora da retenção.
                    aggregatedDetail(deviceId, sessionId, scopedAccountKey)
                } else {
                    TeamSessionDetailUiState.Ready(
                        deviceId = deviceId,
                        sessionId = sessionId,
                        accountKey = scopedAccountKey,
                        result = loaded
                    )
                }
            },
            onFailure = { error ->
                TeamSessionDetailUiState.Error(
                    deviceId = deviceId,
                    sessionId = sessionId,
                    message = error.message ?: UNKNOWN_ERROR_MESSAGE,
                    accountKey = scopedAccountKey
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
    private fun aggregatedDetail(
        deviceId: String,
        sessionId: String,
        scopedAccountKey: String?
    ): TeamSessionDetailUiState {
        val summary = findSessionSummary(deviceId, sessionId, scopedAccountKey)
            ?: return TeamSessionDetailUiState.Error(
                deviceId = deviceId,
                sessionId = sessionId,
                message = SESSION_GONE_MESSAGE,
                accountKey = scopedAccountKey
            )

        return TeamSessionDetailUiState.Ready(
            deviceId = deviceId,
            sessionId = sessionId,
            accountKey = scopedAccountKey,
            result = CliSessionDetailResult(
                detail = CliSessionDetail(summary = summary, turns = emptyList()),
                analytics = computeAnalytics.fromSummary(summary)
            ),
            turnsUnavailable = true
        )
    }

    private fun findSessionSummary(
        deviceId: String,
        sessionId: String,
        scopedAccountKey: String?
    ): CliSessionSummary? {
        val current = _uiState.value as? TeamUsageUiState.Success ?: return null
        val member = current.members.firstOrNull { entry ->
            entry.deviceId == deviceId && entry.accountKey == scopedAccountKey
        } ?: return null
        return member.sessions.firstOrNull { session -> session.sessionId == sessionId }
    }

    /** Descarta o resultado se o usuário já voltou à lista ou abriu outra sessão. */
    private fun publishDetail(
        deviceId: String,
        sessionId: String,
        scopedAccountKey: String?,
        detailState: TeamSessionDetailUiState
    ) {
        val current = _uiState.value
        if (current !is TeamUsageUiState.Success) {
            return
        }
        val open = current.detail
        // A conta entra na comparação porque a visão global pode ter a mesma
        // máquina em duas contas: sem ela a resposta de uma seria publicada no
        // painel da outra.
        if (open == null ||
            open.deviceId != deviceId ||
            open.sessionId != sessionId ||
            open.accountKey != scopedAccountKey
        ) {
            return
        }
        _uiState.value = current.copy(detail = detailState)
    }

    companion object {
        /** Mesma cadência do modal de sessões da máquina. */
        const val DEFAULT_LIVE_INTERVAL_MILLIS = 5_000L
    }
}

package com.usagemonitor.presentation.viewmodel

import com.usagemonitor.domain.entity.ACTIVITY_TIME_ZONE_ID
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.AnthropicProfileRef
import com.usagemonitor.domain.entity.DEFAULT_SPIKE_FACTOR
import com.usagemonitor.domain.entity.HistoryRange
import com.usagemonitor.domain.entity.UsageSpike
import com.usagemonitor.domain.entity.QuotaRiskSummary
import com.usagemonitor.domain.entity.QuotaSeriesKey
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.domain.entity.BreadcrumbCategory
import com.usagemonitor.domain.entity.sanitizeBreadcrumbErrorMessage
import com.usagemonitor.domain.repository.BreadcrumbRecorder
import com.usagemonitor.domain.repository.NoOpBreadcrumbRecorder
import com.usagemonitor.domain.repository.AppUpdateInstaller
import com.usagemonitor.domain.usecase.CheckForAppUpdateUseCase
import com.usagemonitor.domain.usecase.GetAnthropicUsageUseCase
import com.usagemonitor.domain.usecase.GetCodexUsageUseCase
import com.usagemonitor.domain.usecase.GetDeepSeekUsageUseCase
import com.usagemonitor.domain.usecase.GetKiloUsageUseCase
import com.usagemonitor.domain.usecase.GetGeminiUsageUseCase
import com.usagemonitor.domain.usecase.GetCursorUsageUseCase
import com.usagemonitor.domain.usecase.GetAntigravityUsageUseCase
import com.usagemonitor.domain.usecase.GetMiniMaxUsageUseCase
import com.usagemonitor.domain.usecase.GetOpenCodeGoUsageUseCase
import com.usagemonitor.domain.usecase.GetOpenCodeUsageUseCase
import com.usagemonitor.domain.usecase.GetOpenRouterUsageUseCase
import com.usagemonitor.domain.usecase.GetCachedDashboardStatsUseCase
import com.usagemonitor.domain.usecase.GetUsageHistoryUseCase
import com.usagemonitor.domain.usecase.RecordUsageSnapshotUseCase
import com.usagemonitor.domain.usecase.SaveDashboardCacheUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

class DashboardViewModel(
    private val getAnthropicUsage: GetAnthropicUsageUseCase,
    private val getMiniMaxUsage: GetMiniMaxUsageUseCase,
    private val getCodexUsage: GetCodexUsageUseCase,
    private val getDeepSeekUsage: GetDeepSeekUsageUseCase,
    private val enabledApis: StateFlow<Set<ApiSource>>,
    private val recordUsageSnapshot: RecordUsageSnapshotUseCase,
    private val getUsageHistory: GetUsageHistoryUseCase? = null,
    private val getCachedDashboardStats: GetCachedDashboardStatsUseCase? = null,
    private val saveDashboardCache: SaveDashboardCacheUseCase? = null,
    private val getOpenCodeUsage: GetOpenCodeUsageUseCase = unavailableOpenCodeUsage(),
    /**
     * Default que falha pelo mesmo motivo de [getOpenCodeUsage]: a fonte é
     * opt-in e uma build sem o repositório ligado tem de dizer o que falta, não
     * ficar em carga eterna. A mensagem é a de chave ausente porque é essa a
     * condição verdadeira de quem não configurou nada.
     */
    private val getOpenCodeGoUsage: GetOpenCodeGoUsageUseCase = unavailableOpenCodeGoUsage(),
    private val getKiloUsage: GetKiloUsageUseCase = unavailableKiloUsage(),
    /**
     * Default que falha pelo mesmo motivo de [getOpenCodeGoUsage]: fonte
     * opt-in dependente de chave, e uma build sem o repositório ligado tem de
     * dizer o que falta em vez de ficar em carga eterna.
     */
    private val getOpenRouterUsage: GetOpenRouterUsageUseCase = unavailableOpenRouterUsage(),
    private val getGeminiUsage: GetGeminiUsageUseCase = unavailableGeminiUsage(),
    private val getCursorUsage: GetCursorUsageUseCase = unavailableCursorUsage(),
    private val getAntigravityUsage: GetAntigravityUsageUseCase = unavailableAntigravityUsage(),
    private val checkForAppUpdate: CheckForAppUpdateUseCase? = null,
    private val appUpdateReleaseOpener: AppUpdateReleaseOpener = UnsupportedAppUpdateReleaseOpener,
    /**
     * Nulo é "esta build não traz o mecanismo", e nada é baixado nem executado.
     * É o estado do PR 1 do plano de auto-update.
     */
    private val appUpdateInstaller: AppUpdateInstaller? = null,
    private val autoUpdateEnabled: StateFlow<Boolean> = MutableStateFlow(false),
    /**
     * Encerramento ordenado pedido pela faixa ("Reiniciar o app e atualizar").
     * O view model não sabe fechar a aplicação; quem sabe é o `Main.kt`.
     */
    private val onRestartAndUpdateRequested: () -> Unit = {},
    /**
     * Registra que a entrega do pacote ao sistema falhou, com o motivo.
     *
     * Existe porque essa falha é **invisível por construção**: ela acontece com o
     * app já saindo, e quem escreve o recibo é o instalador — que, justamente,
     * não chegou a rodar. Sem isto o usuário fecha o app esperando a atualização,
     * o app não volta, e não há nada no disco dizendo por quê. Foi o que a
     * atividade A20 mediu.
     *
     * Recebe `(versão, motivo)`. A escrita fica no desktop; o view model não
     * conhece arquivo.
     */
    private val onUpdateScheduleFailure: (String, String) -> Unit = { _, _ -> },
    private val currentAppVersion: String = "0.0.0",
    /**
     * Fator corrente da detecção de anomalia, lido das preferências de alerta.
     *
     * Provedor e não valor, pelo mesmo motivo do `stallThresholdProvider` do
     * semáforo: a preferência muda nas Configurações e o view model é construído
     * uma vez só.
     */
    private val spikeFactorProvider: () -> Double = { DEFAULT_SPIKE_FACTOR },
    /** Fuso em que o dia da anomalia é recortado. Injetável para o teste não depender da máquina. */
    private val alertTimeZone: TimeZone = TimeZone.of(ACTIVITY_TIME_ZONE_ID),
    private val clock: Clock = Clock.System,
    private val isAppVisible: StateFlow<Boolean> = MutableStateFlow(true),
    private val anthropicProfiles: StateFlow<List<AnthropicProfileRef>> =
        MutableStateFlow(listOf(AnthropicProfileRef.DEFAULT)),
    private val config: DashboardViewModelConfig = DashboardViewModelConfig(),
    private val persistedNextRefreshAt: Instant? = null,
    private val onNextRefreshAtChanged: (Instant) -> Unit = {},
    /**
     * Trilha de eventos do relatório de bug.
     *
     * Default nulo-de-comportamento pelo mesmo motivo do
     * `UnsupportedAppUpdateReleaseOpener`: nenhum dos vinte testes que constroem
     * este view model tem para onde gravar um passo, e um parâmetro anulável
     * espalharia `?.` por cada ponto de chamada.
     */
    private val breadcrumbs: BreadcrumbRecorder = NoOpBreadcrumbRecorder
) {
    private val initialScheduledRefreshAt: Instant =
        persistedNextRefreshAt?.takeIf { it > clock.now() } ?: (clock.now() + config.pollInterval)

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _nextRefreshAt = MutableStateFlow(initialScheduledRefreshAt)
    val nextRefreshAt: StateFlow<Instant> = _nextRefreshAt.asStateFlow()

    /** O intervalo do polling: é a volta inteira do relógio da contagem na HUD (#293). */
    val pollInterval: Duration
        get() = config.pollInterval

    private val _refreshingTargets = MutableStateFlow<Set<UsageTargetKey>>(emptySet())
    val refreshingTargets: StateFlow<Set<UsageTargetKey>> = _refreshingTargets.asStateFlow()
    private val _refreshingSources = MutableStateFlow<Set<ApiSource>>(emptySet())
    val refreshingSources: StateFlow<Set<ApiSource>> = _refreshingSources.asStateFlow()

    private val _toastMessage = MutableStateFlow<DashboardToast?>(null)
    val toastMessage: StateFlow<DashboardToast?> = _toastMessage.asStateFlow()

    private val _spikes = MutableStateFlow<List<UsageSpike>>(emptyList())

    /**
     * As cotas consumindo hoje muito acima do hábito do usuário.
     *
     * Sai daqui, e não de um laço próprio, porque é derivada do mesmo relatório de
     * histórico que a projeção de risco já lê a cada coleta. Quem a transforma em
     * notificação é o [UsageAlertViewModel].
     */
    val spikes: StateFlow<List<UsageSpike>> = _spikes.asStateFlow()

    private val viewModelScope = CoroutineScope(SupervisorJob() + config.workerDispatcher)

    /** A atualização automática do app: verificação, download, backoff e entrega no encerramento. */
    private val updates = DashboardUpdateCoordinator(
        checkForAppUpdate = checkForAppUpdate,
        appUpdateReleaseOpener = appUpdateReleaseOpener,
        appUpdateInstaller = appUpdateInstaller,
        autoUpdateEnabled = autoUpdateEnabled,
        onRestartAndUpdateRequested = onRestartAndUpdateRequested,
        onUpdateScheduleFailure = onUpdateScheduleFailure,
        currentAppVersion = currentAppVersion,
        clock = clock,
        config = config,
        breadcrumbs = breadcrumbs,
        scope = viewModelScope,
        onReleasePageError = { message -> _toastMessage.value = DashboardToast.ReleasePageError(message) }
    )
    val appUpdateState: StateFlow<AppUpdateUiState?> = updates.state
    private val stateMutex = Mutex()
    private val fetchMutex = Mutex()
    private val pendingFetchMutex = Mutex()
    private val cachedStatsByTarget = mutableMapOf<UsageTargetKey, ApiUsageStats>()
    private val cachedErrorsByTarget = mutableMapOf<UsageTargetKey, UiApiError>()
    private val cachedRiskByTarget = mutableMapOf<UsageTargetKey, Map<QuotaSeriesKey, QuotaRiskSummary>>()
    private val cachedSpikeByTarget = mutableMapOf<UsageTargetKey, List<UsageSpike>>()
    private val sourceFetchSemaphore = Semaphore(config.maxConcurrentSourceFetches.coerceAtLeast(1))
    private val pollWakeUpSignal = Channel<Unit>(capacity = Channel.CONFLATED)
    private val initialFetchCancelled = AtomicBoolean(false)
    @Volatile private var scheduledRefreshAt: Instant = initialScheduledRefreshAt
    private var countdownJob: Job? = null
    private var initFetchJob: Job? = null
    private var pendingFetchRequest: PendingFetchRequest? = null

    init {
        val isPersistedRefreshStillPending = persistedNextRefreshAt != null && persistedNextRefreshAt > clock.now()
        // Reidrata a UI com o último snapshot salvo em vez de deixar a tela
        // presa em Loading. Vale mesmo quando o ciclo já venceu e a coleta vai
        // sair logo em seguida: ela ainda depende da rede, e o app ficou o
        // tempo todo mostrando "Carregando" com dados válidos em disco. As duas
        // rotinas convivem porque o restore só preenche alvo que a coleta ainda
        // não trouxe.
        loadCachedStateIfAvailable()
        if (config.autoStartInitialFetch && !isPersistedRefreshStillPending) {
            initFetchJob = viewModelScope.launch {
                if (initialFetchCancelled.get()) {
                    return@launch
                }
                requestFetch(targets = enabledTargets())
            }
        }
        if (config.autoStartUpdateChecks) {
            updates.startCheckLoop()
        }
        updates.startAutoUpdateSwitchWatcher()
        if (config.autoStartCountdown) {
            startCountdown()
        }
    }

    private fun loadCachedStateIfAvailable() {
        val cacheUseCase = getCachedDashboardStats ?: return
        viewModelScope.launch {
            val cacheResult = cacheUseCase()
            // Falha aqui era silenciosa e o sintoma é o app abrir vazio esperando
            // a primeira coleta -- indistinguível de "a coleta está demorando".
            // Uma vez por arranque, então não há risco de encher a trilha.
            cacheResult.exceptionOrNull()?.let { error ->
                breadcrumbs.recordFailure("ler cache do dashboard", error)
            }
            val cachedStats = cacheResult.getOrNull().orEmpty()
            if (cachedStats.isEmpty()) {
                return@launch
            }
            val restored = mutableListOf<ApiUsageStats>()
            stateMutex.withLock {
                // Não sobrescreve dados já obtidos por uma fetch que tenha
                // completado antes desta corrotina (ex.: refresh manual do
                // usuário, ou a coleta inicial quando o ciclo já venceu). É esta
                // guarda que deixa as duas rotinas correrem juntas.
                val enabled = enabledTargets()
                cachedStats.forEach { stats ->
                    if (isPersistableDashboardStats(stats) &&
                        stats.targetKey in enabled &&
                        stats.targetKey !in cachedStatsByTarget
                    ) {
                        cachedStatsByTarget[stats.targetKey] = stats
                        restored += stats
                    }
                }
                publishUiState(enabled)
            }
            // O snapshot de disco pode trazer um reset que vence antes do poll —
            // inclusive um já vencido enquanto o app esteve fechado.
            nudgeCountdown()
            restoreRiskSummaries(restored)
        }
    }

    /**
     * Recalcula a projeção dos alvos que vieram do cache de disco.
     *
     * O snapshot em disco guarda só o consumo; o risco sai do histórico local,
     * e sem isto o card voltava do restart com os números certos mas sem o ponto
     * do semáforo nem o tooltip de projeção — até o poll seguinte, dez minutos
     * depois. Nada aqui depende de rede: o histórico é o SQLite local, então
     * recomputar sai mais barato que persistir a projeção e ter de invalidá-la.
     *
     * Roda depois de a UI já ter sido publicada com o consumo: o card pinta na
     * hora, e a projeção entra no quadro seguinte em vez de esperar o SQLite.
     * Sequencial de propósito — a conexão é serializada e o mesmo arquivo é
     * disputado pelo indexador de sessões CLI, então paralelizar só criaria
     * contenção.
     */
    private suspend fun restoreRiskSummaries(restored: List<ApiUsageStats>) {
        if (restored.isEmpty() || getUsageHistory == null) {
            return
        }
        val now = clock.now()
        restored.forEach { stats ->
            refreshHistoryDerivedState(stats.targetKey, stats, now, overwriteExisting = false)
        }
        stateMutex.withLock {
            publishUiState(enabledTargets())
        }
    }

    /**
     * Laço único de despertar, com dois gatilhos.
     *
     * O ciclo de dez minutos continua sendo o normal, mas ele sozinho deixava o
     * card repetindo a janela anterior por até um poll inteiro depois do reset —
     * o app só descobria o vencimento quando a API era chamada de novo. Agora o
     * alvo da espera é o que vier primeiro: o poll agendado ou o próximo
     * `periodEndAt` conhecido.
     */
    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (true) {
                val pollTarget = scheduledRefreshAt
                val resetTarget = nextQuotaResetTarget()
                val wakeUpAt = if (resetTarget != null && resetTarget < pollTarget) resetTarget else pollTarget
                val wokeUpForReset = wakeUpAt < pollTarget
                // O rodapé continua contando para o poll: o despertar por reset é
                // uma antecipação, não um novo prazo a anunciar.
                _nextRefreshAt.value = pollTarget
                val waitMillis = (wakeUpAt - clock.now()).inWholeMilliseconds.coerceAtLeast(0L)
                val rescheduled = withTimeoutOrNull(waitMillis) {
                    pollWakeUpSignal.receive()
                } != null
                if (rescheduled) {
                    continue
                }
                // A janela minimizada é justamente o caso do bug: esperar a
                // visibilidade deixaria o card congelado no valor da janela que
                // já venceu. Só o ciclo normal de poll respeita a visibilidade.
                if (!wokeUpForReset && !isAppVisible.value) {
                    isAppVisible.first { it }
                }
                viewModelScope.launch {
                    requestFetch(targets = enabledTargets())
                }
                scheduleNextRefresh()
            }
        }
    }

    /**
     * Instante em que vale a pena coletar por causa de um reset de cota.
     *
     * Só entram cotas com reset conhecido e ainda no futuro: sem reset conhecido
     * o `periodEndAt` é o sentinela distante do mapper, e um reset já vencido
     * viraria espera de zero milissegundo — um laço que bateria na API sem parar.
     */
    private suspend fun nextQuotaResetTarget(): Instant? {
        val snapshot = stateMutex.withLock { cachedStatsByTarget.values.toList() }
        val earliest = earliestKnownQuotaReset(snapshot, clock.now()) ?: return null
        return earliest + config.quotaResetGrace
    }

    /**
     * Faz o laço recalcular o alvo sem mexer no agendamento do poll.
     *
     * Uma coleta pode trazer um `periodEndAt` mais próximo que o alvo em que o
     * laço já está dormindo; sem este empurrão o reset novo só seria visto no
     * poll seguinte.
     */
    private fun nudgeCountdown() {
        pollWakeUpSignal.trySend(Unit)
    }

    private suspend fun requestFetch(
        targets: Set<UsageTargetKey>,
        preserveDataOnFailure: Boolean = false
    ) {
        if (!fetchMutex.tryLock()) {
            enqueuePendingFetch(targets, preserveDataOnFailure)
            fetchMutex.withLock {
                drainPendingFetchQueue() ?: return
            }
            return
        }

        try {
            drainFetchRequests(PendingFetchRequest(targets, preserveDataOnFailure))
        } finally {
            fetchMutex.unlock()
        }
    }

    private suspend fun drainFetchRequests(initialRequest: PendingFetchRequest) {
        var currentRequest: PendingFetchRequest? = initialRequest

        while (currentRequest != null) {
            performFetch(
                targets = currentRequest.targets,
                preserveDataOnFailure = currentRequest.preserveDataOnFailure
            )
            currentRequest = dequeuePendingFetch()
        }
    }

    private suspend fun drainPendingFetchQueue(): PendingFetchRequest? {
        val pendingRequest = dequeuePendingFetch() ?: return null
        drainFetchRequests(pendingRequest)
        return pendingRequest
    }

    private suspend fun enqueuePendingFetch(
        targets: Set<UsageTargetKey>,
        preserveDataOnFailure: Boolean
    ) {
        pendingFetchMutex.withLock {
            pendingFetchRequest = mergePendingFetch(
                existing = pendingFetchRequest,
                targets = targets,
                preserveDataOnFailure = preserveDataOnFailure,
                allTargets = ::enabledTargets
            )
        }
    }

    private suspend fun dequeuePendingFetch(): PendingFetchRequest? {
        return pendingFetchMutex.withLock {
            val request = pendingFetchRequest ?: return@withLock null
            pendingFetchRequest = null
            request
        }
    }

    private suspend fun performFetch(
        targets: Set<UsageTargetKey>,
        preserveDataOnFailure: Boolean
    ) {
        val enabled = enabledTargets()
        val effectiveTargets = targets.filterTo(linkedSetOf()) { target -> target in enabled }
        val snapshotCapturedAt = clock.now()

        if (effectiveTargets.isEmpty()) {
            stateMutex.withLock {
                pruneDisabledTargets(enabled)
                publishUiState(enabled)
            }
            return
        }

        markRefreshing(effectiveTargets, refreshing = true)

        val statsUpdates = mutableMapOf<UsageTargetKey, ApiUsageStats>()
        val errorUpdates = mutableMapOf<UsageTargetKey, UiApiError?>()

        try {
            val fetchResults = coroutineScope {
                effectiveTargets.map { target ->
                    async {
                        sourceFetchSemaphore.withPermit {
                            target to runCatching {
                                withTimeout(config.perSourceTimeout) {
                                    fetchTarget(target).getOrThrow()
                                }
                            }
                        }
                    }
                }.awaitAll()
            }

            fetchResults.forEach { (target, result) ->
                result
                    .onSuccess { stats ->
                        if (isPersistableDashboardStats(stats)) {
                            statsUpdates[target] = stats
                            errorUpdates[target] = null
                            persistSnapshot(stats, snapshotCapturedAt)
                            refreshHistoryDerivedState(target, stats, snapshotCapturedAt)
                        } else {
                            errorUpdates[target] = handleTargetFailure(
                                target,
                                IllegalStateException(
                                    "A resposta do Codex não trouxe nenhuma janela utilizável."
                                )
                            )
                        }
                    }
                    .onFailure { error ->
                        errorUpdates[target] = handleTargetFailure(target, error)
                    }
            }

            stateMutex.withLock {
                val latestEnabledTargets = enabledTargets()
                pruneDisabledTargets(latestEnabledTargets)

                effectiveTargets.forEach { target ->
                    val stats = statsUpdates[target]

                    if (stats != null) {
                        cachedStatsByTarget[target] = stats
                        cachedErrorsByTarget.remove(target)
                    } else {
                        val retained = statsRetainedAfterFailure(
                            target = target,
                            existingStats = cachedStatsByTarget[target],
                            preserveDataOnFailure = preserveDataOnFailure
                        )
                        if (retained == null) {
                            cachedStatsByTarget.remove(target)
                        } else {
                            cachedStatsByTarget[target] = retained
                        }

                        val errorMessage = errorUpdates[target]
                        if (errorMessage == null) {
                            cachedErrorsByTarget.remove(target)
                        } else {
                            cachedErrorsByTarget[target] = errorMessage
                        }
                    }
                }

                publishUiState(latestEnabledTargets)
            }

            // Os resets recém-coletados podem ser anteriores ao alvo em que o
            // laço já está dormindo; sem isto ele só os leria no poll seguinte.
            nudgeCountdown()

            persistDashboardCache()
        } finally {
            markRefreshing(effectiveTargets, refreshing = false)
        }
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    fun refresh() {
        // Só a coleta **pedida pelo usuário** vira passo. O laço de 10 minutos
        // não anota nada quando dá certo: a trilha tem 200 linhas de orçamento, e
        // "coleta ok" repetida é exatamente o que expulsaria dela o passo que
        // explica a falha. O que interessa aqui é a ação que o usuário vai
        // descrever ("cliquei em atualizar e...").
        breadcrumbs.record(BreadcrumbCategory.USE_CASE, "atualização de todas as fontes pedida")
        invalidateAntigravityReadingIfRequested(ApiSource.ANTIGRAVITY)
        scheduleNextRefresh()
        viewModelScope.launch {
            requestFetch(targets = enabledTargets())
            updates.checkForUpdate()
        }
    }

    fun refresh(source: ApiSource) {
        if (source !in enabledApis.value) {
            return
        }

        breadcrumbs.record(BreadcrumbCategory.USE_CASE, "atualização de ${source.name} pedida")
        invalidateAntigravityReadingIfRequested(source)
        scheduleNextRefresh()
        viewModelScope.launch {
            requestFetch(
                targets = enabledTargets().filterTo(linkedSetOf()) { target -> target.source == source },
                preserveDataOnFailure = true
            )
        }
    }

    fun refresh(target: UsageTargetKey) {
        if (target !in enabledTargets()) {
            return
        }
        // O alvo carrega `profileId`, que é interno do app e não identifica
        // ninguém; o apelido do perfil, que é o e-mail digitado, fica de fora.
        breadcrumbs.record(BreadcrumbCategory.USE_CASE, "atualização de ${target.source.name} pedida")
        invalidateAntigravityReadingIfRequested(target.source)
        scheduleNextRefresh()
        viewModelScope.launch {
            requestFetch(targets = setOf(target), preserveDataOnFailure = true)
        }
    }

    /**
     * O Antigravity guarda a última leitura por alguns minutos para o despertar por
     * reset de outra fonte não abrir um processo do CLI a cada vez. O clique do
     * usuário é o único gatilho que pede um número novo, então só ele descarta a
     * leitura guardada.
     */
    private fun invalidateAntigravityReadingIfRequested(source: ApiSource) {
        if (source == ApiSource.ANTIGRAVITY) {
            getAntigravityUsage.invalidateCachedReading()
        }
    }

    fun openUpdateReleasePage() {
        updates.openReleasePage()
    }

    fun cancelCountdown() {
        countdownJob?.cancel()
    }

    fun cancelInitFetch() {
        initialFetchCancelled.set(true)
        initFetchJob?.cancel()
    }

    fun onDestroy() {
        cancelCountdown()
        viewModelScope.cancel()
    }

    private suspend fun fetchTarget(target: UsageTargetKey): Result<ApiUsageStats> {
        return when (target.source) {
            ApiSource.ANTHROPIC -> {
                val profile = anthropicProfiles.value.firstOrNull { it.id == target.profileId }
                    ?: return Result.failure(IllegalStateException("Perfil Anthropic não configurado."))
                getAnthropicUsage(profile)
            }
            ApiSource.MINIMAX -> getMiniMaxUsage()
            ApiSource.CODEX -> getCodexUsage()
            ApiSource.DEEPSEEK -> getDeepSeekUsage()
            ApiSource.OPENCODE -> getOpenCodeUsage()
            ApiSource.OPENCODE_GO -> getOpenCodeGoUsage()
            ApiSource.KILO -> getKiloUsage()
            ApiSource.OPENROUTER -> getOpenRouterUsage()
            ApiSource.GEMINI -> getGeminiUsage()
            ApiSource.CURSOR -> getCursorUsage()
            ApiSource.ANTIGRAVITY -> getAntigravityUsage()
        }
    }

    private fun handleTargetFailure(target: UsageTargetKey, error: Throwable): UiApiError? {
        val source = target.source
        val uiError = uiApiErrorOf(target, error, anthropicProfiles.value)
        val message = uiError.message

        // Funil único de toda falha de coleta, e por isso o único ponto de
        // gravação: um passo por fonte que falhou, em qualquer caminho — poll
        // silencioso, atualização pedida ou recarga de um banner.
        //
        // Vai a mensagem **saneada**, a mesma que a tela mostra, e nunca a crua:
        // `sanitizeUiErrorMessage` já é o filtro que decide o que pode aparecer
        // para o usuário, e o relatório é ainda mais público que a tela dele.
        breadcrumbs.record(
            BreadcrumbCategory.API_CALL,
            "${source.name}: falhou — ${error::class.simpleName ?: "falha"}: ${sanitizeBreadcrumbErrorMessage(message)}"
        )

        // Avaliada antes de rate limit/credencial: falha de conectividade nunca
        // teve resposta HTTP nenhuma, então não pode ser confundida com 429/401 —
        // e sem banner próprio (`warningFor`) o toast genérico dispararia uma vez
        // por fonte, virando ruído quando a rede inteira está sem proxy.
        if (uiError.isConnectivityIssue) {
            return uiError
        }

        if (message.contains(HTTP_RATE_LIMIT_MARKER, ignoreCase = true)) {
            _toastMessage.value = DashboardToast.RateLimit(source)
            return uiError
        }

        if (uiError.isServiceUnavailableIssue) {
            return uiError
        }

        if (!uiError.isConfigurationIssue) {
            _toastMessage.value = DashboardToast.ApiError(
                source = source,
                message = message
            )
        }

        return uiError
    }

    private fun publishUiState(enabledTargets: Set<UsageTargetKey>) {
        _uiState.value = buildDashboardUiState(
            enabledTargets = enabledTargets,
            statsByTarget = cachedStatsByTarget,
            errorsByTarget = cachedErrorsByTarget,
            riskByTarget = cachedRiskByTarget
        )
    }

    private fun pruneDisabledTargets(enabledTargets: Set<UsageTargetKey>) {
        cachedStatsByTarget.keys.removeAll { target -> target !in enabledTargets }
        cachedErrorsByTarget.keys.removeAll { target -> target !in enabledTargets }
        cachedRiskByTarget.keys.removeAll { target -> target !in enabledTargets }
        // Fonte desmarcada não pode continuar alertando: a anomalia dela descreve
        // um consumo que o usuário deixou de monitorar.
        cachedSpikeByTarget.keys.removeAll { target -> target !in enabledTargets }
        publishSpikes()
    }

    private fun markRefreshing(targets: Set<UsageTargetKey>, refreshing: Boolean) {
        _refreshingTargets.update { current ->
            if (refreshing) {
                current + targets
            } else {
                current - targets
            }
        }
        _refreshingSources.value = _refreshingTargets.value.mapTo(linkedSetOf()) { target -> target.source }
    }

    private fun enabledTargets(): Set<UsageTargetKey> = enabledTargetsOf(enabledApis.value, anthropicProfiles.value)

    private suspend fun persistDashboardCache() {
        val cacheUseCase = saveDashboardCache ?: return
        val snapshot = stateMutex.withLock {
            cachedStatsByTarget.values.filter { stats -> isPersistableDashboardStats(stats) }
        }
        if (snapshot.isEmpty()) {
            return
        }
        cacheUseCase(snapshot, clock.now())
    }

    private suspend fun persistSnapshot(stats: ApiUsageStats, capturedAt: Instant) {
        val persistenceResult = recordUsageSnapshot(stats, capturedAt)
        if (persistenceResult.isFailure) {
            // Persistencia de historico nao pode degradar o refresh principal.
        }
    }

    /**
     * @param overwriteExisting `false` no caminho do cache de disco: uma coleta
     * pode ter completado no meio do restore, e a projeção dela é a mais nova.
     * Mesma regra que o consumo restaurado segue em [loadCachedStateIfAvailable].
     */
    private suspend fun refreshHistoryDerivedState(
        target: UsageTargetKey,
        stats: ApiUsageStats,
        capturedAt: Instant,
        overwriteExisting: Boolean = true
    ) {
        val historyUseCase = getUsageHistory ?: return
        val series = runCatching {
            historyUseCase(
                source = stats.source,
                range = HistoryRange.LAST_7_DAYS,
                accountKey = stats.accountContext?.key,
                now = capturedAt
            )
        }.getOrNull()?.series ?: return

        val risks = riskSummariesOf(series)
        // A anomalia de gasto sai do **mesmo** relatório, e não de uma leitura
        // própria: uma segunda ida ao SQLite por alvo a cada dez minutos pagaria
        // de novo por dado idêntico.
        val spikes = spikesOf(series, target, stats, capturedAt, alertTimeZone, spikeFactorProvider())

        // Sob o mutex porque o restore do cache e a coleta escrevem no mesmo
        // mapa: `mutableMapOf` não aguenta dois escritores.
        stateMutex.withLock {
            if (overwriteExisting || target !in cachedRiskByTarget) {
                cachedRiskByTarget[target] = risks
                cachedSpikeByTarget[target] = spikes
            }
            publishSpikes()
        }
    }

    /**
     * Republica a lista achatada de anomalias.
     *
     * **Só sob o [stateMutex]**, como [publishUiState]: um alvo por coroutine
     * escreve `cachedSpikeByTarget`, e percorrer o mapa fora do lock daria
     * `ConcurrentModificationException` numa coleta com duas contas.
     *
     * Remonta a lista inteira em vez de acrescentar à anterior, porque um alvo que
     * deixou de ter anomalia precisa sumir dela.
     */
    private fun publishSpikes() {
        _spikes.value = cachedSpikeByTarget.values.flatten()
    }

    /**
     * Entrega o pacote ao sistema. Chamada no encerramento, depois de o resto do
     * app ter fechado.
     */
    fun scheduleUpdateOnExit() {
        updates.scheduleOnExit()
    }

    /** Ação da faixa no estado pronto. Sem artefato preparado não faz nada. */
    fun restartAndUpdateNow() {
        updates.restartAndUpdateNow()
    }

    private fun scheduleNextRefresh(baseTime: Instant = clock.now()) {
        scheduledRefreshAt = baseTime + config.pollInterval
        _nextRefreshAt.value = scheduledRefreshAt
        onNextRefreshAtChanged(scheduledRefreshAt)
        pollWakeUpSignal.trySend(Unit)
    }
}

package com.usagemonitor.presentation.viewmodel

import com.usagemonitor.domain.entity.ACTIVITY_TIME_ZONE_ID
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.AnthropicProfileRef
import com.usagemonitor.domain.entity.CodexProfileRef
import com.usagemonitor.domain.entity.DEFAULT_SPIKE_FACTOR
import com.usagemonitor.domain.entity.HistoryRange
import com.usagemonitor.domain.entity.UsageSpike
import com.usagemonitor.domain.entity.QuotaRiskSummary
import com.usagemonitor.domain.entity.QuotaSeriesKey
import com.usagemonitor.domain.entity.isReadingFreshEnough
import com.usagemonitor.domain.entity.looksLikeWakeFromSleep
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.domain.entity.BreadcrumbCategory
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
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
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlin.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

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
    /**
     * Há sessão CLI rodando (issue #269): a cadência cai de 5 min para 60 s. Global,
     * como no Codenotch.
     */
    private val isBusy: StateFlow<Boolean> = MutableStateFlow(false),
    private val anthropicProfiles: StateFlow<List<AnthropicProfileRef>> =
        MutableStateFlow(listOf(AnthropicProfileRef.DEFAULT)),
    /** Contas Codex extras (issue #329); a padrão (`~/.codex`) não entra aqui. */
    private val codexProfiles: StateFlow<List<CodexProfileRef>> = MutableStateFlow(emptyList()),
    private val config: DashboardViewModelConfig = DashboardViewModelConfig(),
    private val persistedNextRefreshAt: Instant? = null,
    private val onNextRefreshAtChanged: (Instant) -> Unit = {},
    /** Prazos de backoff gravados antes do último encerramento (issue #269). */
    persistedRateLimitBackoffs: Map<UsageTargetKey, Instant> = emptyMap(),
    onRateLimitBackoffChanged: (Map<UsageTargetKey, Instant>) -> Unit = {},
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
    private val pendingPersistedRefreshAt: Instant? = persistedNextRefreshAt?.takeIf { it > clock.now() }
    private val initialScheduledRefreshAt: Instant = pendingPersistedRefreshAt ?: (clock.now() + config.idlePollInterval)

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _nextRefreshAt = MutableStateFlow(initialScheduledRefreshAt)

    // O último prazo **gravado**, não o da tela (issue #331). O inicial nunca é
    // gravado, e no Windows o relógio fica parado por até ~15 ms: a primeira
    // coleta saía no mesmo instante do construtor, o prazo dela empatava com o
    // inicial e a gravação era pulada.
    private val lastPersistedNextRefreshAt = AtomicReference(persistedNextRefreshAt)
    val nextRefreshAt: StateFlow<Instant> = _nextRefreshAt.asStateFlow()

    private val _currentPollInterval = MutableStateFlow(pollIntervalFor(isBusy.value))

    /**
     * A cadência em vigor, 60 s ou 5 min (issue #269): é a volta inteira do
     * relógio da contagem no balão da engrenagem (#293).
     */
    val currentPollInterval: StateFlow<Duration> = _currentPollInterval.asStateFlow()

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
    private val historyMutex = Mutex()
    private val cachedStatsByTarget = mutableMapOf<UsageTargetKey, ApiUsageStats>()
    private val cachedErrorsByTarget = mutableMapOf<UsageTargetKey, UiApiError>()
    private val cachedRiskByTarget = mutableMapOf<UsageTargetKey, Map<QuotaSeriesKey, QuotaRiskSummary>>()
    private val cachedSpikeByTarget = mutableMapOf<UsageTargetKey, List<UsageSpike>>()
    private val sourceFetchSemaphore = Semaphore(config.maxConcurrentSourceFetches.coerceAtLeast(1))
    private val pollWakeUpSignal = Channel<Unit>(capacity = Channel.CONFLATED)
    private val initialFetchCancelled = AtomicBoolean(false)
    private var countdownJob: Job? = null
    private var initFetchJob: Job? = null
    private val scheduler = DashboardRefreshScheduler(
        initialBackoffs = persistedRateLimitBackoffs,
        onBackoffChanged = onRateLimitBackoffChanged,
        // Reabrir o app dentro do prazo gravado não coleta de novo; sem prazo, o
        // arranque conta como a tentativa — é a coleta inicial que a faz.
        initialAttemptAnchor = pendingPersistedRefreshAt?.minus(config.idlePollInterval) ?: clock.now()
    )
    private val failures = DashboardFailureHandler(
        breadcrumbs = breadcrumbs,
        scheduler = scheduler,
        clock = clock,
        profiles = { anthropicProfiles.value },
        codexProfiles = { codexProfiles.value },
        onToast = { toast -> _toastMessage.value = toast }
    )
    private val targetFetcher = DashboardTargetFetcher(
        getAnthropicUsage, getMiniMaxUsage, getCodexUsage, getDeepSeekUsage, getOpenCodeUsage, getOpenCodeGoUsage,
        getKiloUsage, getOpenRouterUsage, getGeminiUsage, getCursorUsage, getAntigravityUsage,
        anthropicProfiles, codexProfiles
    )
    private val fetchQueue = DashboardFetchQueue(allTargets = ::enabledTargets, perform = ::performFetch)

    init {
        val isPersistedRefreshStillPending = pendingPersistedRefreshAt != null
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
                        isReadingFreshEnough(stats.fetchedAt, clock.now()) &&
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
     * Laço único de despertar, por alvo (issue #269).
     *
     * A cada volta coleta só os alvos devidos (`DashboardRefreshScheduler.dueTargets`):
     * 60 s com sessão CLI rodando, 5 min sem, já no reset vencido, e nunca em
     * backoff. Dorme até o que vier primeiro — a próxima cadência, o próximo
     * reset conhecido ou um sinal (coleta nova, sessão começando, janela voltando).
     *
     * A janela minimizada segura só a cadência. O reset coleta mesmo escondida:
     * esperar a visibilidade deixaria o card congelado na janela que já venceu.
     */
    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            launch {
                isBusy.collect { busy ->
                    _currentPollInterval.value = pollIntervalFor(busy)
                    nudgeCountdown()
                }
            }
            launch { isAppVisible.collect { visible -> if (visible) nudgeCountdown() } }
            while (true) {
                // Uma volta que lança não pode encerrar o laço (issue #326): o
                // `SupervisorJob` manteria o app vivo e a coleta pararia calada,
                // com o card mostrando o último número para sempre.
                try {
                    runCountdownTick()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Throwable) {
                    breadcrumbs.record(BreadcrumbCategory.ERROR, "laço de coleta falhou: ${failure::class.simpleName}")
                    delay(config.pollLoopRecoveryDelay)
                }
            }
        }
    }

    private suspend fun runCountdownTick() {
        val now = clock.now()
        val visible = isAppVisible.value
        val tick = stateMutex.withLock {
            scheduler.dueTargets(enabledTargets(), cachedStatsByTarget::get, now, isBusy.value, config)
        }
        val dispatch = if (visible) tick.due else tick.byReset
        if (dispatch.isNotEmpty()) {
            scheduler.recordAttempt(dispatch, now)
            viewModelScope.launch { requestFetch(dispatch, preserveDataOnFailure = true) }
        }
        val nextPoll = publishNextPoll(now)
        val nextReset = nextQuotaResetTarget()
        val wakeUpAt = listOfNotNull(nextPoll.takeIf { visible }, nextReset).minOrNull()
        val waitMillis = wakeUpAt?.let { (it - now).inWholeMilliseconds.coerceAtLeast(0L) } ?: Long.MAX_VALUE
        val signalled = withTimeoutOrNull(waitMillis) { pollWakeUpSignal.receive() } != null
        if (!signalled && wakeUpAt != null &&
            looksLikeWakeFromSleep(wakeUpAt, clock.now(), config.sleepJumpThreshold)
        ) {
            breadcrumbs.record(BreadcrumbCategory.USE_CASE, "volta do sleep: todas as fontes devidas")
            scheduler.forgetAttempts()
        }
    }

    private fun pollIntervalFor(busy: Boolean): Duration =
        if (busy) config.activePollInterval else config.idlePollInterval

    /**
     * Publica a próxima coleta por cadência — o instante que o rodapé e a HUD
     * contam — e a grava para o arranque seguinte. O reset não entra: é uma
     * antecipação, não um novo prazo a anunciar.
     */
    private fun publishNextPoll(now: Instant = clock.now()): Instant {
        val next = scheduler.nextPollAt(enabledTargets(), now, isBusy.value, config)
            ?: (now + pollIntervalFor(isBusy.value))
        _nextRefreshAt.value = next
        if (lastPersistedNextRefreshAt.getAndSet(next) != next) {
            onNextRefreshAtChanged(next)
        }
        return next
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
    ) = fetchQueue.request(targets, preserveDataOnFailure)

    private suspend fun performFetch(
        targets: Set<UsageTargetKey>,
        preserveDataOnFailure: Boolean
    ) {
        val enabled = enabledTargets()
        val snapshotCapturedAt = clock.now()
        scheduler.retainOnly(enabled)
        // Alvo em backoff não vai à rede (issue #269): cada chamada durante o
        // limite só o prolonga. A leitura dele fica como está.
        val effectiveTargets = targets.filterTo(linkedSetOf()) { target ->
            target in enabled && !scheduler.isInBackoff(target, snapshotCapturedAt)
        }

        if (effectiveTargets.isEmpty()) {
            stateMutex.withLock {
                pruneDisabledTargets(enabled)
                publishUiState(enabled)
            }
            return
        }

        scheduler.recordAttempt(effectiveTargets, snapshotCapturedAt)
        markRefreshing(effectiveTargets, refreshing = true)
        val anthropicOrder = anthropicStaggerOrder(effectiveTargets)

        try {
            coroutineScope {
                effectiveTargets.map { target ->
                    async {
                        // Contas Anthropic devidas juntas saem espaçadas: rajada nos
                        // endpoints de uso e de token é o que o ai-usagebar viu virar 429.
                        val order = anthropicOrder[target] ?: 0
                        if (order > 0) delay(config.anthropicStagger * order)
                        val result = sourceFetchSemaphore.withPermit {
                            runCatching {
                                withTimeout(config.timeoutFor(target.source)) {
                                    targetFetcher.fetch(target).getOrThrow()
                                }
                            }
                        }
                        // Cada fonte entra na tela quando chega (issue #269): antes a
                        // mais lenta segurava todas até o `awaitAll`.
                        applyFetchResult(target, result, snapshotCapturedAt)
                    }
                }.awaitAll()
            }

            // Os resets recém-coletados podem ser anteriores ao alvo em que o
            // laço já está dormindo; sem isto ele só os leria no poll seguinte.
            publishNextPoll()
            nudgeCountdown()

            persistDashboardCache()
        } finally {
            markRefreshing(effectiveTargets, refreshing = false)
        }
    }

    /** O resultado de um alvo, aplicado e publicado sozinho. */
    private suspend fun applyFetchResult(target: UsageTargetKey, result: Result<ApiUsageStats>, capturedAt: Instant) {
        var stats: ApiUsageStats? = null
        var error: UiApiError? = null
        result
            .onSuccess { fetched ->
                if (isPersistableDashboardStats(fetched)) {
                    scheduler.recordSuccess(target, capturedAt)
                    stats = fetched
                    // Sequencial, como era no laço único: a conexão do SQLite é
                    // serializada e disputada pelo indexador de sessões CLI.
                    historyMutex.withLock {
                        persistSnapshot(fetched, capturedAt)
                        refreshHistoryDerivedState(target, fetched, capturedAt)
                    }
                } else {
                    error = failures.handle(target, IllegalStateException("A resposta do Codex não trouxe nenhuma janela utilizável."))
                }
            }
            .onFailure { failure -> error = failures.handle(target, failure) }

        stateMutex.withLock {
            val latestEnabledTargets = enabledTargets()
            pruneDisabledTargets(latestEnabledTargets)
            if (target in latestEnabledTargets) {
                applyTargetOutcome(target, stats, error, capturedAt)
            }
            publishUiState(latestEnabledTargets)
        }
    }

    /** Sob o [stateMutex]: a leitura nova, ou a anterior mantida (`statsRetainedAfterFailure`) com o erro. */
    private fun applyTargetOutcome(target: UsageTargetKey, stats: ApiUsageStats?, error: UiApiError?, now: Instant) {
        if (stats != null) {
            cachedStatsByTarget[target] = stats
            cachedErrorsByTarget.remove(target)
            return
        }
        val retained = statsRetainedAfterFailure(target, cachedStatsByTarget[target], scheduler.lastSuccessAt(target), error, now)
        if (retained == null) cachedStatsByTarget.remove(target) else cachedStatsByTarget[target] = retained
        if (error == null) cachedErrorsByTarget.remove(target) else cachedErrorsByTarget[target] = error
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
        // Clique durante o backoff não vai à rede, e diz até quando (issue #269).
        scheduler.backoffUntil(target, clock.now())?.let { until ->
            _toastMessage.value = DashboardToast.RateLimit(target.source, retryAt = until)
            return
        }
        invalidateAntigravityReadingIfRequested(target.source)
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

    private fun enabledTargets(): Set<UsageTargetKey> = targetFetcher.enabledTargets(enabledApis.value)

    private suspend fun persistDashboardCache() {
        val cacheUseCase = saveDashboardCache ?: return
        val snapshot = stateMutex.withLock {
            cachedStatsByTarget.values
                .filter { stats -> isPersistableDashboardStats(stats) }
                .map { stats -> stats.copy(fetchedAt = stats.fetchedAt ?: scheduler.lastSuccessAt(stats.targetKey)) }
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
}

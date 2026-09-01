package com.usagemonitor.presentation.viewmodel

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageNotice
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.AnthropicProfileRef
import com.usagemonitor.domain.entity.HistoryRange
import com.usagemonitor.domain.entity.QuotaRiskSummary
import com.usagemonitor.domain.entity.QuotaSeriesKey
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.domain.entity.AppUpdateInfo
import com.usagemonitor.domain.entity.BreadcrumbCategory
import com.usagemonitor.domain.entity.breadcrumbReasonOf
import com.usagemonitor.domain.repository.BreadcrumbRecorder
import com.usagemonitor.domain.repository.NoOpBreadcrumbRecorder
import com.usagemonitor.domain.repository.AppUpdateInstaller
import com.usagemonitor.domain.repository.AppUpdatePreparation
import com.usagemonitor.domain.repository.AppUpdateSupport
import com.usagemonitor.domain.repository.KiloRepository
import com.usagemonitor.domain.repository.OpenCodeGoRepository
import com.usagemonitor.domain.repository.OpenCodeRepository
import com.usagemonitor.domain.repository.OpenRouterRepository
import com.usagemonitor.domain.usecase.CheckForAppUpdateUseCase
import com.usagemonitor.domain.usecase.GetAnthropicUsageUseCase
import com.usagemonitor.domain.usecase.GetCodexUsageUseCase
import com.usagemonitor.domain.usecase.GetDeepSeekUsageUseCase
import com.usagemonitor.domain.usecase.GetKiloUsageUseCase
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
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLHandshakeException

private const val HTTP_RATE_LIMIT_MARKER = "HTTP 429"

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
    private val getOpenCodeUsage: GetOpenCodeUsageUseCase = GetOpenCodeUsageUseCase(
        object : OpenCodeRepository {
            override suspend fun getUsage(): Result<ApiUsageStats> {
                return Result.failure(IllegalStateException("OpenCode local database is unavailable"))
            }
        }
    ),
    /**
     * Default que falha pelo mesmo motivo de [getOpenCodeUsage]: a fonte é
     * opt-in e uma build sem o repositório ligado tem de dizer o que falta, não
     * ficar em carga eterna. A mensagem é a de chave ausente porque é essa a
     * condição verdadeira de quem não configurou nada.
     */
    private val getOpenCodeGoUsage: GetOpenCodeGoUsageUseCase = GetOpenCodeGoUsageUseCase(
        object : OpenCodeGoRepository {
            override suspend fun getUsage(): Result<ApiUsageStats> {
                return Result.failure(
                    IllegalStateException(
                        "Chave da API OpenCode não configurada. Abra Configurações > APIs e informe a chave."
                    )
                )
            }
        }
    ),
    private val getKiloUsage: GetKiloUsageUseCase = GetKiloUsageUseCase(
        object : KiloRepository {
            override suspend fun getUsage(): Result<ApiUsageStats> {
                return Result.failure(IllegalStateException("Kilo local database is unavailable"))
            }
        }
    ),
    /**
     * Default que falha pelo mesmo motivo de [getOpenCodeGoUsage]: fonte
     * opt-in dependente de chave, e uma build sem o repositório ligado tem de
     * dizer o que falta em vez de ficar em carga eterna.
     */
    private val getOpenRouterUsage: GetOpenRouterUsageUseCase = GetOpenRouterUsageUseCase(
        object : OpenRouterRepository {
            override suspend fun getUsage(): Result<ApiUsageStats> {
                return Result.failure(
                    IllegalStateException(
                        "Chave da API OpenRouter não configurada. Abra Configurações > APIs e informe a chave."
                    )
                )
            }
        }
    ),
    private val checkForAppUpdate: CheckForAppUpdateUseCase? = null,
    private val appUpdateReleaseOpener: AppUpdateReleaseOpener = UnsupportedAppUpdateReleaseOpener,
    /**
     * Nulo é "esta build não traz o mecanismo", e nada é baixado nem executado.
     * É o estado do PR 1 do plano de auto-update.
     */
    private val appUpdateInstaller: AppUpdateInstaller? = null,
    private val autoUpdateEnabled: StateFlow<Boolean> = MutableStateFlow(false),
    /**
     * Encerramento ordenado pedido pela faixa ("Reiniciar e atualizar agora").
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
    private data class PendingFetchRequest(
        val targets: Set<UsageTargetKey>,
        val preserveDataOnFailure: Boolean
    )

    private val initialScheduledRefreshAt: Instant =
        persistedNextRefreshAt?.takeIf { it > clock.now() } ?: (clock.now() + config.pollInterval)

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _nextRefreshAt = MutableStateFlow(initialScheduledRefreshAt)
    val nextRefreshAt: StateFlow<Instant> = _nextRefreshAt.asStateFlow()

    private val _refreshingTargets = MutableStateFlow<Set<UsageTargetKey>>(emptySet())
    val refreshingTargets: StateFlow<Set<UsageTargetKey>> = _refreshingTargets.asStateFlow()
    private val _refreshingSources = MutableStateFlow<Set<ApiSource>>(emptySet())
    val refreshingSources: StateFlow<Set<ApiSource>> = _refreshingSources.asStateFlow()

    private val _toastMessage = MutableStateFlow<DashboardToast?>(null)
    val toastMessage: StateFlow<DashboardToast?> = _toastMessage.asStateFlow()

    private val _appUpdateState = MutableStateFlow<AppUpdateUiState?>(null)
    val appUpdateState: StateFlow<AppUpdateUiState?> = _appUpdateState.asStateFlow()

    private val viewModelScope = CoroutineScope(SupervisorJob() + config.workerDispatcher)
    private val stateMutex = Mutex()
    private val updateMutex = Mutex()
    private val fetchMutex = Mutex()
    private val pendingFetchMutex = Mutex()
    private val cachedStatsByTarget = mutableMapOf<UsageTargetKey, ApiUsageStats>()
    private val cachedErrorsByTarget = mutableMapOf<UsageTargetKey, UiApiError>()
    private val cachedRiskByTarget = mutableMapOf<UsageTargetKey, Map<QuotaSeriesKey, QuotaRiskSummary>>()
    private val sourceFetchSemaphore = Semaphore(config.maxConcurrentSourceFetches.coerceAtLeast(1))
    private val pollWakeUpSignal = Channel<Unit>(capacity = Channel.CONFLATED)
    private val initialFetchCancelled = AtomicBoolean(false)
    @Volatile private var scheduledRefreshAt: Instant = initialScheduledRefreshAt
    private var countdownJob: Job? = null
    private var initFetchJob: Job? = null
    private var pendingFetchRequest: PendingFetchRequest? = null

    /**
     * Artefato já baixado e conferido, esperando o encerramento.
     *
     * `@Volatile` porque quem escreve é uma corrotina e quem lê é a thread do
     * shutdown hook — mesmo motivo do `scheduledRefreshAt` acima.
     */
    @Volatile private var preparedUpdate: AppUpdatePreparation? = null

    /**
     * Versão **em voo**, e não só a preparada.
     *
     * É esta variável que impede o poll de 10 min de cancelar e reiniciar um
     * download em andamento: durante o download `preparedUpdate` é nulo, e
     * comparar só por ele fazia o ciclo recomeçar do zero a cada passada — um
     * download de 120 MB que levasse mais de 10 min nunca terminaria.
     */
    @Volatile private var downloadingVersion: String? = null
    private var updateDownloadJob: Job? = null

    /**
     * Espera antes de tentar de novo a mesma versão.
     *
     * Sem ela, uma falha recorrente rebaixaria 120 MB a cada 10 min — ~17 GB por
     * dia, indefinidamente. Zerada quando a versão anunciada muda: release nova
     * é uma tentativa nova.
     */
    private var updateBackoff: UpdateBackoff? = null

    private data class UpdateBackoff(
        val version: String,
        val attempts: Int,
        val retryAfter: Instant,
        val reason: AppUpdateFailureReason
    )

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
            startUpdateCheckLoop()
        }
        startAutoUpdateSwitchWatcher()
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
                breadcrumbs.record(
                    BreadcrumbCategory.ERROR,
                    "cache do dashboard não pôde ser lido: ${breadcrumbReasonOf(error)}"
                )
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
            refreshRiskSummaries(stats.targetKey, stats, now, overwriteExisting = false)
        }
        stateMutex.withLock {
            publishUiState(enabledTargets())
        }
    }

    private fun startUpdateCheckLoop() {
        viewModelScope.launch {
            checkForUpdate()
            while (true) {
                delay(config.updateCheckIntervalWhileRunning)
                checkForUpdate()
            }
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
        val now = clock.now()
        val snapshot = stateMutex.withLock { cachedStatsByTarget.values.toList() }

        var earliest: Instant? = null
        for (stats in snapshot) {
            for (quota in stats.quotas) {
                if (!quota.hasKnownResetAt || quota.periodEndAt <= now) {
                    continue
                }
                val currentEarliest = earliest
                if (currentEarliest == null || quota.periodEndAt < currentEarliest) {
                    earliest = quota.periodEndAt
                }
            }
        }

        val resolvedEarliest = earliest ?: return null
        return resolvedEarliest + config.quotaResetGrace
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
            val existingRequest = pendingFetchRequest
            pendingFetchRequest = when {
                existingRequest == null -> PendingFetchRequest(targets, preserveDataOnFailure)
                !existingRequest.preserveDataOnFailure || !preserveDataOnFailure -> {
                    PendingFetchRequest(enabledTargets(), preserveDataOnFailure = false)
                }

                else -> {
                    PendingFetchRequest(
                        targets = existingRequest.targets + targets,
                        preserveDataOnFailure = true
                    )
                }
            }
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
                            refreshRiskSummaries(target, stats, snapshotCapturedAt)
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
                        val existingStats = cachedStatsByTarget[target]
                        val canPreserveCodexCache =
                            target.source == ApiSource.CODEX &&
                                existingStats != null &&
                                isPersistableDashboardStats(existingStats)
                        val shouldRemoveData =
                            (!preserveDataOnFailure && !canPreserveCodexCache) ||
                                target !in cachedStatsByTarget
                        if (shouldRemoveData) {
                            cachedStatsByTarget.remove(target)
                        } else if (canPreserveCodexCache) {
                            cachedStatsByTarget[target] = existingStats!!.copy(
                                notices = existingStats.notices + ApiUsageNotice.SOURCE_UNSTABLE
                            )
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
        scheduleNextRefresh()
        viewModelScope.launch {
            requestFetch(targets = enabledTargets())
            checkForUpdate()
        }
    }

    fun refresh(source: ApiSource) {
        if (source !in enabledApis.value) {
            return
        }

        breadcrumbs.record(BreadcrumbCategory.USE_CASE, "atualização de ${source.name} pedida")
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
        scheduleNextRefresh()
        viewModelScope.launch {
            requestFetch(targets = setOf(target), preserveDataOnFailure = true)
        }
    }

    fun openUpdateReleasePage() {
        // Qualquer estado com versão anunciada abre a página: a faixa oferece o
        // caminho manual também depois de a atualização automática falhar.
        val update = _appUpdateState.value?.update ?: return
        appUpdateReleaseOpener.open(update.releasePageUrl)
            .onFailure { error ->
                _toastMessage.value = DashboardToast.ReleasePageError(
                    error.message ?: "Unknown error"
                )
            }
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
        }
    }

    private fun handleTargetFailure(target: UsageTargetKey, error: Throwable): UiApiError? {
        val source = target.source
        val targetLabel = if (source == ApiSource.ANTHROPIC) {
            anthropicProfiles.value.firstOrNull { it.id == target.profileId }?.let { "Anthropic — ${it.label}" }
        } else {
            null
        }
        val originalMessage = error.message ?: error::class.simpleName ?: "erro desconhecido"
        // Falha de conectividade (proxy ausente/incorreto, DNS, timeout de conexão)
        // é classificada pelo TIPO da exceção, não por substring da mensagem: o
        // texto de `ConnectException`/`SocketTimeoutException` varia por JVM e SO,
        // e não dá para confiar nele. O marcador fixo entra como prefixo — mesmo
        // mecanismo de `HTTP_RATE_LIMIT_MARKER` — para o `UiApiError`/`warningFor`
        // existentes reconhecerem por substring sem precisar de um enum de erro
        // novo. HTTP 407 (proxy exige credencial) não passa por aqui: chega como
        // resposta HTTP normal e cai no mecanismo de marcador de status já usado
        // por 429/503 (ver `RemoteApiDataSource.requireSuccess`).
        val rawMessage = if (isConnectivityFailure(error)) {
            "$NETWORK_CONNECTIVITY_MARKER ($originalMessage)"
        } else {
            originalMessage
        }
        val message = sanitizeUiErrorMessage(source, rawMessage)

        // Funil único de toda falha de coleta, e por isso o único ponto de
        // gravação: um passo por fonte que falhou, em qualquer caminho — poll
        // silencioso, atualização pedida ou recarga de um banner.
        //
        // Vai a mensagem **saneada**, a mesma que a tela mostra, e nunca a crua:
        // `sanitizeUiErrorMessage` já é o filtro que decide o que pode aparecer
        // para o usuário, e o relatório é ainda mais público que a tela dele.
        breadcrumbs.record(BreadcrumbCategory.API_CALL, "${source.name}: falhou — $message")

        val uiError = UiApiError(target = target, message = message, rawMessage = rawMessage, targetLabel = targetLabel)

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

    /**
     * `error.cause` também é checado porque o Ktor às vezes envelopa a exceção de
     * socket original numa própria (ex.: `HttpRequestTimeoutException` não estende
     * `SocketTimeoutException` e não carrega a causa de rede como `cause` sempre,
     * mas outros wrappers do client engine podem).
     */
    private fun isConnectivityFailure(error: Throwable): Boolean {
        return isConnectivityException(error) || isConnectivityException(error.cause)
    }

    private fun isConnectivityException(error: Throwable?): Boolean {
        return when (error) {
            null -> false
            // `ConnectTimeoutException` do Ktor estende `ConnectException`, então
            // este branch já cobre o timeout de conexão do próprio Ktor.
            is UnknownHostException,
            is NoRouteToHostException,
            is ConnectException,
            is SocketTimeoutException,
            is SSLHandshakeException,
            is HttpRequestTimeoutException -> true
            else -> false
        }
    }

    private fun publishUiState(enabledTargets: Set<UsageTargetKey>) {
        if (enabledTargets.isEmpty()) {
            _uiState.value = UiState.NoApisEnabled
            return
        }

        val stats = enabledTargets
            .sortedWith(compareBy<UsageTargetKey> { it.source.ordinal }.thenBy { it.profileId.orEmpty() })
            .mapNotNull { target -> cachedStatsByTarget[target] }

        val errors = enabledTargets
            .sortedWith(compareBy<UsageTargetKey> { it.source.ordinal }.thenBy { it.profileId.orEmpty() })
            .mapNotNull { target -> cachedErrorsByTarget[target] }

        val riskSummaries = enabledTargets
            .mapNotNull { target -> cachedRiskByTarget[target]?.let { risks -> target to risks } }
            .toMap()

        _uiState.value = if (stats.isNotEmpty()) {
            UiState.Success(stats, errors, riskSummaries)
        } else {
            UiState.Error(errors)
        }
    }

    private fun pruneDisabledTargets(enabledTargets: Set<UsageTargetKey>) {
        cachedStatsByTarget.keys.removeAll { target -> target !in enabledTargets }
        cachedErrorsByTarget.keys.removeAll { target -> target !in enabledTargets }
        cachedRiskByTarget.keys.removeAll { target -> target !in enabledTargets }
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

    private fun enabledTargets(): Set<UsageTargetKey> {
        val enabledSources = enabledApis.value
        val targets = linkedSetOf<UsageTargetKey>()
        enabledSources.sortedBy { it.ordinal }.forEach { source ->
            if (source == ApiSource.ANTHROPIC) {
                anthropicProfiles.value.forEach { profile ->
                    targets += UsageTargetKey(ApiSource.ANTHROPIC, profile.id)
                }
            } else {
                targets += UsageTargetKey.forSource(source)
            }
        }
        return targets
    }

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

    /** A quota Codex válida pode ser parcial: o plano pode expor uma só janela. */
    private fun isPersistableDashboardStats(stats: ApiUsageStats): Boolean {
        if (stats.source != ApiSource.CODEX) {
            return true
        }
        if (stats.quotas.isEmpty()) {
            return false
        }
        return stats.quotas.all(::isValidCodexQuota)
    }

    private fun isValidCodexQuota(quota: QuotaInfo): Boolean {
        return quota.unit == UsageUnit.PERCENTAGE &&
            quota.total > 0L && quota.used in 0L..quota.total
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
    private suspend fun refreshRiskSummaries(
        target: UsageTargetKey,
        stats: ApiUsageStats,
        capturedAt: Instant,
        overwriteExisting: Boolean = true
    ) {
        val historyUseCase = getUsageHistory ?: return
        val risks = runCatching {
            historyUseCase(
                source = stats.source,
                range = HistoryRange.LAST_7_DAYS,
                accountKey = stats.accountContext?.key,
                now = capturedAt
            )
        }.getOrNull()?.series
            ?.mapNotNull { series -> series.riskSummary?.let { risk -> series.seriesKey to risk } }
            ?.toMap()
            ?: return

        // Sob o mutex porque o restore do cache e a coleta escrevem no mesmo
        // mapa: `mutableMapOf` não aguenta dois escritores.
        stateMutex.withLock {
            if (overwriteExisting || target !in cachedRiskByTarget) {
                cachedRiskByTarget[target] = risks
            }
        }
    }

    private suspend fun checkForUpdate() {
        val updateUseCase = checkForAppUpdate ?: return

        updateMutex.withLock {
            updateUseCase(currentAppVersion)
                .onSuccess { update ->
                    if (update == null) {
                        forgetPendingUpdate()
                        _appUpdateState.value = null
                        return@onSuccess
                    }

                    onUpdateAnnounced(update)
                }
                .onFailure {
                    // Falha silenciosa: UI mantém estado anterior; próxima janela de poll tenta de novo.
                }
        }
    }

    /**
     * Decide o que fazer com a versão anunciada. Chamada a cada passada do laço
     * de verificação, então a ordem das guardas é o que impede trabalho repetido.
     */
    private fun onUpdateAnnounced(update: AppUpdateInfo) {
        // Release diferente da que estava em curso: o que foi baixado ou falhou
        // antes não descreve mais nada.
        if (trackedVersion() != null && trackedVersion() != update.version) {
            forgetPendingUpdate()
        }

        val prepared = preparedUpdate
        if (prepared != null && prepared.version == update.version) {
            _appUpdateState.value = AppUpdateUiState.Ready(update)
            return
        }

        // Download em voo da mesma versão: não recomeçar. Esta é a guarda que
        // faltava e que fazia o poll de 10 min reiniciar o download do zero.
        if (downloadingVersion == update.version && updateDownloadJob?.isActive == true) {
            return
        }

        if (!canDownloadAutomatically()) {
            _appUpdateState.value = AppUpdateUiState.Available(update)
            return
        }

        val backoff = updateBackoff
        if (backoff != null && backoff.version == update.version) {
            if (clock.now() < backoff.retryAfter) {
                // Continua mostrando a falha: a versão instalada está intacta e o
                // caminho manual segue oferecido na faixa.
                _appUpdateState.value = AppUpdateUiState.Failed(update, backoff.reason)
                return
            }
            if (backoff.attempts >= config.updateRetryBackoff.size) {
                _appUpdateState.value = AppUpdateUiState.Failed(update, backoff.reason)
                return
            }
        }

        startUpdateDownload(update)
    }

    private fun canDownloadAutomatically(): Boolean {
        val installer = appUpdateInstaller ?: return false
        if (!autoUpdateEnabled.value) {
            return false
        }
        return installer.support() == AppUpdateSupport.SUPPORTED
    }

    private fun trackedVersion(): String? {
        return downloadingVersion ?: preparedUpdate?.version ?: updateBackoff?.version
    }

    private fun startUpdateDownload(update: AppUpdateInfo) {
        val installer = appUpdateInstaller ?: return

        downloadingVersion = update.version
        _appUpdateState.value = AppUpdateUiState.Downloading(update, percent = null)

        updateDownloadJob = viewModelScope.launch {
            // O percentual só é publicado quando o número inteiro muda: são ~1900
            // blocos de 64 KB em 120 MB, e emitir a cada bloco faria a tela
            // recompor duas mil vezes para mostrar a mesma dezena.
            var lastPublishedPercent = -1
            val result = installer.prepare(update) { downloadedBytes, totalBytes ->
                val percent = percentOf(downloadedBytes, totalBytes)
                if (percent != null && percent != lastPublishedPercent) {
                    lastPublishedPercent = percent
                    _appUpdateState.value = AppUpdateUiState.Downloading(update, percent)
                }
            }

            downloadingVersion = null
            result
                .onSuccess { preparation ->
                    preparedUpdate = preparation
                    updateBackoff = null
                    _appUpdateState.value = AppUpdateUiState.Ready(update)
                }
                .onFailure {
                    registerUpdateFailure(update, AppUpdateFailureReason.DOWNLOAD)
                }
        }
    }

    private fun percentOf(downloadedBytes: Long, totalBytes: Long?): Int? {
        if (totalBytes == null || totalBytes <= 0L) {
            return null
        }
        return ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
    }

    private fun registerUpdateFailure(update: AppUpdateInfo, reason: AppUpdateFailureReason) {
        val previousAttempts = updateBackoff?.takeIf { it.version == update.version }?.attempts ?: 0
        val attempts = previousAttempts + 1
        val waitFor = config.updateRetryBackoff.getOrNull(attempts - 1)
            ?: config.updateRetryBackoff.last()

        updateBackoff = UpdateBackoff(
            version = update.version,
            attempts = attempts,
            retryAfter = clock.now() + waitFor,
            reason = reason
        )
        _appUpdateState.value = AppUpdateUiState.Failed(update, reason)
    }

    private fun forgetPendingUpdate() {
        updateDownloadJob?.cancel()
        updateDownloadJob = null
        downloadingVersion = null
        preparedUpdate = null
        updateBackoff = null
    }

    /**
     * Reage ao interruptor das Configurações, **nos dois sentidos**.
     *
     * Desligar no meio do download cancela o job **e descarta o que já estava
     * pronto**: um artefato preparado seria aplicado no encerramento, que é
     * exatamente o que o usuário acabou de recusar.
     *
     * Ligar reavalia a versão que a tela já está anunciando. Sem isso o
     * interruptor parece inerte: quem o liga com a faixa de "nova versão" na tela
     * fica olhando para ela sem nada acontecer até o poll seguinte, que pode
     * estar a 10 minutos de distância. Medido na atividade A20, com o usuário
     * ligando o interruptor e relatando que o download não começou.
     *
     * `onUpdateAnnounced` é o mesmo caminho do poll, e não um segundo: ele já
     * carrega as guardas de download em voo, de artefato pronto e de backoff.
     */
    private fun startAutoUpdateSwitchWatcher() {
        viewModelScope.launch {
            autoUpdateEnabled.collect { enabled ->
                val current = _appUpdateState.value
                if (enabled) {
                    if (current != null) {
                        onUpdateAnnounced(current.update)
                    }
                    return@collect
                }
                forgetPendingUpdate()
                if (current != null) {
                    _appUpdateState.value = AppUpdateUiState.Available(current.update)
                }
            }
        }
    }

    /**
     * Entrega o pacote ao sistema. Chamada no encerramento, depois de o resto do
     * app ter fechado — o instalador espera este processo sair de qualquer forma,
     * mas a ordem correta não custa nada.
     */
    fun scheduleUpdateOnExit() {
        val installer = appUpdateInstaller ?: return
        if (!autoUpdateEnabled.value) {
            return
        }
        val preparation = preparedUpdate ?: return
        // O Result não pode ser descartado aqui. Este é o último ponto do
        // processo em que ainda se sabe alguma coisa: se a entrega falhar, o
        // instalador não roda, não escreve recibo, e o usuário vê o app fechar e
        // não voltar — sem uma linha no disco explicando.
        installer.schedule(preparation).onFailure { error ->
            val reason = error.message?.takeIf { it.isNotBlank() }
                ?: error::class.simpleName
                ?: "unknown"
            onUpdateScheduleFailure(preparation.version, reason)
        }
    }

    /** Ação da faixa no estado pronto. Sem artefato preparado não faz nada. */
    fun restartAndUpdateNow() {
        if (appUpdateInstaller == null || preparedUpdate == null) {
            return
        }
        onRestartAndUpdateRequested()
    }

    private fun scheduleNextRefresh(baseTime: Instant = clock.now()) {
        scheduledRefreshAt = baseTime + config.pollInterval
        _nextRefreshAt.value = scheduledRefreshAt
        onNextRefreshAtChanged(scheduledRefreshAt)
        pollWakeUpSignal.trySend(Unit)
    }
}

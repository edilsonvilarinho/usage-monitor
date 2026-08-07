package com.usagemonitor.presentation.viewmodel

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.AnthropicProfileRef
import com.usagemonitor.domain.entity.HistoryRange
import com.usagemonitor.domain.entity.QuotaRiskSummary
import com.usagemonitor.domain.entity.QuotaSeriesKey
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.domain.repository.KiloRepository
import com.usagemonitor.domain.repository.OpenCodeRepository
import com.usagemonitor.domain.usecase.CheckForAppUpdateUseCase
import com.usagemonitor.domain.usecase.GetAnthropicUsageUseCase
import com.usagemonitor.domain.usecase.GetCodexUsageUseCase
import com.usagemonitor.domain.usecase.GetDeepSeekUsageUseCase
import com.usagemonitor.domain.usecase.GetKiloUsageUseCase
import com.usagemonitor.domain.usecase.GetMiniMaxUsageUseCase
import com.usagemonitor.domain.usecase.GetOpenCodeUsageUseCase
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
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.concurrent.atomic.AtomicBoolean

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
    private val getKiloUsage: GetKiloUsageUseCase = GetKiloUsageUseCase(
        object : KiloRepository {
            override suspend fun getUsage(): Result<ApiUsageStats> {
                return Result.failure(IllegalStateException("Kilo local database is unavailable"))
            }
        }
    ),
    private val checkForAppUpdate: CheckForAppUpdateUseCase? = null,
    private val appUpdateReleaseOpener: AppUpdateReleaseOpener = UnsupportedAppUpdateReleaseOpener,
    private val currentAppVersion: String = "0.0.0",
    private val clock: Clock = Clock.System,
    private val isAppVisible: StateFlow<Boolean> = MutableStateFlow(true),
    private val anthropicProfiles: StateFlow<List<AnthropicProfileRef>> =
        MutableStateFlow(listOf(AnthropicProfileRef.DEFAULT)),
    private val config: DashboardViewModelConfig = DashboardViewModelConfig(),
    private val persistedNextRefreshAt: Instant? = null,
    private val onNextRefreshAtChanged: (Instant) -> Unit = {}
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

    init {
        val isPersistedRefreshStillPending = persistedNextRefreshAt != null && persistedNextRefreshAt > clock.now()
        if (isPersistedRefreshStillPending) {
            // Ainda não é hora do próximo ciclo: reidrata a UI com o último
            // snapshot salvo em vez de deixar a tela presa em Loading.
            loadCachedStateIfAvailable()
        }
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
        if (config.autoStartCountdown) {
            startCountdown()
        }
    }

    private fun loadCachedStateIfAvailable() {
        val cacheUseCase = getCachedDashboardStats ?: return
        viewModelScope.launch {
            val cachedStats = cacheUseCase().getOrNull().orEmpty()
            if (cachedStats.isEmpty()) {
                return@launch
            }
            stateMutex.withLock {
                // Não sobrescreve dados já obtidos por uma fetch que tenha
                // completado antes desta corrotina (ex.: refresh manual do usuário).
                val enabled = enabledTargets()
                cachedStats.forEach { stats ->
                    if (stats.targetKey in enabled && stats.targetKey !in cachedStatsByTarget) {
                        cachedStatsByTarget[stats.targetKey] = stats
                    }
                }
                publishUiState(enabled)
            }
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

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (true) {
                val currentTarget = scheduledRefreshAt
                _nextRefreshAt.value = currentTarget
                val waitMillis = (currentTarget - clock.now()).inWholeMilliseconds.coerceAtLeast(0L)
                val rescheduled = withTimeoutOrNull(waitMillis) {
                    pollWakeUpSignal.receive()
                } != null
                if (rescheduled) {
                    continue
                }
                if (!isAppVisible.value) {
                    isAppVisible.first { it }
                }
                viewModelScope.launch {
                    requestFetch(targets = enabledTargets())
                }
                scheduleNextRefresh()
            }
        }
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
                        statsUpdates[target] = stats
                        errorUpdates[target] = null
                        persistSnapshot(stats, snapshotCapturedAt)
                        refreshRiskSummaries(target, stats, snapshotCapturedAt)
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
                        val shouldRemoveData = !preserveDataOnFailure || target !in cachedStatsByTarget
                        if (shouldRemoveData) {
                            cachedStatsByTarget.remove(target)
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

            persistDashboardCache()
        } finally {
            markRefreshing(effectiveTargets, refreshing = false)
        }
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    fun refresh() {
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
        scheduleNextRefresh()
        viewModelScope.launch {
            requestFetch(targets = setOf(target), preserveDataOnFailure = true)
        }
    }

    fun openUpdateReleasePage() {
        val update = (_appUpdateState.value as? AppUpdateUiState.Available)?.update ?: return
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
            ApiSource.KILO -> getKiloUsage()
        }
    }

    private fun handleTargetFailure(target: UsageTargetKey, error: Throwable): UiApiError? {
        val source = target.source
        val targetLabel = if (source == ApiSource.ANTHROPIC) {
            anthropicProfiles.value.firstOrNull { it.id == target.profileId }?.let { "Anthropic — ${it.label}" }
        } else {
            null
        }
        val rawMessage = error.message ?: error::class.simpleName ?: "erro desconhecido"
        val message = sanitizeUiErrorMessage(source, rawMessage)

        if (message.contains(HTTP_RATE_LIMIT_MARKER, ignoreCase = true)) {
            _toastMessage.value = DashboardToast.RateLimit(source)
            return UiApiError(target = target, message = message, rawMessage = rawMessage, targetLabel = targetLabel)
        }

        val uiError = UiApiError(target = target, message = message, rawMessage = rawMessage, targetLabel = targetLabel)

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
        val snapshot = stateMutex.withLock { cachedStatsByTarget.values.toList() }
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

    private suspend fun refreshRiskSummaries(target: UsageTargetKey, stats: ApiUsageStats, capturedAt: Instant) {
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

        cachedRiskByTarget[target] = risks
    }

    private suspend fun checkForUpdate() {
        val updateUseCase = checkForAppUpdate ?: return

        updateMutex.withLock {
            updateUseCase(currentAppVersion)
                .onSuccess { update ->
                    if (update == null) {
                        _appUpdateState.value = null
                        return@onSuccess
                    }

                    _appUpdateState.value = AppUpdateUiState.Available(update)
                }
                .onFailure {
                    // Falha silenciosa: UI mantém estado anterior; próxima janela de poll tenta de novo.
                }
        }
    }

    private fun scheduleNextRefresh(baseTime: Instant = clock.now()) {
        scheduledRefreshAt = baseTime + config.pollInterval
        _nextRefreshAt.value = scheduledRefreshAt
        onNextRefreshAtChanged(scheduledRefreshAt)
        pollWakeUpSignal.trySend(Unit)
    }
}

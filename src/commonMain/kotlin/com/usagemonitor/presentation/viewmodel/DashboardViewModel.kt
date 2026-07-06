package com.usagemonitor.presentation.viewmodel

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.repository.KiloRepository
import com.usagemonitor.domain.repository.OpenCodeRepository
import com.usagemonitor.domain.usecase.CheckForAppUpdateUseCase
import com.usagemonitor.domain.usecase.GetAnthropicUsageUseCase
import com.usagemonitor.domain.usecase.GetCodexUsageUseCase
import com.usagemonitor.domain.usecase.GetDeepSeekUsageUseCase
import com.usagemonitor.domain.usecase.GetKiloUsageUseCase
import com.usagemonitor.domain.usecase.GetMiniMaxUsageUseCase
import com.usagemonitor.domain.usecase.GetOpenCodeUsageUseCase
import com.usagemonitor.domain.usecase.RecordUsageSnapshotUseCase
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
    private val config: DashboardViewModelConfig = DashboardViewModelConfig()
) {
    private data class PendingFetchRequest(
        val targetSources: Set<ApiSource>,
        val preserveDataOnFailure: Boolean
    )

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _nextRefreshAt = MutableStateFlow(clock.now() + config.pollInterval)
    val nextRefreshAt: StateFlow<Instant> = _nextRefreshAt.asStateFlow()

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
    private val cachedStatsBySource = mutableMapOf<ApiSource, ApiUsageStats>()
    private val cachedErrorsBySource = mutableMapOf<ApiSource, UiApiError>()
    private val sourceFetchSemaphore = Semaphore(config.maxConcurrentSourceFetches.coerceAtLeast(1))
    private val pollWakeUpSignal = Channel<Unit>(capacity = Channel.CONFLATED)
    private val initialFetchCancelled = AtomicBoolean(false)
    @Volatile private var scheduledRefreshAt: Instant = clock.now() + config.pollInterval
    private var countdownJob: Job? = null
    private var initFetchJob: Job? = null
    private var pendingFetchRequest: PendingFetchRequest? = null

    init {
        if (config.autoStartInitialFetch) {
            initFetchJob = viewModelScope.launch {
                if (initialFetchCancelled.get()) {
                    return@launch
                }
                requestFetch(targetSources = enabledApis.value)
            }
        }
        if (config.autoStartUpdateChecks) {
            startUpdateCheckLoop()
        }
        if (config.autoStartCountdown) {
            startCountdown()
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
                    requestFetch(targetSources = enabledApis.value)
                }
                scheduleNextRefresh()
            }
        }
    }

    private suspend fun requestFetch(
        targetSources: Set<ApiSource>,
        preserveDataOnFailure: Boolean = false
    ) {
        if (!fetchMutex.tryLock()) {
            enqueuePendingFetch(targetSources, preserveDataOnFailure)
            fetchMutex.withLock {
                drainPendingFetchQueue() ?: return
            }
            return
        }

        try {
            drainFetchRequests(PendingFetchRequest(targetSources, preserveDataOnFailure))
        } finally {
            fetchMutex.unlock()
        }
    }

    private suspend fun drainFetchRequests(initialRequest: PendingFetchRequest) {
        var currentRequest: PendingFetchRequest? = initialRequest

        while (currentRequest != null) {
            performFetch(
                targetSources = currentRequest.targetSources,
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
        targetSources: Set<ApiSource>,
        preserveDataOnFailure: Boolean
    ) {
        pendingFetchMutex.withLock {
            val existingRequest = pendingFetchRequest
            pendingFetchRequest = when {
                existingRequest == null -> PendingFetchRequest(targetSources, preserveDataOnFailure)
                !existingRequest.preserveDataOnFailure || !preserveDataOnFailure -> {
                    PendingFetchRequest(enabledApis.value, preserveDataOnFailure = false)
                }

                else -> {
                    PendingFetchRequest(
                        targetSources = existingRequest.targetSources + targetSources,
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
        targetSources: Set<ApiSource>,
        preserveDataOnFailure: Boolean
    ) {
        val enabled = enabledApis.value
        val effectiveSources = targetSources.filterTo(linkedSetOf()) { source -> source in enabled }
        val snapshotCapturedAt = clock.now()

        if (effectiveSources.isEmpty()) {
            stateMutex.withLock {
                pruneDisabledSources(enabled)
                publishUiState(enabled)
            }
            return
        }

        markRefreshing(effectiveSources, refreshing = true)

        val statsUpdates = mutableMapOf<ApiSource, ApiUsageStats>()
        val errorUpdates = mutableMapOf<ApiSource, UiApiError?>()

        try {
            val fetchResults = coroutineScope {
                effectiveSources.map { source ->
                    async {
                        sourceFetchSemaphore.withPermit {
                            source to runCatching {
                                withTimeout(config.perSourceTimeout) {
                                    fetchSource(source).getOrThrow()
                                }
                            }
                        }
                    }
                }.awaitAll()
            }

            fetchResults.forEach { (source, result) ->
                result
                    .onSuccess { stats ->
                        statsUpdates[source] = stats
                        errorUpdates[source] = null
                        persistSnapshot(stats, snapshotCapturedAt)
                    }
                    .onFailure { error ->
                        errorUpdates[source] = handleSourceFailure(source, error)
                    }
            }

            stateMutex.withLock {
                val latestEnabledSources = enabledApis.value
                pruneDisabledSources(latestEnabledSources)

                effectiveSources.forEach { source ->
                    val stats = statsUpdates[source]

                    if (stats != null) {
                        cachedStatsBySource[source] = stats
                        cachedErrorsBySource.remove(source)
                    } else {
                        val shouldRemoveData = !preserveDataOnFailure || source !in cachedStatsBySource
                        if (shouldRemoveData) {
                            cachedStatsBySource.remove(source)
                        }

                        val errorMessage = errorUpdates[source]
                        if (errorMessage == null) {
                            cachedErrorsBySource.remove(source)
                        } else {
                            cachedErrorsBySource[source] = errorMessage
                        }
                    }
                }

                publishUiState(latestEnabledSources)
            }
        } finally {
            markRefreshing(effectiveSources, refreshing = false)
        }
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    fun refresh() {
        scheduleNextRefresh()
        viewModelScope.launch {
            requestFetch(targetSources = enabledApis.value)
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
                targetSources = setOf(source),
                preserveDataOnFailure = true
            )
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

    private suspend fun fetchSource(source: ApiSource): Result<ApiUsageStats> {
        return when (source) {
            ApiSource.ANTHROPIC -> getAnthropicUsage()
            ApiSource.MINIMAX -> getMiniMaxUsage()
            ApiSource.CODEX -> getCodexUsage()
            ApiSource.DEEPSEEK -> getDeepSeekUsage()
            ApiSource.OPENCODE -> getOpenCodeUsage()
            ApiSource.KILO -> getKiloUsage()
        }
    }

    private fun handleSourceFailure(source: ApiSource, error: Throwable): UiApiError? {
        val rawMessage = error.message ?: error::class.simpleName ?: "erro desconhecido"
        val message = sanitizeUiErrorMessage(source, rawMessage)

        if (message.contains(HTTP_RATE_LIMIT_MARKER, ignoreCase = true)) {
            _toastMessage.value = DashboardToast.RateLimit(source)
            return UiApiError(source = source, message = message, rawMessage = rawMessage)
        }

        val uiError = UiApiError(source = source, message = message, rawMessage = rawMessage)

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

    private fun publishUiState(enabledSources: Set<ApiSource>) {
        if (enabledSources.isEmpty()) {
            _uiState.value = UiState.NoApisEnabled
            return
        }

        val stats = enabledSources
            .sortedBy { source -> source.ordinal }
            .mapNotNull { source -> cachedStatsBySource[source] }

        val errors = enabledSources
            .sortedBy { source -> source.ordinal }
            .mapNotNull { source -> cachedErrorsBySource[source] }

        _uiState.value = if (stats.isNotEmpty()) {
            UiState.Success(stats, errors)
        } else {
            UiState.Error(errors)
        }
    }

    private fun pruneDisabledSources(enabledSources: Set<ApiSource>) {
        cachedStatsBySource.keys.removeAll { source -> source !in enabledSources }
        cachedErrorsBySource.keys.removeAll { source -> source !in enabledSources }
    }

    private fun markRefreshing(sources: Set<ApiSource>, refreshing: Boolean) {
        _refreshingSources.update { current ->
            if (refreshing) {
                current + sources
            } else {
                current - sources
            }
        }
    }

    private suspend fun persistSnapshot(stats: ApiUsageStats, capturedAt: Instant) {
        val persistenceResult = recordUsageSnapshot(stats, capturedAt)
        if (persistenceResult.isFailure) {
            // Persistencia de historico nao pode degradar o refresh principal.
        }
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
        pollWakeUpSignal.trySend(Unit)
    }
}

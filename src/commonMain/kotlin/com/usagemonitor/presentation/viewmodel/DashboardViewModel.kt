package com.usagemonitor.presentation.viewmodel

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppUpdateInfo
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.usecase.CheckForAppUpdateUseCase
import com.usagemonitor.domain.usecase.GetAnthropicUsageUseCase
import com.usagemonitor.domain.usecase.GetCodexUsageUseCase
import com.usagemonitor.domain.usecase.GetDeepSeekUsageUseCase
import com.usagemonitor.domain.usecase.GetKiloUsageUseCase
import com.usagemonitor.domain.usecase.GetMiniMaxUsageUseCase
import com.usagemonitor.domain.usecase.GetOpenCodeUsageUseCase
import com.usagemonitor.domain.usecase.RecordUsageSnapshotUseCase
import com.usagemonitor.domain.repository.KiloRepository
import com.usagemonitor.domain.repository.OpenCodeRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.seconds
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

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
    private val appUpdateInstaller: AppUpdateInstaller = UnsupportedAppUpdateInstaller,
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

    private val _shouldExitForUpdate = MutableStateFlow(false)
    val shouldExitForUpdate: StateFlow<Boolean> = _shouldExitForUpdate.asStateFlow()

    private val viewModelScope = CoroutineScope(SupervisorJob() + config.workerDispatcher)
    private val stateMutex = Mutex()
    private val updateMutex = Mutex()
    private val fetchMutex = Mutex()
    private val pendingFetchMutex = Mutex()
    private val cachedStatsBySource = mutableMapOf<ApiSource, ApiUsageStats>()
    private val cachedErrorsBySource = mutableMapOf<ApiSource, UiApiError>()
    private var countdownJob: Job? = null
    private var initFetchJob: Job? = null
    private var autoInstallAttemptedVersion: String? = null
    private var pendingFetchRequest: PendingFetchRequest? = null

    init {
        initFetchJob = viewModelScope.launch {
            requestFetch(targetSources = enabledApis.value)
        }
        startUpdateCheckLoop()
        startCountdown()
    }

    private fun startUpdateCheckLoop() {
        viewModelScope.launch {
            checkForUpdate(autoInstall = true)
            while (true) {
                delay(config.updateCheckIntervalWhileRunning)
                checkForUpdate(autoInstall = false)
            }
        }
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (true) {
                _nextRefreshAt.value = clock.now() + config.pollInterval
                delay(config.pollInterval)
                if (!isAppVisible.value) {
                    isAppVisible.first { it }
                }
                viewModelScope.launch { requestFetch(targetSources = enabledApis.value) }
            }
        }
    }

    private suspend fun requestFetch(
        targetSources: Set<ApiSource>,
        preserveDataOnFailure: Boolean = false
    ) {
        if (!fetchMutex.tryLock()) {
            enqueuePendingFetch(targetSources, preserveDataOnFailure)
            return
        }

        try {
            var currentRequest = PendingFetchRequest(targetSources, preserveDataOnFailure)
            while (true) {
                performFetch(
                    targetSources = currentRequest.targetSources,
                    preserveDataOnFailure = currentRequest.preserveDataOnFailure
                )

                val nextRequest = dequeuePendingFetch() ?: break
                currentRequest = nextRequest
            }
        } finally {
            fetchMutex.unlock()
        }
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
            effectiveSources.forEach { source ->
                fetchSource(source)
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
        startCountdown()
        viewModelScope.launch {
            requestFetch(targetSources = enabledApis.value)
        }
    }

    fun refresh(source: ApiSource) {
        if (source !in enabledApis.value) {
            return
        }

        startCountdown()
        viewModelScope.launch {
            requestFetch(
                targetSources = setOf(source),
                preserveDataOnFailure = true
            )
        }
    }

    fun retryUpdateInstallation() {
        val currentState = _appUpdateState.value
        val update = when (currentState) {
            is AppUpdateUiState.Available -> currentState.update
            is AppUpdateUiState.Failed -> if (currentState.automaticInstallSupported) currentState.update else null
            is AppUpdateUiState.Downloading -> null
            is AppUpdateUiState.Installing -> null
            is AppUpdateUiState.Restarting -> null
            null -> null
        }

        if (update == null || !canInstallAutomatically(update)) {
            return
        }

        startAutomaticUpdate(update)
    }

    fun cancelCountdown() {
        countdownJob?.cancel()
    }

    fun cancelInitFetch() {
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
        val message = error.message ?: "erro desconhecido"

        if (message.contains(HTTP_RATE_LIMIT_MARKER, ignoreCase = true)) {
            _toastMessage.value = DashboardToast.RateLimit(source)
            return UiApiError(source = source, message = message)
        }

        val uiError = UiApiError(source = source, message = message)

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
        // Persistência best-effort: falha não deve interromper UI nem propagar.
        recordUsageSnapshot(stats, capturedAt)
    }

    private suspend fun checkForUpdate(autoInstall: Boolean) {
        val updateUseCase = checkForAppUpdate ?: return

        updateMutex.withLock {
            val currentState = _appUpdateState.value

            if (
                currentState is AppUpdateUiState.Downloading ||
                currentState is AppUpdateUiState.Installing ||
                currentState is AppUpdateUiState.Restarting
            ) {
                return@withLock
            }

            updateUseCase(currentAppVersion)
                .onSuccess { update ->
                    if (update == null) {
                        if (currentState !is AppUpdateUiState.Failed) {
                            _appUpdateState.value = null
                        }
                        return@onSuccess
                    }

                    if (autoInstall && canInstallAutomatically(update) && autoInstallAttemptedVersion != update.version) {
                        autoInstallAttemptedVersion = update.version
                        startAutomaticUpdate(update)
                        return@onSuccess
                    }

                    if (currentState is AppUpdateUiState.Failed && currentState.update?.version == update.version) {
                        return@onSuccess
                    }

                    if (currentState is AppUpdateUiState.Restarting && currentState.update.version == update.version) {
                        return@onSuccess
                    }

                    _appUpdateState.value = AppUpdateUiState.Available(
                        update = update,
                        automaticInstallSupported = canInstallAutomatically(update)
                    )
                }
                .onFailure {
                    // Falha silenciosa: UI mantém estado anterior; próxima janela de poll tenta de novo.
                }
        }
    }

    private fun startAutomaticUpdate(update: AppUpdateInfo) {
        _appUpdateState.value = AppUpdateUiState.Downloading(update)

        viewModelScope.launch {
            appUpdateInstaller.prepareUpdateInstallation(update) { stage ->
                when (stage) {
                    AutomaticUpdateStage.INSTALLING -> {
                        _appUpdateState.value = AppUpdateUiState.Installing(update)
                    }

                    AutomaticUpdateStage.RESTARTING -> {
                        _appUpdateState.value = AppUpdateUiState.Restarting(update)
                    }
                }
            }
                .onSuccess { action ->
                    when (action) {
                        PreparedUpdateAction.ExitAndInstall -> {
                            _appUpdateState.value = AppUpdateUiState.Installing(update)
                            delay(config.installerHandoffDelay)
                            _shouldExitForUpdate.value = true
                        }

                        PreparedUpdateAction.RestartAndExit -> {
                            _appUpdateState.value = AppUpdateUiState.Restarting(update)
                            delay(config.restartHandoffDelay)
                            _shouldExitForUpdate.value = true
                        }
                    }
                }
                .onFailure { error ->
                    _appUpdateState.value = AppUpdateUiState.Failed(
                        update = update,
                        message = error.message ?: "Unknown error",
                        automaticInstallSupported = canInstallAutomatically(update)
                    )
                }
        }
    }

    private fun canInstallAutomatically(update: AppUpdateInfo): Boolean {
        return appUpdateInstaller.canInstall(update)
    }
}

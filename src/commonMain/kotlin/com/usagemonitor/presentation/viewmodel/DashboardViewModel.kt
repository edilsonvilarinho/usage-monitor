package com.usagemonitor.presentation.viewmodel

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.usecase.GetAnthropicUsageUseCase
import com.usagemonitor.domain.usecase.GetCodexUsageUseCase
import com.usagemonitor.domain.usecase.GetMiniMaxUsageUseCase
import com.usagemonitor.domain.usecase.RecordUsageSnapshotUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

private const val POLL_INTERVAL_SECONDS = 600

class DashboardViewModel(
    private val getAnthropicUsage: GetAnthropicUsageUseCase,
    private val getMiniMaxUsage: GetMiniMaxUsageUseCase,
    private val getCodexUsage: GetCodexUsageUseCase,
    private val enabledApis: StateFlow<Set<ApiSource>>,
    private val recordUsageSnapshot: RecordUsageSnapshotUseCase,
    private val clock: Clock = Clock.System
) {
    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _secondsUntilRefresh = MutableStateFlow(POLL_INTERVAL_SECONDS)
    val secondsUntilRefresh: StateFlow<Int> = _secondsUntilRefresh.asStateFlow()

    private val _refreshingSources = MutableStateFlow<Set<ApiSource>>(emptySet())
    val refreshingSources: StateFlow<Set<ApiSource>> = _refreshingSources.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val stateMutex = Mutex()
    private val cachedStatsBySource = mutableMapOf<ApiSource, ApiUsageStats>()
    private val cachedErrorsBySource = mutableMapOf<ApiSource, UiApiError>()
    private var countdownJob: Job? = null
    private var initFetchJob: Job? = null

    init {
        initFetchJob = viewModelScope.launch {
            fetchUsage(targetSources = enabledApis.value)
        }
        startCountdown()
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (true) {
                _secondsUntilRefresh.value = POLL_INTERVAL_SECONDS
                for (secondsLeft in POLL_INTERVAL_SECONDS downTo 1) {
                    _secondsUntilRefresh.value = secondsLeft
                    delay(1_000L)
                }
                viewModelScope.launch {
                    fetchUsage(targetSources = enabledApis.value)
                }
            }
        }
    }

    private suspend fun fetchUsage(
        targetSources: Set<ApiSource>,
        preserveDataOnFailure: Boolean = false
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
            fetchUsage(targetSources = enabledApis.value)
        }
    }

    fun refresh(source: ApiSource) {
        if (source !in enabledApis.value) {
            return
        }

        startCountdown()
        viewModelScope.launch {
            fetchUsage(
                targetSources = setOf(source),
                preserveDataOnFailure = true
            )
        }
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
        }
    }

    private fun handleSourceFailure(source: ApiSource, error: Throwable): UiApiError? {
        if (error.message?.contains("429") == true) {
            _toastMessage.value = "RATE_LIMIT:${source.name}"
            return null
        }

        val message = error.message ?: "erro desconhecido"
        val uiError = UiApiError(source = source, message = message)

        if (!uiError.isConfigurationIssue) {
            _toastMessage.value = "ERROR:${source.name}:${message.replace(":", "_")}"
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

        println("[fetchUsage] stats=${stats.size} errors=${errors.size}")

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
        runCatching {
            recordUsageSnapshot(stats, capturedAt)
        }.onFailure { error ->
            println("[history] failed to persist snapshot for ${stats.source}: ${error.message}")
        }
    }
}

package com.usagemonitor.presentation.viewmodel

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.usecase.GetAnthropicUsageUseCase
import com.usagemonitor.domain.usecase.GetMiniMaxUsageUseCase
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

private const val POLL_INTERVAL_SECONDS = 600

class DashboardViewModel(
    private val getAnthropicUsage: GetAnthropicUsageUseCase,
    private val getMiniMaxUsage: GetMiniMaxUsageUseCase,
    private val enabledApis: StateFlow<Set<ApiSource>>
) {
    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _secondsUntilRefresh = MutableStateFlow(POLL_INTERVAL_SECONDS)
    val secondsUntilRefresh: StateFlow<Int> = _secondsUntilRefresh.asStateFlow()

    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var countdownJob: Job? = null
    private var initFetchJob: Job? = null

    init {
        initFetchJob = viewModelScope.launch { fetchUsage() }
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
                viewModelScope.launch { fetchUsage() }
            }
        }
    }

    private suspend fun fetchUsage() {
        val stats = mutableListOf<ApiUsageStats>()
        val errors = mutableListOf<String>()
        val enabled = enabledApis.value

        if (ApiSource.ANTHROPIC in enabled) {
            getAnthropicUsage()
                .onSuccess { stats.add(it) }
                .onFailure { err ->
                    if (err.message?.contains("429") == true) {
                        println("[fetchUsage] Anthropic rate limited, skipping error for this cycle")
                    } else {
                        errors.add("Anthropic: ${err.message ?: "erro desconhecido"}")
                    }
                }
        }

        if (ApiSource.MINIMAX in enabled) {
            getMiniMaxUsage()
                .onSuccess { stats.add(it) }
                .onFailure { err -> errors.add("MiniMax: ${err.message ?: "erro desconhecido"}") }
        }

        println("[fetchUsage] stats=${stats.size} errors=${errors.size}")
        errors.forEach { e -> println("[fetchUsage] ERROR: $e") }

        _uiState.value = if (stats.isNotEmpty()) {
            UiState.Success(stats, errors)
        } else {
            UiState.Error(errors.joinToString("\n"))
        }
    }

    fun refresh() {
        startCountdown()
        viewModelScope.launch { fetchUsage() }
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
}

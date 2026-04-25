package com.usagemonitor.presentation.viewmodel

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

private const val POLL_INTERVAL_MINUTES = 10

class DashboardViewModel(
    private val getAnthropicUsage: GetAnthropicUsageUseCase,
    private val getMiniMaxUsage: GetMiniMaxUsageUseCase
) {
    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _minutesUntilRefresh = MutableStateFlow(0)
    val minutesUntilRefresh: StateFlow<Int> = _minutesUntilRefresh.asStateFlow()

    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pollingJob: Job? = null

    init {
        startPolling()
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (true) {
                _minutesUntilRefresh.value = 0
                fetchUsage()
                for (minutesLeft in POLL_INTERVAL_MINUTES downTo 1) {
                    _minutesUntilRefresh.value = minutesLeft
                    delay(60 * 1_000L)
                }
            }
        }
    }

    private suspend fun fetchUsage() {
        val stats = mutableListOf<ApiUsageStats>()
        val errors = mutableListOf<String>()

        getAnthropicUsage()
            .onSuccess { stats.add(it) }
            .onFailure { errors.add("Anthropic: ${it.message ?: "erro desconhecido"}") }

        getMiniMaxUsage()
            .onSuccess { stats.add(it) }
            .onFailure { errors.add("MiniMax: ${it.message ?: "erro desconhecido"}") }

        _uiState.value = if (stats.isNotEmpty()) {
            UiState.Success(stats, errors)
        } else {
            UiState.Error(errors.joinToString("\n"))
        }
    }

    // Cancela o ciclo atual e reinicia imediatamente
    fun refresh() {
        _uiState.value = UiState.Loading
        startPolling()
    }

    fun onDestroy() {
        viewModelScope.cancel()
    }
}

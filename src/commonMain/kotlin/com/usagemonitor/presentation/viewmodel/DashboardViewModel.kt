package com.usagemonitor.presentation.viewmodel

import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.usecase.GetAnthropicUsageUseCase
import com.usagemonitor.domain.usecase.GetMiniMaxUsageUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel do Dashboard.
 *
 * Em Kotlin/Compose, o ViewModel é responsável por:
 * 1. Manter o estado da UI (UiState) como um StateFlow imutável
 * 2. Executar lógica assíncrona via coroutines
 * 3. Expor dados prontos para a UI consumir
 *
 * `StateFlow` é equivalente a um `ref()` reativo do Vue.js:
 * a UI observa o flow e re-renderiza automaticamente quando muda.
 *
 * Diferença importante: o ViewModel não conhece nenhum componente
 * de UI — ele só expõe estado. A UI é quem decide como renderizar.
 */
class DashboardViewModel(
    private val getAnthropicUsage: GetAnthropicUsageUseCase,
    private val getMiniMaxUsage: GetMiniMaxUsageUseCase
) {
    // Estado interno mutável — só o ViewModel pode alterar
    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)

    // Estado público imutável — a UI só lê, nunca escreve
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /**
     * Escopo de coroutines do ViewModel.
     *
     * `SupervisorJob` garante que se uma coroutine filha falhar,
     * as outras continuam executando (sem SupervisorJob, uma falha
     * cancela todas as coroutines do escopo).
     *
     * `Dispatchers.Default` é a thread pool para trabalho CPU/IO
     * (equivalente a uma Promise no Node.js — não bloqueia a UI).
     */
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        // Inicia o loop de polling assim que o ViewModel é criado
        startPolling()
    }

    /**
     * Loop de polling silencioso: busca dados e aguarda 10 minutos.
     *
     * Por que `while (true)` e não um Timer?
     * Coroutines são leves e cooperativas — o `delay()` não bloqueia
     * uma thread, apenas suspende a coroutine. É idiomático em Kotlin.
     *
     * O loop termina automaticamente quando `onDestroy()` cancela o escopo.
     */
    private fun startPolling() {
        viewModelScope.launch {
            while (true) {
                fetchUsage()
                // Aguarda 10 minutos antes da próxima atualização silenciosa
                delay(10 * 60 * 1_000L)
            }
        }
    }

    /**
     * Busca dados das duas APIs em sequência e atualiza o UiState.
     *
     * Estratégia de resiliência:
     * - Se pelo menos uma API retornar dados, mostra Success (com dados parciais)
     * - Só emite Error se TODAS as APIs falharem
     * - O polling não para em caso de erro — tenta novamente em 10 minutos
     */
    private suspend fun fetchUsage() {
        val stats = mutableListOf<ApiUsageStats>()
        val errors = mutableListOf<String>()

        // Busca Anthropic
        getAnthropicUsage()
            .onSuccess { stats.add(it) }
            .onFailure { errors.add("Anthropic: ${it.message ?: "erro desconhecido"}") }

        // Busca MiniMax
        getMiniMaxUsage()
            .onSuccess { stats.add(it) }
            .onFailure { errors.add("MiniMax: ${it.message ?: "erro desconhecido"}") }

        // Atualiza o estado conforme os resultados
        _uiState.value = if (stats.isNotEmpty()) {
            UiState.Success(stats)
        } else {
            UiState.Error(errors.joinToString("\n"))
        }
    }

    /**
     * Permite forçar uma atualização manual (ex: botão "Atualizar" na UI).
     * Emite Loading temporariamente, depois o resultado da busca.
     */
    fun refresh() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            fetchUsage()
        }
    }

    /**
     * Cancela todas as coroutines em execução.
     * Deve ser chamado quando a janela for fechada (onCloseRequest no Main.kt).
     */
    fun onDestroy() {
        viewModelScope.cancel()
    }
}

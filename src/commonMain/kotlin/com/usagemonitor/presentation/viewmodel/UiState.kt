package com.usagemonitor.presentation.viewmodel

import com.usagemonitor.domain.entity.ApiUsageStats

/**
 * Representa todos os estados possíveis da UI do Dashboard.
 *
 * `sealed interface` é como um "union type" do TypeScript:
 *   type UiState = Loading | Success | Error
 *
 * A vantagem é que o compilador Kotlin OBRIGA o código a tratar todos
 * os casos num `when` — equivalente a um switch exaustivo.
 *
 * Padrão de uso no Compose:
 *   when (val state = uiState) {
 *       is UiState.Loading -> { ... }
 *       is UiState.Success -> { state.data ... }
 *       is UiState.Error   -> { state.message ... }
 *   }
 */
sealed interface UiState {

    /** Primeira carga: nenhum dado disponível ainda. */
    data object Loading : UiState

    /**
     * Dados disponíveis (pelo menos uma API retornou com sucesso).
     * A lista pode ter 1 ou 2 itens se uma API falhar silenciosamente.
     */
    data class Success(val data: List<ApiUsageStats>) : UiState

    /** Todas as APIs falharam. Mostra mensagem de erro ao utilizador. */
    data class Error(val message: String) : UiState
}

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
     * `errors` lista APIs que falharam parcialmente (visíveis na UI).
     */
    data class Success(val data: List<ApiUsageStats>, val errors: List<String> = emptyList()) : UiState

    /** Todas as APIs falharam. Mostra mensagem de erro ao utilizador. */
    data class Error(val message: String) : UiState
}

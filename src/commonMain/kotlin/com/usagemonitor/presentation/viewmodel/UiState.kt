package com.usagemonitor.presentation.viewmodel

import com.usagemonitor.domain.entity.ApiSource
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
    data class Success(val data: List<ApiUsageStats>, val errors: List<UiApiError> = emptyList()) : UiState

    /** Todas as APIs falharam. Mostra mensagem de erro ao utilizador. */
    data class Error(val errors: List<UiApiError>) : UiState {
        val message: String = errors.joinToString("\n") { error -> error.formattedMessage }
    }
}

data class UiApiError(
    val source: ApiSource,
    val message: String
) {
    val formattedMessage: String
        get() = "${sourceLabel(source)}: $message"

    val isAnthropicCredentialIssue: Boolean
        get() = source == ApiSource.ANTHROPIC && isAnthropicCredentialMessage(message)

    val isMiniMaxEnvVarIssue: Boolean
        get() = source == ApiSource.MINIMAX && isMiniMaxEnvVarMessage(message)

    val isConfigurationIssue: Boolean
        get() = isAnthropicCredentialIssue || isMiniMaxEnvVarIssue
}

private fun sourceLabel(source: ApiSource): String {
    return when (source) {
        ApiSource.ANTHROPIC -> "Anthropic"
        ApiSource.MINIMAX -> "MiniMax"
        ApiSource.CODEX -> "Codex"
    }
}

private fun isAnthropicCredentialMessage(message: String): Boolean {
    return message.contains("Credenciais não encontradas", ignoreCase = true) ||
        message.contains("Credentials not found", ignoreCase = true) ||
        message.contains("Token refresh retornou sem access_token", ignoreCase = true) ||
        message.contains("Token refresh returned without access_token", ignoreCase = true)
}

private fun isMiniMaxEnvVarMessage(message: String): Boolean {
    return message.contains("MINIMAX_API_KEY", ignoreCase = true) &&
        (
            message.contains("não configurada", ignoreCase = true) ||
                message.contains("not configured", ignoreCase = true)
            )
}

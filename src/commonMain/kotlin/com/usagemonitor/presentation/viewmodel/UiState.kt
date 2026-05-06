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
 *       UiState.NoApisEnabled -> { ... }
 *       is UiState.Success -> { state.data ... }
 *       is UiState.Error   -> { state.message ... }
 *   }
 */
sealed interface UiState {

    /** Primeira carga: nenhum dado disponível ainda. */
    data object Loading : UiState

    /** Nenhuma API foi habilitada nas configurações. */
    data object NoApisEnabled : UiState

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
        ApiSource.DEEPSEEK -> "DeepSeek"
    }
}

// Marcadores usados para classificar erros como problemas de configuração
// (ex.: arquivo de credenciais ausente, env var faltando). Mantidos como
// substrings para tolerar pequenas variações de formatação nas mensagens.
private val ANTHROPIC_CREDENTIAL_MARKERS = listOf(
    "Credenciais não encontradas",
    "Credentials not found",
    "Token refresh retornou sem access_token",
    "Token refresh returned without access_token"
)

private const val MINIMAX_ENV_VAR_NAME = "MINIMAX_API_KEY"
private val MINIMAX_ENV_VAR_STATE_MARKERS = listOf("não configurada", "not configured")

private fun isAnthropicCredentialMessage(message: String): Boolean {
    return ANTHROPIC_CREDENTIAL_MARKERS.any { marker -> message.contains(marker, ignoreCase = true) }
}

private fun isMiniMaxEnvVarMessage(message: String): Boolean {
    if (!message.contains(MINIMAX_ENV_VAR_NAME, ignoreCase = true)) {
        return false
    }
    return MINIMAX_ENV_VAR_STATE_MARKERS.any { marker -> message.contains(marker, ignoreCase = true) }
}

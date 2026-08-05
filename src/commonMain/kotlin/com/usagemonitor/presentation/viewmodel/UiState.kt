package com.usagemonitor.presentation.viewmodel

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.displayName
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.UsageTargetKey

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
    val target: UsageTargetKey,
    val message: String,
    val rawMessage: String = message,
    val targetLabel: String? = null
) {
    constructor(
        source: ApiSource,
        message: String,
        rawMessage: String = message
    ) : this(UsageTargetKey.forSource(source), message, rawMessage)

    val source: ApiSource
        get() = target.source

    val formattedMessage: String
        get() {
            val label = targetLabel?.takeIf { it.isNotBlank() } ?: sourceLabel(source)
            return "$label: $message"
        }

    val isAnthropicCredentialIssue: Boolean
        get() = source == ApiSource.ANTHROPIC && isAnthropicCredentialMessage(message)

    val isMiniMaxEnvVarIssue: Boolean
        get() = source == ApiSource.MINIMAX && isMiniMaxEnvVarMessage(message)

    val isMiniMaxInactivePlanIssue: Boolean
        get() = source == ApiSource.MINIMAX && isMiniMaxInactivePlanMessage(message)

    val isOpenCodeLocalIssue: Boolean
        get() = source == ApiSource.OPENCODE && isOpenCodeLocalMessage(message)

    val isKiloLocalIssue: Boolean
        get() = source == ApiSource.KILO && isKiloLocalMessage(message)

    val isRateLimitIssue: Boolean
        get() = isRateLimitMessage(message)

    val isServiceUnavailableIssue: Boolean
        get() = isServiceUnavailableMessage(message)

    val isConfigurationIssue: Boolean
        get() = isAnthropicCredentialIssue ||
            isMiniMaxEnvVarIssue ||
            isMiniMaxInactivePlanIssue ||
            isOpenCodeLocalIssue ||
            isKiloLocalIssue
}

internal fun sanitizeUiErrorMessage(source: ApiSource, rawMessage: String): String {
    val flattened = rawMessage.replace(Regex("\\s+"), " ").trim()
    val redacted = flattened
        .replace(Regex("Bearer\\s+[A-Za-z0-9._\\-]+", RegexOption.IGNORE_CASE), "Bearer [REDACTED]")
        .replace(Regex("cap_sid=[^;\\s]+", RegexOption.IGNORE_CASE), "cap_sid=[REDACTED]")
        .replace(Regex("access_token[\"'=:\\s]+[A-Za-z0-9._\\-]+", RegexOption.IGNORE_CASE), "access_token=[REDACTED]")
        .replace(Regex("refresh_token[\"'=:\\s]+[A-Za-z0-9._\\-]+", RegexOption.IGNORE_CASE), "refresh_token=[REDACTED]")

    if (redacted.isBlank()) {
        return if (source == ApiSource.ANTHROPIC) "Anthropic request failed with an empty error message" else "Request failed with an empty error message"
    }

    if (redacted.contains("Enable JavaScript and cookies to continue", ignoreCase = true)) {
        return "Cloudflare challenge page returned by chatgpt.com"
    }

    return redacted.take(220)
}

private fun sourceLabel(source: ApiSource): String {
    return source.displayName()
}

// Marcadores usados para classificar erros como problemas de configuração
// (ex.: arquivo de credenciais ausente, env var faltando). Mantidos como
// substrings para tolerar pequenas variações de formatação nas mensagens.
private val ANTHROPIC_CREDENTIAL_MARKERS = listOf(
    "Credenciais não encontradas",
    "Credentials not found",
    "Token refresh retornou sem access_token",
    "Token refresh returned without access_token",
    "Sua sessão do Claude Code está sem a permissão esperada ou desatualizada",
    "Claude Code session is missing the expected permission or is outdated"
)

private const val MINIMAX_ENV_VAR_NAME = "MINIMAX_API_KEY"
private val MINIMAX_ENV_VAR_STATE_MARKERS = listOf("não configurada", "not configured")
private val MINIMAX_INACTIVE_PLAN_MARKERS = listOf(
    "MiniMax sem plano/token ativo",
    "no active token plan subscription",
    "inactive token plan"
)
private val OPENCODE_LOCAL_MARKERS = listOf(
    "OpenCode local database not found",
    "OpenCode local database is unavailable"
)
private val KILO_LOCAL_MARKERS = listOf(
    "Kilo local database not found",
    "Kilo local database is unavailable"
)
private val RATE_LIMIT_MARKERS = listOf(
    "HTTP 429",
    "rate limited",
    "rate limit",
    "Too Many Requests"
)
private val SERVICE_UNAVAILABLE_MARKERS = listOf(
    "HTTP 503",
    "service unavailable",
    "upstream connect error",
    "disconnect/reset before headers",
    "remote connection failure"
)

private fun isAnthropicCredentialMessage(message: String): Boolean {
    return ANTHROPIC_CREDENTIAL_MARKERS.any { marker -> message.contains(marker, ignoreCase = true) }
}

private fun isMiniMaxEnvVarMessage(message: String): Boolean {
    if (!message.contains(MINIMAX_ENV_VAR_NAME, ignoreCase = true)) {
        return false
    }
    return MINIMAX_ENV_VAR_STATE_MARKERS.any { marker -> message.contains(marker, ignoreCase = true) }
}

private fun isMiniMaxInactivePlanMessage(message: String): Boolean {
    return MINIMAX_INACTIVE_PLAN_MARKERS.any { marker -> message.contains(marker, ignoreCase = true) }
}

private fun isOpenCodeLocalMessage(message: String): Boolean {
    return OPENCODE_LOCAL_MARKERS.any { marker -> message.contains(marker, ignoreCase = true) }
}

private fun isKiloLocalMessage(message: String): Boolean {
    return KILO_LOCAL_MARKERS.any { marker -> message.contains(marker, ignoreCase = true) }
}

private fun isRateLimitMessage(message: String): Boolean {
    return RATE_LIMIT_MARKERS.any { marker -> message.contains(marker, ignoreCase = true) }
}

private fun isServiceUnavailableMessage(message: String): Boolean {
    return SERVICE_UNAVAILABLE_MARKERS.any { marker -> message.contains(marker, ignoreCase = true) }
}

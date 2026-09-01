package com.usagemonitor.presentation.viewmodel

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.displayName
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.QuotaRiskSummary
import com.usagemonitor.domain.entity.QuotaSeriesKey
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
    data class Success(
        val data: List<ApiUsageStats>,
        val errors: List<UiApiError> = emptyList(),
        val riskSummaries: Map<UsageTargetKey, Map<QuotaSeriesKey, QuotaRiskSummary>> = emptyMap()
    ) : UiState

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

    val isMiniMaxApiKeyIssue: Boolean
        get() = source == ApiSource.MINIMAX && isMiniMaxApiKeyMessage(message)

    val isMiniMaxInactivePlanIssue: Boolean
        get() = source == ApiSource.MINIMAX && isMiniMaxInactivePlanMessage(message)

    val isOpenCodeLocalIssue: Boolean
        get() = source == ApiSource.OPENCODE && isOpenCodeLocalMessage(message)

    val isOpenCodeGoApiKeyIssue: Boolean
        get() = source == ApiSource.OPENCODE_GO && isOpenCodeGoApiKeyMessage(message)

    val isOpenCodeGoSubscriptionIssue: Boolean
        get() = source == ApiSource.OPENCODE_GO && isOpenCodeGoSubscriptionMessage(message)

    val isKiloLocalIssue: Boolean
        get() = source == ApiSource.KILO && isKiloLocalMessage(message)

    val isRateLimitIssue: Boolean
        get() = isRateLimitMessage(message)

    val isServiceUnavailableIssue: Boolean
        get() = isServiceUnavailableMessage(message)

    /**
     * Falha de conectividade (proxy corporativo ausente/incorreto, DNS, timeout
     * de conexão) — issue #174. Categoria própria, **fora** de
     * [isConfigurationIssue]: a causa não é uma credencial errada, e tratar como
     * tal orientaria o usuário a revisar login em vez de proxy.
     */
    val isConnectivityIssue: Boolean
        get() = message.contains(NETWORK_CONNECTIVITY_MARKER, ignoreCase = true)

    /** Proxy exige autenticação e a credencial enviada (ou nenhuma) foi recusada. */
    val isProxyAuthIssue: Boolean
        get() = isProxyAuthMessage(message)

    val isConfigurationIssue: Boolean
        get() = isAnthropicCredentialIssue ||
            isMiniMaxApiKeyIssue ||
            isMiniMaxInactivePlanIssue ||
            isOpenCodeLocalIssue ||
            isOpenCodeGoApiKeyIssue ||
            isOpenCodeGoSubscriptionIssue ||
            isKiloLocalIssue ||
            isProxyAuthIssue
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
// (ex.: arquivo de credenciais ausente, chave de API ausente). Mantidos como
// substrings para tolerar pequenas variações de formatação nas mensagens.
private val ANTHROPIC_CREDENTIAL_MARKERS = listOf(
    "Credenciais não encontradas",
    "Credentials not found",
    "Token refresh retornou sem access_token",
    "Token refresh returned without access_token",
    // A renovação que falha no HTTP passou a trazer status e corpo. Sem o marcador
    // a mensagem cairia no bloco genérico de erro e perderia a orientação de login.
    // Rate limit e indisponibilidade são testados antes disto em `warningFor`, então
    // um 429/503 na renovação continua no banner de "aguarde".
    "Token refresh falhou",
    "Token refresh failed",
    "Sua sessão do Claude Code está sem a permissão esperada ou desatualizada",
    "Claude Code session is missing the expected permission or is outdated"
)

private const val MINIMAX_API_KEY_MISSING_MARKER = "Chave da API MiniMax não configurada"
private const val MINIMAX_API_KEY_MISSING_MARKER_EN = "MiniMax API key not configured"
private val MINIMAX_INACTIVE_PLAN_MARKERS = listOf(
    "MiniMax sem plano/token ativo",
    "no active token plan subscription",
    "inactive token plan"
)
private val OPENCODE_LOCAL_MARKERS = listOf(
    "OpenCode local database not found",
    "OpenCode local database is unavailable"
)
// A chave do OpenCode Go é o mesmo segredo do `chat/completions` do Zen, então o
// texto fala de "chave da API OpenCode" e não "chave do Go".
private const val OPENCODE_GO_API_KEY_MISSING_MARKER = "Chave da API OpenCode não configurada"
private const val OPENCODE_GO_API_KEY_MISSING_MARKER_EN = "OpenCode API key not configured"

// Chave válida sem assinatura Go é estado normal de quem só usa o Zen pago — vira
// banner de configuração, nunca o pedido de revisar a credencial.
private val OPENCODE_GO_SUBSCRIPTION_MARKERS = listOf(
    "OpenCode Go sem assinatura ativa",
    "OpenCode Go subscription required",
    "EntitlementError"
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

/**
 * Nunca mostrado ao usuário — é só um token de correspondência que
 * `DashboardViewModel.handleTargetFailure` embute na mensagem quando o tipo da
 * exceção indica falha de conectividade (issue #174). Mesmo mecanismo de
 * `HTTP_RATE_LIMIT_MARKER`, só que acionado por tipo de exceção em vez de
 * status HTTP.
 */
internal const val NETWORK_CONNECTIVITY_MARKER = "usage-monitor:network-connectivity-failure"

// Ao contrário da falha de conectividade, HTTP 407 chega como resposta HTTP
// normal (ver `RemoteApiDataSource.requireSuccess`), então cai no mesmo
// mecanismo de marcador por substring dos demais status.
private val PROXY_AUTH_MARKERS = listOf(
    "HTTP 407",
    "Proxy Authentication Required"
)

private fun isAnthropicCredentialMessage(message: String): Boolean {
    return ANTHROPIC_CREDENTIAL_MARKERS.any { marker -> message.contains(marker, ignoreCase = true) }
}

private fun isMiniMaxApiKeyMessage(message: String): Boolean {
    return message.contains(MINIMAX_API_KEY_MISSING_MARKER, ignoreCase = true) ||
        message.contains(MINIMAX_API_KEY_MISSING_MARKER_EN, ignoreCase = true)
}

private fun isMiniMaxInactivePlanMessage(message: String): Boolean {
    return MINIMAX_INACTIVE_PLAN_MARKERS.any { marker -> message.contains(marker, ignoreCase = true) }
}

private fun isOpenCodeLocalMessage(message: String): Boolean {
    return OPENCODE_LOCAL_MARKERS.any { marker -> message.contains(marker, ignoreCase = true) }
}

private fun isOpenCodeGoApiKeyMessage(message: String): Boolean {
    return message.contains(OPENCODE_GO_API_KEY_MISSING_MARKER, ignoreCase = true) ||
        message.contains(OPENCODE_GO_API_KEY_MISSING_MARKER_EN, ignoreCase = true)
}

private fun isOpenCodeGoSubscriptionMessage(message: String): Boolean {
    return OPENCODE_GO_SUBSCRIPTION_MARKERS.any { marker -> message.contains(marker, ignoreCase = true) }
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

private fun isProxyAuthMessage(message: String): Boolean {
    return PROXY_AUTH_MARKERS.any { marker -> message.contains(marker, ignoreCase = true) }
}

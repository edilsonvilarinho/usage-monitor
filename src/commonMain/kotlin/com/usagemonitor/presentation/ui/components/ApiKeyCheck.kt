package com.usagemonitor.presentation.ui.components

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.viewmodel.NETWORK_CONNECTIVITY_MARKER
import com.usagemonitor.presentation.viewmodel.UiApiError
import com.usagemonitor.presentation.viewmodel.isConnectivityFailure
import com.usagemonitor.presentation.viewmodel.sanitizeUiErrorMessage

/** Resultado do "Testar chave" da aba APIs — par próprio, não o de proxy nem o de Time. */
enum class ApiKeyCheckStatus { IDLE, CHECKING, OK, FAILED }

/**
 * Espelha `ProxyConnectionUiState`, com um campo a mais: [tone].
 *
 * Lá bastam OK e CRITICAL porque a conexão ou passa ou não passa. Aqui existe um
 * terceiro veredito — chave **válida** sem assinatura, sem plano, ou serviço que
 * não respondeu a tempo —, e chamar isso de falha vermelha mandaria o usuário
 * trocar uma credencial que está correta.
 */
data class ApiKeyCheckUiState(
    val status: ApiKeyCheckStatus = ApiKeyCheckStatus.IDLE,
    val message: String? = null,
    val tone: AppTone = AppTone.NEUTRAL
)

/**
 * Veredito do teste de uma chave de API (issue #204).
 *
 * **Função pura, e a classificação é a mesma do dashboard — não uma paralela.**
 * O [error] recebido é o que o repositório da fonte devolveu, e é ele quem já
 * traduziu o que só ele sabe: a MiniMax responde `HTTP 200` com `status_code`
 * de erro no corpo, e o OpenCode Go transforma o `403 EntitlementError` em
 * "sem assinatura ativa". Reclassificar por status na camada de UI abriria um
 * segundo dono da mesma decisão, que é o que a issue pede para evitar.
 *
 * O caminho é o mesmo de `DashboardViewModel.handleTargetFailure`: marcador de
 * conectividade embutido por TIPO de exceção, saneamento — que já redige
 * `Bearer …` — e um [UiApiError], de cujas propriedades sai o veredito. A ordem
 * dos testes importa e é a de `warningFor`: 429 e 503 são avaliados **antes** de
 * credencial, senão um limite temporário viraria "revise a chave".
 *
 * [error] nulo é sucesso: a coleta completa devolveu dados.
 */
fun apiKeyCheckResult(
    source: ApiSource,
    error: Throwable?,
    language: AppLanguage
): ApiKeyCheckUiState {
    val isPt = language == AppLanguage.PT

    if (error == null) {
        return ApiKeyCheckUiState(
            status = ApiKeyCheckStatus.OK,
            message = if (isPt) "Chave válida." else "Key is valid.",
            tone = AppTone.OK
        )
    }

    val originalMessage = error.message ?: error::class.simpleName ?: "erro desconhecido"
    val rawMessage = if (isConnectivityFailure(error)) {
        "$NETWORK_CONNECTIVITY_MARKER ($originalMessage)"
    } else {
        originalMessage
    }
    val uiError = UiApiError(
        source = source,
        message = sanitizeUiErrorMessage(source, rawMessage),
        rawMessage = rawMessage
    )

    // Primeiro: nunca houve resposta HTTP nenhuma. Não pode ser confundido com
    // 401 nem com 429, e a orientação é sobre rede, não sobre credencial.
    if (uiError.isConnectivityIssue) {
        return failed(
            if (isPt) {
                "Sem conexão. Se a rede exige proxy, configure em Configurações > Rede."
            } else {
                "No connection. If the network requires a proxy, configure it under Settings > Network."
            }
        )
    }

    if (uiError.isProxyAuthIssue) {
        return failed(
            if (isPt) {
                "O proxy recusou a credencial (HTTP 407). Revise em Configurações > Rede."
            } else {
                "The proxy rejected the credential (HTTP 407). Review it under Settings > Network."
            }
        )
    }

    // 429 e 503 dizem explicitamente que NÃO houve veredito sobre a chave: a
    // requisição chegou e o serviço se recusou a responder por motivo próprio.
    if (uiError.isRateLimitIssue) {
        return inconclusive(
            if (isPt) {
                "A API respondeu HTTP 429. Não dá para concluir sobre a chave; aguarde e tente de novo."
            } else {
                "The API returned HTTP 429. No verdict on the key is possible; wait and try again."
            }
        )
    }

    if (uiError.isServiceUnavailableIssue) {
        return inconclusive(
            if (isPt) {
                "Serviço indisponível no momento. Não dá para concluir sobre a chave."
            } else {
                "The service is unavailable right now. No verdict on the key is possible."
            }
        )
    }

    // Chave correta, conta sem o produto contratado. Testado ANTES de
    // `isUnauthorizedIssue` porque a origem dos dois é um 403 — e mandar assinar
    // é o oposto de mandar revisar a chave.
    if (uiError.isOpenCodeGoSubscriptionIssue) {
        return inconclusive(
            if (isPt) {
                "Chave válida, sem assinatura Go ativa."
            } else {
                "Key is valid, but there is no active Go subscription."
            }
        )
    }

    if (uiError.isMiniMaxInactivePlanIssue) {
        return inconclusive(
            if (isPt) {
                "Chave válida, sem plano ativo na MiniMax."
            } else {
                "Key is valid, but there is no active MiniMax plan."
            }
        )
    }

    if (uiError.isUnauthorizedIssue) {
        return failed(
            if (isPt) {
                "Chave recusada pela API (HTTP 401/403). Revise a chave."
            } else {
                "The API rejected the key (HTTP 401/403). Review the key."
            }
        )
    }

    // A mensagem do repositório, verbatim. É o caso da MiniMax com chave inválida,
    // que responde HTTP 200 e cujo motivo só o repositório sabe formular.
    return failed(uiError.message)
}

private fun failed(message: String): ApiKeyCheckUiState {
    return ApiKeyCheckUiState(
        status = ApiKeyCheckStatus.FAILED,
        message = message,
        tone = AppTone.CRITICAL
    )
}

/**
 * Terminou sem veredito sobre a chave — ou a chave está certa e falta outra
 * coisa. `FAILED` como status, porque não houve leitura de uso; âmbar como tom,
 * porque não há credencial a corrigir.
 */
private fun inconclusive(message: String): ApiKeyCheckUiState {
    return ApiKeyCheckUiState(
        status = ApiKeyCheckStatus.FAILED,
        message = message,
        tone = AppTone.WARNING
    )
}

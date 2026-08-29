package com.usagemonitor.data.datasource

import com.usagemonitor.data.dto.CodexUsageResponse
import com.usagemonitor.data.dto.CodexWeeklyUsageResponse
import com.usagemonitor.data.dto.DeepSeekBalanceResponse
import com.usagemonitor.data.dto.GitHubReleaseDto
import com.usagemonitor.data.dto.AnthropicUsageResponse
import com.usagemonitor.data.dto.MiniMaxTokenPlanResponse
import com.usagemonitor.data.dto.OpenCodeGoUsageResponse
import com.usagemonitor.data.mapper.resolveExtraCredits
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

private const val CLAUDE_USER_AGENT = "claude-code/1.0.0"
private const val ANTHROPIC_BETA_OAUTH = "oauth-2025-04-20"
private const val GITHUB_API_VERSION = "2022-11-28"
private const val USAGE_MONITOR_USER_AGENT = "UsageMonitorDesktop"

// Aberto para permitir fakes em testes unitários (substituem chamadas HTTP reais).
open class RemoteApiDataSource(
    private val httpClient: HttpClient,
    private val codexDiagnosticsRecorder: CodexDiagnosticsRecorder = NoOpCodexDiagnosticsRecorder,
    private val anthropicCreditsDiagnosticsRecorder: AnthropicCreditsDiagnosticsRecorder =
        NoOpAnthropicCreditsDiagnosticsRecorder
) {

    // Mesmas flags do `Json` do ContentNegotiation em Main.kt: o caminho de
    // diagnóstico não pode desserializar sob regras diferentes das do caminho
    // normal, senão registraria um comportamento que a coleta não tem.
    private val diagnosticsJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Busca uso atual via endpoint dedicado OAuth da Anthropic.
     * Retorna utilização (fração 0-1) das janelas de 5h e 7d.
     *
     * Requer `anthropic-beta: oauth-2025-04-20` para aceitar token OAuth do Claude.ai.
     */
    open suspend fun fetchAnthropicUsage(accessToken: String): AnthropicUsageResponse {
        val response = requireSuccess(
            response = httpClient.get("https://api.anthropic.com/api/oauth/usage") {
                header("Authorization", "Bearer $accessToken")
                header("User-Agent", CLAUDE_USER_AGENT)
                header("anthropic-beta", ANTHROPIC_BETA_OAUTH)
                header("Accept", "application/json")
                contentType(ContentType.Application.Json)
            },
            sourceName = "Anthropic"
        )

        // Com o registro desligado — o normal — o corpo continua sendo lido uma
        // vez só, pelo ContentNegotiation. Ler o texto sempre para guardá-lo
        // custaria uma cópia da resposta em toda coleta de toda conta.
        if (!anthropicCreditsDiagnosticsRecorder.isEnabled) {
            return response.body()
        }

        val payload = response.bodyAsText()
        val parsed = diagnosticsJson.decodeFromString<AnthropicUsageResponse>(payload)
        recordAnthropicCreditsDiagnostics(payload = payload, parsed = parsed)
        return parsed
    }

    /**
     * O desfecho vem de [resolveExtraCredits], a mesma função que o mapper usa —
     * um segundo julgamento aqui poderia registrar um motivo que a tela não teve.
     */
    private fun recordAnthropicCreditsDiagnostics(payload: String, parsed: AnthropicUsageResponse) {
        val resolution = resolveExtraCredits(
            extraUsage = parsed.extraUsage,
            spend = parsed.spend
        )
        val root = runCatching { diagnosticsJson.parseToJsonElement(payload).jsonObject }.getOrNull()

        anthropicCreditsDiagnosticsRecorder.record(
            AnthropicCreditsDiagnosticsEvent(
                timestamp = Clock.System.now().toString(),
                outcome = resolution.outcome.name,
                extraUsageRaw = root?.get("extra_usage")?.toString(),
                spendRaw = root?.get("spend")?.toString()
            )
        )
    }

    open suspend fun fetchMiniMaxTokenPlan(apiKey: String): MiniMaxTokenPlanResponse {
        val response = requireSuccess(
            response = httpClient.get("https://www.minimax.io/v1/token_plan/remains") {
                header("Authorization", "Bearer $apiKey")
                contentType(ContentType.Application.Json)
            },
            sourceName = "MiniMax"
        )

        return response.body()
    }

    open suspend fun fetchCodexFiveHourUsage(session: CodexSession): CodexUsageResponse {
        var failureRecorded = false

        try {
            val response = httpClient.get("https://chatgpt.com/backend-api/wham/usage") {
                header("Authorization", "Bearer ${session.accessToken}")
                header("Cookie", "cap_sid=${session.capSid}")
                val accountId = session.accountContext.key.workspaceId
                if (!accountId.isNullOrBlank()) {
                    header("ChatGPT-Account-Id", accountId)
                }
                header("Accept", "application/json")
                header("User-Agent", "Codex/0.125.0")
                contentType(ContentType.Application.Json)
            }

            if (!response.status.isSuccess()) {
                val body = response.bodyAsText()
                codexDiagnosticsRecorder.recordFailure(
                    CodexDiagnosticsFailureEvent(
                        timestamp = Clock.System.now().toString(),
                        statusCode = response.status.value,
                        failureKind = "http_error",
                        message = summarizeCodexFailure(body)
                    )
                )
                failureRecorded = true
                throw IllegalStateException("Codex HTTP ${response.status.value}: $body")
            }

            return try {
                val payload = response.body<CodexUsageResponse>()
                val diagnosticWindow = payload.rateLimit.primaryWindow
                    ?: payload.rateLimit.secondaryWindow
                codexDiagnosticsRecorder.recordSuccess(
                    CodexDiagnosticsSuccessEvent(
                        timestamp = Clock.System.now().toString(),
                        sourceKind = if (payload.rateLimit.primaryWindow != null) {
                            "primary_window"
                        } else {
                            "secondary_window"
                        },
                        planType = payload.planType,
                        allowed = payload.rateLimit.allowed,
                        limitReached = payload.rateLimit.limitReached,
                        primaryUsedPercent = diagnosticWindow?.usedPercent,
                        primaryResetAt = diagnosticWindow?.resetAt,
                        primaryResetAfterSeconds = diagnosticWindow?.resetAfterSeconds,
                        primaryLimitWindowSeconds = diagnosticWindow?.limitWindowSeconds
                    )
                )
                payload
            } catch (error: Throwable) {
                codexDiagnosticsRecorder.recordFailure(
                    CodexDiagnosticsFailureEvent(
                        timestamp = Clock.System.now().toString(),
                        failureKind = "parse_error",
                        message = summarizeCodexFailure(error.message ?: error::class.simpleName ?: "unknown parse error")
                    )
                )
                failureRecorded = true
                throw error
            }
        } catch (error: Throwable) {
            if (!failureRecorded) {
                codexDiagnosticsRecorder.recordFailure(
                    CodexDiagnosticsFailureEvent(
                        timestamp = Clock.System.now().toString(),
                        failureKind = "request_error",
                        message = summarizeCodexFailure(error.message ?: error::class.simpleName ?: "unknown request error")
                    )
                )
            }
            throw error
        }
    }

    open suspend fun fetchCodexWeeklyUsage(session: CodexSession): CodexWeeklyUsageResponse {
        throw UnsupportedOperationException(
            "Codex weekly usage source not implemented yet. " +
            "The official Codex usage response did not include secondary_window."
        )
    }

    open suspend fun fetchDeepSeekBalance(apiKey: String): DeepSeekBalanceResponse {
        val response = requireSuccess(
            response = httpClient.get("https://api.deepseek.com/user/balance") {
                header("Authorization", "Bearer $apiKey")
                contentType(ContentType.Application.Json)
            },
            sourceName = "DeepSeek"
        )

        return response.body()
    }

    /**
     * Uso da assinatura OpenCode Go.
     *
     * O endpoint existe em produção mas **não está documentado publicamente** e
     * não declara versão — o mesmo risco já aceito no `usage` da Anthropic. Por
     * isso os campos do DTO são todos opcionais e o corpo de erro chega inteiro
     * na mensagem via [requireSuccess]: é dele que o repositório distingue o 403
     * de "não tem assinatura Go" do 401 de chave inválida.
     */
    open suspend fun fetchOpenCodeGoUsage(apiKey: String): OpenCodeGoUsageResponse {
        val response = requireSuccess(
            response = httpClient.get("https://opencode.ai/zen/go/v1/usage") {
                header("Authorization", "Bearer $apiKey")
                contentType(ContentType.Application.Json)
            },
            sourceName = "OpenCode Go"
        )

        return response.body()
    }

    /**
     * [feedUrlOverride] troca a URL da API por outra, para o smoke test de
     * atualização não exigir publicar uma release de verdade a cada tentativa.
     * Ver `USAGE_MONITOR_UPDATE_FEED_URL` em `AppUpdateRepositoryImpl`.
     */
    /**
     * A release de uma tag específica, ou `null` quando ela **não existe**.
     *
     * Existe separada de [fetchLatestGitHubRelease] porque as novidades são as
     * da versão **em execução**, não as da última publicada: quem atualizou
     * 37 → 39 enquanto a 40 já saiu leria as notas erradas.
     *
     * **404 é resposta, não erro.** Toda troca de versão consulta esta rota, e
     * uma build cuja tag ainda não foi publicada — o intervalo entre subir
     * `version` no `build.gradle.kts` e criar a tag — perguntaria por uma release
     * que não existe. Como falha de rede não marca a versão como vista, de
     * propósito, isso viraria uma requisição repetida em toda abertura por uma
     * resposta que não vai mudar. Só o 404 cai aqui: 401, 403 e 5xx continuam
     * falha, senão um problema de acesso passaria por "release sem novidades".
     *
     * Com [feedUrlOverride] definido a URL é usada tal como veio — o servidor de
     * teste do smoke test serve uma release só, e montar um caminho de tag em
     * cima dele daria 404.
     */
    open suspend fun fetchGitHubReleaseByTag(
        owner: String,
        repository: String,
        tag: String,
        feedUrlOverride: String? = null
    ): GitHubReleaseDto? {
        val url = feedUrlOverride?.takeIf { it.isNotBlank() }
            ?: "https://api.github.com/repos/$owner/$repository/releases/tags/$tag"
        val rawResponse = httpClient.get(url) {
            header("Accept", "application/vnd.github+json")
            header("User-Agent", USAGE_MONITOR_USER_AGENT)
            header("X-GitHub-Api-Version", GITHUB_API_VERSION)
            contentType(ContentType.Application.Json)
        }

        if (rawResponse.status == HttpStatusCode.NotFound) {
            return null
        }

        return requireSuccess(response = rawResponse, sourceName = "GitHub release").body()
    }

    open suspend fun fetchLatestGitHubRelease(
        owner: String,
        repository: String,
        feedUrlOverride: String? = null
    ): GitHubReleaseDto {
        val url = feedUrlOverride?.takeIf { it.isNotBlank() }
            ?: "https://api.github.com/repos/$owner/$repository/releases/latest"
        val response = requireSuccess(
            response = httpClient.get(url) {
                header("Accept", "application/vnd.github+json")
                header("User-Agent", USAGE_MONITOR_USER_AGENT)
                header("X-GitHub-Api-Version", GITHUB_API_VERSION)
                contentType(ContentType.Application.Json)
            },
            sourceName = "GitHub release"
        )

        return response.body()
    }

    private suspend fun requireSuccess(
        response: HttpResponse,
        sourceName: String
    ): HttpResponse {
        if (!response.status.isSuccess()) {
            val body = response.bodyAsText()
            throw IllegalStateException("$sourceName HTTP ${response.status.value}: $body")
        }

        return response
    }

    private fun summarizeCodexFailure(rawMessage: String): String {
        val flattened = rawMessage.replace(Regex("\\s+"), " ").trim()
        val redacted = flattened
            .replace(Regex("Bearer\\s+[A-Za-z0-9._\\-]+", RegexOption.IGNORE_CASE), "Bearer [REDACTED]")
            .replace(Regex("cap_sid=[^;\\s]+", RegexOption.IGNORE_CASE), "cap_sid=[REDACTED]")

        if (redacted.contains("Enable JavaScript and cookies to continue", ignoreCase = true)) {
            return "Cloudflare challenge page returned by chatgpt.com"
        }

        if (redacted.isBlank()) {
            return "empty error message"
        }

        return redacted.take(180)
    }
}

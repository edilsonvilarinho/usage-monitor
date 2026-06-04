package com.usagemonitor.data.datasource

import com.usagemonitor.data.dto.CodexUsageResponse
import com.usagemonitor.data.dto.DeepSeekBalanceResponse
import com.usagemonitor.data.dto.GitHubReleaseDto
import com.usagemonitor.data.dto.AnthropicUsageResponse
import com.usagemonitor.data.dto.MiniMaxTokenPlanResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.datetime.Clock

private const val CLAUDE_USER_AGENT = "claude-code/1.0.0"
private const val ANTHROPIC_BETA_OAUTH = "oauth-2025-04-20"
private const val GITHUB_API_VERSION = "2022-11-28"
private const val USAGE_MONITOR_USER_AGENT = "UsageMonitorDesktop"

// Aberto para permitir fakes em testes unitários (substituem chamadas HTTP reais).
open class RemoteApiDataSource(
    private val httpClient: HttpClient,
    private val codexDiagnosticsRecorder: CodexDiagnosticsRecorder = NoOpCodexDiagnosticsRecorder
) {

    /**
     * Busca uso atual via endpoint dedicado OAuth da Anthropic.
     * Retorna utilização (fração 0-1) das janelas de 5h e 7d.
     *
     * Requer `anthropic-beta: oauth-2025-04-20` para aceitar token OAuth do Claude.ai.
     */
    open suspend fun fetchAnthropicUsage(accessToken: String): AnthropicUsageResponse {
        val response = httpClient.get("https://api.anthropic.com/api/oauth/usage") {
            header("Authorization", "Bearer $accessToken")
            header("User-Agent", CLAUDE_USER_AGENT)
            header("anthropic-beta", ANTHROPIC_BETA_OAUTH)
            header("Accept", "application/json")
            contentType(ContentType.Application.Json)
        }

        if (!response.status.isSuccess()) {
            val body = response.bodyAsText()
            throw IllegalStateException("Anthropic HTTP ${response.status.value}: $body")
        }

        return response.body()
    }

    open suspend fun fetchMiniMaxTokenPlan(apiKey: String): MiniMaxTokenPlanResponse {
        return httpClient.get("https://www.minimax.io/v1/token_plan/remains") {
            header("Authorization", "Bearer $apiKey")
            contentType(ContentType.Application.Json)
        }.body()
    }

    open suspend fun fetchCodexUsage(session: CodexSession): CodexUsageResponse {
        var failureRecorded = false

        try {
            val response = httpClient.get("https://chatgpt.com/backend-api/codex/usage") {
                header("Authorization", "Bearer ${session.accessToken}")
                header("Cookie", "cap_sid=${session.capSid}")
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
                codexDiagnosticsRecorder.recordSuccess(
                    CodexDiagnosticsSuccessEvent(
                        timestamp = Clock.System.now().toString(),
                        planType = payload.planType,
                        allowed = payload.rateLimit.allowed,
                        limitReached = payload.rateLimit.limitReached,
                        primaryUsedPercent = payload.rateLimit.primaryWindow.usedPercent,
                        primaryResetAt = payload.rateLimit.primaryWindow.resetAt,
                        primaryResetAfterSeconds = payload.rateLimit.primaryWindow.resetAfterSeconds,
                        primaryLimitWindowSeconds = payload.rateLimit.primaryWindow.limitWindowSeconds,
                        secondaryUsedPercent = payload.rateLimit.secondaryWindow.usedPercent,
                        secondaryResetAt = payload.rateLimit.secondaryWindow.resetAt,
                        secondaryResetAfterSeconds = payload.rateLimit.secondaryWindow.resetAfterSeconds,
                        secondaryLimitWindowSeconds = payload.rateLimit.secondaryWindow.limitWindowSeconds
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

    open suspend fun fetchDeepSeekBalance(apiKey: String): DeepSeekBalanceResponse {
        val response = httpClient.get("https://api.deepseek.com/user/balance") {
            header("Authorization", "Bearer $apiKey")
            contentType(ContentType.Application.Json)
        }

        if (!response.status.isSuccess()) {
            val body = response.bodyAsText()
            throw IllegalStateException("DeepSeek HTTP ${response.status.value}: $body")
        }

        return response.body()
    }

    open suspend fun fetchLatestGitHubRelease(owner: String, repository: String): GitHubReleaseDto {
        val response = httpClient.get("https://api.github.com/repos/$owner/$repository/releases/latest") {
            header("Accept", "application/vnd.github+json")
            header("User-Agent", USAGE_MONITOR_USER_AGENT)
            header("X-GitHub-Api-Version", GITHUB_API_VERSION)
            contentType(ContentType.Application.Json)
        }

        if (!response.status.isSuccess()) {
            val body = response.bodyAsText()
            throw IllegalStateException("GitHub release HTTP ${response.status.value}: $body")
        }

        return response.body()
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

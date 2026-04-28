package com.usagemonitor.data.datasource

import com.usagemonitor.data.dto.CodexUsageResponse
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

private const val CLAUDE_USER_AGENT = "claude-code/1.0.0"
private const val ANTHROPIC_BETA_OAUTH = "oauth-2025-04-20"

class RemoteApiDataSource(private val httpClient: HttpClient) {

    /**
     * Busca uso atual via endpoint dedicado OAuth da Anthropic.
     * Retorna utilização (fração 0-1) das janelas de 5h e 7d.
     *
     * Requer `anthropic-beta: oauth-2025-04-20` para aceitar token OAuth do Claude.ai.
     */
    suspend fun fetchAnthropicUsage(accessToken: String): AnthropicUsageResponse {
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

    suspend fun fetchMiniMaxTokenPlan(apiKey: String): MiniMaxTokenPlanResponse {
        return httpClient.get("https://www.minimax.io/v1/token_plan/remains") {
            header("Authorization", "Bearer $apiKey")
            contentType(ContentType.Application.Json)
        }.body()
    }

    suspend fun fetchCodexUsage(session: CodexSession): CodexUsageResponse {
        val response = httpClient.get("https://chatgpt.com/backend-api/codex/usage") {
            header("Authorization", "Bearer ${session.accessToken}")
            header("Cookie", "cap_sid=${session.capSid}")
            header("Accept", "application/json")
            header("User-Agent", "Codex/0.125.0")
            contentType(ContentType.Application.Json)
        }

        if (!response.status.isSuccess()) {
            val body = response.bodyAsText()
            throw IllegalStateException("Codex HTTP ${response.status.value}: $body")
        }

        return response.body()
    }
}

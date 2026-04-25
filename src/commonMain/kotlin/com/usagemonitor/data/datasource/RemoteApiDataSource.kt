package com.usagemonitor.data.datasource

import com.usagemonitor.data.dto.AnthropicRateLimitDto
import com.usagemonitor.data.dto.MiniMaxTokenPlanResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess

/**
 * Realiza todas as chamadas HTTP às APIs externas (Anthropic e MiniMax).
 *
 * O `HttpClient` é injetado via construtor — não é criado aqui.
 * Isso permite substituir por um cliente fake nos testes (testability).
 *
 * A engine concreta (OkHttp) é configurada no Main.kt (desktopMain).
 */
class RemoteApiDataSource(private val httpClient: HttpClient) {

    /**
     * Faz uma chamada mínima à Anthropic para obter os headers de rate limit.
     *
     * Por que uma chamada mínima?
     * A Anthropic não tem endpoint de "saldo de tokens" separado.
     * Os headers `anthropic-ratelimit-*` são retornados em QUALQUER resposta.
     * Usamos o modelo mais barato (claude-haiku) com max_tokens=1 para
     * minimizar o consumo.
     */
    suspend fun fetchAnthropicRateLimit(accessToken: String): AnthropicRateLimitDto {
        val response: HttpResponse = httpClient.post("https://api.anthropic.com/v1/messages") {
            header("Authorization", "Bearer $accessToken")
            header("anthropic-version", "2023-06-01")
            contentType(ContentType.Application.Json)
            setBody(
                """{"model":"claude-haiku-4-5-20251001","max_tokens":1,"messages":[{"role":"user","content":"hi"}]}"""
            )
        }

        // Falha rápida com mensagem útil em caso de erro HTTP
        if (!response.status.isSuccess()) {
            val body = response.bodyAsText()
            throw IllegalStateException("Anthropic HTTP ${response.status.value}: $body")
        }

        // Extrai os headers de rate limit da resposta HTTP
        val tokensLimit = response.headers["anthropic-ratelimit-tokens-limit"]
            ?.toLongOrNull() ?: 0L

        val tokensRemaining = response.headers["anthropic-ratelimit-tokens-remaining"]
            ?.toLongOrNull() ?: 0L

        val tokensReset = response.headers["anthropic-ratelimit-tokens-reset"]
            ?: throw IllegalStateException("Header anthropic-ratelimit-tokens-reset ausente")

        return AnthropicRateLimitDto(
            tokensLimit = tokensLimit,
            tokensRemaining = tokensRemaining,
            tokensReset = tokensReset
        )
    }

    /**
     * Busca as cotas de todos os modelos do plano MiniMax.
     *
     * `.body<T>()` do Ktor deserializa o JSON automaticamente para o tipo T,
     * usando kotlinx.serialization (configurado no HttpClient via plugin).
     */
    suspend fun fetchMiniMaxTokenPlan(apiKey: String): MiniMaxTokenPlanResponse {
        return httpClient.get("https://www.minimax.io/v1/token_plan/remains") {
            header("Authorization", "Bearer $apiKey")
            contentType(ContentType.Application.Json)
        }.body()
    }
}

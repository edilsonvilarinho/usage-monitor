package com.usagemonitor.data.dto

/**
 * DTO para os headers de rate limit da Anthropic.
 *
 * Diferente do MiniMax, estes dados NÃO vêm de um body JSON.
 * Eles são extraídos dos headers HTTP da resposta:
 *   - anthropic-ratelimit-tokens-limit
 *   - anthropic-ratelimit-tokens-remaining
 *   - anthropic-ratelimit-tokens-reset
 *
 * Por isso não é `@Serializable` — é populado manualmente pelo datasource.
 */
data class AnthropicRateLimitDto(
    // Limite total de tokens na janela atual
    val tokensLimit: Long,

    // Tokens ainda disponíveis na janela
    val tokensRemaining: Long,

    // Quando a janela reseta, em formato ISO 8601 (ex: "2025-01-01T00:01:00Z")
    val tokensReset: String
)

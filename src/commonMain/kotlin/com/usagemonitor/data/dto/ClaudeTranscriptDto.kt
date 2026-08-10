package com.usagemonitor.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Uma linha do transcript `~/.claude/projects/<slug>/<sessionId>.jsonl`.
 *
 * O arquivo mistura muitos tipos de linha (`assistant`, `user`, `attachment`,
 * `system`, `file-history-*`, ...). Só interessam as de `type == "assistant"`
 * com `message.usage` presente. Campos desconhecidos são ignorados: o formato
 * muda entre versões do Claude Code e uma chave nova não pode quebrar a leitura.
 */
@Serializable
data class ClaudeTranscriptLineDto(
    val type: String? = null,
    val uuid: String? = null,
    @SerialName("sessionId") val sessionId: String? = null,
    val timestamp: String? = null,
    val cwd: String? = null,
    @SerialName("gitBranch") val gitBranch: String? = null,
    @SerialName("isSidechain") val isSidechain: Boolean = false,
    val message: ClaudeTranscriptMessageDto? = null
)

@Serializable
data class ClaudeTranscriptMessageDto(
    val id: String? = null,
    val model: String? = null,
    val usage: ClaudeTranscriptUsageDto? = null
)

@Serializable
data class ClaudeTranscriptUsageDto(
    @SerialName("input_tokens") val inputTokens: Long = 0L,
    @SerialName("output_tokens") val outputTokens: Long = 0L,
    @SerialName("cache_read_input_tokens") val cacheReadInputTokens: Long = 0L,
    @SerialName("cache_creation_input_tokens") val cacheCreationInputTokens: Long = 0L,
    @SerialName("cache_creation") val cacheCreation: ClaudeTranscriptCacheCreationDto? = null
) {
    /**
     * Tokens de cache write com TTL de 5 minutos.
     *
     * Quando `cache_creation` não vem na linha (versões antigas do CLI), todo o
     * `cache_creation_input_tokens` é atribuído ao tier de 5 minutos, que é o
     * TTL padrão da API. É a única atribuição defensável sem o detalhamento.
     */
    val cacheWrite5mTokens: Long
        get() = cacheCreation?.ephemeral5mInputTokens ?: cacheCreationInputTokens

    val cacheWrite1hTokens: Long
        get() = cacheCreation?.ephemeral1hInputTokens ?: 0L
}

@Serializable
data class ClaudeTranscriptCacheCreationDto(
    @SerialName("ephemeral_5m_input_tokens") val ephemeral5mInputTokens: Long = 0L,
    @SerialName("ephemeral_1h_input_tokens") val ephemeral1hInputTokens: Long = 0L
)

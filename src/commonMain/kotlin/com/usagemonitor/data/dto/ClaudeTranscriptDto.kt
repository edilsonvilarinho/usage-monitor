package com.usagemonitor.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

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
    val usage: ClaudeTranscriptUsageDto? = null,
    /**
     * Blocos da mensagem, lidos como [JsonElement] cru.
     *
     * O campo é polimórfico no transcript: nas linhas do assistente é um array
     * de blocos (`text`, `thinking`, `tool_use`), e em outras é uma string. Um
     * tipo fixo faria o parse da linha inteira falhar num dos dois casos.
     *
     * Só o **nome** da ferramenta é extraído daqui — nunca o `input` nem o texto
     * da resposta, na mesma regra que o envio para o time segue.
     */
    val content: JsonElement? = null
) {
    /**
     * Ferramentas invocadas nesta mensagem, com a contagem de chamadas.
     *
     * Vazio quando `content` não é um array de blocos, o que é o caso normal das
     * linhas que não são do assistente.
     */
    val toolCalls: Map<String, Int>
        get() {
            val blocks = content as? JsonArray ?: return emptyMap()
            val counts = mutableMapOf<String, Int>()
            for (block in blocks) {
                val obj = block as? JsonObject ?: continue
                if ((obj["type"] as? JsonPrimitive)?.contentOrNull != TOOL_USE_BLOCK_TYPE) {
                    continue
                }
                val name = (obj["name"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: continue
                counts[name] = (counts[name] ?: 0) + 1
            }
            return counts
        }
}

private const val TOOL_USE_BLOCK_TYPE = "tool_use"

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

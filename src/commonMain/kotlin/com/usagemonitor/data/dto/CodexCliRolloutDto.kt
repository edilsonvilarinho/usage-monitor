package com.usagemonitor.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class CodexCliRolloutTypeDto(
    @SerialName("type") val type: String? = null
)

/**
 * Só o envelope de uma linha que pode iniciar um pedido ao modelo: tipo,
 * instante e, no `payload`, o tipo do item e o papel. O conteúdo (mensagem,
 * saída de ferramenta) não é declarado e nunca é lido.
 */
@Serializable
data class CodexCliRequestMarkerLineDto(
    @SerialName("type") val type: String? = null,
    @SerialName("timestamp") val timestamp: String? = null,
    @SerialName("payload") val payload: CodexCliRequestMarkerPayloadDto? = null
)

@Serializable
data class CodexCliRequestMarkerPayloadDto(
    @SerialName("type") val type: String? = null,
    @SerialName("role") val role: String? = null
)

@Serializable
data class CodexCliSessionMetaLineDto(
    @SerialName("type") val type: String? = null,
    @SerialName("payload") val payload: CodexCliSessionMetaPayloadDto? = null
)

@Serializable
data class CodexCliSessionMetaPayloadDto(
    @SerialName("session_id") val sessionId: String? = null,
    @SerialName("id") val id: String? = null,
    @SerialName("cwd") val cwd: String? = null,
    @SerialName("originator") val originator: String? = null,
    @SerialName("cli_version") val cliVersion: String? = null,
    @SerialName("source") val source: JsonElement? = null,
    @SerialName("thread_source") val threadSource: String? = null
)

@Serializable
data class CodexCliTurnContextLineDto(
    @SerialName("type") val type: String? = null,
    @SerialName("timestamp") val timestamp: String? = null,
    @SerialName("payload") val payload: CodexCliTurnContextPayloadDto? = null
)

@Serializable
data class CodexCliTurnContextPayloadDto(
    @SerialName("turn_id") val turnId: String? = null,
    @SerialName("cwd") val cwd: String? = null,
    @SerialName("model") val model: String? = null
)

@Serializable
data class CodexCliTokenUsageRecordLineDto(
    @SerialName("type") val type: String? = null,
    @SerialName("timestamp") val timestamp: String? = null,
    @SerialName("payload") val payload: CodexCliTokenUsageRecordPayloadDto? = null
)

@Serializable
data class CodexCliTokenUsageRecordPayloadDto(
    @SerialName("thread_id") val threadId: String? = null,
    @SerialName("turn_id") val turnId: String? = null,
    @SerialName("session_id") val sessionId: String? = null,
    @SerialName("response_id") val responseId: String? = null,
    @SerialName("usage") val usage: CodexCliUsageDto? = null
)

@Serializable
data class CodexCliUsageDto(
    @SerialName("input_tokens") val inputTokens: JsonElement? = null,
    @SerialName("cached_input_tokens") val cachedInputTokens: JsonElement? = null,
    @SerialName("cache_write_input_tokens") val cacheWriteInputTokens: JsonElement? = null,
    @SerialName("output_tokens") val outputTokens: JsonElement? = null,
    @SerialName("reasoning_output_tokens") val reasoningOutputTokens: JsonElement? = null,
    @SerialName("total_tokens") val totalTokens: JsonElement? = null
)

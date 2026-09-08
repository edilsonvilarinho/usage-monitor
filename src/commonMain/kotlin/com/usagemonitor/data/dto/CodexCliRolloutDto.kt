package com.usagemonitor.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class CodexCliRolloutTypeDto(
    val type: String? = null
)

@Serializable
data class CodexCliSessionMetaLineDto(
    val type: String? = null,
    val payload: CodexCliSessionMetaPayloadDto? = null
)

@Serializable
data class CodexCliSessionMetaPayloadDto(
    @SerialName("session_id") val sessionId: String? = null,
    val id: String? = null,
    val cwd: String? = null,
    val originator: String? = null,
    @SerialName("cli_version") val cliVersion: String? = null,
    val source: JsonElement? = null,
    @SerialName("thread_source") val threadSource: String? = null
)

@Serializable
data class CodexCliTurnContextLineDto(
    val type: String? = null,
    val timestamp: String? = null,
    val payload: CodexCliTurnContextPayloadDto? = null
)

@Serializable
data class CodexCliTurnContextPayloadDto(
    @SerialName("turn_id") val turnId: String? = null,
    val cwd: String? = null,
    val model: String? = null
)

@Serializable
data class CodexCliTokenUsageRecordLineDto(
    val type: String? = null,
    val timestamp: String? = null,
    val payload: CodexCliTokenUsageRecordPayloadDto? = null
)

@Serializable
data class CodexCliTokenUsageRecordPayloadDto(
    @SerialName("thread_id") val threadId: String? = null,
    @SerialName("turn_id") val turnId: String? = null,
    @SerialName("session_id") val sessionId: String? = null,
    @SerialName("response_id") val responseId: String? = null,
    val usage: CodexCliUsageDto? = null
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

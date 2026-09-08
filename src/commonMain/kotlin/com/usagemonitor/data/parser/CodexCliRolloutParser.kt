package com.usagemonitor.data.parser

import com.usagemonitor.data.dto.CodexCliRolloutTypeDto
import com.usagemonitor.data.dto.CodexCliSessionMetaLineDto
import com.usagemonitor.data.dto.CodexCliTokenUsageRecordLineDto
import com.usagemonitor.data.dto.CodexCliTurnContextLineDto
import com.usagemonitor.domain.entity.CodexCliRolloutSource
import com.usagemonitor.domain.entity.CodexCliSessionTurn
import com.usagemonitor.domain.entity.CodexCliUsageDelta
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class CodexCliRolloutMetadata(
    val sessionId: String,
    val cwd: String? = null,
    val source: CodexCliRolloutSource = CodexCliRolloutSource.UNKNOWN,
    val rawSource: String? = null,
    val threadSource: String? = null,
    val cliVersion: String? = null
)

data class CodexCliParsedRollout(
    val metadata: CodexCliRolloutMetadata? = null,
    val turns: List<CodexCliSessionTurn> = emptyList(),
    val skippedLines: Int = 0,
    val unknownLines: Int = 0
)

/** Parser de contrato observável. Não desserializa `response_item` nem conteúdo de ferramenta. */
class CodexCliRolloutParser(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
) {
    fun parse(lines: Iterable<String>, knownMetadata: CodexCliRolloutMetadata? = null): CodexCliParsedRollout {
        var metadata = knownMetadata
        var skippedLines = 0
        var unknownLines = 0
        var sequence = 0
        val contexts = mutableMapOf<String, TurnContext>()
        val turns = mutableListOf<CodexCliSessionTurn>()

        for (rawLine in lines) {
            if (rawLine.isBlank()) {
                continue
            }
            val type = runCatching {
                json.decodeFromString<CodexCliRolloutTypeDto>(rawLine).type
            }.getOrNull()
            if (type == null) {
                skippedLines++
                continue
            }

            when (type) {
                SESSION_META_TYPE -> {
                    val line = runCatching {
                        json.decodeFromString<CodexCliSessionMetaLineDto>(rawLine)
                    }.getOrNull()
                    val payload = line?.payload
                    if (payload == null) {
                        skippedLines++
                        continue
                    }
                    val sessionId = payload?.sessionId?.takeIf { value -> value.isNotBlank() }
                        ?: payload?.id?.takeIf { value -> value.isNotBlank() }
                    if (sessionId == null) {
                        skippedLines++
                    } else {
                        val rawSource = payload.source.asStringOrNull()
                        metadata = CodexCliRolloutMetadata(
                            sessionId = sessionId,
                            cwd = payload.cwd,
                            source = CodexCliRolloutSource.fromRaw(rawSource),
                            rawSource = rawSource,
                            threadSource = payload.threadSource,
                            cliVersion = payload.cliVersion
                        )
                    }
                }

                TURN_CONTEXT_TYPE -> {
                    val line = runCatching {
                        json.decodeFromString<CodexCliTurnContextLineDto>(rawLine)
                    }.getOrNull()
                    val payload = line?.payload
                    val turnId = payload?.turnId?.takeIf { value -> value.isNotBlank() }
                    if (turnId != null) {
                        contexts[turnId] = TurnContext(
                            model = payload.model,
                            cwd = payload.cwd
                        )
                    }
                }

                TOKEN_USAGE_RECORD_TYPE -> {
                    val line = runCatching {
                        json.decodeFromString<CodexCliTokenUsageRecordLineDto>(rawLine)
                    }.getOrNull()
                    val payload = line?.payload
                    val sessionId = payload?.sessionId?.takeIf { value -> value.isNotBlank() }
                    val turnId = payload?.turnId?.takeIf { value -> value.isNotBlank() }
                    val responseId = payload?.responseId?.takeIf { value -> value.isNotBlank() }
                    val timestamp = line?.timestamp?.let { value -> runCatching { Instant.parse(value) }.getOrNull() }
                    val usage = payload?.usage?.toUsageDelta()
                    if (sessionId == null || turnId == null || responseId == null || timestamp == null || usage == null) {
                        skippedLines++
                        continue
                    }
                    val context = contexts[turnId]
                    val sourceMetadata = metadata
                    turns += CodexCliSessionTurn(
                        sessionId = sessionId,
                        turnId = turnId,
                        responseId = responseId,
                        seq = sequence++,
                        ts = timestamp,
                        model = context?.model,
                        cwd = context?.cwd ?: sourceMetadata?.cwd,
                        source = sourceMetadata?.source ?: CodexCliRolloutSource.UNKNOWN,
                        rawSource = sourceMetadata?.rawSource,
                        threadSource = sourceMetadata?.threadSource,
                        usage = usage
                    )
                }

                EVENT_MSG_TYPE, RESPONSE_ITEM_TYPE, WORLD_STATE_TYPE -> Unit
                else -> unknownLines++
            }
        }

        return CodexCliParsedRollout(
            metadata = metadata,
            turns = turns,
            skippedLines = skippedLines,
            unknownLines = unknownLines
        )
    }

    private fun JsonElement?.asStringOrNull(): String? {
        return (this as? JsonPrimitive)?.contentOrNull?.takeIf { value -> value.isNotBlank() }
    }

    private fun com.usagemonitor.data.dto.CodexCliUsageDto.toUsageDelta(): CodexCliUsageDelta? {
        val values = listOf(inputTokens, cachedInputTokens, cacheWriteInputTokens, outputTokens, reasoningOutputTokens, totalTokens)
            .map { element -> element.toNonNegativeLong() ?: return null }
        return CodexCliUsageDelta(
            inputTokens = values[0],
            cachedInputTokens = values[1],
            cacheWriteInputTokens = values[2],
            outputTokens = values[3],
            reasoningOutputTokens = values[4],
            totalTokens = values[5]
        )
    }

    private fun JsonElement?.toNonNegativeLong(): Long? {
        val value = (this as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: return null
        return value.takeIf { number -> number >= 0L }
    }

    private data class TurnContext(
        val model: String?,
        val cwd: String?
    )

    private companion object {
        const val SESSION_META_TYPE = "session_meta"
        const val TURN_CONTEXT_TYPE = "turn_context"
        const val TOKEN_USAGE_RECORD_TYPE = "token_usage_record"
        const val EVENT_MSG_TYPE = "event_msg"
        const val RESPONSE_ITEM_TYPE = "response_item"
        const val WORLD_STATE_TYPE = "world_state"
    }
}

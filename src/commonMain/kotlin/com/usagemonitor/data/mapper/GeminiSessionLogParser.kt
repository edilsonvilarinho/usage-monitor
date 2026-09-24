package com.usagemonitor.data.mapper

import com.usagemonitor.data.datasource.GeminiMessageUsage
import com.usagemonitor.data.datasource.GeminiSessionUsage
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Parser seletivo do JSONL documentado pelo Gemini CLI.
 *
 * Não lê `content`, `displayContent`, argumentos ou resultados de ferramentas
 * para o modelo de domínio. Eventos de rewind e checkpoints são aplicados
 * antes da agregação, como no leitor oficial de sessões.
 */
internal object GeminiSessionLogParser {
    private val json = Json { isLenient = true }

    data class ParseResult(
        val recognizedRecords: Int,
        val sessionId: String?,
        val messages: List<GeminiMessageUsage>
    )

    fun parse(lines: Iterable<String>): ParseResult {
        var recognizedRecords = 0
        var sessionId: String? = null
        val messages = linkedMapOf<String, ParsedMessage>()

        lines.forEach { line ->
            if (line.isBlank()) return@forEach
            val record = runCatching { json.parseToJsonElement(line).jsonObject }.getOrNull()
                ?: return@forEach

            val rewindId = record.stringValue("\$rewindTo")
            if (rewindId != null) {
                recognizedRecords += 1
                val ids = messages.keys.toList()
                val rewindIndex = ids.indexOf(rewindId)
                if (rewindIndex < 0) {
                    messages.clear()
                } else {
                    ids.drop(rewindIndex).forEach(messages::remove)
                }
                return@forEach
            }

            val checkpoint = record["\$set"]?.objectOrNull()
            if (checkpoint != null) {
                recognizedRecords += 1
                messages.clear()
                checkpoint.stringValue("sessionId")?.let { value -> sessionId = value }
                checkpoint["messages"]?.arrayOrNull()?.forEach { message ->
                    message.objectOrNull()?.let { value -> replaceMessage(value, messages) }
                }
                return@forEach
            }

            record.stringValue("sessionId")?.let { value ->
                sessionId = value
                recognizedRecords += 1
            }
            record.stringValue("projectHash")?.let { recognizedRecords += 1 }
            val embeddedMessages = record["messages"]?.arrayOrNull()
            if (embeddedMessages != null) {
                recognizedRecords += 1
                embeddedMessages.forEach { message ->
                    message.objectOrNull()?.let { value -> replaceMessage(value, messages) }
                }
                return@forEach
            }

            if (record.stringValue("id") != null) {
                recognizedRecords += 1
                replaceMessage(record, messages)
            }
        }

        return ParseResult(
            recognizedRecords = recognizedRecords,
            sessionId = sessionId,
            messages = messages.values.mapNotNull { message -> message.toUsageOrNull() }
        )
    }

    private fun replaceMessage(record: JsonObject, messages: MutableMap<String, ParsedMessage>) {
        val id = record.stringValue("id") ?: return
        // Registros não Gemini também participam da ordem de rewind e podem
        // substituir o mesmo ID; sem tokens, não entram no consumo agregado.
        val timestamp = record.stringValue("timestamp")?.let { value ->
            runCatching { Instant.parse(value) }.getOrNull()
        }
        val tokens = record["tokens"]?.objectOrNull()?.longValue("total")
        val modelName = record.stringValue("model")?.takeIf(String::isNotBlank) ?: UNKNOWN_MODEL
        messages[id] = ParsedMessage(id, timestamp, modelName, tokens)
    }

    private data class ParsedMessage(
        val id: String,
        val timestamp: Instant?,
        val modelName: String,
        val totalTokens: Long?
    ) {
        fun toUsageOrNull(): GeminiMessageUsage? {
            val capturedAt = timestamp ?: return null
            val tokens = totalTokens?.takeIf { value -> value >= 0L } ?: return null
            return GeminiMessageUsage(
                messageId = id,
                capturedAt = capturedAt,
                modelName = modelName,
                totalTokens = tokens
            )
        }
    }

    private fun JsonObject.stringValue(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull()

    private fun JsonObject.longValue(key: String): Long? =
        (this[key] as? JsonPrimitive)?.content?.toLongOrNull()

    private fun JsonElement.objectOrNull(): JsonObject? = this as? JsonObject

    private fun JsonElement.arrayOrNull(): JsonArray? = this as? JsonArray

    private fun JsonPrimitive.contentOrNull(): String? = if (isString) content else null

    private const val UNKNOWN_MODEL = "Unknown Gemini model"
}

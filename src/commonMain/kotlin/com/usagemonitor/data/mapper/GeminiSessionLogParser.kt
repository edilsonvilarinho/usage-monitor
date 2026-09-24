package com.usagemonitor.data.mapper

import com.usagemonitor.data.datasource.GeminiMessageUsage
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Parser seletivo do JSONL de sessões do Gemini CLI
 * (`~/.gemini/tmp/<projeto>/chats/<sessão>.jsonl`).
 *
 * O arquivo é append-only: uma linha de cabeçalho (`sessionId`, `projectHash`),
 * registros de mensagem e dois tipos de patch, `{"$set":{...}}` e
 * `{"$rewindTo":"<id>"}`. Três regras que a forma do arquivo impõe, conferidas
 * contra o leitor do Codenotch (`GeminiCLIUsage.swift`, Gemini CLI 0.58.0):
 *
 * - **Uma chamada é gravada duas vezes com o mesmo `id`**: sem `tokens` quando o
 *   turno começa e com eles quando o `usageMetadata` chega. Só a gravação com
 *   tokens conta, e uma gravação posterior sem tokens não apaga a que tinha.
 * - **`$set` não mexe na contagem.** O CLI grava `$set lastUpdated` depois de cada
 *   mensagem; a versão anterior limpava tudo a cada patch, e a sessão real
 *   terminava contando perto de zero.
 * - **`$rewindTo` desfaz a conversa, não a cobrança.** O Google cobrou a chamada
 *   que o usuário voltou atrás, e ela continua contada.
 *
 * `tokens.total` já soma entrada, saída, pensamento e ferramenta, e `cached` é
 * subconjunto de `input`: somar as partes contaria o cache duas vezes.
 *
 * Não lê `content`, `displayContent`, argumentos nem resultados de ferramenta para
 * o modelo de domínio.
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
        val answered = linkedMapOf<String, GeminiMessageUsage>()

        lines.forEach { line ->
            if (line.isBlank()) return@forEach
            val record = runCatching { json.parseToJsonElement(line).jsonObject }.getOrNull()
                ?: return@forEach

            if (record.containsKey(REWIND_KEY)) {
                recognizedRecords += 1
                return@forEach
            }

            val patch = record[SET_KEY]?.objectOrNull()
            if (patch != null) {
                recognizedRecords += 1
                patch.stringValue("sessionId")?.let { value -> sessionId = value }
                // Forma antiga que embute as mensagens no patch: entram pela mesma
                // dedup por `id`, então nada é contado duas vezes.
                patch["messages"]?.arrayOrNull()?.forEach { message ->
                    message.objectOrNull()?.let { value -> recordCall(value, answered) }
                }
                return@forEach
            }

            record.stringValue("sessionId")?.let { value ->
                sessionId = value
                recognizedRecords += 1
            }
            if (record.stringValue("projectHash") != null) recognizedRecords += 1

            record["messages"]?.arrayOrNull()?.let { embedded ->
                recognizedRecords += 1
                embedded.forEach { message -> message.objectOrNull()?.let { value -> recordCall(value, answered) } }
                return@forEach
            }

            if (record.stringValue("id") != null) {
                recognizedRecords += 1
                recordCall(record, answered)
            }
        }

        return ParseResult(
            recognizedRecords = recognizedRecords,
            sessionId = sessionId,
            messages = answered.values.toList()
        )
    }

    /** Só a gravação que traz tokens vira chamada; a última delas vence. */
    private fun recordCall(record: JsonObject, answered: MutableMap<String, GeminiMessageUsage>) {
        val id = record.stringValue("id") ?: return
        val type = record.stringValue("type")
        if (type != null && type != GEMINI_RECORD_TYPE) return
        val tokens = record["tokens"]?.objectOrNull()?.longValue("total")?.takeIf { value -> value >= 0L } ?: return
        val timestamp = record.stringValue("timestamp")
            ?.let { value -> runCatching { Instant.parse(value) }.getOrNull() }
            ?: return
        val modelName = record.stringValue("model")?.takeIf(String::isNotBlank) ?: UNKNOWN_MODEL
        answered[id] = GeminiMessageUsage(
            messageId = id,
            capturedAt = timestamp,
            modelName = modelName,
            totalTokens = tokens
        )
    }

    private fun JsonObject.stringValue(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull()

    private fun JsonObject.longValue(key: String): Long? =
        (this[key] as? JsonPrimitive)?.content?.toLongOrNull()

    private fun JsonElement.objectOrNull(): JsonObject? = this as? JsonObject

    private fun JsonElement.arrayOrNull(): JsonArray? = this as? JsonArray

    private fun JsonPrimitive.contentOrNull(): String? = if (isString) content else null

    private const val UNKNOWN_MODEL = "Unknown Gemini model"
    private const val GEMINI_RECORD_TYPE = "gemini"
    private const val SET_KEY = "\$set"
    private const val REWIND_KEY = "\$rewindTo"
}

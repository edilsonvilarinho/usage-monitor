package com.usagemonitor.data

import com.usagemonitor.data.dto.ClaudeTranscriptLineDto
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

class ClaudeTranscriptToolsTest {

    @Test
    fun `tool_use blocks become a call count per tool`() {
        val line = json.decodeFromString<ClaudeTranscriptLineDto>(
            """
            {"type":"assistant","sessionId":"s1","timestamp":"2026-08-01T10:00:00Z",
             "message":{"id":"m1","model":"claude-opus-5","content":[
               {"type":"thinking","thinking":"..."},
               {"type":"text","text":"vou ler o arquivo"},
               {"type":"tool_use","id":"t1","name":"Read","input":{"file_path":"/etc/passwd"}},
               {"type":"tool_use","id":"t2","name":"Read","input":{"file_path":"/etc/hosts"}},
               {"type":"tool_use","id":"t3","name":"Bash","input":{"command":"ls"}}
             ],"usage":{"input_tokens":1,"output_tokens":2}}}
            """.trimIndent()
        )

        assertEquals(mapOf("Read" to 2, "Bash" to 1), line.message?.toolCalls)
    }

    /**
     * O campo é polimórfico: array nas linhas do assistente, string em outras.
     * Um tipo fixo faria o parse da linha inteira falhar num dos dois casos.
     */
    @Test
    fun `a string content does not break the line`() {
        val line = json.decodeFromString<ClaudeTranscriptLineDto>(
            """
            {"type":"user","sessionId":"s1","timestamp":"2026-08-01T10:00:00Z",
             "message":{"content":"texto simples"}}
            """.trimIndent()
        )

        assertEquals(emptyMap(), line.message?.toolCalls)
    }

    @Test
    fun `a message without content has no tools`() {
        val line = json.decodeFromString<ClaudeTranscriptLineDto>(
            """
            {"type":"assistant","sessionId":"s1","timestamp":"2026-08-01T10:00:00Z",
             "message":{"id":"m1","usage":{"input_tokens":1,"output_tokens":2}}}
            """.trimIndent()
        )

        assertEquals(emptyMap(), line.message?.toolCalls)
    }

    @Test
    fun `a tool_use without a name is ignored`() {
        val line = json.decodeFromString<ClaudeTranscriptLineDto>(
            """
            {"type":"assistant","sessionId":"s1","timestamp":"2026-08-01T10:00:00Z",
             "message":{"id":"m1","content":[{"type":"tool_use","id":"t1"}],
             "usage":{"input_tokens":1,"output_tokens":2}}}
            """.trimIndent()
        )

        assertTrue(line.message?.toolCalls.orEmpty().isEmpty())
    }
}

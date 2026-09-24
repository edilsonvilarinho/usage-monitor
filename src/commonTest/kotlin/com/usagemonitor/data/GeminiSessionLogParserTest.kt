package com.usagemonitor.data

import com.usagemonitor.data.mapper.GeminiSessionLogParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeminiSessionLogParserTest {

    /**
     * A forma que o CLI realmente grava (Gemini CLI 0.58.0, conferida pelo leitor do
     * Codenotch): cada chamada duas vezes com o mesmo `id` — sem tokens ao começar,
     * com tokens ao terminar —, um `$set lastUpdated` depois de cada mensagem e um
     * `$rewindTo`. A versão anterior limpava tudo no `$set` e apagava a chamada
     * desfeita; as duas coisas subcontavam o que o Google cobrou.
     */
    @Test
    fun `counts every answered call once across set patches and rewinds`() {
        val parsed = GeminiSessionLogParser.parse(
            listOf(
                """{"sessionId":"session-one","projectHash":"project-hash","startTime":"2026-09-23T09:59:00Z","kind":"main"}""",
                """{"id":"user-1","type":"user","timestamp":"2026-09-23T10:00:00Z","content":"private-prompt-marker"}""",
                """{"${'$'}set":{"lastUpdated":"2026-09-23T10:00:00Z"}}""",
                """{"id":"call-1","type":"gemini","timestamp":"2026-09-23T10:00:01Z","model":"gemini-2.5-pro","content":""}""",
                """{"${'$'}set":{"lastUpdated":"2026-09-23T10:00:01Z"}}""",
                """{"id":"call-1","type":"gemini","timestamp":"2026-09-23T10:00:01Z","model":"gemini-2.5-pro","tokens":{"input":12345,"output":218,"cached":11000,"thoughts":64,"tool":0,"total":12627},"content":"private-answer-marker"}""",
                """{"${'$'}set":{"lastUpdated":"2026-09-23T10:00:05Z"}}""",
                """{"id":"call-2","type":"gemini","timestamp":"2026-09-23T10:01:00Z","model":"gemini-2.5-flash","tokens":{"total":300}}""",
                """{"${'$'}set":{"lastUpdated":"2026-09-23T10:01:02Z"}}""",
                """{"${'$'}rewindTo":"call-2"}""",
                """{"id":"call-3","type":"gemini","timestamp":"2026-09-23T10:02:00Z","model":"gemini-2.5-flash","tokens":{"total":40}}""",
                """{"${'$'}set":{"lastUpdated":"2026-09-23T10:02:01Z"}}"""
            )
        )

        assertEquals("session-one", parsed.sessionId)
        assertEquals(listOf("call-1", "call-2", "call-3"), parsed.messages.map { it.messageId })
        // `total` já inclui o cache: 12627, e não 12627 + 11000.
        assertEquals(listOf(12_627L, 300L, 40L), parsed.messages.map { it.totalTokens })
        assertFalse(parsed.toString().contains("private-prompt-marker"))
        assertFalse(parsed.toString().contains("private-answer-marker"))
    }

    @Test
    fun `a later write without tokens does not erase the one that had them`() {
        val parsed = GeminiSessionLogParser.parse(
            listOf(
                """{"id":"call","type":"gemini","timestamp":"2026-09-23T10:00:00Z","model":"model-a","tokens":{"total":20}}""",
                """{"id":"call","type":"gemini","timestamp":"2026-09-23T10:00:00Z","model":"model-a"}"""
            )
        )

        assertEquals(20L, parsed.messages.single().totalTokens)
    }

    @Test
    fun `the latest write with tokens wins for a repeated id`() {
        val parsed = GeminiSessionLogParser.parse(
            listOf(
                """{"id":"call","type":"gemini","timestamp":"2026-09-23T10:00:00Z","model":"model-a","tokens":{"total":10}}""",
                """{"id":"call","type":"gemini","timestamp":"2026-09-23T10:00:01Z","model":"model-b","tokens":{"total":25}}"""
            )
        )

        assertEquals("model-b", parsed.messages.single().modelName)
        assertEquals(25L, parsed.messages.single().totalTokens)
    }

    @Test
    fun `embedded messages in a set patch go through the same dedup`() {
        val parsed = GeminiSessionLogParser.parse(
            listOf(
                """{"id":"call","type":"gemini","timestamp":"2026-09-23T10:00:00Z","model":"model-a","tokens":{"total":10}}""",
                """{"${'$'}set":{"sessionId":"session-two","messages":[{"id":"call","type":"gemini","timestamp":"2026-09-23T10:00:00Z","model":"model-a","tokens":{"total":10}},{"id":"other","type":"gemini","timestamp":"2026-09-23T10:02:00Z","model":"model-c","tokens":{"total":30}}]}}"""
            )
        )

        assertEquals("session-two", parsed.sessionId)
        assertEquals(listOf(10L, 30L), parsed.messages.map { it.totalTokens })
    }

    @Test
    fun `missing model, fractional timestamps and non-gemini records`() {
        val parsed = GeminiSessionLogParser.parse(
            listOf(
                """{"id":"a","type":"gemini","timestamp":"2026-09-23T10:00:00.123Z","tokens":{"total":5}}""",
                """{"id":"b","type":"gemini","timestamp":"2026-09-23T10:00:01Z","model":"","tokens":{"total":6}}""",
                """{"id":"c","type":"info","timestamp":"2026-09-23T10:00:02Z","tokens":{"total":999}}""",
                """{"id":"d","type":"gemini","timestamp":"not-a-date","tokens":{"total":7}}""",
                """{"id":"e","type":"gemini","timestamp":"2026-09-23T10:00:03Z","tokens":{"total":-1}}"""
            )
        )

        assertEquals(listOf("a", "b"), parsed.messages.map { it.messageId })
        assertTrue(parsed.messages.all { it.modelName == "Unknown Gemini model" })
    }

    @Test
    fun `valid sessions with no token counters remain recognized and produce no usage`() {
        val parsed = GeminiSessionLogParser.parse(
            listOf(
                """{"sessionId":"empty-session","projectHash":"project-hash"}""",
                """{"id":"message","type":"gemini","timestamp":"2026-09-23T10:00:00Z","model":"gemini-2.5-flash"}"""
            )
        )

        assertTrue(parsed.recognizedRecords > 0)
        assertTrue(parsed.messages.isEmpty())
    }

    @Test
    fun `corrupt-only files are not recognized as a Gemini session`() {
        val parsed = GeminiSessionLogParser.parse(listOf("not-json", "[invalid", "{}"))

        assertEquals(0, parsed.recognizedRecords)
        assertTrue(parsed.messages.isEmpty())
    }
}

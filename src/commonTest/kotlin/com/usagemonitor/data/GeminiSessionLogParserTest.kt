package com.usagemonitor.data

import com.usagemonitor.data.mapper.GeminiSessionLogParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeminiSessionLogParserTest {

    @Test
    fun `uses reported total and removes rewound messages without reading conversation content`() {
        val parsed = GeminiSessionLogParser.parse(
            listOf(
                """{"sessionId":"session-one","projectHash":"project-hash"}""",
                """{"id":"first","type":"gemini","timestamp":"2026-09-23T10:00:00Z","model":"gemini-2.5-flash","tokens":{"input":100,"output":30,"cached":20,"total":110},"content":[{"text":"private-prompt-marker"}]}""",
                """{"id":"rewound","type":"gemini","timestamp":"2026-09-23T10:01:00Z","model":"gemini-2.5-pro","tokens":{"total":250}}""",
                """{"${'$'}rewindTo":"rewound"}""",
                """{"id":"rewound","type":"gemini","timestamp":"2026-09-23T10:02:00Z","model":"gemini-2.5-pro","tokens":{"total":40}}""",
                """{"id":"without-tokens","type":"gemini","timestamp":"2026-09-23T10:03:00Z","model":"gemini-2.5-flash"}"""
            )
        )

        assertEquals("session-one", parsed.sessionId)
        assertEquals(listOf("first", "rewound"), parsed.messages.map { message -> message.messageId })
        assertEquals(listOf(110L, 40L), parsed.messages.map { message -> message.totalTokens })
        assertEquals("gemini-2.5-flash", parsed.messages.first().modelName)
        assertFalse(parsed.toString().contains("private-prompt-marker"))
        assertTrue(parsed.recognizedRecords > 0)
    }

    @Test
    fun `checkpoint replaces prior messages and duplicate IDs keep their latest metric`() {
        val parsed = GeminiSessionLogParser.parse(
            listOf(
                """{"sessionId":"session-two","projectHash":"project-hash"}""",
                """{"id":"duplicate","type":"gemini","timestamp":"2026-09-23T10:00:00Z","model":"model-a","tokens":{"total":10}}""",
                """{"id":"duplicate","type":"gemini","timestamp":"2026-09-23T10:01:00Z","model":"model-b","tokens":{"total":20}}""",
                """{"${'$'}set":{"sessionId":"session-two","messages":[{"id":"checkpoint","type":"gemini","timestamp":"2026-09-23T10:02:00Z","model":"model-c","tokens":{"total":30}}]}}"""
            )
        )

        assertEquals(listOf("checkpoint"), parsed.messages.map { message -> message.messageId })
        assertEquals("model-c", parsed.messages.single().modelName)
        assertEquals(30L, parsed.messages.single().totalTokens)
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

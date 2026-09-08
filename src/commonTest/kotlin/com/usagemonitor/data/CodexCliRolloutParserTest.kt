package com.usagemonitor.data

import com.usagemonitor.data.parser.CodexCliRolloutParser
import com.usagemonitor.domain.entity.CodexCliRolloutSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CodexCliRolloutParserTest {
    @Test
    fun `parses metadata turn context and usage delta without response content`() {
        val parsed = CodexCliRolloutParser().parse(
            listOf(
                """{"type":"session_meta","payload":{"session_id":"s-1","cwd":"C:/work","cli_version":"0.153.4","source":"exec","thread_source":"user"}}""",
                """{"type":"turn_context","timestamp":"2026-09-08T12:00:00Z","payload":{"turn_id":"t-1","cwd":"C:/work","model":"gpt-test"}}""",
                """{"type":"response_item","payload":{"type":"message","content":[{"text":"do not retain"}]}}""",
                """{"type":"token_usage_record","timestamp":"2026-09-08T12:00:01Z","payload":{"session_id":"s-1","turn_id":"t-1","response_id":"r-1","usage":{"input_tokens":100,"cached_input_tokens":40,"cache_write_input_tokens":5,"output_tokens":20,"reasoning_output_tokens":7,"total_tokens":132},"turn_token_usage":{"total_tokens":999}}}"""
            )
        )

        assertEquals("s-1", parsed.metadata?.sessionId)
        assertEquals(CodexCliRolloutSource.EXEC, parsed.metadata?.source)
        assertEquals(1, parsed.turns.size)
        assertEquals("gpt-test", parsed.turns.single().model)
        assertEquals(40, parsed.turns.single().usage.cachedInputTokens)
        assertEquals(132, parsed.turns.single().usage.totalTokens)
        assertEquals(0, parsed.skippedLines)
        assertEquals(0, parsed.unknownLines)
    }

    @Test
    fun `counts malformed unknown and incomplete records without failing`() {
        val parsed = CodexCliRolloutParser().parse(
            listOf(
                "not-json",
                """{"type":"future_event","payload":{}}""",
                """{"type":"token_usage_record","timestamp":"bad","payload":{"session_id":"s","turn_id":"t","response_id":"r","usage":{"total_tokens":1}}}"""
            )
        )

        assertEquals(2, parsed.skippedLines)
        assertEquals(1, parsed.unknownLines)
        assertTrue(parsed.turns.isEmpty())
    }

    @Test
    fun `ignores cumulative usage blocks and keeps response identity`() {
        val parsed = CodexCliRolloutParser().parse(
            listOf(
                """{"type":"session_meta","payload":{"session_id":"s","source":"cli"}}""",
                """{"type":"token_usage_record","timestamp":"2026-09-08T12:00:00Z","payload":{"session_id":"s","turn_id":"t","response_id":"r","usage":{"input_tokens":1,"cached_input_tokens":0,"cache_write_input_tokens":0,"output_tokens":2,"reasoning_output_tokens":0,"total_tokens":3},"thread_token_usage":{"input_tokens":500,"total_tokens":500}}}"""
            )
        )

        assertEquals("r", parsed.turns.single().responseId)
        assertEquals(3, parsed.turns.single().usage.totalTokens)
    }
}

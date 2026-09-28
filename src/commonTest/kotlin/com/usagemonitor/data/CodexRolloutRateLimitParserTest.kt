package com.usagemonitor.data

import com.usagemonitor.data.parser.CodexRolloutRateLimitParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Linhas no formato observado nos rollouts locais em 2026-09-27; o limite
 * `GPT-5.3-Codex-Spark` reproduz o relato do codenotch#286.
 */
class CodexRolloutRateLimitParserTest {

    private fun tokenCount(timestamp: String, limitId: String, limitName: String?, secondaryUsed: Double) =
        """{"timestamp":"$timestamp","type":"event_msg","payload":{"type":"token_count","info":null,""" +
            """"rate_limits":{"limit_id":"$limitId","limit_name":${limitName?.let { "\"$it\"" } ?: "null"},""" +
            """"primary":{"used_percent":0.0,"window_minutes":300,"resets_at":1790567009},""" +
            """"secondary":{"used_percent":$secondaryUsed,"window_minutes":10080,"resets_at":1791066495},""" +
            """"credits":{"has_credits":false,"unlimited":false,"balance":"0"},"plan_type":"prolite"}}}"""

    @Test
    fun `keeps the latest event of each limit id`() {
        val lines = sequenceOf(
            tokenCount("2026-09-27T10:00:00Z", "codex", null, 5.0),
            tokenCount("2026-09-27T10:05:00Z", "spark", "GPT-5.3-Codex-Spark", 90.0),
            tokenCount("2026-09-27T10:10:00Z", "spark", "GPT-5.3-Codex-Spark", 100.0),
            tokenCount("2026-09-27T09:00:00Z", "spark", "GPT-5.3-Codex-Spark", 10.0)
        )

        val result = CodexRolloutRateLimitParser.latestByLimitId(lines)

        assertEquals(listOf("codex", "spark"), result.map { it.limits.limitId })
        assertEquals(100.0, result.last().limits.secondary?.usedPercent)
        assertEquals("prolite", result.last().limits.planType)
    }

    @Test
    fun `ignores truncated lines and other events`() {
        val lines = sequenceOf(
            """{"timestamp":"2026-09-27T10:00:00Z","type":"event_msg","payload":{"type":"agent_message","rate_limits":{"limit_id":"x"}}}""",
            tokenCount("2026-09-27T10:00:00Z", "codex", null, 5.0).dropLast(20),
            "not json \"rate_limits\""
        )

        assertTrue(CodexRolloutRateLimitParser.latestByLimitId(lines).isEmpty())
    }
}

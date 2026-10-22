package com.usagemonitor.terminal

import com.usagemonitor.AnthropicProfileOrigin
import com.usagemonitor.AnthropicProfileRecord
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.UsageTargetKey
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ClaudeCodeStatusInputTest {

    private val home = File("/home/dev")

    @Test
    fun `reads session, transcript and model from the Claude Code json, ignoring the rest`() {
        val input = ClaudeCodeStatusInput.parse(
            """{"hook_event_name":"Status","session_id":"abc-123","transcript_path":"/home/dev/.claude-work/projects/-w/abc-123.jsonl",
               "cwd":"/w","model":{"id":"claude-opus-5-5","display_name":"Opus 5.5"},"cost":{"total_cost_usd":1.2}}"""
        )
        assertEquals(ClaudeCodeStatusInput("abc-123", "/home/dev/.claude-work/projects/-w/abc-123.jsonl", "Opus 5.5"), input)
    }

    @Test
    fun `missing or broken input never fails`() {
        assertEquals(ClaudeCodeStatusInput(), ClaudeCodeStatusInput.parse(""))
        assertEquals(ClaudeCodeStatusInput(), ClaudeCodeStatusInput.parse("not json"))
        assertEquals(ClaudeCodeStatusInput(sessionId = "x"), ClaudeCodeStatusInput.parse("""{"session_id":"x","model":"odd"}"""))
    }

    @Test
    fun `config directory comes from CLAUDE_CONFIG_DIR, then the transcript, then the default`() {
        val input = ClaudeCodeStatusInput(transcriptPath = "/home/dev/.claude-work/projects/-w/abc.jsonl")
        assertEquals(File("/srv/claude"), claudeConfigDirectory(mapOf("CLAUDE_CONFIG_DIR" to "/srv/claude"), input, home))
        assertEquals(File("/home/dev/.claude-work"), claudeConfigDirectory(emptyMap(), input, home))
        assertEquals(File("/home/dev/.claude"), claudeConfigDirectory(emptyMap(), ClaudeCodeStatusInput(), home))
    }

    @Test
    fun `the account is the stored profile pointing at that directory`() {
        val profiles = listOf(
            AnthropicProfileRecord("default", "pessoal", "/home/dev/.claude", true, AnthropicProfileOrigin.DEFAULT),
            AnthropicProfileRecord("work", "trabalho", "/home/dev/.claude-work/", true, AnthropicProfileOrigin.DISCOVERED)
        )
        assertEquals(UsageTargetKey(ApiSource.ANTHROPIC, "work"), anthropicTargetFor(File("/home/dev/.claude-work"), profiles))
        assertEquals(UsageTargetKey(ApiSource.ANTHROPIC, "default"), anthropicTargetFor(File("/home/dev/.claude"), profiles))
        assertNull(anthropicTargetFor(File("/home/dev/.claude-other"), profiles))
    }

    @Test
    fun `a symbolic link to the stored directory still finds the account`() {
        val real = kotlin.io.path.createTempDirectory("usage-monitor-real").toFile()
        val link = File(real.parentFile, "usage-monitor-link-${System.nanoTime()}")
        try {
            java.nio.file.Files.createSymbolicLink(link.toPath(), real.toPath())
            val profiles = listOf(AnthropicProfileRecord("default", "pessoal", real.absolutePath, true, AnthropicProfileOrigin.DEFAULT))
            assertEquals(UsageTargetKey(ApiSource.ANTHROPIC, "default"), anthropicTargetFor(link, profiles))
        } finally {
            link.delete()
            real.deleteRecursively()
        }
    }
}

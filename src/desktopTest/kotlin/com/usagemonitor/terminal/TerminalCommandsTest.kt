package com.usagemonitor.terminal

import com.usagemonitor.data.datasource.LocalDashboardCacheDataSource
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CliSessionMetrics
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.presentation.terminal.TerminalPalette
import com.usagemonitor.presentation.terminal.TerminalProfile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Files
import java.util.UUID
import java.util.prefs.Preferences
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class TerminalCommandsTest {

    private val now = Instant.parse("2026-10-21T17:23:00Z")
    private val clock = object : Clock {
        override fun now(): Instant = now
    }

    private fun run(command: TerminalCommand, collectedAt: Instant?): Pair<Int, String> {
        val node = Preferences.userRoot().node("com.usagemonitor.tests.${UUID.randomUUID()}")
        try {
            val dir = Files.createTempDirectory("usage-monitor-terminal").toFile()
            val cache = File(dir, "dashboard-cache.json")
            if (collectedAt != null) {
                val stats = ApiUsageStats(
                    source = ApiSource.ANTHROPIC,
                    targetKey = UsageTargetKey(ApiSource.ANTHROPIC, "default"),
                    apiName = "Anthropic",
                    quotas = listOf(QuotaInfo(label = "Claude 5h", used = 62, total = 100, periodEndAt = now + 2.hours, unit = UsageUnit.PERCENTAGE)),
                    fetchedAt = collectedAt
                )
                runBlocking { LocalDashboardCacheDataSource(cache).save(listOf(stats), collectedAt) }
            }
            val buffer = ByteArrayOutputStream()
            val console = TerminalConsole(TerminalProfile(TerminalPalette.NONE, unicode = true), PrintStream(buffer, true, "UTF-8"))
            val reader = TerminalReader(cache, File(dir, "app.lock"), node, clock)
            val code = TerminalCommands.run(command, AppLanguage.PT, reader, console)
            return code to buffer.toString(Charsets.UTF_8)
        } finally {
            runCatching {
                node.removeNode()
                node.flush()
            }
        }
    }

    @Test
    fun `status prints the table and exits with 0`() {
        val (code, text) = run(TerminalCommand.Status(), now - 1.minutes)
        assertEquals(0, code)
        assertTrue(text.contains("Claude 5h"))
        assertTrue(text.contains("Nada está coletando"))
    }

    @Test
    fun `status exits with 3 when the reading is older than max age`() {
        val (code, text) = run(TerminalCommand.Status(maxAge = 10.minutes), now - 18.minutes)
        assertEquals(3, code)
        assertTrue(text.startsWith("sem leitura"))
    }

    @Test
    fun `status exits with 3 without any reading`() {
        assertEquals(3, run(TerminalCommand.Status(), collectedAt = null).first)
    }

    @Test
    fun `statusline finds the account of the calling Claude Code and the session in the index`() {
        val node = Preferences.userRoot().node("com.usagemonitor.tests.${UUID.randomUUID()}")
        try {
            val dir = Files.createTempDirectory("usage-monitor-terminal").toFile()
            val configDir = File(dir, ".claude-work")
            node.node("anthropicProfiles").node("work").put("path", configDir.absolutePath)
            val cache = File(dir, "dashboard-cache.json")
            val stats = ApiUsageStats(
                source = ApiSource.ANTHROPIC,
                targetKey = UsageTargetKey(ApiSource.ANTHROPIC, "work"),
                apiName = "Anthropic",
                profileLabel = "trabalho",
                quotas = listOf(QuotaInfo(label = "Claude 5h", used = 62, total = 100, periodEndAt = now + 2.hours, unit = UsageUnit.PERCENTAGE)),
                fetchedAt = now
            )
            runBlocking { LocalDashboardCacheDataSource(cache).save(listOf(stats), now) }
            val buffer = ByteArrayOutputStream()
            val console = TerminalConsole(TerminalProfile(TerminalPalette.NONE, unicode = true), PrintStream(buffer, true, "UTF-8"))
            // Barra invertida do caminho do Windows escapada, como o Claude Code manda no JSON.
            val transcript = File(configDir, "projects/-w/s-1.jsonl").absolutePath.replace("\\", "\\\\")
            val input = """{"session_id":"s-1","transcript_path":"$transcript","model":{"display_name":"Opus 5.5"}}"""
            val code = TerminalCommands.run(
                TerminalCommand.ClaudeCodeStatusLine, AppLanguage.PT, TerminalReader(cache, File(dir, "app.lock"), node, clock), console,
                statusLineInput = { input },
                sessionMetrics = { id -> if (id == "s-1") CliSessionMetrics(1_840_000, 0, null) else null },
                environment = emptyMap(),
                home = dir
            )
            assertEquals(0, code)
            assertEquals("◑ trabalho · 5h 62% ███████▍░░░░ 16:23 · sessão $1.84 · Opus 5.5", buffer.toString(Charsets.UTF_8).trim())
        } finally {
            runCatching {
                node.removeNode()
                node.flush()
            }
        }
    }

    /** O relógio anda só quando o `wait` pausa; a coleta "acontece" no meio da pausa. */
    private class WaitScenario(start: Instant) {
        var now = start
        val clock = object : Clock {
            override fun now(): Instant = now
        }
    }

    private fun waitRun(command: TerminalCommand.Wait, percents: List<Long>): Triple<Int, String, Int> {
        val node = Preferences.userRoot().node("com.usagemonitor.tests.${UUID.randomUUID()}")
        try {
            val dir = Files.createTempDirectory("usage-monitor-terminal").toFile()
            val cache = File(dir, "dashboard-cache.json")
            val scenario = WaitScenario(now)
            fun collect(used: Long) {
                val stats = ApiUsageStats(
                    source = ApiSource.CODEX,
                    apiName = "Codex",
                    quotas = listOf(QuotaInfo(label = "Codex 5h", used = used, total = 100, periodEndAt = now + 2.hours, unit = UsageUnit.PERCENTAGE)),
                    fetchedAt = scenario.now
                )
                runBlocking { LocalDashboardCacheDataSource(cache).save(listOf(stats), scenario.now) }
            }
            collect(percents.first())
            var pauses = 0
            val buffer = ByteArrayOutputStream()
            val console = TerminalConsole(TerminalProfile(TerminalPalette.NONE, unicode = true), PrintStream(buffer, true, "UTF-8"))
            val reader = TerminalReader(cache, File(dir, "app.lock"), node, scenario.clock)
            val code = TerminalCommands.run(command, AppLanguage.PT, reader, console, pause = { duration ->
                pauses += 1
                scenario.now += duration
                percents.getOrNull(pauses)?.let(::collect)
            })
            return Triple(code, buffer.toString(Charsets.UTF_8), pauses)
        } finally {
            runCatching {
                node.removeNode()
                node.flush()
            }
        }
    }

    @Test
    fun `wait rereads the cache until the quota drops and prints only when it changes`() {
        val (code, text, pauses) = waitRun(TerminalCommand.Wait("codex:codex-5h", below = 50), listOf(80, 80, 72, 10))
        assertEquals(0, code)
        assertEquals(3, pauses)
        val lines = text.trim().lines()
        assertEquals(4, lines.size)
        assertTrue(lines[0].startsWith("▲ Nada está coletando"))
        assertTrue(lines[1].contains("(agora 80%)"))
        assertTrue(lines[2].contains("(agora 72%)"))
        assertEquals("✓ Codex 5h (Codex) está em 10%, abaixo de 50%", lines[3])
    }

    @Test
    fun `wait gives up with 1 at the timeout`() {
        val (code, text, pauses) = waitRun(TerminalCommand.Wait("codex:codex-5h", below = 50, timeout = 40.seconds), listOf(80))
        assertEquals(1, code)
        assertEquals(3, pauses)
        assertTrue(text.trim().lines().last().startsWith("tempo esgotado"))
    }
}

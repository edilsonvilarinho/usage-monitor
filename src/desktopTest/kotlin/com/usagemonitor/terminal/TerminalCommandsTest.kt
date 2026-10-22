package com.usagemonitor.terminal

import com.russhwolf.settings.PreferencesSettings
import com.usagemonitor.data.datasource.LocalDashboardCacheDataSource
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.AppLanguage
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
            val reader = TerminalReader(cache, File(dir, "app.lock"), PreferencesSettings(node), clock)
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
}

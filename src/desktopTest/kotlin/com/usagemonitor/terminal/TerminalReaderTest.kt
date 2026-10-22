package com.usagemonitor.terminal

import com.russhwolf.settings.PreferencesSettings
import com.usagemonitor.LANGUAGE_KEY
import com.usagemonitor.SingleInstanceGuard
import com.usagemonitor.data.datasource.LocalDashboardCacheDataSource
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.QuotaZone
import com.usagemonitor.domain.entity.UsageAlertSettings
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.persistAlertSettings
import java.io.File
import java.nio.file.Files
import java.util.UUID
import java.util.prefs.Preferences
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class TerminalReaderTest {

    private val now = Instant.parse("2026-10-21T17:23:00Z")
    private val fixedClock = object : Clock {
        override fun now(): Instant = now
    }

    private fun tempDir(): File = Files.createTempDirectory("usage-monitor-terminal").toFile()

    private fun stats(used: Long) = ApiUsageStats(
        source = ApiSource.ANTHROPIC,
        targetKey = UsageTargetKey(ApiSource.ANTHROPIC, "default"),
        apiName = "Anthropic",
        profileLabel = "pessoal",
        quotas = listOf(QuotaInfo(label = "Claude 5h", used = used, total = 100, periodEndAt = now + 2.hours, unit = UsageUnit.PERCENTAGE)),
        fetchedAt = now
    )

    @Test
    fun `le a ultima leitura do cache do app, com os limiares e o idioma das preferencias`() = withTestSettings { node, settings ->
        val dir = tempDir()
        val cache = File(dir, "dashboard-cache.json")
        runBlocking { LocalDashboardCacheDataSource(cache).save(listOf(stats(80)), now) }
        persistAlertSettings(settings, UsageAlertSettings(quotaPercents = listOf(70, 90)))
        settings.putString(LANGUAGE_KEY, AppLanguage.EN.name)

        val reading = TerminalReader(cache, File(dir, "app.lock"), node, fixedClock).read(languageOverride = null)

        val account = reading.accounts.single()
        assertEquals("pessoal", account.profile)
        assertEquals(QuotaZone.ATTENTION, account.zoneOf(account.quotas.single()))
        assertEquals(AppLanguage.EN, reading.language)
        assertEquals(now, reading.lastCollectedAt)
        assertFalse(reading.collectorRunning)
    }

    @Test
    fun `--locale vence a preferencia gravada`() = withTestSettings { node, settings ->
        val dir = tempDir()
        settings.putString(LANGUAGE_KEY, AppLanguage.EN.name)
        val reading = TerminalReader(File(dir, "dashboard-cache.json"), File(dir, "app.lock"), node, fixedClock).read(AppLanguage.PT)
        assertEquals(AppLanguage.PT, reading.language)
    }

    @Test
    fun `sem cache nao ha contas nem leitura`() = withTestSettings { node, _ ->
        val dir = tempDir()
        val reading = TerminalReader(File(dir, "dashboard-cache.json"), File(dir, "app.lock"), node, fixedClock).read(null)
        assertTrue(reading.accounts.isEmpty())
        assertEquals(null, reading.lastCollectedAt)
    }

    @Test
    fun `com o app segurando a trava, ha coletor vivo, e a leitura nao a toma`() = withTestSettings { node, _ ->
        val dir = tempDir()
        val lock = File(dir, "app.lock")
        val app = SingleInstanceGuard.tryAcquire(lock)
        try {
            val reading = TerminalReader(File(dir, "dashboard-cache.json"), lock, node, fixedClock).read(null)
            assertTrue(reading.collectorRunning)
        } finally {
            app?.close()
        }
        // Solta a trava ao terminar: o app ainda consegue abrir depois da leitura.
        val reopened = SingleInstanceGuard.tryAcquire(lock)
        try {
            assertTrue(reopened != null)
        } finally {
            reopened?.close()
        }
    }

    private fun withTestSettings(block: (Preferences, PreferencesSettings) -> Unit) {
        val node = Preferences.userRoot().node("com.usagemonitor.tests.${UUID.randomUUID()}")
        try {
            block(node, PreferencesSettings(node))
        } finally {
            runCatching {
                node.removeNode()
                node.flush()
            }
        }
    }
}

package com.usagemonitor.terminal

import com.usagemonitor.SingleInstanceGuard
import com.usagemonitor.data.datasource.LocalCollectorHealthDataSource
import com.usagemonitor.data.datasource.LocalDashboardCacheDataSource
import com.usagemonitor.data.datasource.LocalUsageHistoryDataSource
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.CollectorHealth
import com.usagemonitor.domain.entity.CollectorSourceHealth
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.UsageAccountContext
import com.usagemonitor.domain.entity.UsageAccountKey
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.presentation.terminal.TuiState
import java.io.File
import java.nio.file.Files
import java.util.UUID
import java.util.prefs.Preferences
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class TuiReaderTest {

    private val now = Instant.parse("2026-10-21T17:23:00Z")
    private val clock = object : Clock {
        override fun now(): Instant = now
    }

    private fun stats(source: ApiSource, profile: String?, label: String, used: Long, apiName: String) = ApiUsageStats(
        source = source,
        targetKey = UsageTargetKey(source, profile),
        apiName = apiName,
        profileLabel = profile?.let { "conta $it" },
        quotas = listOf(QuotaInfo(label = label, used = used, total = 100, periodEndAt = now + 2.hours, unit = UsageUnit.PERCENTAGE)),
        fetchedAt = now - 11.minutes
    )

    @Test
    fun `cada conta recebe a falha da saude do coletor, pela chave ou pelo rotulo antigo`() {
        val dir = Files.createTempDirectory("usage-monitor-tui").toFile()
        val node = Preferences.userRoot().node("com.usagemonitor.tests.${UUID.randomUUID()}")
        val lock = File(dir, "app.lock")
        val cache = File(dir, "dashboard-cache.json")
        runBlocking {
            LocalDashboardCacheDataSource(cache).save(
                listOf(stats(ApiSource.ANTHROPIC, "work", "Claude 5h", 91, "Anthropic"), stats(ApiSource.CODEX, null, "Codex 5h", 24, "Codex")),
                now
            )
        }
        val health = LocalCollectorHealthDataSource(File(dir, "collector-health.json"))
        health.write(
            CollectorHealth(
                updatedAtEpochMillis = now.toEpochMilliseconds(),
                nextCollectionAtEpochMillis = (now + 1.minutes).toEpochMilliseconds(),
                sources = listOf(
                    CollectorSourceHealth(ApiSource.ANTHROPIC, "Anthropic conta work", ok = false, retryAtEpochMillis = (now + 4.minutes).toEpochMilliseconds(), target = "ANTHROPIC:work", failure = "limite da API"),
                    // Coletor anterior à #441: sem a chave do alvo, só o rótulo.
                    CollectorSourceHealth(ApiSource.CODEX, "Codex", ok = false)
                )
            )
        )
        val app = SingleInstanceGuard.tryAcquire(lock)
        try {
            val reader = TuiReader(TerminalReader(cache, lock, node, clock), health = health)
            val data = reader.load(TuiState())

            val (anthropic, codex) = data.accounts
            assertEquals("limite da API", anthropic.failure?.reason)
            assertEquals(now + 4.minutes, anthropic.failure?.retryAt)
            assertEquals(now + 1.minutes, codex.failure?.retryAt)
            assertNull(codex.failure?.reason)
            assertTrue(data.lastFailed)
            assertFalse(data.teamConfigured)
        } finally {
            app?.close()
            runCatching { node.removeNode() }
            dir.deleteRecursively()
        }
    }

    @Test
    fun `sem coletor a saude e de uma execucao que acabou e nao conta`() {
        val dir = Files.createTempDirectory("usage-monitor-tui").toFile()
        val node = Preferences.userRoot().node("com.usagemonitor.tests.${UUID.randomUUID()}")
        val cache = File(dir, "dashboard-cache.json")
        runBlocking { LocalDashboardCacheDataSource(cache).save(listOf(stats(ApiSource.CODEX, null, "Codex 5h", 24, "Codex")), now) }
        val health = LocalCollectorHealthDataSource(File(dir, "collector-health.json"))
        health.write(CollectorHealth(now.toEpochMilliseconds(), sources = listOf(CollectorSourceHealth(ApiSource.CODEX, "Codex", ok = false, target = "CODEX"))))
        try {
            val data = TuiReader(TerminalReader(cache, File(dir, "app.lock"), node, clock), health = health).load(TuiState())
            assertNull(data.accounts.single().failure)
            assertNull(data.collector)
        } finally {
            runCatching { node.removeNode() }
            dir.deleteRecursively()
        }
    }

    @Test
    fun `as ultimas 24 h saem do historico em somente leitura`() {
        val dir = Files.createTempDirectory("usage-monitor-tui").toFile()
        val node = Preferences.userRoot().node("com.usagemonitor.tests.${UUID.randomUUID()}")
        val cache = File(dir, "dashboard-cache.json")
        val history = File(dir, "usage-history.db")
        val codex = stats(ApiSource.CODEX, null, "Codex 5h", 24, "Codex").copy(
            accountContext = UsageAccountContext(UsageAccountKey(ApiSource.CODEX, "codex-a", "workspace-a"), email = "a@example.com")
        )
        runBlocking {
            LocalDashboardCacheDataSource(cache).save(listOf(codex), now)
            LocalUsageHistoryDataSource(history).use { writer ->
                writer.insertSnapshot(codex.copy(quotas = listOf(codex.quotas.single().copy(used = 10))), now - 3.hours)
                writer.insertSnapshot(codex, now - 10.minutes)
            }
        }
        try {
            TuiStores(cliIndex = File(dir, "sem-indice.db"), codexIndex = File(dir, "sem-codex.db"), history = history).use { stores ->
                val data = TuiReader(TerminalReader(cache, File(dir, "app.lock"), node, clock), stores).load(TuiState())
                val spark = data.accounts.single().last24h
                assertEquals(48, spark?.cells?.size)
                assertEquals(10, spark?.cells?.get(42))
                assertEquals(24, spark?.cells?.get(47))
                // Sem índice do Codex, a sessão ativa fica sem medida, e nenhum banco nasce.
                assertNull(data.accounts.single().activeSession)
                assertFalse(File(dir, "sem-codex.db").exists())
            }
        } finally {
            runCatching { node.removeNode() }
            dir.deleteRecursively()
        }
    }
}

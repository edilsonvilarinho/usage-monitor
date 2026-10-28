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
import com.usagemonitor.presentation.terminal.TuiActiveSession
import com.usagemonitor.presentation.terminal.TuiAnalysisState
import com.usagemonitor.presentation.terminal.TuiState
import com.usagemonitor.presentation.terminal.TuiTab
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

/** Um nó em memória que conta os `sync()`: o que importa é a TUI pedir ao disco o que outra JVM gravou. */
private class CountingPreferences : java.util.prefs.AbstractPreferences(null, "") {
    private val values = mutableMapOf<String, String>()
    var syncs = 0

    override fun putSpi(key: String, value: String) {
        values[key] = value
    }

    override fun getSpi(key: String): String? = values[key]
    override fun removeSpi(key: String) {
        values.remove(key)
    }

    override fun removeNodeSpi() = Unit
    override fun keysSpi(): Array<String> = values.keys.toTypedArray()
    override fun childrenNamesSpi(): Array<String> = emptyArray()
    override fun childSpi(name: String): java.util.prefs.AbstractPreferences = CountingPreferences()
    override fun syncSpi() {
        syncs += 1
    }

    override fun flushSpi() = Unit
}

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
                // A aba 3 (#463, AI4): as janelas e as leituras da cota, pelo mesmo caso de uso do app.
                val row = TuiReader(TerminalReader(cache, File(dir, "app.lock"), node, clock), stores).load(TuiState(tab = TuiTab.HISTORY)).history?.rows?.single()
                assertEquals(2, row?.points?.size)
                assertEquals(listOf(24), row?.windows?.map { window -> window.peakPercent })
            }
        } finally {
            runCatching { node.removeNode() }
            dir.deleteRecursively()
        }
    }

    @Test
    fun `cada leitura ressincroniza as preferencias que o coletor grava em outra JVM`() {
        val dir = Files.createTempDirectory("usage-monitor-tui").toFile()
        val node = CountingPreferences()
        try {
            val reader = TuiReader(TerminalReader(File(dir, "dashboard-cache.json"), File(dir, "app.lock"), node, clock), health = LocalCollectorHealthDataSource(File(dir, "collector-health.json")))
            reader.load(TuiState())
            reader.load(TuiState())
            // Dois `sync()` por leitura: o primeiro reconhece a gravação da outra JVM, o segundo relê.
            assertEquals(4, node.syncs)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `the overview shows the Claude Code session active in the last 10 minutes and none when it is older`() {
        val dir = Files.createTempDirectory("usage-monitor-tui").toFile()
        val node = Preferences.userRoot().node("com.usagemonitor.tests.${UUID.randomUUID()}")
        val cache = File(dir, "dashboard-cache.json")
        runBlocking { LocalDashboardCacheDataSource(cache).save(listOf(stats(ApiSource.ANTHROPIC, "default", "Claude 5h", 40, "Anthropic")), now) }
        val index = ClaudeTranscriptFixture.indexed(
            dir,
            mapOf(
                "agora" to listOf(
                    ClaudeTranscriptFixture.assistantLine("agora", "m-1", "2026-10-21T17:05:00Z", 2_000, 200),
                    ClaudeTranscriptFixture.assistantLine("agora", "m-2", "2026-10-21T17:20:00Z", 1_000, 100)
                )
            )
        )
        try {
            TuiStores(cliIndex = index, codexIndex = File(dir, "sem-codex.db"), history = File(dir, "sem-historico.db")).use { stores ->
                val active = TuiReader(TerminalReader(cache, File(dir, "app.lock"), node, clock), stores).load(TuiState()).accounts.single().activeSession
                assertEquals("usage-monitor", active?.project)
                assertEquals("claude-opus-5", active?.model)
                assertTrue((active?.costMicros ?: 0L) > 0L, active.toString())
                assertFalse(active?.costIncomplete ?: true)

                val later = object : Clock {
                    override fun now(): Instant = now + 30.minutes
                }
                val idle = TuiReader(TerminalReader(cache, File(dir, "app.lock"), node, later), stores).load(TuiState()).accounts.single().activeSession
                assertEquals(TuiActiveSession.NONE, idle)
            }
        } finally {
            runCatching { node.removeNode() }
            dir.deleteRecursively()
        }
    }

    @Test
    fun `the Codex default account reads its active session without a price, and other tabs skip it`() {
        val dir = Files.createTempDirectory("usage-monitor-tui").toFile()
        val node = Preferences.userRoot().node("com.usagemonitor.tests.${UUID.randomUUID()}")
        val cache = File(dir, "dashboard-cache.json")
        runBlocking { LocalDashboardCacheDataSource(cache).save(listOf(stats(ApiSource.CODEX, null, "Codex 5h", 24, "Codex")), now) }
        val codex = CodexRolloutFixture.indexed(
            dir, "codex-1", "/workspace/usage-monitor",
            listOf(CodexRolloutFixture.Response("t-1", "2026-10-21T17:15:00Z", "2026-10-21T17:18:00Z", input = 900, cached = 300, output = 90))
        )
        try {
            TuiStores(cliIndex = File(dir, "sem-indice.db"), codexIndex = codex, history = File(dir, "sem-historico.db")).use { stores ->
                val reader = TuiReader(TerminalReader(cache, File(dir, "app.lock"), node, clock), stores)

                val active = reader.load(TuiState()).accounts.single().activeSession
                assertEquals("usage-monitor", active?.project)
                assertEquals("gpt-5.5-codex", active?.model)
                assertNull(active?.costMicros)
                assertFalse(active?.costIncomplete ?: true)
                assertNull(reader.load(TuiState(tab = TuiTab.SESSIONS)).accounts.single().activeSession)
            }
        } finally {
            runCatching { node.removeNode() }
            dir.deleteRecursively()
        }
    }

    @Test
    fun `the analysis tab keeps the chosen account inside the eligible ones`() {
        val dir = Files.createTempDirectory("usage-monitor-tui").toFile()
        val node = Preferences.userRoot().node("com.usagemonitor.tests.${UUID.randomUUID()}")
        val cache = File(dir, "dashboard-cache.json")
        runBlocking {
            LocalDashboardCacheDataSource(cache).save(
                listOf(stats(ApiSource.ANTHROPIC, "work", "Claude 5h", 40, "Anthropic"), stats(ApiSource.CODEX, null, "Codex 5h", 24, "Codex")),
                now
            )
        }
        try {
            val withoutStores = TuiReader(TerminalReader(cache, File(dir, "app.lock"), node, clock))
            val past = withoutStores.load(TuiState(tab = TuiTab.ANALYSIS, analysis = TuiAnalysisState(account = 9))).analysis
            assertEquals(listOf(ApiSource.ANTHROPIC, ApiSource.CODEX), past?.accounts?.map { account -> account.source })
            assertEquals(1, past?.index)
            // Sem os bancos, sem análise: a aba diz que não há histórico.
            assertNull(past?.analysis)

            TuiStores(cliIndex = File(dir, "sem-indice.db"), codexIndex = File(dir, "sem-codex.db"), history = File(dir, "sem-historico.db")).use { stores ->
                val first = TuiReader(TerminalReader(cache, File(dir, "app.lock"), node, clock), stores).load(TuiState(tab = TuiTab.ANALYSIS, analysis = TuiAnalysisState(account = -3))).analysis
                assertEquals(0, first?.index)
            }
        } finally {
            runCatching { node.removeNode() }
            dir.deleteRecursively()
        }
    }
}

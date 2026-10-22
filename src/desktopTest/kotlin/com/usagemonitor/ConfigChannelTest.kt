package com.usagemonitor

import com.usagemonitor.data.datasource.LocalConfigRequestChannel
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.CollectorKind
import com.usagemonitor.domain.repository.NoOpBreadcrumbRecorder
import com.usagemonitor.terminal.ConfigDelivery
import com.usagemonitor.terminal.TerminalConfigClient
import java.io.File
import java.nio.file.Files
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** O canal de configuração do terminal para o coletor (#440, decisão D1). */
class ConfigChannelTest {

    private val dir: File = Files.createTempDirectory("usage-monitor-config").toFile()
    private val node: Preferences = Preferences.userRoot().node("com.usagemonitor.tests.${UUID.randomUUID()}")

    @AfterTest
    fun cleanup() {
        runCatching {
            node.removeNode()
            node.flush()
        }
        dir.deleteRecursively()
    }

    private fun store() = AppSettingsStore(NoOpBreadcrumbRecorder, null, node, dataFile = { name -> File(dir, name) }, homeDirectory = { dir })

    private fun channel() = LocalConfigRequestChannel(File(dir, "control"))

    @Test
    fun `a request is taken once, in order, and its reply is read once`() {
        val channel = channel()
        val first = channel.submit("um")
        val second = channel.submit("dois")
        assertEquals(listOf("um", "dois"), channel.takePending().map { (_, payload) -> payload })
        assertTrue(channel.takePending().isEmpty())
        assertFalse(File(dir, "control/requests/$first.json").exists())

        channel.reply(second, "feito")
        assertEquals("feito", channel.awaitReply(second, timeoutMillis = 0))
        assertNull(channel.awaitReply(second, timeoutMillis = 0))
    }

    @Test
    fun `a withdrawn request is never applied, and a taken one cannot be withdrawn`() {
        val channel = channel()
        val withdrawn = channel.submit("a")
        assertTrue(channel.withdraw(withdrawn))
        assertTrue(channel.takePending().isEmpty())

        val taken = channel.submit("b")
        channel.takePending()
        assertFalse(channel.withdraw(taken))
    }

    @Test
    fun `a request file is readable only by its owner`() {
        val channel = channel()
        val id = channel.submit("segredo")
        val permissions = runCatching { Files.getPosixFilePermissions(File(dir, "control/requests/$id.json").toPath()) }.getOrNull()
        // Só no POSIX há o que conferir; no Windows a permissão é por ACL.
        if (permissions != null) {
            assertEquals(setOf("OWNER_READ", "OWNER_WRITE"), permissions.map { permission -> permission.name }.toSet())
        }
    }

    @Test
    fun `a source that needs a key is not enabled without it`() {
        val store = store()
        val outcome = ConfigApplier(store).apply(ConfigChange.SourceEnabled(ApiSource.MINIMAX.name, true))
        assertEquals(ConfigOutcome.Refused(ConfigRefusals.KEY_REQUIRED, ApiSource.MINIMAX.name), outcome)
        assertFalse(ApiSource.MINIMAX in store.enabledApis.value)
        store.close()
    }

    @Test
    fun `enabling a source writes the preference and asks for a new collection`() {
        val refreshed = mutableListOf<ApiSource>()
        val store = store()
        val applier = ConfigApplier(store, ConfigHooks(refreshSource = { source -> refreshed += source }))

        assertEquals(ConfigOutcome.Applied(), applier.apply(ConfigChange.SourceEnabled(ApiSource.CODEX.name, true)))
        assertEquals(listOf(ApiSource.CODEX), refreshed)
        assertEquals(listOf(ApiSource.CODEX), readApiSourceCollection(store.settings, ENABLED_APIS_KEY))
        store.close()

        assertEquals(setOf(ApiSource.CODEX), store().enabledApis.value)
        assertEquals(ConfigOutcome.Refused(ConfigRefusals.UNKNOWN_SOURCE, "NOPE"), applier.apply(ConfigChange.SourceEnabled("NOPE", true)))
    }

    @Test
    fun `the collector serves a request from the terminal and the client hears the answer`() {
        val store = store()
        val applier = ConfigApplier(store)
        val applied = mutableListOf<ConfigChange>()
        val collector = thread {
            repeat(50) {
                serveConfigRequests(channel(), applier) { change -> applied += change }
                Thread.sleep(20)
            }
        }
        val client = TerminalConfigClient(collector = { CollectorKind.SERVICE }, channel = channel(), localStore = { error("o coletor está vivo") })

        val delivery = client.apply(ConfigChange.SourceEnabled(ApiSource.ANTHROPIC.name, true))
        collector.join()

        assertEquals(ConfigDelivery.Answered(ConfigOutcome.Applied(), CollectorKind.SERVICE), delivery)
        assertEquals(listOf<ConfigChange>(ConfigChange.SourceEnabled(ApiSource.ANTHROPIC.name, true)), applied)
        assertTrue(ApiSource.ANTHROPIC in store.enabledApis.value)
        store.close()
    }

    @Test
    fun `a silent collector leaves nothing changed and nothing queued`() {
        val client = TerminalConfigClient(collector = { CollectorKind.APP }, channel = channel(), timeoutMillis = 200, localStore = { error("o coletor está vivo") })

        val delivery = client.apply(ConfigChange.SourceEnabled(ApiSource.ANTHROPIC.name, true))

        assertEquals(ConfigDelivery.NotAnswered(CollectorKind.APP), delivery)
        assertTrue(channel().takePending().isEmpty())
    }

    @Test
    fun `without a collector the terminal applies the change itself`() {
        val client = TerminalConfigClient(collector = { null }, channel = channel(), localStore = ::store)

        val delivery = client.apply(ConfigChange.SourceEnabled(ApiSource.CODEX.name, true))

        assertEquals(ConfigDelivery.Answered(ConfigOutcome.Applied(), null), delivery)
        assertEquals(setOf(ApiSource.CODEX), store().enabledApis.value)
    }
}

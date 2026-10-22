package com.usagemonitor.terminal

import com.usagemonitor.AppSettingsStore
import com.usagemonitor.CommandLineRequest
import com.usagemonitor.ConfigApplier
import com.usagemonitor.data.datasource.LocalConfigRequestChannel
import com.usagemonitor.data.datasource.TelegramBotApi
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CollectorKind
import com.usagemonitor.domain.entity.TeamIntegrationSettings
import com.usagemonitor.domain.repository.NoOpBreadcrumbRecorder
import com.usagemonitor.parseCommandLine
import com.usagemonitor.presentation.terminal.TerminalPalette
import com.usagemonitor.presentation.terminal.TerminalProfile
import com.usagemonitor.serveConfigRequests
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.prefs.Preferences
import kotlin.concurrent.thread

/**
 * Uma pasta de dados e um nó de preferências temporários para exercitar os comandos de
 * configurar (#440) de ponta a ponta: o parser, o cliente e o [ConfigApplier]. Com
 * [collector], uma thread faz o papel do coletor vivo e atende os pedidos.
 */
internal class TerminalConfigHarness(
    private val collector: CollectorKind? = null,
    private val language: AppLanguage = AppLanguage.PT,
    /** A falha da coleta com a chave candidata; `null` é chave válida. */
    private val keyProbe: (ApiSource, String) -> Throwable? = { _, _ -> null },
    private val telegramApi: () -> TelegramBotApi = { error("o teste não fala com o Telegram") },
    private val teamCheck: (TeamIntegrationSettings, List<TeamCheckAccount>) -> List<String> = { _, _ -> emptyList() }
) : AutoCloseable {
    val dir: File = Files.createTempDirectory("usage-monitor-config").toFile()
    val node: Preferences = Preferences.userRoot().node("com.usagemonitor.tests.${UUID.randomUUID()}")
    private val channel = LocalConfigRequestChannel(File(dir, "control"))
    private val serving = AtomicBoolean(true)
    private val collectorStore: AppSettingsStore? = collector?.let { store() }
    private val collectorThread = collectorStore?.let { store ->
        val applier = ConfigApplier(store)
        thread(isDaemon = true) {
            while (serving.get()) {
                serveConfigRequests(channel, applier)
                Thread.sleep(10)
            }
        }
    }

    fun store(): AppSettingsStore =
        AppSettingsStore(NoOpBreadcrumbRecorder, null, node, dataFile = { name -> File(dir, name) }, homeDirectory = { dir }, environment = { null })

    fun file(name: String): File = File(dir, name)

    /** Roda a linha de comando e devolve o código e a saída, sem cor. */
    fun run(vararg arguments: String, unicode: Boolean = true, secret: String? = null): Pair<Int, String> {
        val request = parseCommandLine(arrayOf(*arguments))
        val command = (request as? CommandLineRequest.Terminal)?.command as? ConfigCommand
            ?: error("não é um comando de configurar: $request")
        val buffer = ByteArrayOutputStream()
        val console = TerminalConsole(TerminalProfile(TerminalPalette.NONE, unicode = unicode), PrintStream(buffer, true, "UTF-8"))
        val client = TerminalConfigClient(collector = { collector }, channel = channel, localStore = ::store, timeoutMillis = 3_000)
        val runner = TerminalConfigRunner(
            client, TerminalReader(File(dir, "dashboard-cache.json"), File(dir, "app.lock"), node), node, ::file,
            secretInput = { _ -> secret },
            home = dir,
            codexHome = File(dir, ".codex"),
            keyProbe = keyProbe,
            telegramApi = telegramApi,
            teamCheck = teamCheck,
            webAddresses = { listOf("192.168.0.10") }
        )
        val code = runner.run(command, language, console)
        return code to buffer.toString(Charsets.UTF_8)
    }

    override fun close() {
        serving.set(false)
        collectorThread?.join()
        collectorStore?.close()
        runCatching {
            node.removeNode()
            node.flush()
        }
        dir.deleteRecursively()
    }
}

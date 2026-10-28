package com.usagemonitor.terminal

import com.usagemonitor.SingleInstanceGuard
import com.usagemonitor.data.datasource.CollectorRegistration
import com.usagemonitor.data.datasource.LocalAlertLogDataSource
import com.usagemonitor.data.datasource.LocalCollectorHealthDataSource
import com.usagemonitor.data.datasource.LocalCollectorRegistrationDataSource
import com.usagemonitor.data.datasource.LocalDashboardCacheDataSource
import com.usagemonitor.domain.entity.AlertDestination
import com.usagemonitor.domain.entity.AlertLogEntry
import com.usagemonitor.domain.entity.AlertLogKind
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CollectorHealth
import com.usagemonitor.domain.entity.CollectorKind
import com.usagemonitor.domain.entity.CollectorSourceHealth
import com.usagemonitor.domain.entity.CollectorTeamHealth
import com.usagemonitor.domain.entity.CollectorTelegramHealth
import com.usagemonitor.domain.entity.CollectorWebHealth
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
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** `usage-monitor service …` (#440, 5.1): a unit do systemd do usuário e a saúde. */
class TerminalServiceCommandsTest {

    private val now = Instant.parse("2026-10-22T17:23:12Z")
    private val clock = object : Clock {
        override fun now(): Instant = now
    }
    private val dir: File = Files.createTempDirectory("usage-monitor-service").toFile()
    private val node: Preferences = Preferences.userRoot().node("com.usagemonitor.tests.${UUID.randomUUID()}")
    private val calls = mutableListOf<List<String>>()

    @AfterTest
    fun cleanup() {
        runCatching {
            node.removeNode()
            node.flush()
        }
        dir.deleteRecursively()
    }

    /** O systemd de mentira: grava o que foi chamado e responde como um servidor com linger desligado. */
    /** Resposta própria de um teste; `null` cai na resposta padrão abaixo. */
    private var answer: (List<String>) -> ProcessResult? = { null }

    private val fakeSystem = ProcessRunner { command ->
        calls += command
        answer(command) ?: when {
            command.contains("show-environment") -> ProcessResult(0, "PATH=/usr/bin")
            command.contains("show") -> ProcessResult(0, "LoadState=loaded\nActiveState=active\nSubState=running\nMainPID=4121\nMemoryCurrent=190840832\n")
            command.first() == "loginctl" -> ProcessResult(0, "no\n")
            else -> ProcessResult(0, "")
        }
    }

    private fun commands(
        isLinux: Boolean = true,
        defaultDataRoot: Boolean = true,
        appPath: String? = "/opt/usage-monitor/bin/Usage Monitor"
    ) = TerminalServiceCommands(
        systemd = SystemdUserService(fakeSystem, File(dir, ".config")),
        runner = fakeSystem,
        streaming = { command -> calls += command; 0 },
        reader = TerminalReader(File(dir, "dashboard-cache.json"), File(dir, "app.lock"), node, clock, LocalCollectorRegistrationDataSource(File(dir, "collector.json"))),
        registration = LocalCollectorRegistrationDataSource(File(dir, "collector.json")),
        health = LocalCollectorHealthDataSource(File(dir, "collector-health.json")),
        alertLog = LocalAlertLogDataSource(File(dir, "alerts.jsonl")),
        isLinux = isLinux,
        defaultDataRoot = defaultDataRoot,
        appPath = appPath,
        home = dir,
        user = "dev",
        dataFile = { name -> File(dir, ".usage-monitor/$name") },
        clock = clock
    )

    private fun run(command: ServiceCommand, commands: TerminalServiceCommands = commands()): Pair<Int, String> {
        val buffer = ByteArrayOutputStream()
        val console = TerminalConsole(TerminalProfile(TerminalPalette.NONE, unicode = true), PrintStream(buffer, true, "UTF-8"))
        val code = commands.run(command, AppLanguage.PT, console)
        // Fim de linha e separador do Windows: a unit e os caminhos do teste são os do Linux.
        return code to buffer.toString(Charsets.UTF_8).replace("\r\n", "\n").replace('\\', '/')
    }

    @Test
    fun `the unit runs the package binary quoted, restarts on failure and treats SIGTERM as success`() {
        val unit = SystemdUserService.unitText("/opt/usage-monitor/bin/Usage Monitor")
        assertTrue(unit.contains("ExecStart=\"/opt/usage-monitor/bin/Usage Monitor\" service run\n"), unit)
        assertTrue(unit.contains("Restart=on-failure\nRestartSec=30\nSuccessExitStatus=143\n"), unit)
        assertTrue(unit.contains("WantedBy=default.target"), unit)
    }

    @Test
    fun `the script install runs the stable launcher, so the service follows updates`() {
        val launcher = File(dir, ".local/bin/usage-monitor").apply { parentFile.mkdirs(); writeText("#!/bin/sh\n"); setExecutable(true) }
        val tree = File(dir, ".local/share/usage-monitor/versions/41.12.0/Usage Monitor/bin/Usage Monitor").apply { parentFile.mkdirs(); writeText("") }
        assertEquals(launcher.path, SystemdUserService.executable(tree.path, dir))
        assertEquals("/opt/usage-monitor/bin/Usage Monitor", SystemdUserService.executable("/opt/usage-monitor/bin/Usage Monitor", dir))
        assertEquals(null, SystemdUserService.executable(null, dir))
    }

    @Test
    fun `install writes the unit, enables and starts it, and shows the linger command`() {
        val (code, output) = run(ServiceCommand.Install)

        assertEquals(0, code, output)
        assertEquals(
            "✓ serviço criado em ~/.config/systemd/user/usage-monitor.service\n" +
                "✓ ativo e marcado para iniciar com o sistema\n" +
                "▲ Para continuar rodando depois que você sair do SSH, rode uma vez:\n" +
                "    sudo loginctl enable-linger dev\n",
            output
        )
        assertTrue(File(dir, ".config/systemd/user/usage-monitor.service").isFile)
        assertTrue(calls.contains(listOf("systemctl", "--user", "enable", "--now", "usage-monitor.service")))
    }

    @Test
    fun `the service exists only on Linux, only in the default data folder and only installed`() {
        assertEquals(1, run(ServiceCommand.Install, commands(isLinux = false)).first)
        assertTrue(run(ServiceCommand.Status, commands(isLinux = false)).second.contains("usage-monitor config set system.autostart true"))
        assertTrue(run(ServiceCommand.Install, commands(defaultDataRoot = false)).second.contains("usage-monitor --user-data-dir <pasta> service run"))
        assertTrue(run(ServiceCommand.Install, commands(appPath = null)).second.startsWith("▲ Este Usage Monitor não foi instalado"))
        assertEquals(1, run(ServiceCommand.Control("restart")).first)
        assertTrue(calls.none { command -> command.contains("restart") })
    }

    @Test
    fun `status puts together the unit, the collector, the health and the last alert`() {
        run(ServiceCommand.Install)
        val stats = ApiUsageStats(
            source = ApiSource.ANTHROPIC, targetKey = UsageTargetKey(ApiSource.ANTHROPIC, "default"), apiName = "Anthropic", profileLabel = "Max",
            quotas = listOf(QuotaInfo(label = "Claude 5h", used = 95, total = 100, periodEndAt = now + 2.hours, unit = UsageUnit.PERCENTAGE)),
            fetchedAt = now - 12.seconds
        )
        runBlocking { LocalDashboardCacheDataSource(File(dir, "dashboard-cache.json")).save(listOf(stats), now - 12.seconds) }
        LocalCollectorRegistrationDataSource(File(dir, "collector.json")).write(CollectorRegistration(CollectorKind.SERVICE, 4121L, now - 76.hours, "41.12.0"))
        LocalCollectorHealthDataSource(File(dir, "collector-health.json")).write(
            CollectorHealth(
                updatedAtEpochMillis = (now - 10.seconds).toEpochMilliseconds(),
                nextCollectionAtEpochMillis = (now + 48.seconds).toEpochMilliseconds(),
                sources = listOf(CollectorSourceHealth(ApiSource.ANTHROPIC, "Anthropic Max", ok = true), CollectorSourceHealth(ApiSource.CODEX, "Codex cliente", ok = false)),
                telegram = CollectorTelegramHealth("connected", "uso_bot", 1),
                team = CollectorTeamHealth(true, "https://time.exemplo.com", (now - 12.seconds).toEpochMilliseconds()),
                web = CollectorWebHealth("running", 8787, listOf("10.0.0.12"))
            )
        )
        LocalAlertLogDataSource(File(dir, "alerts.jsonl")).append(AlertLogEntry((now - 92.minutes).toEpochMilliseconds(), AlertLogKind.CRITICAL, "Max · Claude 5h", 91, listOf(AlertDestination.TELEGRAM)))
        val lock = SingleInstanceGuard.tryAcquire(File(dir, "app.lock"))
        try {
            val (code, output) = run(ServiceCommand.Status)

            assertEquals(0, code, output)
            val lines = output.lines()
            assertEquals("● usage-monitor ativo há 3d04h · pid 4121 · 182 MB", lines[0])
            assertEquals("  coleta       última 14:23 BRT · próxima 14:24 · 1 cotas, 1 crítica", lines[1])
            assertEquals("  fontes       ✓ Anthropic Max  ▲ Codex cliente", lines[2])
            assertEquals("  telegram     conectado como @uso_bot · 1 conversa · último aviso 12:51 BRT", lines[3])
            assertEquals("  time         enviado há 12 s para time.exemplo.com", lines[4])
            assertEquals("  acesso web   http://10.0.0.12:8787 · exige o token", lines[5])
            assertEquals("  versão       41.12.0 · pacote do sistema (atualize com o apt ou o dnf)", lines[6])
        } finally {
            lock?.close()
        }
    }

    @Test
    fun `logs follows the journal of the unit and points to the app log`() {
        val (code, output) = run(ServiceCommand.Logs(follow = true, lines = 20))
        assertEquals(0, code)
        assertEquals(listOf("journalctl", "--user", "--unit", "usage-monitor.service", "--lines", "20", "--no-pager", "--follow"), calls.last())
        assertTrue(output.contains("~/.usage-monitor/diagnostics/app.log"), output)
    }

    @Test
    fun `uninstall refuses without the unit and otherwise disables, deletes and reloads`() {
        val (refused, hint) = run(ServiceCommand.Uninstall)
        assertEquals(1, refused)
        assertTrue(hint.contains("O serviço não está instalado") && hint.contains("usage-monitor service install"), hint)
        assertTrue(calls.none { command -> command.contains("disable") })

        run(ServiceCommand.Install)
        calls.clear()
        val (code, output) = run(ServiceCommand.Uninstall)

        assertEquals(0, code, output)
        assertEquals("✓ serviço removido\n", output)
        assertEquals(
            listOf(
                listOf("systemctl", "--user", "disable", "--now", "usage-monitor.service"),
                listOf("systemctl", "--user", "daemon-reload")
            ),
            calls
        )
        assertTrue(!File(dir, ".config/systemd/user/usage-monitor.service").exists())
    }

    @Test
    fun `start stop and restart say what happened and repeat what systemd refused`() {
        run(ServiceCommand.Install)

        val (stopped, stopOutput) = run(ServiceCommand.Control("stop"))
        assertEquals(0, stopped)
        assertEquals("✓ serviço parado\n", stopOutput)
        assertEquals(listOf("systemctl", "--user", "stop", "usage-monitor.service"), calls.last())

        answer = { command -> if (command.contains("restart")) ProcessResult(5, "Unit usage-monitor.service is masked.\n") else null }
        val (failed, failOutput) = run(ServiceCommand.Control("restart"))
        assertEquals(1, failed)
        assertEquals("▲ O systemd recusou: Unit usage-monitor.service is masked.\n", failOutput)
    }

    @Test
    fun `install stops when the user systemd does not answer and shows linger`() {
        answer = { command -> if (command.contains("show-environment")) ProcessResult(1, "Failed to connect to bus: No medium found\nsecond line") else null }

        val (code, output) = run(ServiceCommand.Install)

        assertEquals(1, code)
        assertTrue(output.startsWith("▲ O systemd do usuário não respondeu (systemctl --user: Failed to connect to bus: No medium found)"), output)
        assertTrue(output.contains("sudo loginctl enable-linger dev"), output)
        assertTrue(!File(dir, ".config/systemd/user/usage-monitor.service").exists())
    }

    @Test
    fun `install keeps the written unit but reports when enabling fails`() {
        answer = { command -> if (command.contains("enable")) ProcessResult(1, "Access denied\n") else null }

        val (code, output) = run(ServiceCommand.Install)

        assertEquals(1, code)
        assertEquals(
            "✓ serviço criado em ~/.config/systemd/user/usage-monitor.service\n" +
                "▲ O systemd recusou: Access denied\n",
            output
        )
    }

    @Test
    fun `install with the app collecting says the service takes over later and skips linger when on`() {
        answer = { command -> if (command.first() == "loginctl") ProcessResult(0, "yes\n") else null }
        LocalCollectorRegistrationDataSource(File(dir, "collector.json")).write(CollectorRegistration(CollectorKind.APP, 77L, now - 1.hours, "41.12.0"))
        val lock = SingleInstanceGuard.tryAcquire(File(dir, "app.lock"))
        try {
            val (code, output) = run(ServiceCommand.Install)

            assertEquals(0, code, output)
            assertTrue(output.contains("▲ O app está aberto e coletando: o serviço assume quando ele fechar."), output)
            assertTrue(!output.contains("enable-linger"), output)
        } finally {
            lock?.close()
        }
    }

    @Test
    fun `status names the script install and leaves the memory out of a stopped unit`() {
        val tree = File(dir, ".local/share/usage-monitor/versions/41.12.0/Usage Monitor/bin/Usage Monitor").apply { parentFile.mkdirs(); writeText("") }
        val commands = commands(appPath = tree.path)
        run(ServiceCommand.Install, commands)
        answer = { command -> if (command.contains("show")) ProcessResult(0, "LoadState=loaded\nActiveState=inactive\nMemoryCurrent=190840832\n") else null }

        val (_, output) = run(ServiceCommand.Status, commands)

        assertTrue(output.contains("instalado pelo script (atualize rodando o instalador da versão nova)"), output)
        assertTrue(!output.contains("182 MB"), output)
    }

    @Test
    fun `logs without follow asks the journal for the requested lines only`() {
        val (code, _) = run(ServiceCommand.Logs(follow = false, lines = 5))

        assertEquals(0, code)
        assertEquals(listOf("journalctl", "--user", "--unit", "usage-monitor.service", "--lines", "5", "--no-pager"), calls.last())
    }

    @Test
    fun `the unit folder follows XDG_CONFIG_HOME and falls back to the home config`() {
        assertEquals(File("/xdg"), TerminalServiceCommands.configHome(mapOf("XDG_CONFIG_HOME" to "/xdg"), "/home/dev"))
        assertEquals(File("/home/dev", ".config"), TerminalServiceCommands.configHome(mapOf("XDG_CONFIG_HOME" to " "), "/home/dev"))
        assertEquals(File("/home/dev", ".config"), TerminalServiceCommands.configHome(emptyMap(), "/home/dev"))
    }
}

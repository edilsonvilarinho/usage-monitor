package com.usagemonitor.terminal

import com.usagemonitor.CURRENT_APP_VERSION
import com.usagemonitor.HeadlessService
import com.usagemonitor.data.AppDataDirectory
import com.usagemonitor.data.datasource.LocalAlertLogDataSource
import com.usagemonitor.data.datasource.LocalCollectorHealthDataSource
import com.usagemonitor.data.datasource.LocalCollectorRegistrationDataSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CollectorKind
import com.usagemonitor.presentation.terminal.TerminalAnsi
import com.usagemonitor.presentation.terminal.TerminalConfigView
import com.usagemonitor.presentation.terminal.TerminalInstallKind
import com.usagemonitor.presentation.terminal.TerminalLine
import com.usagemonitor.presentation.terminal.TerminalServiceLabels
import com.usagemonitor.presentation.terminal.TerminalServiceStatus
import com.usagemonitor.presentation.terminal.TerminalServiceStatusLabels
import com.usagemonitor.presentation.terminal.TerminalServiceView
import java.io.File
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Executa `usage-monitor service …` (#440, 5.1) e devolve o código de saída. O `run` é o
 * motor; o resto fala com o systemd do usuário (D3: só no Linux) e lê o que o coletor
 * grava (`collector.json`, `collector-health.json`, `alerts.jsonl`).
 */
internal class TerminalServiceCommands(
    private val systemd: SystemdUserService = SystemdUserService(ProcessRunner.SYSTEM, configHome()),
    private val runner: ProcessRunner = ProcessRunner.SYSTEM,
    /** O journal acompanhado (`--follow`) escreve direto no terminal, até o Ctrl+C. */
    private val streaming: (List<String>) -> Int = { command -> ProcessBuilder(command).inheritIO().start().waitFor() },
    private val reader: TerminalReader = TerminalReader(),
    private val registration: LocalCollectorRegistrationDataSource = LocalCollectorRegistrationDataSource(),
    private val health: LocalCollectorHealthDataSource = LocalCollectorHealthDataSource(),
    private val alertLog: LocalAlertLogDataSource = LocalAlertLogDataSource(),
    private val isLinux: Boolean = System.getProperty("os.name").orEmpty().lowercase().contains("linux"),
    private val defaultDataRoot: Boolean = AppDataDirectory.isDefault,
    private val appPath: String? = System.getProperty("jpackage.app-path"),
    private val home: File = File(System.getProperty("user.home").orEmpty()),
    private val user: String = System.getProperty("user.name").orEmpty(),
    private val dataFile: (String) -> File = AppDataDirectory::file,
    private val clock: Clock = Clock.System
) {

    fun run(command: ServiceCommand, languageOverride: AppLanguage?, console: TerminalConsole): Int {
        val language = reader.language(languageOverride)
        val labels = TerminalServiceLabels(language)
        val glyphs = console.profile.glyphs
        fun print(lines: List<TerminalLine>) = console.print(TerminalAnsi.render(lines, console.profile))
        if (command == ServiceCommand.Run) {
            return HeadlessService.run(languageOverride, labels, console::printText)
        }
        if (!isLinux) {
            print(TerminalConfigView.warning(labels.onlyLinux, "usage-monitor config set system.autostart true", glyphs))
            return EXIT_REFUSED
        }
        return when (command) {
            ServiceCommand.Run -> EXIT_REFUSED
            ServiceCommand.Install -> install(labels, console)
            ServiceCommand.Uninstall -> {
                if (!systemd.unitFile.isFile) {
                    print(TerminalConfigView.warning(labels.notInstalled, "usage-monitor service install", glyphs))
                    return EXIT_REFUSED
                }
                systemd.systemctl("disable", "--now", SystemdUserService.UNIT)
                systemd.unitFile.delete()
                systemd.systemctl("daemon-reload")
                print(listOf(TerminalConfigView.done(labels.removed, null, glyphs)))
                0
            }
            is ServiceCommand.Control -> {
                if (!systemd.unitFile.isFile) {
                    print(TerminalConfigView.warning(labels.notInstalled, "usage-monitor service install", glyphs))
                    return EXIT_REFUSED
                }
                val result = systemd.systemctl(command.action, SystemdUserService.UNIT)
                if (result.exitCode == 0) {
                    print(listOf(TerminalConfigView.done(labels.actionDone(command.action), null, glyphs)))
                    0
                } else {
                    print(TerminalConfigView.warning(labels.actionFailed(result.output.trim()), null, glyphs))
                    EXIT_REFUSED
                }
            }
            ServiceCommand.Status -> {
                val output = TerminalServiceView.status(statusInput(languageOverride), clock.now(), TerminalServiceStatusLabels(language), console.profile)
                print(output.lines)
                output.exitCode
            }
            is ServiceCommand.Logs -> {
                val journal = listOf("journalctl", "--user", "--unit", SystemdUserService.UNIT, "--lines", command.lines.toString(), "--no-pager") +
                    if (command.follow) listOf("--follow") else emptyList()
                print(listOf(TerminalConfigView.note(labels.appLogNote + " " + shown(dataFile("diagnostics/app.log").path))))
                streaming(journal)
            }
        }
    }

    private fun install(labels: TerminalServiceLabels, console: TerminalConsole): Int {
        val glyphs = console.profile.glyphs
        fun print(lines: List<TerminalLine>) = console.print(TerminalAnsi.render(lines, console.profile))
        if (!defaultDataRoot) {
            print(TerminalConfigView.warning(labels.customDataRoot, "usage-monitor --user-data-dir <pasta> service run", glyphs))
            return EXIT_REFUSED
        }
        val executable = SystemdUserService.executable(appPath, home)
        if (executable == null) {
            print(TerminalConfigView.warning(labels.notInstalledApp, null, glyphs))
            return EXIT_REFUSED
        }
        val unavailable = systemd.unavailableReason()
        if (unavailable != null) {
            print(TerminalConfigView.warning(labels.noSystemd(unavailable), "sudo loginctl enable-linger $user", glyphs))
            return EXIT_REFUSED
        }
        systemd.writeUnit(executable)
        systemd.systemctl("daemon-reload")
        val enabled = systemd.systemctl("enable", "--now", SystemdUserService.UNIT)
        val lines = mutableListOf(TerminalConfigView.done(labels.unitWritten(shown(systemd.unitFile.path)), null, glyphs))
        if (enabled.exitCode != 0) {
            print(lines + TerminalConfigView.warning(labels.actionFailed(enabled.output.trim()), null, glyphs))
            return EXIT_REFUSED
        }
        lines += TerminalConfigView.done(labels.enabledAndStarted, null, glyphs)
        if (reader.collector() == CollectorKind.APP) {
            lines += TerminalConfigView.warning(labels.appCollecting, null, glyphs)
        }
        if (systemd.lingers(user) == false) {
            lines += TerminalConfigView.warning(labels.lingerHint, "sudo loginctl enable-linger $user", glyphs)
        }
        print(lines)
        return 0
    }

    /** Junta o systemd, o registro de quem coleta, a saúde gravada, a leitura e o registro de avisos. */
    private fun statusInput(languageOverride: AppLanguage?): TerminalServiceStatus {
        val reading = reader.read(languageOverride)
        val collector = reading.collector
        val properties = if (systemd.unitFile.isFile) systemd.properties() else emptyMap()
        val registered = registration.read()?.takeIf { collector != null }
        val report = health.read()?.takeIf { collector != null }
        val history = dataFile("usage-history.db")
        val lastAlert = alertLog.read(startOfTodayBrt(reading.now).toEpochMilliseconds()).maxByOrNull { entry -> entry.atEpochMillis }
        val canonicalApp = appPath?.let { path -> runCatching { File(path).canonicalPath }.getOrDefault(path) }
        return TerminalServiceStatus(
            unitInstalled = systemd.unitFile.isFile && properties["LoadState"] != "not-found",
            unitActive = properties["ActiveState"] == "active",
            collector = collector,
            collectorPid = registered?.pid,
            collectorSince = registered?.startedAt,
            memoryBytes = properties["MemoryCurrent"]?.toLongOrNull()?.takeIf { properties["ActiveState"] == "active" },
            lastCollectedAt = reading.lastCollectedAt,
            nextCollectionAt = report?.nextCollectionAtEpochMillis?.let(Instant::fromEpochMilliseconds),
            zones = reading.accounts.flatMap { account -> account.quotas.map(account::zoneOf) },
            healthUpdatedAt = report?.updatedAtEpochMillis?.let(Instant::fromEpochMilliseconds),
            sources = report?.sources.orEmpty(),
            historyPath = history.takeIf { file -> file.isFile }?.let { file -> shown(file.path) },
            historyBytes = history.takeIf { file -> file.isFile }?.length(),
            telegram = report?.telegram,
            lastAlertAt = lastAlert?.let { entry -> Instant.fromEpochMilliseconds(entry.atEpochMillis) },
            team = report?.team,
            web = report?.web,
            version = registered?.version ?: CURRENT_APP_VERSION,
            installKind = when {
                canonicalApp == null -> null
                // Trecho, e não começo: no Silverblue `/opt` é link para `/var/opt`.
                canonicalApp.contains("/opt/usage-monitor/") -> TerminalInstallKind.PACKAGE
                canonicalApp.contains("/usage-monitor/versions/") -> TerminalInstallKind.SCRIPT
                else -> null
            }
        )
    }

    private fun shown(path: String): String {
        val homePath = home.path.trimEnd(File.separatorChar)
        return if (homePath.isNotEmpty() && path.startsWith(homePath + File.separator)) "~" + path.removePrefix(homePath) else path
    }

    companion object {
        private const val EXIT_REFUSED = 1

        /** `XDG_CONFIG_HOME`, ou `~/.config`: onde o systemd do usuário procura as units. */
        fun configHome(environment: Map<String, String> = System.getenv(), home: String = System.getProperty("user.home").orEmpty()): File =
            environment["XDG_CONFIG_HOME"]?.takeIf { value -> value.isNotBlank() }?.let(::File) ?: File(home, ".config")

        fun run(command: ServiceCommand, languageOverride: AppLanguage?, console: TerminalConsole): Int =
            TerminalServiceCommands().run(command, languageOverride, console)
    }
}

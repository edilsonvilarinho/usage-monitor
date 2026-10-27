package com.usagemonitor

import com.usagemonitor.update.resolveLinuxInstallRoot
import com.usagemonitor.update.resolveLinuxStableLauncherPath
import java.io.File

/**
 * O que o autostart lê e executa do sistema: plataforma, pastas, `reg.exe` e
 * `launchctl`, e o executável do app.
 *
 * Injetável porque a suíte só roda no Windows: sem isto, a entrada `.desktop`
 * do Linux e o plist do macOS só eram exercitados por inspeção de texto, e o
 * caminho que grava, apaga e migra nunca rodava em teste. O app usa
 * [system], lido de novo a cada chamada — a variável `XDG_CONFIG_HOME` e a
 * pasta pessoal são as do processo naquele instante.
 */
internal class AutoStartHost(
    val platform: AutoStartManager.Platform,
    val homeDirectory: File,
    val xdgConfigHome: String?,
    val runCommand: (List<String>) -> AutoStartCommandResult,
    val executablePath: () -> String?,
    val stableLauncherPath: () -> String?,
    val installRoot: () -> String?,
    val isExecutable: (String) -> Boolean = AutoStartManager::isExecutableFile
) {
    companion object {
        fun system(): AutoStartHost = AutoStartHost(
            platform = AutoStartManager.currentPlatform(),
            homeDirectory = File(System.getProperty("user.home").orEmpty()),
            xdgConfigHome = System.getenv("XDG_CONFIG_HOME"),
            runCommand = AutoStartManager::runCommand,
            executablePath = { AutoStartManager.resolveExecutablePath() },
            stableLauncherPath = { resolveLinuxStableLauncherPath() },
            installRoot = { resolveLinuxInstallRoot() }
        )
    }
}

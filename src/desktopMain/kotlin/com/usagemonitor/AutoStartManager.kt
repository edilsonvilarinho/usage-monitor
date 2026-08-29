package com.usagemonitor

import com.usagemonitor.update.normalizePosixPath
import com.usagemonitor.update.resolveLinuxInstallRoot
import com.usagemonitor.update.resolveLinuxStableLauncherPath
import java.io.File

object AutoStartManager {

    private const val WINDOWS_RUN_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    private const val WINDOWS_UNINSTALL_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\Usage Monitor"
    private const val WINDOWS_VALUE_NAME = "UsageMonitor"
    private const val WINDOWS_INSTALL_LOCATION_VALUE = "InstallLocation"
    private const val LINUX_AUTOSTART_FILE = "usage-monitor.desktop"
    private const val MACOS_LAUNCH_AGENT_LABEL = "com.usagemonitor.app"
    private const val MACOS_LAUNCH_AGENT_FILE = "$MACOS_LAUNCH_AGENT_LABEL.plist"
    private const val APP_DISPLAY_NAME = "Usage Monitor"

    fun isAutoStartSupported(): Boolean {
        return currentPlatform() != Platform.OTHER
    }

    fun isAutoStartEnabled(): Boolean {
        return when (currentPlatform()) {
            Platform.WINDOWS -> isWindowsAutoStartEnabled()
            Platform.LINUX -> linuxAutostartFile().exists()
            Platform.MACOS -> macAutostartFile().exists()
            Platform.OTHER -> false
        }
    }

    fun setAutoStart(enabled: Boolean): Boolean {
        return when (currentPlatform()) {
            Platform.WINDOWS -> setWindowsAutoStart(enabled)
            Platform.LINUX -> setLinuxAutoStart(enabled)
            Platform.MACOS -> setMacAutoStart(enabled)
            Platform.OTHER -> false
        }
    }

    fun syncFromPreference(enabled: Boolean): Boolean {
        return setAutoStart(enabled)
    }

    /**
     * Reescreve a entrada de inicializacao quando ela nao carrega
     * [StartupOrigin.AUTO_START_ARGUMENT].
     *
     * Sem isto, quem ja tinha o app configurado ficaria para tras: o argumento so
     * chegaria ao alternar o interruptor ou ao reinstalar, e ate la todo arranque
     * por autostart seria registrado como manual. A migracao acontece por baixo,
     * sem acao de quem usa.
     *
     * Devolve `true` quando a entrada esta atualizada ao fim -- inclusive quando
     * ja estava.
     */
    fun ensureAutoStartCommandCurrent(): Boolean {
        if (!isAutoStartEnabled()) {
            return false
        }

        val currentCommand = readAutoStartCommand()
        val needsMigration = autoStartCommandNeedsMigration(currentCommand) ||
            (currentPlatform() == Platform.LINUX && linuxEntryPointsIntoVersionedTree(currentCommand))
        if (!needsMigration) {
            return true
        }

        return setAutoStart(enabled = true)
    }

    /**
     * Se a entrada Linux aponta para dentro de `versions/<versao>` em vez de para
     * o launcher estavel.
     *
     * Uma entrada assim funciona **ate a primeira atualizacao**: a arvore que ela
     * nomeia e podada dois ciclos depois, e o autostart passa a apontar para um
     * caminho que nao existe -- sem erro na tela, porque nada no app le a entrada
     * depois de escreve-la.
     *
     * Entrada **ausente nao migra**, pelo mesmo motivo do argumento `--autostart`:
     * ligaria a inicializacao de quem a desligou.
     */
    private fun linuxEntryPointsIntoVersionedTree(currentCommand: String?): Boolean {
        return linuxAutoStartNeedsLauncherMigration(
            currentCommand = currentCommand,
            stableLauncherPath = resolveLinuxStableLauncherPath(),
            versionsPrefix = resolveLinuxInstallRoot()?.let { root -> "$root/versions/" }
        )
    }

    private fun readAutoStartCommand(): String? {
        return when (currentPlatform()) {
            Platform.WINDOWS -> readWindowsAutoStartCommand()
            Platform.LINUX -> readFileOrNull(linuxAutostartFile())
            Platform.MACOS -> readFileOrNull(macAutostartFile())
            Platform.OTHER -> null
        }
    }

    private fun readFileOrNull(file: File): String? {
        return runCatching { file.takeIf { it.isFile }?.readText() }.getOrNull()
    }

    private fun readWindowsAutoStartCommand(): String? {
        val result = runCommand(
            listOf("reg", "query", WINDOWS_RUN_KEY, "/v", WINDOWS_VALUE_NAME)
        )
        if (result.exitCode != 0) {
            return null
        }

        return result.output.lineSequence()
            .map(String::trim)
            .firstNotNullOfOrNull { line ->
                Regex("""^$WINDOWS_VALUE_NAME\s+REG_\w+\s+(.+)$""")
                    .find(line)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
            }
    }

    internal fun windowsAutoStartCommand(executablePath: String): String {
        return "\"$executablePath\" ${StartupOrigin.AUTO_START_ARGUMENT}"
    }

    /**
     * Entrada ausente nao migra: nao ha o que reescrever.
     *
     * A procura e por fronteira e nao por token separado por espaco, porque o
     * texto examinado tem tres formas diferentes -- valor da chave `Run`,
     * `Exec=` do `.desktop` e `<string>` do plist -- e so a fronteira funciona nas
     * tres sem recortar cada uma. Ela tambem evita casar com `--autostart-algo`.
     */
    internal fun autoStartCommandNeedsMigration(currentCommand: String?): Boolean {
        val command = currentCommand?.trim()?.takeIf { it.isNotBlank() } ?: return false
        val argument = Regex("""(?<![\w-])""" + Regex.escape(StartupOrigin.AUTO_START_ARGUMENT) + """(?![\w-])""")
        return !argument.containsMatchIn(command)
    }

    private fun currentPlatform(): Platform {
        val osName = System.getProperty("os.name").lowercase()
        return when {
            osName.contains("win") -> Platform.WINDOWS
            osName.contains("linux") -> Platform.LINUX
            osName.contains("mac") || osName.contains("darwin") -> Platform.MACOS
            else -> Platform.OTHER
        }
    }

    private fun isWindowsAutoStartEnabled(): Boolean {
        val result = runCommand(
            listOf("reg", "query", WINDOWS_RUN_KEY, "/v", WINDOWS_VALUE_NAME)
        )
        return result.exitCode == 0
    }

    private fun setWindowsAutoStart(enabled: Boolean): Boolean {
        if (enabled) {
            val executablePath = resolveExecutablePath() ?: return false
            val command = windowsAutoStartCommand(executablePath)
            val result = runCommand(
                listOf(
                    "reg",
                    "add",
                    WINDOWS_RUN_KEY,
                    "/v",
                    WINDOWS_VALUE_NAME,
                    "/t",
                    "REG_SZ",
                    "/d",
                    command,
                    "/f"
                )
            )
            return result.exitCode == 0
        }

        val result = runCommand(
            listOf("reg", "delete", WINDOWS_RUN_KEY, "/v", WINDOWS_VALUE_NAME, "/f")
        )
        return result.exitCode == 0 || !isWindowsAutoStartEnabled()
    }

    private fun setLinuxAutoStart(enabled: Boolean): Boolean {
        val autostartFile = linuxAutostartFile()

        if (!enabled) {
            return !autostartFile.exists() || autostartFile.delete()
        }

        val executablePath = linuxAutoStartExecutablePath(
            stableLauncherPath = resolveLinuxStableLauncherPath(),
            fallback = ::resolveExecutablePath
        ) ?: return false
        val parentDir = File(executablePath).parentFile?.absolutePath ?: return false
        val desktopEntry = buildLinuxDesktopEntry(executablePath, parentDir)

        return runCatching {
            autostartFile.parentFile.mkdirs()
            autostartFile.writeText(desktopEntry)
            true
        }.getOrDefault(false)
    }

    private fun setMacAutoStart(enabled: Boolean): Boolean {
        val launchAgentFile = macAutostartFile()

        if (!enabled) {
            if (!launchAgentFile.exists()) {
                return true
            }
            // O unload é best effort: o que define o estado é a presença do plist.
            runCommand(listOf("launchctl", "unload", "-w", launchAgentFile.absolutePath))
            return launchAgentFile.delete()
        }

        val executablePath = resolveExecutablePath() ?: return false

        val written = runCatching {
            launchAgentFile.parentFile.mkdirs()
            launchAgentFile.writeText(buildLaunchAgentPlist(executablePath))
            true
        }.getOrDefault(false)

        if (!written) {
            return false
        }

        runCommand(listOf("launchctl", "load", "-w", launchAgentFile.absolutePath))
        return true
    }

    /**
     * Para onde a entrada de inicializacao do Linux deve apontar.
     *
     * O launcher estavel vem primeiro **quando ele existe e e executavel**: ele
     * le `current` e sobrevive a troca de versao, enquanto um caminho dentro de
     * `versions/<versao>` deixa de existir na segunda atualizacao. A presenca do
     * arquivo e o teste certo, e nao o resolvedor de origem: numa instalacao
     * `.deb` o launcher simplesmente nao esta la, e o fallback e o que sempre foi.
     *
     * Funcao pura com os dois lados injetados porque a suite roda no Windows, e
     * ali nao existe nem `~/.local/bin` nem bit de execucao.
     */
    internal fun linuxAutoStartExecutablePath(
        stableLauncherPath: String?,
        isExecutable: (String) -> Boolean = { path -> File(path).let { it.isFile && it.canExecute() } },
        fallback: () -> String?
    ): String? {
        val launcher = stableLauncherPath?.trim()?.takeIf { it.isNotBlank() }
        if (launcher != null && isExecutable(launcher)) {
            return launcher
        }
        return fallback()
    }

    /**
     * Se a entrada existente aponta para dentro da arvore versionada.
     *
     * Tres respostas negativas, e todas com motivo: entrada **ausente** nao migra
     * (ligaria a inicializacao de quem a desligou); sem launcher estavel nao ha
     * para onde migrar; e uma entrada que **ja** nomeia o launcher estavel esta
     * atualizada -- reescreve-la seria trabalho sem mudanca.
     */
    internal fun linuxAutoStartNeedsLauncherMigration(
        currentCommand: String?,
        stableLauncherPath: String?,
        versionsPrefix: String?
    ): Boolean {
        val command = currentCommand?.trim()?.takeIf { it.isNotBlank() } ?: return false
        val launcher = stableLauncherPath?.trim()?.takeIf { it.isNotBlank() } ?: return false
        val prefix = versionsPrefix?.trim()?.takeIf { it.isNotBlank() } ?: return false

        if (command.contains(launcher)) {
            return false
        }
        return command.contains(normalizePosixPath(prefix) + "/")
    }

    /**
     * Estado da entrada de autostart do Linux, em **dois booleanos**.
     *
     * Nunca o caminho: ele carrega o nome do usuario, e este e o mesmo arquivo
     * que o relatorio de bug empacota para uma issue publica. Booleano responde
     * a mesma pergunta sem carregar identidade.
     */
    internal data class LinuxAutostartEntryState(
        val present: Boolean,
        val valid: Boolean
    )

    /**
     * Le a entrada de autostart e diz se ela **funcionaria**.
     *
     * `present` e o que [isAutoStartEnabled] ja respondia -- o arquivo existe --,
     * e foi por essa ser a unica pergunta que o defeito do `Path=` entre aspas
     * passou despercebido: o interruptor ficava ligado com uma entrada que o
     * spawn recusava no `chdir`. `valid` e a pergunta que faltava.
     *
     * Leitor e teste de execucao **injetados** porque a suite roda no Windows:
     * ali nao existe `~/.config/autostart` nem bit de execucao.
     */
    internal fun inspectLinuxAutostartEntry(
        readEntry: () -> String? = { readFileOrNull(linuxAutostartFile()) },
        isExecutable: (String) -> Boolean = { path -> File(path).let { it.isFile && it.canExecute() } }
    ): LinuxAutostartEntryState {
        val entry = runCatching { readEntry() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: return LinuxAutostartEntryState(present = false, valid = false)

        val program = desktopEntryValue(entry, "Exec")?.let(::firstDesktopExecArgument)
        val workingDirectory = desktopEntryValue(entry, "Path")

        // As duas condicoes cobrem os dois modos de falha silenciosa ja vistos:
        // o executavel podado junto com a arvore versionada, e o diretorio de
        // trabalho entre aspas.
        val valid = program != null &&
            runCatching { isExecutable(program) }.getOrDefault(false) &&
            workingDirectory != null &&
            !workingDirectory.startsWith("\"")

        return LinuxAutostartEntryState(present = true, valid = valid)
    }

    /** Valor cru de uma chave do grupo `[Desktop Entry]`, sem interpretar nada. */
    private fun desktopEntryValue(entry: String, key: String): String? {
        return entry.lineSequence()
            .map(String::trim)
            .firstNotNullOfOrNull { line ->
                line.removePrefix("$key=")
                    .takeIf { it != line }
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
            }
    }

    /**
     * Primeiro argumento do `Exec=`, que e o programa.
     *
     * Aqui as aspas **sao** da especificacao e precisam ser desfeitas: no `Exec`
     * elas delimitam o argumento e o leitor as remove antes do spawn. E o oposto
     * exato do `Path=`, lido verbatim -- e a razao de as duas chaves nao se
     * escreverem do mesmo jeito.
     */
    private fun firstDesktopExecArgument(exec: String): String? {
        if (!exec.startsWith("\"")) {
            return exec.substringBefore(' ').takeIf { it.isNotBlank() }
        }

        val program = StringBuilder()
        var index = 1
        while (index < exec.length) {
            val character = exec[index]
            when {
                character == '\\' && index + 1 < exec.length -> {
                    program.append(exec[index + 1])
                    index += 2
                }

                character == '"' -> return program.toString().takeIf { it.isNotBlank() }

                else -> {
                    program.append(character)
                    index += 1
                }
            }
        }

        // Aspas sem fechamento: entrada corrompida, e nao um caminho.
        return null
    }

    /**
     * As duas chaves nao se escrevem do mesmo jeito, e a simetria custou o
     * arranque inteiro no Linux.
     *
     * A Desktop Entry Specification define regras de aspas **apenas para a chave
     * `Exec`** (secao "The Exec key"): ali as aspas separam os argumentos e sao
     * removidas pelo leitor. `Path` e do tipo `string` e e lida **verbatim** --
     * a GLib guarda o valor em `info->path` e o passa como `working_directory`
     * do `g_spawn`, e o KIO o passa para `QProcess::setWorkingDirectory`. Um
     * diretorio cujo nome literal comeca com aspas nao existe, o spawn falha no
     * `chdir` e nada aparece na tela: `isAutoStartEnabled()` so testa se o
     * arquivo existe, entao o interruptor continua ligado (issue #120).
     */
    internal fun buildLinuxDesktopEntry(executablePath: String, parentDir: String): String {
        return buildString {
            appendLine("[Desktop Entry]")
            appendLine("Type=Application")
            appendLine("Version=1.0")
            appendLine("Name=$APP_DISPLAY_NAME")
            appendLine("Exec=${quoteDesktopValue(executablePath)} ${StartupOrigin.AUTO_START_ARGUMENT}")
            appendLine("Path=$parentDir")
            appendLine("Terminal=false")
            appendLine("X-GNOME-Autostart-enabled=true")
        }
    }

    internal fun buildLaunchAgentPlist(executablePath: String): String {
        return buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine(
                """<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" """ +
                    """"http://www.apple.com/DTDs/PropertyList-1.0.dtd">"""
            )
            appendLine("""<plist version="1.0">""")
            appendLine("<dict>")
            appendLine("    <key>Label</key>")
            appendLine("    <string>$MACOS_LAUNCH_AGENT_LABEL</string>")
            appendLine("    <key>ProgramArguments</key>")
            appendLine("    <array>")
            appendLine("        <string>${escapeXml(executablePath)}</string>")
            appendLine("        <string>${StartupOrigin.AUTO_START_ARGUMENT}</string>")
            appendLine("    </array>")
            appendLine("    <key>RunAtLoad</key>")
            appendLine("    <true/>")
            appendLine("</dict>")
            appendLine("</plist>")
        }
    }

    private fun escapeXml(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }

    private fun macAutostartFile(): File {
        val homeDir = System.getProperty("user.home")
        return File(homeDir, "Library/LaunchAgents/$MACOS_LAUNCH_AGENT_FILE")
    }

    private fun linuxAutostartFile(): File {
        val configHome = System.getenv("XDG_CONFIG_HOME")
            ?.takeIf { it.isNotBlank() }
            ?: File(System.getProperty("user.home"), ".config").absolutePath
        return File(configHome, "autostart/$LINUX_AUTOSTART_FILE")
    }

    internal fun resolveExecutablePath(
        environment: RuntimeEnvironment = RuntimeEnvironment.current()
    ): String? {
        val candidates = buildExecutableCandidates(environment)
        return candidates.firstOrNull { candidate ->
            val file = File(candidate)
            file.exists() && file.isFile && !isJavaLauncher(file.name)
        }?.let { File(it).absolutePath }
    }

    internal fun buildExecutableCandidates(environment: RuntimeEnvironment): List<String> {
        val launcherNames = launcherNamesFor(environment.platform)
        val candidates = linkedSetOf<String>()

        addDirectCandidate(candidates, environment.jpackageAppPath)
        addDirectCandidate(candidates, environment.processCommand)

        environment.appDirectories.forEach { directory ->
            launcherNames.forEach { launcherName ->
                addDirectCandidate(candidates, File(directory, launcherName).path)
            }
        }

        if (environment.platform == Platform.LINUX) {
            listOf(
                "/usr/bin/usage-monitor",
                "/usr/local/bin/usage-monitor",
                "/opt/usage-monitor/bin/usage-monitor",
                "/opt/Usage Monitor/bin/Usage Monitor",
                "/opt/Usage Monitor/Usage Monitor"
            ).forEach { path ->
                addDirectCandidate(candidates, path)
            }
        }

        if (environment.platform == Platform.MACOS) {
            val homeDir = System.getProperty("user.home")
            listOfNotNull(
                "/Applications/$APP_DISPLAY_NAME.app/Contents/MacOS/$APP_DISPLAY_NAME",
                homeDir?.let {
                    File(it, "Applications/$APP_DISPLAY_NAME.app/Contents/MacOS/$APP_DISPLAY_NAME").path
                }
            ).forEach { path ->
                addDirectCandidate(candidates, path)
            }
        }

        return candidates.toList()
    }

    private fun launcherNamesFor(platform: Platform): List<String> {
        return when (platform) {
            Platform.WINDOWS -> listOf(
                "Usage Monitor.exe",
                "UsageMonitor.exe"
            )

            Platform.LINUX -> listOf(
                "usage-monitor",
                "Usage Monitor",
                "bin/usage-monitor",
                "bin/Usage Monitor"
            )

            Platform.MACOS -> listOf(
                "$APP_DISPLAY_NAME.app/Contents/MacOS/$APP_DISPLAY_NAME",
                "Contents/MacOS/$APP_DISPLAY_NAME",
                APP_DISPLAY_NAME
            )

            Platform.OTHER -> emptyList()
        }
    }

    private fun addDirectCandidate(target: MutableSet<String>, path: String?) {
        val normalizedPath = path
            ?.trim()
            ?.trim('"')
            ?.takeIf { it.isNotBlank() }
            ?: return
        target += normalizedPath
    }

    /**
     * Exposta para o resolvedor de origem da instalação
     * ([com.usagemonitor.update.WindowsInstallOriginResolver]) reusar esta
     * leitura em vez de abrir um segundo caminho para a mesma chave HKCU — duas
     * leituras do mesmo valor divergiriam no dia em que uma delas mudasse.
     */
    internal fun readWindowsInstallLocationOrNull(): String? = readWindowsInstallLocation()

    private fun readWindowsInstallLocation(): String? {
        if (currentPlatform() != Platform.WINDOWS) {
            return null
        }

        val result = runCommand(
            listOf(
                "reg",
                "query",
                WINDOWS_UNINSTALL_KEY,
                "/v",
                WINDOWS_INSTALL_LOCATION_VALUE
            )
        )
        if (result.exitCode != 0) {
            return null
        }

        return result.output.lineSequence()
            .map(String::trim)
            .firstNotNullOfOrNull { line ->
                Regex("""^InstallLocation\s+REG_\w+\s+(.+)$""")
                    .find(line)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
            }
    }

    private fun isJavaLauncher(fileName: String): Boolean {
        val normalized = fileName.lowercase()
        return normalized == "java" ||
            normalized == "java.exe" ||
            normalized == "javaw.exe"
    }

    private fun quoteDesktopValue(value: String): String {
        return "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
    }

    private fun runCommand(command: List<String>): CommandResult {
        return runCatching {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            CommandResult(
                exitCode = process.waitFor(),
                output = output
            )
        }.getOrElse {
            CommandResult(exitCode = -1, output = it.message.orEmpty())
        }
    }

    internal enum class Platform {
        WINDOWS,
        LINUX,
        MACOS,
        OTHER
    }

    internal data class RuntimeEnvironment(
        val platform: Platform,
        val processCommand: String?,
        val jpackageAppPath: String?,
        val appDirectories: List<String>
    ) {
        companion object {
            fun current(): RuntimeEnvironment {
                val platform = currentPlatform()
                val userDir = System.getProperty("user.dir")
                val skikoLibraryPath = System.getProperty("skiko.library.path")
                val composeResourcesDir = System.getProperty("compose.application.resources.dir")
                val windowsInstallLocation = readWindowsInstallLocation()

                val appDirectories = linkedSetOf<String>()
                addDirectoryCandidate(appDirectories, skikoLibraryPath)
                addDirectoryCandidate(appDirectories, composeResourcesDir?.let { File(it).parent })
                addDirectoryCandidate(appDirectories, windowsInstallLocation)
                addDirectoryCandidate(appDirectories, ProcessHandle.current().info().command().orElse(null)?.let { File(it).parent })
                addDirectoryCandidate(appDirectories, userDir)

                return RuntimeEnvironment(
                    platform = platform,
                    processCommand = ProcessHandle.current().info().command().orElse(null),
                    jpackageAppPath = System.getProperty("jpackage.app-path"),
                    appDirectories = appDirectories.toList()
                )
            }

            private fun addDirectoryCandidate(target: MutableSet<String>, path: String?) {
                val normalizedPath = path
                    ?.trim()
                    ?.trim('"')
                    ?.takeIf { it.isNotBlank() }
                    ?: return
                target += normalizedPath
            }
        }
    }

    private data class CommandResult(
        val exitCode: Int,
        val output: String
    )
}

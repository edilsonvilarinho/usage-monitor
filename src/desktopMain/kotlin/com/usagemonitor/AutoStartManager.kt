package com.usagemonitor

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
        if (!autoStartCommandNeedsMigration(currentCommand)) {
            return true
        }

        return setAutoStart(enabled = true)
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

        val executablePath = resolveExecutablePath() ?: return false
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

    internal fun buildLinuxDesktopEntry(executablePath: String, parentDir: String): String {
        return buildString {
            appendLine("[Desktop Entry]")
            appendLine("Type=Application")
            appendLine("Version=1.0")
            appendLine("Name=$APP_DISPLAY_NAME")
            appendLine("Exec=${quoteDesktopValue(executablePath)} ${StartupOrigin.AUTO_START_ARGUMENT}")
            appendLine("Path=${quoteDesktopValue(parentDir)}")
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

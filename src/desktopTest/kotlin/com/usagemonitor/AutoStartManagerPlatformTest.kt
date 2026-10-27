package com.usagemonitor

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * O caminho que grava, apaga e migra a entrada de inicialização nos três
 * sistemas, contra uma pasta temporária e um executor que só registra os
 * comandos. A suíte roda no Windows; o `AutoStartHost` é o que deixa o
 * `.desktop` e o plist serem gravados aqui de verdade.
 */
class AutoStartManagerPlatformTest {

    private val root: File = Files.createTempDirectory("autostart-host-").toFile()
    private val commands = mutableListOf<List<String>>()

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    private fun host(
        platform: AutoStartManager.Platform,
        xdgConfigHome: String? = null,
        executable: String? = "/opt/usage-monitor/bin/usage-monitor",
        stableLauncher: String? = null,
        installRoot: String? = null,
        executables: Set<String> = emptySet(),
        respond: (List<String>) -> AutoStartCommandResult = { AutoStartCommandResult(0, "") }
    ) = AutoStartHost(
        platform = platform,
        homeDirectory = root,
        xdgConfigHome = xdgConfigHome,
        runCommand = { command ->
            commands += command
            respond(command)
        },
        executablePath = { executable },
        stableLauncherPath = { stableLauncher },
        installRoot = { installRoot },
        isExecutable = { path -> path in executables }
    )

    private val linuxEntry get() = File(root, ".config/autostart/usage-monitor.desktop")
    private val macPlist get() = File(root, "Library/LaunchAgents/com.usagemonitor.app.plist")

    @Test
    fun `the platform comes from the os name`() {
        assertEquals(AutoStartManager.Platform.WINDOWS, AutoStartManager.currentPlatform("Windows 11"))
        assertEquals(AutoStartManager.Platform.LINUX, AutoStartManager.currentPlatform("Linux"))
        assertEquals(AutoStartManager.Platform.MACOS, AutoStartManager.currentPlatform("Mac OS X"))
        assertEquals(AutoStartManager.Platform.MACOS, AutoStartManager.currentPlatform("Darwin"))
        assertEquals(AutoStartManager.Platform.OTHER, AutoStartManager.currentPlatform("FreeBSD"))
    }

    @Test
    fun `an unknown platform has no auto start and refuses to write one`() {
        val other = host(AutoStartManager.Platform.OTHER)

        assertFalse(AutoStartManager.isAutoStartSupported(other))
        assertFalse(AutoStartManager.isAutoStartEnabled(other))
        assertEquals(AutoStartResult.Failure("plataforma não compatível"), AutoStartManager.setAutoStart(true, other))
        assertNull(AutoStartManager.ensureAutoStartCommandCurrent(other))
        assertTrue(commands.isEmpty())
    }

    @Test
    fun `linux writes the desktop entry under the home config and removes it`() {
        val linux = host(AutoStartManager.Platform.LINUX)
        assertTrue(AutoStartManager.isAutoStartSupported(linux))
        assertFalse(AutoStartManager.isAutoStartEnabled(linux))

        assertEquals(AutoStartResult.Success, AutoStartManager.setAutoStart(true, linux))

        assertTrue(AutoStartManager.isAutoStartEnabled(linux))
        val entry = linuxEntry.readText()
        assertTrue(entry.contains("Exec=\"/opt/usage-monitor/bin/usage-monitor\" ${StartupOrigin.AUTO_START_ARGUMENT}"), entry)
        assertEquals(File("/opt/usage-monitor/bin/usage-monitor").parentFile.absolutePath, desktopEntryValue(entry, "Path"))

        assertEquals(AutoStartResult.Success, AutoStartManager.setAutoStart(false, linux))
        assertFalse(linuxEntry.exists())
        // Desligar o que já está desligado não é falha.
        assertEquals(AutoStartResult.Success, AutoStartManager.setAutoStart(false, linux))
        assertTrue(commands.isEmpty(), "o Linux não chama processo nenhum")
    }

    @Test
    fun `linux honours XDG_CONFIG_HOME when it is set`() {
        val config = File(root, "xdg")
        val linux = host(AutoStartManager.Platform.LINUX, xdgConfigHome = config.absolutePath)

        AutoStartManager.setAutoStart(true, linux)

        assertTrue(File(config, "autostart/usage-monitor.desktop").isFile)
        assertFalse(linuxEntry.exists())
    }

    @Test
    fun `linux points the entry at the stable launcher when it is executable`() {
        val launcher = "/home/me/.local/bin/usage-monitor"
        val linux = host(AutoStartManager.Platform.LINUX, stableLauncher = launcher, executables = setOf(launcher))

        AutoStartManager.setAutoStart(true, linux)

        assertEquals(launcher, firstDesktopExecArgument(desktopEntryValue(linuxEntry.readText(), "Exec")!!))
    }

    @Test
    fun `linux without an executable reports it and writes nothing`() {
        val linux = host(AutoStartManager.Platform.LINUX, executable = null)

        assertEquals(
            AutoStartResult.Failure("executável do aplicativo não localizado"),
            AutoStartManager.setAutoStart(true, linux)
        )
        assertFalse(linuxEntry.exists())
    }

    @Test
    fun `linux reports a write failure without throwing`() {
        // A pasta `autostart` é um arquivo: a gravação do `.desktop` falha.
        File(root, ".config").mkdirs()
        File(root, ".config/autostart").writeText("not a directory")
        val linux = host(AutoStartManager.Platform.LINUX)

        val result = AutoStartManager.setAutoStart(true, linux)

        assertIs<AutoStartResult.Failure>(result)
        assertTrue(result.reason.startsWith("gravação do autostart"), result.reason)
    }

    @Test
    fun `an off linux entry is never migrated`() {
        assertNull(AutoStartManager.ensureAutoStartCommandCurrent(host(AutoStartManager.Platform.LINUX)))
        assertFalse(linuxEntry.exists())
    }

    @Test
    fun `a current linux entry is left alone`() {
        val program = "/opt/usage-monitor/bin/usage-monitor"
        val linux = host(AutoStartManager.Platform.LINUX, executables = setOf(program))
        AutoStartManager.setAutoStart(true, linux)
        val written = linuxEntry.readText()
        linuxEntry.setLastModified(STAMP)

        assertEquals(AutoStartResult.Success, AutoStartManager.ensureAutoStartCommandCurrent(linux))

        assertEquals(written, linuxEntry.readText())
        assertEquals(STAMP, linuxEntry.lastModified())
    }

    @Test
    fun `a linux entry without the origin argument is rewritten`() {
        val program = "/opt/usage-monitor/bin/usage-monitor"
        linuxEntry.parentFile.mkdirs()
        linuxEntry.writeText("[Desktop Entry]\nExec=\"$program\"\nPath=/opt/usage-monitor/bin\n")
        val linux = host(AutoStartManager.Platform.LINUX, executables = setOf(program))

        assertEquals(AutoStartResult.Success, AutoStartManager.ensureAutoStartCommandCurrent(linux))

        assertTrue(linuxEntry.readText().contains(StartupOrigin.AUTO_START_ARGUMENT))
    }

    @Test
    fun `a linux entry inside the versioned tree moves to the stable launcher`() {
        val installRoot = "/home/me/.local/share/usage-monitor"
        val versioned = "$installRoot/versions/41.0.0/bin/usage-monitor"
        val launcher = "/home/me/.local/bin/usage-monitor"
        linuxEntry.parentFile.mkdirs()
        linuxEntry.writeText(
            "[Desktop Entry]\nExec=\"$versioned\" ${StartupOrigin.AUTO_START_ARGUMENT}\nPath=$installRoot/versions/41.0.0/bin\n"
        )
        val linux = host(
            AutoStartManager.Platform.LINUX,
            stableLauncher = launcher,
            installRoot = installRoot,
            executables = setOf(versioned, launcher)
        )

        assertEquals(AutoStartResult.Success, AutoStartManager.ensureAutoStartCommandCurrent(linux))

        assertEquals(launcher, firstDesktopExecArgument(desktopEntryValue(linuxEntry.readText(), "Exec")!!))
    }

    @Test
    fun `a broken linux entry is repaired`() {
        // O executável que a entrada nomeia não existe mais: a entrada está presente e inválida.
        linuxEntry.parentFile.mkdirs()
        linuxEntry.writeText(
            "[Desktop Entry]\nExec=\"/gone/usage-monitor\" ${StartupOrigin.AUTO_START_ARGUMENT}\nPath=/gone\n"
        )
        val linux = host(AutoStartManager.Platform.LINUX)

        assertEquals(AutoStartResult.Success, AutoStartManager.ensureAutoStartCommandCurrent(linux))

        assertEquals(
            "/opt/usage-monitor/bin/usage-monitor",
            firstDesktopExecArgument(desktopEntryValue(linuxEntry.readText(), "Exec")!!)
        )
    }

    @Test
    fun `macOS writes the launch agent, loads it and unloads it before removing`() {
        val mac = host(AutoStartManager.Platform.MACOS, executable = "/Applications/Usage Monitor.app/Contents/MacOS/Usage Monitor")
        assertFalse(AutoStartManager.isAutoStartEnabled(mac))

        assertEquals(AutoStartResult.Success, AutoStartManager.setAutoStart(true, mac))

        assertTrue(AutoStartManager.isAutoStartEnabled(mac))
        assertTrue(macPlist.readText().contains("<string>/Applications/Usage Monitor.app/Contents/MacOS/Usage Monitor</string>"))
        assertEquals(listOf("launchctl", "load", "-w", macPlist.absolutePath), commands.single())

        assertEquals(AutoStartResult.Success, AutoStartManager.setAutoStart(false, mac))
        assertFalse(macPlist.exists())
        assertEquals(listOf("launchctl", "unload", "-w", macPlist.absolutePath), commands.last())
    }

    @Test
    fun `macOS off without a launch agent runs nothing`() {
        val mac = host(AutoStartManager.Platform.MACOS)

        assertEquals(AutoStartResult.Success, AutoStartManager.setAutoStart(false, mac))
        assertTrue(commands.isEmpty())
    }

    @Test
    fun `macOS without an executable reports it and writes nothing`() {
        val mac = host(AutoStartManager.Platform.MACOS, executable = null)

        assertEquals(
            AutoStartResult.Failure("executável do aplicativo não localizado"),
            AutoStartManager.setAutoStart(true, mac)
        )
        assertFalse(macPlist.exists())
        assertTrue(commands.isEmpty())
    }

    @Test
    fun `macOS reports a write failure and does not load anything`() {
        File(root, "Library").mkdirs()
        File(root, "Library/LaunchAgents").writeText("not a directory")
        val mac = host(AutoStartManager.Platform.MACOS)

        val result = AutoStartManager.setAutoStart(true, mac)

        assertIs<AutoStartResult.Failure>(result)
        assertTrue(result.reason.startsWith("gravação do autostart"), result.reason)
        assertTrue(commands.isEmpty())
    }

    @Test
    fun `a macOS launch agent without the origin argument is rewritten`() {
        macPlist.parentFile.mkdirs()
        macPlist.writeText("<plist><string>/Applications/Usage Monitor.app/Contents/MacOS/Usage Monitor</string></plist>")
        val mac = host(AutoStartManager.Platform.MACOS)

        assertEquals(AutoStartResult.Success, AutoStartManager.ensureAutoStartCommandCurrent(mac))

        assertTrue(macPlist.readText().contains("<string>${StartupOrigin.AUTO_START_ARGUMENT}</string>"))
    }

    @Test
    fun `windows reads the Run value through reg query`() {
        val windows = host(AutoStartManager.Platform.WINDOWS) { AutoStartCommandResult(0, "") }

        assertTrue(AutoStartManager.isAutoStartEnabled(windows))
        assertEquals(
            listOf("reg", "query", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run", "/v", "UsageMonitor"),
            commands.single()
        )
        val missing = host(AutoStartManager.Platform.WINDOWS) { AutoStartCommandResult(1, "ERROR") }
        assertFalse(AutoStartManager.isAutoStartEnabled(missing))
    }

    @Test
    fun `a windows entry that already carries the argument is not rewritten`() {
        val output = "\r\nHKEY_CURRENT_USER\\...\\Run\r\n    UsageMonitor    REG_SZ    \"C:\\Apps\\Usage Monitor.exe\" " +
            "${StartupOrigin.AUTO_START_ARGUMENT}\r\n"
        val windows = host(AutoStartManager.Platform.WINDOWS) { AutoStartCommandResult(0, output) }

        assertEquals(AutoStartResult.Success, AutoStartManager.ensureAutoStartCommandCurrent(windows))

        assertTrue(commands.all { command -> command[1] == "query" }, commands.toString())
    }

    @Test
    fun `a windows entry without the argument is imported again`() {
        val output = "    UsageMonitor    REG_SZ    \"C:\\Apps\\Usage Monitor.exe\"\r\n"
        val windows = host(AutoStartManager.Platform.WINDOWS, executable = "C:\\Apps\\Usage Monitor.exe") {
            AutoStartCommandResult(0, output)
        }

        assertEquals(AutoStartResult.Success, AutoStartManager.ensureAutoStartCommandCurrent(windows))

        assertEquals(listOf("reg", "import"), commands.last().take(2))
    }

    @Test
    fun `windows off deletes the Run value and tolerates a value already gone`() {
        val windows = host(AutoStartManager.Platform.WINDOWS) { command ->
            // `delete` falha porque o valor não existe; o `query` seguinte confirma que está desligado.
            AutoStartCommandResult(1, "ERROR: ${command[1]}")
        }

        assertEquals(AutoStartResult.Success, AutoStartManager.setAutoStart(false, windows))
        assertEquals(listOf("delete", "query"), commands.map { command -> command[1] })
    }

    @Test
    fun `windows off reports the delete failure when the value is still there`() {
        val windows = host(AutoStartManager.Platform.WINDOWS) { command ->
            if (command[1] == "delete") AutoStartCommandResult(5, "ERROR: Access is denied.") else AutoStartCommandResult(0, "")
        }

        val result = AutoStartManager.setAutoStart(false, windows)

        assertEquals(AutoStartResult.Failure("reg.exe delete retornou código 5: ERROR: Access is denied.", exitCode = 5), result)
    }

    @Test
    fun `the registry value is read from the reg query line and nothing else`() {
        val output = """
            HKEY_CURRENT_USER\Software\Microsoft\Windows\CurrentVersion\Uninstall\Usage Monitor
                InstallLocation    REG_SZ    C:\Users\me\AppData\Local\Usage Monitor
        """.trimIndent()

        assertEquals(
            "C:\\Users\\me\\AppData\\Local\\Usage Monitor",
            AutoStartManager.parseRegistryQueryValue(output, "InstallLocation")
        )
        assertNull(AutoStartManager.parseRegistryQueryValue(output, "UsageMonitor"))
        assertNull(AutoStartManager.parseRegistryQueryValue("    InstallLocation    REG_SZ    ", "InstallLocation"))
    }

    @Test
    fun `a command that cannot start becomes exit code minus one with the reason`() {
        val result = AutoStartManager.runCommand(listOf("usage-monitor-command-that-does-not-exist-${System.nanoTime()}"))

        assertEquals(-1, result.exitCode)
        assertTrue(result.output.startsWith("falha ao iniciar usage-monitor-command-that-does-not-exist-"), result.output)
    }

    @Test
    fun `a command that runs returns its exit code and output`() {
        // O próprio `java` desta JVM: não depende do PATH da máquina de CI.
        val java = ProcessHandle.current().info().command().orElseThrow()
        val result = AutoStartManager.runCommand(listOf(java, "-version"))

        assertEquals(0, result.exitCode)
        assertTrue(result.output.contains("version"), result.output)
    }

    @Test
    fun `only an executable regular file counts as executable`() {
        val directory = File(root, "dir").apply { mkdirs() }

        assertFalse(AutoStartManager.isExecutableFile(directory.absolutePath))
        assertFalse(AutoStartManager.isExecutableFile(File(root, "missing").absolutePath))
    }

    private companion object {
        const val STAMP = 1_000_000_000_000L
    }
}

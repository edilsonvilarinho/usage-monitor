package com.usagemonitor

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutoStartManagerTest {

    @Test
    fun `resolve executable path prefers direct jpackage launcher`() {
        val tempDir = createTempDir()
        try {
            val launcher = createFile(tempDir, "Usage Monitor.exe")

            val resolved = AutoStartManager.resolveExecutablePath(
                AutoStartManager.RuntimeEnvironment(
                    platform = AutoStartManager.Platform.WINDOWS,
                    processCommand = createFile(tempDir, "javaw.exe").absolutePath,
                    jpackageAppPath = launcher.absolutePath,
                    appDirectories = emptyList()
                )
            )

            assertEquals(launcher.absolutePath, resolved)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `resolve executable path falls back to launcher inside app directory`() {
        val tempDir = createTempDir()
        try {
            val launcher = createFile(tempDir, "bin/usage-monitor")

            val resolved = AutoStartManager.resolveExecutablePath(
                AutoStartManager.RuntimeEnvironment(
                    platform = AutoStartManager.Platform.LINUX,
                    processCommand = createFile(tempDir, "java").absolutePath,
                    jpackageAppPath = null,
                    appDirectories = listOf(tempDir.absolutePath)
                )
            )

            assertEquals(launcher.absolutePath, resolved)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `resolve executable path uses windows install directory hint when process is java launcher`() {
        val tempDir = createTempDir()
        try {
            val installDir = File(tempDir, "installed-app").apply { mkdirs() }
            val launcher = createFile(installDir, "Usage Monitor.exe")

            val resolved = AutoStartManager.resolveExecutablePath(
                AutoStartManager.RuntimeEnvironment(
                    platform = AutoStartManager.Platform.WINDOWS,
                    processCommand = createFile(tempDir, "java.exe").absolutePath,
                    jpackageAppPath = null,
                    appDirectories = listOf(installDir.absolutePath)
                )
            )

            assertEquals(launcher.absolutePath, resolved)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `resolve executable path finds macOS bundle launcher inside app directory`() {
        val tempDir = createTempDir()
        try {
            val launcher = createFile(tempDir, "Usage Monitor.app/Contents/MacOS/Usage Monitor")

            val resolved = AutoStartManager.resolveExecutablePath(
                AutoStartManager.RuntimeEnvironment(
                    platform = AutoStartManager.Platform.MACOS,
                    processCommand = createFile(tempDir, "java").absolutePath,
                    jpackageAppPath = null,
                    appDirectories = listOf(tempDir.absolutePath)
                )
            )

            assertEquals(launcher.absolutePath, resolved)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `launch agent plist carries label program and run at load`() {
        val plist = AutoStartManager.buildLaunchAgentPlist("/Applications/Usage Monitor.app/Contents/MacOS/Usage Monitor")

        assertTrue(plist.contains("<string>com.usagemonitor.app</string>"))
        assertTrue(plist.contains("<string>/Applications/Usage Monitor.app/Contents/MacOS/Usage Monitor</string>"))
        assertTrue(plist.contains("<key>RunAtLoad</key>"))
        assertTrue(plist.contains("<true/>"))
    }

    @Test
    fun `launch agent plist escapes xml sensitive characters in the path`() {
        val plist = AutoStartManager.buildLaunchAgentPlist("/Users/dev & co/Usage Monitor")

        assertTrue(plist.contains("<string>/Users/dev &amp; co/Usage Monitor</string>"))
    }

    // O processo lancado pela chave Run e o lancado pelo atalho tem o mesmo pai
    // (o Explorer): sem o argumento nao ha como distinguir a origem do arranque.
    @Test
    fun `windows auto start command carries the origin argument`() {
        val command = AutoStartManager.windowsAutoStartCommand("""C:\Program Files\Usage Monitor\Usage Monitor.exe""")

        assertEquals(""""C:\Program Files\Usage Monitor\Usage Monitor.exe" --autostart""", command)
    }

    @Test
    fun `linux desktop entry carries the origin argument after the quoted path`() {
        val entry = AutoStartManager.buildLinuxDesktopEntry("/opt/usage-monitor/usage-monitor", "/opt/usage-monitor")

        assertTrue(entry.contains("""Exec="/opt/usage-monitor/usage-monitor" --autostart"""), entry)
    }

    @Test
    fun `launch agent plist carries the origin argument as its own program argument`() {
        val plist = AutoStartManager.buildLaunchAgentPlist("/Applications/Usage Monitor.app/Contents/MacOS/Usage Monitor")

        assertTrue(plist.contains("<string>--autostart</string>"), plist)
    }

    @Test
    fun `an entry without the origin argument is migrated`() {
        assertTrue(AutoStartManager.autoStartCommandNeedsMigration(""""C:\Users\dev\Usage Monitor.exe""""))
    }

    @Test
    fun `an entry that already carries the argument is left alone in all three formats`() {
        assertFalse(
            AutoStartManager.autoStartCommandNeedsMigration(""""C:\Users\dev\Usage Monitor.exe" --autostart""")
        )
        assertFalse(
            AutoStartManager.autoStartCommandNeedsMigration(
                AutoStartManager.buildLinuxDesktopEntry("/opt/usage-monitor", "/opt")
            )
        )
        assertFalse(
            AutoStartManager.autoStartCommandNeedsMigration(
                AutoStartManager.buildLaunchAgentPlist("/Applications/Usage Monitor")
            )
        )
    }

    // Entrada ausente nao migra: nao ha o que reescrever, e reescrever aqui
    // ligaria a inicializacao de quem a desligou.
    @Test
    fun `a missing or blank entry is not migrated`() {
        assertFalse(AutoStartManager.autoStartCommandNeedsMigration(null))
        assertFalse(AutoStartManager.autoStartCommandNeedsMigration("   "))
    }

    @Test
    fun `a similar looking argument does not count as the origin argument`() {
        assertTrue(AutoStartManager.autoStartCommandNeedsMigration(""""app.exe" --autostart-delayed"""))
    }

    private fun createTempDir(): File {
        return kotlin.io.path.createTempDirectory("autostart-manager-test").toFile()
    }

    private fun createFile(root: File, relativePath: String): File {
        val file = File(root, relativePath)
        file.parentFile?.mkdirs()
        file.writeText("test")
        return file
    }
}

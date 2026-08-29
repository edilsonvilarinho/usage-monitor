package com.usagemonitor

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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

    /**
     * A Desktop Entry Specification define regras de aspas **apenas para a chave
     * `Exec`** (secao "The Exec key"). `Path` e do tipo `string` e e lido
     * verbatim: a GLib o guarda em `info->path` e o passa como
     * `working_directory` do `g_spawn`; o KIO o passa para
     * `QProcess::setWorkingDirectory`. Um diretorio cujo nome literal comeca com
     * aspas nao existe, o spawn falha no `chdir` e **nada aparece na tela** --
     * `isAutoStartEnabled()` so testa se o arquivo existe.
     *
     * O teste que existia afirmava o `Exec=` e nunca o `Path=`: foi por ali que
     * o defeito passou.
     */
    @Test
    fun `linux desktop entry writes the working directory verbatim`() {
        val entry = AutoStartManager.buildLinuxDesktopEntry(
            "/home/edils/.local/bin/usage-monitor",
            "/home/edils/.local/bin"
        )

        assertTrue(entry.contains("\nPath=/home/edils/.local/bin\n"), entry)
        assertFalse(entry.contains("Path=\""), entry)
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

    // --- Linux: launcher estavel ---------------------------------------------

    private val linuxRoot = "/home/edils/.local/share/usage-monitor"
    private val stableLauncher = "/home/edils/.local/bin/usage-monitor"
    private val versionedExecutable = "$linuxRoot/versions/39.0.0/Usage Monitor/bin/Usage Monitor"

    /**
     * O launcher estavel le `current` e sobrevive a troca de versao. Um caminho
     * dentro de `versions/<versao>` deixa de existir na segunda atualizacao, e a
     * falha e silenciosa: nada no app le a entrada depois de escreve-la.
     */
    @Test
    fun `the linux entry points at the stable launcher when it exists`() {
        assertEquals(
            stableLauncher,
            AutoStartManager.linuxAutoStartExecutablePath(
                stableLauncherPath = stableLauncher,
                isExecutable = { path -> path == stableLauncher },
                fallback = { versionedExecutable }
            )
        )
    }

    /**
     * Numa instalacao `.deb` o launcher estavel simplesmente nao esta la, e o
     * fallback e o que sempre foi. E por isso que o teste e a presenca do
     * arquivo, e nao o resolvedor de origem.
     */
    @Test
    fun `without the stable launcher the linux entry falls back`() {
        assertEquals(
            "/opt/usage-monitor/bin/usage-monitor",
            AutoStartManager.linuxAutoStartExecutablePath(
                stableLauncherPath = stableLauncher,
                isExecutable = { false },
                fallback = { "/opt/usage-monitor/bin/usage-monitor" }
            )
        )
        assertNull(
            AutoStartManager.linuxAutoStartExecutablePath(
                stableLauncherPath = null,
                isExecutable = { true },
                fallback = { null }
            )
        )
    }

    @Test
    fun `an entry inside the versioned tree is migrated`() {
        assertTrue(
            AutoStartManager.linuxAutoStartNeedsLauncherMigration(
                currentCommand = AutoStartManager.buildLinuxDesktopEntry(
                    versionedExecutable,
                    "$linuxRoot/versions/39.0.0/Usage Monitor/bin"
                ),
                stableLauncherPath = stableLauncher,
                versionsPrefix = "$linuxRoot/versions/"
            )
        )
    }

    @Test
    fun `an entry already at the stable launcher is left alone`() {
        assertFalse(
            AutoStartManager.linuxAutoStartNeedsLauncherMigration(
                currentCommand = AutoStartManager.buildLinuxDesktopEntry(
                    stableLauncher,
                    "/home/edils/.local/bin"
                ),
                stableLauncherPath = stableLauncher,
                versionsPrefix = "$linuxRoot/versions/"
            )
        )
    }

    /**
     * Uma instalacao `.deb` aponta para `/opt` e nao para a arvore versionada:
     * reescreve-la seria mexer numa instalacao que este mecanismo nao governa.
     */
    @Test
    fun `an entry outside the versioned tree is not migrated`() {
        assertFalse(
            AutoStartManager.linuxAutoStartNeedsLauncherMigration(
                currentCommand = AutoStartManager.buildLinuxDesktopEntry(
                    "/opt/usage-monitor/bin/usage-monitor",
                    "/opt/usage-monitor/bin"
                ),
                stableLauncherPath = stableLauncher,
                versionsPrefix = "$linuxRoot/versions/"
            )
        )
    }

    /** Entrada ausente nao migra: ligaria a inicializacao de quem a desligou. */
    @Test
    fun `a missing linux entry is never migrated`() {
        assertFalse(
            AutoStartManager.linuxAutoStartNeedsLauncherMigration(
                currentCommand = null,
                stableLauncherPath = stableLauncher,
                versionsPrefix = "$linuxRoot/versions/"
            )
        )
        assertFalse(
            AutoStartManager.linuxAutoStartNeedsLauncherMigration(
                currentCommand = "   ",
                stableLauncherPath = stableLauncher,
                versionsPrefix = "$linuxRoot/versions/"
            )
        )
    }

    /** Sem launcher estavel ou sem raiz nao ha para onde migrar. */
    @Test
    fun `nothing is migrated without a destination`() {
        val entry = AutoStartManager.buildLinuxDesktopEntry(versionedExecutable, "/x")

        assertFalse(
            AutoStartManager.linuxAutoStartNeedsLauncherMigration(
                currentCommand = entry,
                stableLauncherPath = null,
                versionsPrefix = "$linuxRoot/versions/"
            )
        )
        assertFalse(
            AutoStartManager.linuxAutoStartNeedsLauncherMigration(
                currentCommand = entry,
                stableLauncherPath = stableLauncher,
                versionsPrefix = null
            )
        )
    }

    // --- Linux: estado da entrada de autostart --------------------------------

    /**
     * `present` sozinho e o que o interruptor ja perguntava, e foi por isso que o
     * defeito do `Path=` passou: o arquivo existia e a entrada nao subia o app.
     */
    @Test
    fun `a well formed linux entry is present and valid`() {
        val state = AutoStartManager.inspectLinuxAutostartEntry(
            readEntry = {
                AutoStartManager.buildLinuxDesktopEntry(stableLauncher, "/home/edils/.local/bin")
            },
            isExecutable = { path -> path == stableLauncher }
        )

        assertTrue(state.present)
        assertTrue(state.valid)
    }

    /** A entrada que as versoes anteriores escreviam: `Path` entre aspas. */
    @Test
    fun `a linux entry with a quoted working directory is present but not valid`() {
        val legacyEntry = """
            [Desktop Entry]
            Type=Application
            Name=Usage Monitor
            Exec="$stableLauncher" --autostart
            Path="/home/edils/.local/bin"
            Terminal=false
        """.trimIndent()

        val state = AutoStartManager.inspectLinuxAutostartEntry(
            readEntry = { legacyEntry },
            isExecutable = { path -> path == stableLauncher }
        )

        assertTrue(state.present)
        assertFalse(state.valid)
    }

    /**
     * A arvore versionada e podada dois ciclos depois da atualizacao, e a entrada
     * passa a nomear um caminho que nao existe -- sem erro na tela.
     */
    @Test
    fun `a linux entry pointing at a pruned executable is present but not valid`() {
        val state = AutoStartManager.inspectLinuxAutostartEntry(
            readEntry = {
                AutoStartManager.buildLinuxDesktopEntry(versionedExecutable, "$linuxRoot/versions/39.0.0")
            },
            isExecutable = { false }
        )

        assertTrue(state.present)
        assertFalse(state.valid)
    }

    /** Arquivo ausente, em branco ou ilegivel: nao ha entrada para julgar. */
    @Test
    fun `a missing linux entry is neither present nor valid`() {
        listOf<() -> String?>(
            { null },
            { "   " },
            { throw java.io.IOException("ilegível") }
        ).forEach { reader ->
            val state = AutoStartManager.inspectLinuxAutostartEntry(
                readEntry = reader,
                isExecutable = { true }
            )

            assertFalse(state.present)
            assertFalse(state.valid)
        }
    }

    // --- Linux: entrada quebrada como terceiro motivo de migracao -------------

    /**
     * Reescrever uma entrada que ja funciona seria trabalho sem mudanca, e a cada
     * arranque.
     */
    @Test
    fun `a working linux entry is not repaired`() {
        assertFalse(
            AutoStartManager.linuxAutoStartNeedsRepair(
                currentCommand = AutoStartManager.buildLinuxDesktopEntry(
                    stableLauncher,
                    "/home/edils/.local/bin"
                ),
                isExecutable = { path -> path == stableLauncher }
            )
        )
    }

    /**
     * O caso da issue #120: a entrada existe, o interruptor esta ligado, e o
     * `Path=` entre aspas nomeia um diretorio que nao existe -- o spawn falha no
     * `chdir` e nada aparece na tela. Sem este motivo de migracao a correcao do
     * `Path=` nao alcancaria ninguem que ja esteja afetado.
     */
    @Test
    fun `a linux entry with a quoted working directory is repaired`() {
        val legacyEntry = """
            [Desktop Entry]
            Type=Application
            Name=Usage Monitor
            Exec="$stableLauncher" --autostart
            Path="/home/edils/.local/bin"
            Terminal=false
        """.trimIndent()

        assertTrue(
            AutoStartManager.linuxAutoStartNeedsRepair(
                currentCommand = legacyEntry,
                isExecutable = { path -> path == stableLauncher }
            )
        )
    }

    /**
     * Entrada ausente nao migra, e a regra continua intacta com o motivo novo:
     * reescrever aqui ligaria a inicializacao de quem a desligou.
     */
    @Test
    fun `a missing linux entry is never repaired`() {
        assertFalse(
            AutoStartManager.linuxAutoStartNeedsRepair(
                currentCommand = null,
                isExecutable = { true }
            )
        )
        assertFalse(
            AutoStartManager.linuxAutoStartNeedsRepair(
                currentCommand = "   ",
                isExecutable = { true }
            )
        )
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

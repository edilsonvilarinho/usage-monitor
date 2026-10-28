package com.usagemonitor

import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A leitura do autostart no arranque: o valor real do sistema chega às
 * preferências, a migração que falha vai à trilha e a instância fora da raiz
 * padrão não toca em nada da instância padrão (#419).
 */
class AppPreferenceEffectsTest {

    private val root: File = Files.createTempDirectory("launch-autostart-").toFile()
    private val commands = mutableListOf<List<String>>()
    private val resolved = mutableListOf<Boolean>()
    private val breadcrumbs = RecordingBreadcrumbRecorder()
    private var menuIconChecks = 0

    private val linuxEntry get() = File(root, ".config/autostart/usage-monitor.desktop")

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    private fun host(
        platform: AutoStartManager.Platform = AutoStartManager.Platform.LINUX,
        executable: String? = "/opt/usage-monitor/bin/usage-monitor",
        installRoot: () -> String? = { null }
    ) = AutoStartHost(
        platform = platform,
        homeDirectory = root,
        xdgConfigHome = null,
        runCommand = { command ->
            commands += command
            AutoStartCommandResult(0, "")
        },
        executablePath = { executable },
        stableLauncherPath = { null },
        installRoot = installRoot,
        isExecutable = { true }
    )

    private fun resolve(host: AutoStartHost, isDefaultDataRoot: Boolean = true) = runBlocking {
        resolveAutoStartAtLaunch(
            isDefaultDataRoot = isDefaultDataRoot,
            host = host,
            applyResolved = { value -> resolved += value },
            breadcrumbs = breadcrumbs,
            ensureMenuIcon = { menuIconChecks += 1 }
        )
    }

    @Test
    fun `an instance outside the default data root reads and rewrites nothing`() {
        linuxEntry.parentFile.mkdirs()
        linuxEntry.writeText("[Desktop Entry]\nExec=\"/old/usage-monitor\"\n")

        resolve(host(), isDefaultDataRoot = false)

        assertEquals(emptyList(), resolved)
        assertEquals(0, menuIconChecks, "o ícone de menu também é da instância padrão")
        assertEquals("[Desktop Entry]\nExec=\"/old/usage-monitor\"\n", linuxEntry.readText())
    }

    @Test
    fun `the system answer reaches the preferences and the menu icon is checked`() {
        resolve(host())

        assertEquals(listOf(false), resolved)
        assertEquals(1, menuIconChecks)
        assertEquals(emptyList(), breadcrumbs.messages)
    }

    @Test
    fun `an entry without the origin argument is migrated in place`() {
        linuxEntry.parentFile.mkdirs()
        linuxEntry.writeText("[Desktop Entry]\nExec=\"/opt/usage-monitor/bin/usage-monitor\"\n")

        resolve(host())

        assertEquals(listOf(true), resolved)
        assertTrue(linuxEntry.readText().contains(StartupOrigin.AUTO_START_ARGUMENT), linuxEntry.readText())
        assertEquals(emptyList(), breadcrumbs.messages)
    }

    @Test
    fun `a migration that cannot find the executable is recorded and leaves the entry alone`() {
        val original = "[Desktop Entry]\nExec=\"/opt/usage-monitor/bin/usage-monitor\"\n"
        linuxEntry.parentFile.mkdirs()
        linuxEntry.writeText(original)

        resolve(host(executable = null))

        assertEquals(listOf(true), resolved)
        assertEquals(
            listOf("atualizar comando de inicialização falhou: executável do aplicativo não localizado"),
            breadcrumbs.messages
        )
        assertEquals(original, linuxEntry.readText())
        assertEquals(1, menuIconChecks)
    }

    @Test
    fun `a migration that throws is recorded and the launch goes on`() {
        linuxEntry.parentFile.mkdirs()
        linuxEntry.writeText("[Desktop Entry]\nExec=\"/opt/usage-monitor/bin/usage-monitor\" ${StartupOrigin.AUTO_START_ARGUMENT}\n")

        resolve(host(installRoot = { throw IllegalStateException("raiz ilegível") }))

        assertEquals(listOf(true), resolved)
        assertEquals(1, breadcrumbs.messages.size)
        assertTrue(breadcrumbs.messages.single().startsWith("atualizar comando de inicialização falhou:"), breadcrumbs.messages.single())
        assertEquals(1, menuIconChecks)
    }

    @Test
    fun `a platform without auto start skips the reading but still checks the menu icon`() {
        resolve(host(platform = AutoStartManager.Platform.OTHER))

        assertEquals(emptyList(), resolved)
        assertEquals(1, menuIconChecks)
        assertTrue(commands.isEmpty())
    }
}

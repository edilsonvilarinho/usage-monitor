package com.usagemonitor

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConsoleLauncherTest {

    private val installDir = File("C:\\Users\\someone\\AppData\\Local\\Usage Monitor")

    @Test
    fun `the console launcher relaunches the gui exe next to it with the same arguments`() {
        val command = ConsoleLauncher.guiRelaunchCommand(
            isWindows = true,
            appPath = File(installDir, "usage-monitor.exe").path,
            arguments = arrayOf("--bogus", " ", "")
        )

        assertEquals(listOf(File(installDir, "Usage Monitor.exe").path, "--bogus"), command)
    }

    @Test
    fun `the launcher name is compared without case`() {
        val command = ConsoleLauncher.guiRelaunchCommand(
            isWindows = true,
            appPath = File(installDir, "Usage-Monitor.EXE").path,
            arguments = emptyArray()
        )

        assertEquals(listOf(File(installDir, "Usage Monitor.exe").path), command)
    }

    @Test
    fun `the gui exe itself never relaunches`() {
        // Relançar a partir do exe GUI seria um laço de processos.
        assertNull(
            ConsoleLauncher.guiRelaunchCommand(
                isWindows = true,
                appPath = File(installDir, "Usage Monitor.exe").path,
                arguments = emptyArray()
            )
        )
    }

    @Test
    fun `gradle run and other platforms never relaunch`() {
        assertNull(ConsoleLauncher.guiRelaunchCommand(isWindows = true, appPath = null, arguments = emptyArray()))
        assertNull(ConsoleLauncher.guiRelaunchCommand(isWindows = true, appPath = "  ", arguments = emptyArray()))
        assertNull(
            ConsoleLauncher.guiRelaunchCommand(
                isWindows = false,
                appPath = "/opt/usage-monitor/bin/usage-monitor.exe",
                arguments = emptyArray()
            )
        )
    }

    @Test
    fun `a missing gui exe fails with a non zero exit code`() {
        val missing = File(kotlin.io.path.createTempDirectory("console-launcher-test").toFile(), "Usage Monitor.exe")
        try {
            assertEquals(1, ConsoleLauncher.relaunch(listOf(missing.path)))
        } finally {
            missing.parentFile.deleteRecursively()
        }
    }
}

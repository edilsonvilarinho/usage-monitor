package com.usagemonitor.update

import com.usagemonitor.domain.entity.AppUpdateInfo
import com.usagemonitor.presentation.viewmodel.AutomaticUpdateStage
import com.usagemonitor.presentation.viewmodel.PreparedUpdateAction
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DesktopAppUpdateInstallerTest {

    private fun sampleUpdate(downloadUrl: String = "https://example.com/usage-monitor_13.0.0_amd64.deb"): AppUpdateInfo {
        return AppUpdateInfo(
            version = "13.0.0",
            releasePageUrl = "https://example.com/releases/tag/v13.0.0",
            linuxDebInstallerDownloadUrl = downloadUrl
        )
    }

    @Test
    fun `linux automatic update requires dpkg`() {
        val support = getLinuxAutomaticUpdateSupport(
            update = sampleUpdate(),
            executablePathResolver = { "/opt/Usage Monitor/bin/usage-monitor" },
            commandAvailabilityChecker = { command -> command == "pkcon" },
            debPackageInstallationChecker = { true }
        )

        assertFalse(support.isSupported)
        assertEquals("dpkg is required to install the Linux update automatically.", support.failureMessage)
    }

    @Test
    fun `linux automatic update requires deb-managed launcher`() {
        val support = getLinuxAutomaticUpdateSupport(
            update = sampleUpdate(),
            executablePathResolver = { "/home/user/apps/usage-monitor/usage-monitor" },
            commandAvailabilityChecker = { true },
            debPackageInstallationChecker = { false }
        )

        assertFalse(support.isSupported)
        assertEquals(
            "Automatic Linux updates currently require the app to be installed from the published DEB package.",
            support.failureMessage
        )
    }

    @Test
    fun `linux automatic update requires pkcon`() {
        val support = getLinuxAutomaticUpdateSupport(
            update = sampleUpdate(),
            executablePathResolver = { "/usr/bin/usage-monitor" },
            commandAvailabilityChecker = { command -> command == "dpkg" },
            debPackageInstallationChecker = { true }
        )

        assertFalse(support.isSupported)
        assertEquals(
            "PackageKit pkcon is required to install the Linux update automatically.",
            support.failureMessage
        )
    }

    @Test
    fun `linux automatic update support keeps launcher path and pkcon command`() {
        val support = getLinuxAutomaticUpdateSupport(
            update = sampleUpdate(),
            executablePathResolver = { "/usr/bin/usage-monitor" },
            commandAvailabilityChecker = { command -> command == "dpkg" || command == "pkcon" },
            debPackageInstallationChecker = { true }
        )

        assertTrue(support.isSupported)
        assertEquals("/usr/bin/usage-monitor", support.executablePath)
        assertEquals(listOf("pkcon", "--noninteractive", "install-local"), support.packageManager?.command)
        assertEquals("pkcon install-local", support.packageManager?.displayName)
        assertEquals(null, support.failureMessage)
    }

    @Test
    fun `linux automatic update installs via pkcon and requests restart`() {
        val downloadedDeb = createTempDebFile()
        val launcher = createTempLauncherFile()
        val commands = mutableListOf<List<String>>()
        val stages = mutableListOf<AutomaticUpdateStage>()
        var invocations = 0

        val result = prepareLinuxUpdateInstallation(
            update = sampleUpdate(downloadedDeb.toURI().toString()),
            executablePathResolver = { launcher.absolutePath },
            commandAvailabilityChecker = { command -> command == "dpkg" || command == "pkcon" },
            debPackageInstallationChecker = { true },
            processLauncher = { command, _ ->
                commands += command
                invocations += 1
                when (invocations) {
                    1 -> FakeProcess(exitCode = 0, output = "install complete")
                    2 -> FakeProcess(exitCode = 0, output = "", waitCompletes = false)
                    else -> error("unexpected process invocation")
                }
            },
            onStageChanged = { stages += it }
        )

        assertTrue(result.isSuccess)
        assertIs<PreparedUpdateAction.RestartAndExit>(result.getOrThrow())
        assertEquals(
            listOf("pkcon", "--noninteractive", "install-local"),
            commands.first().dropLast(1)
        )
        assertTrue(commands.first().last().endsWith("${File.separator}UsageMonitor-13.0.0.deb"))
        assertEquals(listOf(launcher.absolutePath), commands.last())
        assertEquals(
            listOf(AutomaticUpdateStage.INSTALLING, AutomaticUpdateStage.RESTARTING),
            stages
        )
    }

    @Test
    fun `linux automatic update fails when pkcon cannot authenticate`() {
        val downloadedDeb = createTempDebFile()
        val launcher = createTempLauncherFile()
        val stages = mutableListOf<AutomaticUpdateStage>()

        val result = prepareLinuxUpdateInstallation(
            update = sampleUpdate(downloadedDeb.toURI().toString()),
            executablePathResolver = { launcher.absolutePath },
            commandAvailabilityChecker = { command -> command == "dpkg" || command == "pkcon" },
            debPackageInstallationChecker = { true },
            processLauncher = { _, _ ->
                FakeProcess(exitCode = 1, output = "Authentication failed")
            },
            onStageChanged = { stages += it }
        )

        assertTrue(result.isFailure)
        assertEquals(
            "pkcon install-local could not authenticate the Linux update request.",
            result.exceptionOrNull()?.message
        )
        assertEquals(listOf(AutomaticUpdateStage.INSTALLING), stages)
    }

    @Test
    fun `linux automatic update fails when restarted app cannot be launched`() {
        val downloadedDeb = createTempDebFile()
        val launcher = createTempLauncherFile()
        var invocations = 0

        val result = prepareLinuxUpdateInstallation(
            update = sampleUpdate(downloadedDeb.toURI().toString()),
            executablePathResolver = { launcher.absolutePath },
            commandAvailabilityChecker = { command -> command == "dpkg" || command == "pkcon" },
            debPackageInstallationChecker = { true },
            processLauncher = { _, _ ->
                invocations += 1
                when (invocations) {
                    1 -> FakeProcess(exitCode = 0, output = "install complete")
                    2 -> FakeProcess(exitCode = 1, output = "launcher boom")
                    else -> error("unexpected process invocation")
                }
            },
            onStageChanged = {}
        )

        assertTrue(result.isFailure)
        assertEquals(
            "Installed application launcher could not be started after the Linux update: launcher boom",
            result.exceptionOrNull()?.message
        )
    }

    @Test
    fun `windows launcher script uses setup executable directly`() {
        val script = buildWindowsLauncherScript(
            processId = 42,
            installerPath = """C:\temp\UsageMonitor-Setup-13.0.0.exe"""
        )

        assertTrue(script.contains("Start-Process -FilePath \$installerPath"))
        assertFalse(script.contains("msiexec.exe"))
    }

    @Test
    fun `windows launcher script uses msiexec for msi installers`() {
        val script = buildWindowsLauncherScript(
            processId = 42,
            installerPath = """C:\temp\UsageMonitor-13.0.0.msi"""
        )

        assertTrue(script.contains("""Start-Process -FilePath "msiexec.exe""""))
        assertTrue(script.contains("@('/i', \$installerPath)"))
    }

    private fun createTempDebFile(): File {
        val file = Files.createTempFile("usage-monitor-update-test", ".deb").toFile()
        file.writeText("fake deb content")
        file.deleteOnExit()
        return file
    }

    private fun createTempLauncherFile(): File {
        val file = Files.createTempFile("usage-monitor-launcher-test", ".sh").toFile()
        file.writeText("#!/bin/sh\nexit 0\n")
        file.deleteOnExit()
        return file
    }

    private class FakeProcess(
        private val exitCode: Int,
        output: String,
        private val waitCompletes: Boolean = true
    ) : Process() {
        private val input = ByteArrayInputStream(output.toByteArray())
        private val output = ByteArrayOutputStream()

        override fun getOutputStream() = output

        override fun getInputStream() = input

        override fun getErrorStream() = ByteArrayInputStream(ByteArray(0))

        override fun waitFor(): Int = exitCode

        override fun waitFor(timeout: Long, unit: java.util.concurrent.TimeUnit): Boolean = waitCompletes

        override fun exitValue(): Int = exitCode

        override fun destroy() = Unit

        override fun destroyForcibly(): Process = this

        override fun isAlive(): Boolean = !waitCompletes
    }
}

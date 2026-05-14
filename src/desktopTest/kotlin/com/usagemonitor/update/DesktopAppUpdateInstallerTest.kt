package com.usagemonitor.update

import com.usagemonitor.domain.entity.AppUpdateInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopAppUpdateInstallerTest {

    private val sampleUpdate = AppUpdateInfo(
        version = "13.0.0",
        releasePageUrl = "https://example.com/releases/tag/v13.0.0",
        linuxDebInstallerDownloadUrl = "https://example.com/usage-monitor_13.0.0_amd64.deb"
    )

    @Test
    fun `linux automatic update requires dpkg`() {
        val support = getLinuxAutomaticUpdateSupport(
            update = sampleUpdate,
            executablePathResolver = { "/opt/Usage Monitor/bin/usage-monitor" },
            commandAvailabilityChecker = { command -> command == "pkexec" },
            debPackageInstallationChecker = { true }
        )

        assertFalse(support.isSupported)
        assertEquals("dpkg is required to install the Linux update automatically.", support.failureMessage)
    }

    @Test
    fun `linux automatic update requires deb-managed launcher`() {
        val support = getLinuxAutomaticUpdateSupport(
            update = sampleUpdate,
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
    fun `linux automatic update support keeps launcher path for deb install`() {
        val support = getLinuxAutomaticUpdateSupport(
            update = sampleUpdate,
            executablePathResolver = { "/usr/bin/usage-monitor" },
            commandAvailabilityChecker = { true },
            debPackageInstallationChecker = { true }
        )

        assertTrue(support.isSupported)
        assertEquals("/usr/bin/usage-monitor", support.executablePath)
        assertEquals(null, support.failureMessage)
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
}

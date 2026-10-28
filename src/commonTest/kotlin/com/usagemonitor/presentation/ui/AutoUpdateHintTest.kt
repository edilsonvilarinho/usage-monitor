package com.usagemonitor.presentation.ui

import com.usagemonitor.domain.entity.AppUpdatePlatform
import com.usagemonitor.domain.entity.AppUpdateReceipt
import com.usagemonitor.domain.entity.AppUpdateReceiptStatus
import com.usagemonitor.domain.repository.AppUpdateSupport
import com.usagemonitor.presentation.ui.components.autoUpdateHint
import com.usagemonitor.presentation.ui.components.lastUpdateReceiptLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * O motivo de a atualização automática não estar disponível, em inglês e por
 * plataforma: o texto nomeia o instalador certo, ou nenhum quando não sabe.
 */
class AutoUpdateHintTest {

    @Test
    fun `each reason has its own english text naming the right installer`() {
        assertEquals(
            "Not available on macOS: the package is unsigned and Gatekeeper requires manual approval.",
            autoUpdateHint(AppUpdateSupport.UNSUPPORTED_PLATFORM, isPt = false, platform = AppUpdatePlatform.MACOS)
        )
        assertEquals(
            "Not available on this platform. Automatic updates cover Windows and user-space Linux.",
            autoUpdateHint(AppUpdateSupport.UNSUPPORTED_PLATFORM, isPt = false, platform = null)
        )
        assertEquals(
            "Only available for the user-space install made by the .sh installer. This copy came from a .deb/.rpm package " +
                "or from outside it, and updating it here would touch files owned by the package manager.",
            autoUpdateHint(AppUpdateSupport.UNSUPPORTED_INSTALL_ORIGIN, isPt = false, platform = AppUpdatePlatform.LINUX)
        )
        assertEquals(
            "Only available for installs made by the .exe installer. This copy came from the MSI or from outside it, " +
                "and updating it here would create a second install.",
            autoUpdateHint(AppUpdateSupport.UNSUPPORTED_INSTALL_ORIGIN, isPt = false, platform = AppUpdatePlatform.WINDOWS)
        )
        assertEquals(
            "Only available for installs made by the app's official installer.",
            autoUpdateHint(AppUpdateSupport.UNSUPPORTED_INSTALL_ORIGIN, isPt = false, platform = null)
        )
        assertEquals(
            "Disponível apenas nas instalações feitas pelo instalador oficial do aplicativo.",
            autoUpdateHint(AppUpdateSupport.UNSUPPORTED_INSTALL_ORIGIN, isPt = true, platform = null)
        )
        assertEquals(
            "No package is published for this machine's architecture. Automatic updates cover x86_64 only.",
            autoUpdateHint(AppUpdateSupport.UNSUPPORTED_ARCHITECTURE, isPt = false)
        )
        assertEquals(
            "Unavailable with --user-data-dir: the installer reopens the app on the default folder. Update from the instance without the option.",
            autoUpdateHint(AppUpdateSupport.ISOLATED_DATA_ROOT, isPt = false)
        )
    }

    @Test
    fun `every reason reads differently in portuguese and english`() {
        val platforms = listOf<AppUpdatePlatform?>(null) + AppUpdatePlatform.entries
        for (support in AppUpdateSupport.entries) {
            for (platform in platforms) {
                assertNotEquals(autoUpdateHint(support, isPt = true, platform), autoUpdateHint(support, isPt = false, platform), "$support/$platform")
            }
        }
    }

    @Test
    fun `a failed update says the installed version was left untouched`() {
        val receipt = AppUpdateReceipt(version = "41.2.0", previousVersion = "41.1.0", status = AppUpdateReceiptStatus.FAILED, reason = "locked")

        assertEquals(
            "Last update: 41.1.0 → 41.2.0 failed (locked). The installed version was left untouched.",
            lastUpdateReceiptLine(receipt, isPt = false)
        )
        assertEquals(
            "Last update: 41.2.0 failed. The installed version was left untouched.",
            lastUpdateReceiptLine(receipt.copy(previousVersion = null, reason = null), isPt = false)
        )
    }
}

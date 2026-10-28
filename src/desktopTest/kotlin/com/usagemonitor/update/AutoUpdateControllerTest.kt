package com.usagemonitor.update

import com.usagemonitor.domain.entity.AppUpdateInfo
import com.usagemonitor.domain.repository.AppUpdateInstaller
import com.usagemonitor.domain.entity.AppUpdatePlatform
import com.usagemonitor.domain.repository.AppUpdatePreparation
import com.usagemonitor.domain.repository.AppUpdateSupport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class AutoUpdateControllerTest {

    private class NamedInstaller(private val support: AppUpdateSupport = AppUpdateSupport.SUPPORTED) : AppUpdateInstaller {
        override fun support(): AppUpdateSupport = support
        override suspend fun prepare(update: AppUpdateInfo, onProgress: (Long, Long?) -> Unit): Result<AppUpdatePreparation> =
            error("não usado")
        override fun schedule(preparation: AppUpdatePreparation): Result<Unit> = error("não usado")
    }

    private val windows = NamedInstaller()
    private val linux = NamedInstaller()
    private val built = mutableListOf<String>()

    private fun installerFor(platform: AppUpdatePlatform?, isolated: Boolean = false) = autoUpdateInstallerFor(
        platform = platform,
        isolatedDataRoot = isolated,
        windows = {
            built += "windows"
            windows
        },
        linux = {
            built += "linux"
            linux
        }
    )

    @Test
    fun `each platform gets only its own installer`() {
        assertSame(windows, installerFor(AppUpdatePlatform.WINDOWS))
        assertSame(linux, installerFor(AppUpdatePlatform.LINUX))
        assertEquals(listOf("windows", "linux"), built)
    }

    @Test
    fun `macOS and an unknown platform get no installer and build none`() {
        assertNull(installerFor(AppUpdatePlatform.MACOS))
        assertNull(installerFor(null))
        assertEquals(emptyList<String>(), built)
    }

    @Test
    fun `an isolated data root never builds an installer`() {
        assertNull(installerFor(AppUpdatePlatform.WINDOWS, isolated = true))
        assertNull(installerFor(AppUpdatePlatform.LINUX, isolated = true))
        assertEquals(emptyList<String>(), built)
    }

    @Test
    fun `the switches publish the new value and persist it`() {
        val persisted = mutableListOf<Boolean>()
        val persistedBeta = mutableListOf<Boolean>()
        val controller = AutoUpdateController(
            installer = windows,
            platform = AppUpdatePlatform.WINDOWS,
            enabled = MutableStateFlow(true),
            lastReceipt = null,
            feedUrlOverride = null,
            persist = { value -> persisted += value },
            receiveBetaUpdates = MutableStateFlow(false),
            persistReceiveBetaUpdates = { value -> persistedBeta += value }
        )

        controller.setEnabled(false)
        controller.setReceiveBetaUpdates(true)

        assertEquals(false, controller.enabled.value)
        assertEquals(listOf(false), persisted)
        assertEquals(true, controller.receiveBetaUpdates.value)
        assertEquals(listOf(true), persistedBeta)
    }

    @Test
    fun `a restart request runs the action bound after construction`() {
        val controller = AutoUpdateController(
            installer = null,
            platform = AppUpdatePlatform.LINUX,
            enabled = MutableStateFlow(true),
            lastReceipt = null,
            feedUrlOverride = null,
            persist = {}
        )
        var restarts = 0

        controller.requestRestart()
        controller.bindRestart { restarts += 1 }
        controller.requestRestart()

        assertEquals(1, restarts, "antes do vínculo o pedido não faz nada")
        assertEquals(AppUpdateSupport.UNAVAILABLE, controller.support)
    }
}

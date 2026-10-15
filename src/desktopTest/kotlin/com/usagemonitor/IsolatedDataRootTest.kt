package com.usagemonitor

import com.usagemonitor.domain.entity.AppUpdatePlatform
import com.usagemonitor.domain.repository.AppUpdateSupport
import com.usagemonitor.update.AutoUpdateController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals

class IsolatedDataRootTest {

    @Test
    fun `an isolated data root wins over every other reason`() {
        for (platform in listOf(AppUpdatePlatform.WINDOWS, AppUpdatePlatform.LINUX, AppUpdatePlatform.MACOS, null)) {
            assertEquals(AppUpdateSupport.ISOLATED_DATA_ROOT, controller(platform, isolatedDataRoot = true).support)
        }
    }

    @Test
    fun `the default root keeps the platform reason`() {
        assertEquals(AppUpdateSupport.UNAVAILABLE, controller(AppUpdatePlatform.WINDOWS, isolatedDataRoot = false).support)
        assertEquals(AppUpdateSupport.UNSUPPORTED_PLATFORM, controller(AppUpdatePlatform.MACOS, isolatedDataRoot = false).support)
    }

    @Test
    fun `the tray names the isolated folder first, so the tooltip cut never hides it`() {
        assertEquals("Usage Monitor", trayAppName(isolatedDataRootName = null))
        assertEquals("Usage Monitor (perfil-teste)", trayAppName(isolatedDataRootName = "perfil-teste"))
    }

    private fun controller(platform: AppUpdatePlatform?, isolatedDataRoot: Boolean) = AutoUpdateController(
        installer = null,
        platform = platform,
        enabled = MutableStateFlow(true),
        lastReceipt = null,
        feedUrlOverride = null,
        persist = {},
        isolatedDataRoot = isolatedDataRoot
    )
}

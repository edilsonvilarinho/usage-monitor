package com.usagemonitor.domain

import com.usagemonitor.domain.entity.AppUpdateInfo
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppUpdateInfoTest {

    @Test
    fun `a beta version is a prerelease by default`() {
        assertTrue(AppUpdateInfo(version = "42.0.0-beta.1", releasePageUrl = "").isPrerelease)
    }

    @Test
    fun `a stable version is not a prerelease by default`() {
        assertFalse(AppUpdateInfo(version = "42.0.0", releasePageUrl = "").isPrerelease)
    }

    @Test
    fun `the prerelease flag can be set explicitly`() {
        // Release marcada como prerelease no GitHub sem sufixo na versão.
        assertTrue(AppUpdateInfo(version = "42.0.0", releasePageUrl = "", isPrerelease = true).isPrerelease)
    }
}

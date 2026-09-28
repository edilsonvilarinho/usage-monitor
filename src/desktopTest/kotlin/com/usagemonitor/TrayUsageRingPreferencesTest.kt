package com.usagemonitor

import com.russhwolf.settings.PreferencesSettings
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class TrayUsageRingPreferencesTest {

    @Test
    fun `tray usage ring defaults to disabled`() {
        withTestSettings { settings ->
            assertFalse(readPersistedTrayUsageRing(settings))
        }
    }

    @Test
    fun `tray usage ring round trips the stored value`() {
        withTestSettings { settings ->
            persistTrayUsageRing(settings, true)
            assertEquals(true, readPersistedTrayUsageRing(settings))

            persistTrayUsageRing(settings, false)
            assertFalse(readPersistedTrayUsageRing(settings))
        }
    }

    private fun withTestSettings(block: (PreferencesSettings) -> Unit) {
        val nodeName = "com.usagemonitor.tests.${UUID.randomUUID()}"
        val preferencesNode = Preferences.userRoot().node(nodeName)
        try {
            block(PreferencesSettings(preferencesNode))
        } finally {
            runCatching {
                preferencesNode.removeNode()
                preferencesNode.flush()
            }
        }
    }
}

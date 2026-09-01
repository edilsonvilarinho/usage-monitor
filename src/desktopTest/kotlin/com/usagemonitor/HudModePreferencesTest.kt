package com.usagemonitor

import com.russhwolf.settings.PreferencesSettings
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HudModePreferencesTest {

    @Test
    fun `read persisted hud mode defaults to disabled`() {
        withTestSettings { settings ->
            assertFalse(readPersistedHudMode(settings))
        }
    }

    @Test
    fun `persist and read hud mode round trips the stored value`() {
        withTestSettings { settings ->
            persistHudMode(settings, true)

            assertEquals(true, readPersistedHudMode(settings))
        }
    }

    @Test
    fun `persist hud mode can turn it back off`() {
        withTestSettings { settings ->
            persistHudMode(settings, true)
            persistHudMode(settings, false)

            assertFalse(readPersistedHudMode(settings))
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

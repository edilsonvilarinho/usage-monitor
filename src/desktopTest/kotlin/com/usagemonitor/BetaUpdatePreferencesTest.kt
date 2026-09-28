package com.usagemonitor

import com.russhwolf.settings.PreferencesSettings
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BetaUpdatePreferencesTest {

    /** Ninguém entra no canal beta porque o app foi atualizado. */
    @Test
    fun `beta updates are off when the preference was never written`() {
        withTestSettings { settings ->
            assertFalse(readPersistedReceiveBetaUpdates(settings))
        }
    }

    @Test
    fun `persist and read round trips both values`() {
        withTestSettings { settings ->
            persistReceiveBetaUpdates(settings, true)
            assertTrue(readPersistedReceiveBetaUpdates(settings))

            persistReceiveBetaUpdates(settings, false)
            assertFalse(readPersistedReceiveBetaUpdates(settings))
        }
    }

    @Test
    fun `the stored key is the one the app reads back`() {
        withTestSettings { settings ->
            persistReceiveBetaUpdates(settings, true)

            assertEquals(true, settings.getBoolean("receiveBetaUpdates", false))
        }
    }

    @Test
    fun `the beta preference is independent from automatic updates`() {
        withTestSettings { settings ->
            persistReceiveBetaUpdates(settings, true)

            assertFalse(readPersistedAutoUpdateEnabled(settings))
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

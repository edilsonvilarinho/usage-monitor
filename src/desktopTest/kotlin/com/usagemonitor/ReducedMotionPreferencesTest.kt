package com.usagemonitor

import com.russhwolf.settings.PreferencesSettings
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ReducedMotionPreferencesTest {

    @Test
    fun `read persisted reduced motion defaults to disabled`() {
        withTestSettings { settings ->
            assertFalse(readPersistedReducedMotion(settings))
        }
    }

    @Test
    fun `persist and read reduced motion round trips the stored value`() {
        withTestSettings { settings ->
            persistReducedMotion(settings, true)

            assertEquals(true, readPersistedReducedMotion(settings))
        }
    }

    @Test
    fun `persist reduced motion can turn it back off`() {
        withTestSettings { settings ->
            persistReducedMotion(settings, true)
            persistReducedMotion(settings, false)

            assertFalse(readPersistedReducedMotion(settings))
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

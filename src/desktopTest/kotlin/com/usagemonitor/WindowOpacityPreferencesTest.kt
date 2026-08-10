package com.usagemonitor

import com.russhwolf.settings.PreferencesSettings
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertEquals

class WindowOpacityPreferencesTest {

    @Test
    fun `read persisted window opacity falls back to fully opaque when preference is absent`() {
        withTestSettings { settings ->
            assertEquals(100, readPersistedWindowOpacityPercent(settings))
        }
    }

    @Test
    fun `persist and read window opacity round trips the stored value`() {
        withTestSettings { settings ->
            persistWindowOpacityPercent(settings, 75)

            assertEquals(75, readPersistedWindowOpacityPercent(settings))
        }
    }

    @Test
    fun `read persisted window opacity clamps values below the supported range`() {
        withTestSettings { settings ->
            settings.putInt("windowOpacityPercent", 10)

            assertEquals(50, readPersistedWindowOpacityPercent(settings))
        }
    }

    @Test
    fun `read persisted window opacity clamps values above the supported range`() {
        withTestSettings { settings ->
            settings.putInt("windowOpacityPercent", 250)

            assertEquals(100, readPersistedWindowOpacityPercent(settings))
        }
    }

    @Test
    fun `persist window opacity stores the clamped value`() {
        withTestSettings { settings ->
            persistWindowOpacityPercent(settings, 30)

            assertEquals(50, settings.getInt("windowOpacityPercent", -1))
        }
    }

    @Test
    fun `clamp window opacity keeps values already inside the range`() {
        assertEquals(50, clampWindowOpacityPercent(50))
        assertEquals(83, clampWindowOpacityPercent(83))
        assertEquals(100, clampWindowOpacityPercent(100))
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

package com.usagemonitor

import com.russhwolf.settings.PreferencesSettings
import com.usagemonitor.domain.entity.DEFAULT_UI_SCALE_PERCENT
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertEquals

class UiScalePreferencesTest {

    @Test
    fun `read persisted ui scale falls back to the default when preference is absent`() {
        withTestSettings { settings ->
            assertEquals(115, readPersistedUiScalePercent(settings))
        }
    }

    @Test
    fun `persist and read ui scale round trips the stored value`() {
        withTestSettings { settings ->
            persistUiScalePercent(settings, 130)

            assertEquals(130, readPersistedUiScalePercent(settings))
        }
    }

    @Test
    fun `read persisted ui scale clamps values below the supported range`() {
        withTestSettings { settings ->
            settings.putInt("uiScalePercent", 20)

            assertEquals(80, readPersistedUiScalePercent(settings))
        }
    }

    @Test
    fun `read persisted ui scale clamps values above the supported range`() {
        withTestSettings { settings ->
            settings.putInt("uiScalePercent", 400)

            assertEquals(150, readPersistedUiScalePercent(settings))
        }
    }

    @Test
    fun `read persisted ui scale snaps a value off the step grid`() {
        withTestSettings { settings ->
            settings.putInt("uiScalePercent", 113)

            assertEquals(115, readPersistedUiScalePercent(settings))
        }
    }

    @Test
    fun `persist ui scale stores the clamped value`() {
        withTestSettings { settings ->
            persistUiScalePercent(settings, 1000)

            assertEquals(150, settings.getInt("uiScalePercent", -1))
        }
    }

    @Test
    fun `clamp ui scale keeps values already on the grid`() {
        assertEquals(80, clampUiScalePercent(80))
        assertEquals(115, clampUiScalePercent(115))
        assertEquals(150, clampUiScalePercent(150))
    }

    @Test
    fun `has persisted ui scale distinguishes never chosen from chose the default`() {
        withTestSettings { settings ->
            assertEquals(false, hasPersistedUiScale(settings))

            persistUiScalePercent(settings, DEFAULT_UI_SCALE_PERCENT)

            assertEquals(true, hasPersistedUiScale(settings))
        }
    }

    @Test
    fun `ui scale factor converts percent into a multiplier`() {
        assertEquals(1f, uiScaleFactor(100))
        assertEquals(1.15f, uiScaleFactor(115))
        assertEquals(1.5f, uiScaleFactor(150))
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

package com.usagemonitor

import com.russhwolf.settings.PreferencesSettings
import com.usagemonitor.presentation.ui.theme.AppThemePreset
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertEquals

class ThemePresetPreferencesTest {

    @Test
    fun `new preset round trips and updates legacy mode`() {
        withTestSettings { settings ->
            persistThemePreset(settings, AppThemePreset.AMEIXA_DARK)

            assertEquals(AppThemePreset.AMEIXA_DARK, readPersistedThemePreset(settings))
            assertEquals(true, settings.getBoolean(LEGACY_IS_DARK_KEY, false))
        }
    }

    @Test
    fun `legacy dark preference migrates to default dark preset`() {
        withTestSettings { settings ->
            settings.putBoolean(LEGACY_IS_DARK_KEY, true)

            assertEquals(AppThemePreset.OBSIDIANA_DARK, readPersistedThemePreset(settings))
        }
    }

    @Test
    fun `legacy light preference migrates to default light preset`() {
        withTestSettings { settings ->
            settings.putBoolean(LEGACY_IS_DARK_KEY, false)

            assertEquals(AppThemePreset.PORCELANA_LIGHT, readPersistedThemePreset(settings))
        }
    }

    @Test
    fun `invalid preset falls back to legacy mode`() {
        withTestSettings { settings ->
            settings.putString(THEME_PRESET_KEY, "does-not-exist")
            settings.putBoolean(LEGACY_IS_DARK_KEY, false)

            assertEquals(AppThemePreset.PORCELANA_LIGHT, readPersistedThemePreset(settings))
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

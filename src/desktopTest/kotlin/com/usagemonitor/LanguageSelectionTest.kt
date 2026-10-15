package com.usagemonitor

import com.russhwolf.settings.PreferencesSettings
import com.usagemonitor.domain.entity.AppLanguage
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LanguageSelectionTest {

    @Test
    fun `without override the saved choice rules`() {
        withTestSettings { settings ->
            settings.putString(LANGUAGE_KEY, AppLanguage.EN.name)

            assertEquals(AppLanguage.EN, LanguageSelection(settings, initialOverride = null).current())
        }
    }

    @Test
    fun `the override wins and is never saved`() {
        withTestSettings { settings ->
            settings.putString(LANGUAGE_KEY, AppLanguage.PT.name)
            val selection = LanguageSelection(settings, initialOverride = AppLanguage.EN)

            assertEquals(AppLanguage.EN, selection.current())
            assertEquals(AppLanguage.PT.name, settings.getStringOrNull(LANGUAGE_KEY))
        }
    }

    @Test
    fun `the override does not create a saved choice either`() {
        withTestSettings { settings ->
            LanguageSelection(settings, initialOverride = AppLanguage.EN).current()

            assertNull(settings.getStringOrNull(LANGUAGE_KEY))
        }
    }

    @Test
    fun `choosing a language saves it and ends the override`() {
        withTestSettings { settings ->
            settings.putString(LANGUAGE_KEY, AppLanguage.PT.name)
            val selection = LanguageSelection(settings, initialOverride = AppLanguage.EN)

            selection.choose(AppLanguage.PT)

            assertEquals(AppLanguage.PT, selection.current())
            assertEquals(AppLanguage.PT.name, settings.getStringOrNull(LANGUAGE_KEY))
        }
    }

    @Test
    fun `the shell starts in the override and hands the choice to the selection`() {
        withTestSettings { settings ->
            settings.putString(LANGUAGE_KEY, AppLanguage.PT.name)
            val selection = LanguageSelection(settings, initialOverride = AppLanguage.EN)
            val shell = AppShellState(settings, initialTargets = emptyList(), languageSelection = selection)

            assertEquals(AppLanguage.EN, shell.language)

            shell.changeLanguage(AppLanguage.EN)

            assertEquals(AppLanguage.EN, selection.current())
            assertEquals(AppLanguage.EN.name, settings.getStringOrNull(LANGUAGE_KEY))
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

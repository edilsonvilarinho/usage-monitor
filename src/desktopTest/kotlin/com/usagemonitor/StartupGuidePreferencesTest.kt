package com.usagemonitor

import com.russhwolf.settings.PreferencesSettings
import kotlinx.datetime.LocalDate
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StartupGuidePreferencesTest {

    @Test
    fun `nothing was completed and tips start on`() {
        withTestSettings { settings ->
            assertNull(readOnboardingCompletedVersion(settings))
            assertTrue(readStartupTipsEnabled(settings))
            assertNull(readStartupTipLastShownDate(settings))
            assertNull(readStartupTipNextIndex(settings))
        }
    }

    @Test
    fun `values round trip under the keys the app reads back`() {
        withTestSettings { settings ->
            persistOnboardingCompletedVersion(settings, "41.11.0")
            persistStartupTipsEnabled(settings, false)
            persistStartupTipLastShownDate(settings, LocalDate(2026, 10, 13))
            persistStartupTipNextIndex(settings, 4)

            assertEquals("41.11.0", readOnboardingCompletedVersion(settings))
            assertFalse(readStartupTipsEnabled(settings))
            assertEquals(LocalDate(2026, 10, 13), readStartupTipLastShownDate(settings))
            assertEquals(4, readStartupTipNextIndex(settings))
            assertEquals("2026-10-13", settings.getStringOrNull("startupTipLastShownDate"))
        }
    }

    @Test
    fun `an unreadable date counts as never shown`() {
        // Data ilegível não pode esconder a dica para sempre nem derrubar a abertura.
        withTestSettings { settings ->
            settings.putString("startupTipLastShownDate", "ontem")

            assertNull(readStartupTipLastShownDate(settings))
        }
    }

    @Test
    fun `a blank guide mark reads as never completed`() {
        withTestSettings { settings ->
            settings.putString("onboardingCompletedVersion", "  ")

            assertNull(readOnboardingCompletedVersion(settings))
        }
    }

    /** Nó descartável, removido no `finally`, como nos outros testes de preferência. */
    private fun withTestSettings(block: (PreferencesSettings) -> Unit) {
        val nodeName = "com.usagemonitor.tests.${UUID.randomUUID()}"
        val preferencesNode = Preferences.userRoot().node(nodeName)
        try {
            block(PreferencesSettings(preferencesNode))
        } finally {
            preferencesNode.removeNode()
        }
    }
}

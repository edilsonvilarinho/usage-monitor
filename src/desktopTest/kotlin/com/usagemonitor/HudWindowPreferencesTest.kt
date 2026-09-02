package com.usagemonitor

import com.russhwolf.settings.PreferencesSettings
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HudWindowPreferencesTest {

    @Test
    fun `read persisted hud position is absent before the pill is ever dragged`() {
        withTestSettings { settings ->
            assertNull(readPersistedHudPosition(settings))
        }
    }

    @Test
    fun `persist and read hud position round trips both axes`() {
        withTestSettings { settings ->
            persistHudPosition(settings, xDp = 1046f, yDp = 704f)

            assertEquals(PersistedHudPosition(xDp = 1046, yDp = 704), readPersistedHudPosition(settings))
        }
    }

    @Test
    fun `persist hud position rounds fractional coordinates`() {
        withTestSettings { settings ->
            persistHudPosition(settings, xDp = 1045.6f, yDp = 703.4f)

            assertEquals(PersistedHudPosition(xDp = 1046, yDp = 703), readPersistedHudPosition(settings))
        }
    }

    /**
     * `Dp.Unspecified` chega aqui como `NaN`. Arredondá-lo poria a janela em
     * zero na abertura seguinte — um canto que ninguém escolheu.
     */
    @Test
    fun `persist hud position ignores coordinates without measurement`() {
        withTestSettings { settings ->
            persistHudPosition(settings, xDp = Float.NaN, yDp = 704f)

            assertNull(readPersistedHudPosition(settings))
        }
    }

    @Test
    fun `persist hud position overwrites the previous corner`() {
        withTestSettings { settings ->
            persistHudPosition(settings, xDp = 1046f, yDp = 0f)
            persistHudPosition(settings, xDp = 0f, yDp = 704f)

            assertEquals(PersistedHudPosition(xDp = 0, yDp = 704), readPersistedHudPosition(settings))
        }
    }

    /** Meia posição não é posição: um eixo herdado e o outro default é um canto arbitrário. */
    @Test
    fun `half a stored position reads as absent`() {
        withTestSettings { settings ->
            settings.putString("hudWindowX", "1046")

            assertNull(readPersistedHudPosition(settings))
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

package com.usagemonitor

import com.russhwolf.settings.PreferencesSettings
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CardsOnlyModeMigrationTest {

    @Test
    fun `cards only turned on migrates to the hud and drops the key`() {
        withTestSettings { settings ->
            settings.putBoolean("cardsOnlyMode", true)

            migrateCardsOnlyModeToHud(settings)

            assertTrue(readPersistedHudMode(settings))
            assertFalse(settings.hasKey("cardsOnlyMode"))
        }
    }

    @Test
    fun `cards only turned off drops the key without touching the hud`() {
        withTestSettings { settings ->
            settings.putBoolean("cardsOnlyMode", false)

            migrateCardsOnlyModeToHud(settings)

            assertFalse(settings.hasKey("hudMode"))
            assertFalse(settings.hasKey("cardsOnlyMode"))
        }
    }

    /** Sem a chave, nada é gravado: a regra da instalação nova lê a ausência de `hudMode`. */
    @Test
    fun `without the key nothing is written`() {
        withTestSettings { settings ->
            migrateCardsOnlyModeToHud(settings)

            assertFalse(settings.hasKey("hudMode"))
        }
    }

    /** A migração vale uma vez: desligar a HUD depois não pode ser desfeito no arranque seguinte. */
    @Test
    fun `turning the hud off after the migration sticks`() {
        withTestSettings { settings ->
            settings.putBoolean("cardsOnlyMode", true)
            migrateCardsOnlyModeToHud(settings)
            persistHudMode(settings, false)

            migrateCardsOnlyModeToHud(settings)

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

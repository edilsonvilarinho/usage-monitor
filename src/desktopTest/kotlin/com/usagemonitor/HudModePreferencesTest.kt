package com.usagemonitor

import com.russhwolf.settings.PreferencesSettings
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    // ── HUD padrão na instalação nova (issue #277) ──────────────────────────

    @Test
    fun `fresh install marks the hud default as pending and stores the standard mode`() {
        withTestSettings { settings ->
            assertTrue(markHudDefaultPendingOnFreshInstall(settings, hasUpdateReceipt = false))
            assertFalse(readPersistedHudMode(settings))
            // Gravar o modo fecha a porta: a execução seguinte não é mais nova,
            // mas a pendência continua até a troca acontecer.
            assertFalse(isFreshInstall(settings, hasUpdateReceipt = false))
            assertTrue(markHudDefaultPendingOnFreshInstall(settings, hasUpdateReceipt = false))
        }
    }

    /** Quem já usa o app e nunca mexeu no modo não é arrastado para a HUD. */
    @Test
    fun `an existing install never gets the hud by default`() {
        withTestSettings { settings ->
            settings.putString("windowPlacement", "FLOATING")

            assertFalse(markHudDefaultPendingOnFreshInstall(settings, hasUpdateReceipt = false))
            assertFalse(readPersistedHudMode(settings))
        }
    }

    /** Atualização de uma versão que nunca gravou nada: o recibo diz que não é instalação nova. */
    @Test
    fun `an update receipt means an upgrade, not a fresh install`() {
        withTestSettings { settings ->
            assertFalse(markHudDefaultPendingOnFreshInstall(settings, hasUpdateReceipt = true))
        }
    }

    @Test
    fun `clearing the pending switch keeps it cleared`() {
        withTestSettings { settings ->
            markHudDefaultPendingOnFreshInstall(settings, hasUpdateReceipt = false)
            clearHudDefaultPending(settings)

            assertFalse(markHudDefaultPendingOnFreshInstall(settings, hasUpdateReceipt = false))
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

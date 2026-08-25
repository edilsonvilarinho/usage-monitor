package com.usagemonitor

import com.russhwolf.settings.PreferencesSettings
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReleaseNotesPreferencesTest {

    /**
     * Sem marca nenhuma, a janela precisa poder abrir: quem instalou o app
     * agora e atualizou em seguida nunca viu novidade nenhuma.
     */
    @Test
    fun `no version was seen when the preference was never written`() {
        withTestSettings { settings ->
            assertNull(readPersistedReleaseNotesSeenVersion(settings))
        }
    }

    @Test
    fun `persist and read round trip the version`() {
        withTestSettings { settings ->
            persistReleaseNotesSeenVersion(settings, "39.0.0")

            assertEquals("39.0.0", readPersistedReleaseNotesSeenVersion(settings))
        }
    }

    @Test
    fun `a blank stored value reads as nothing seen`() {
        // Marca em branco não identifica versão nenhuma, e tratá-la como vista
        // esconderia as novidades da versão corrente para sempre.
        withTestSettings { settings ->
            settings.putString("releaseNotesSeenVersion", "   ")

            assertNull(readPersistedReleaseNotesSeenVersion(settings))
        }
    }

    @Test
    fun `the stored key is the one the app reads back`() {
        withTestSettings { settings ->
            persistReleaseNotesSeenVersion(settings, "39.0.0")

            assertEquals("39.0.0", settings.getStringOrNull("releaseNotesSeenVersion"))
        }
    }

    /**
     * Nó descartável e removido no `finally`: o mesmo desenho dos outros testes
     * de preferência do projeto, que não sujam o registro de quem roda a suíte.
     */
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

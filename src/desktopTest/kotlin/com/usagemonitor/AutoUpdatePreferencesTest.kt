package com.usagemonitor

import com.russhwolf.settings.PreferencesSettings
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutoUpdatePreferencesTest {

    /**
     * O default é o contrato desta preferência: são ~120 MB por versão e uma
     * substituição de arquivos sem confirmação no fechamento da janela. Um app
     * que passasse a fazer isso porque foi atualizado teria ligado a
     * funcionalidade em nome do usuário.
     */
    @Test
    fun `automatic updates are off when the preference was never written`() {
        withTestSettings { settings ->
            assertFalse(readPersistedAutoUpdateEnabled(settings))
        }
    }

    @Test
    fun `persist and read round trips both values`() {
        withTestSettings { settings ->
            persistAutoUpdateEnabled(settings, true)
            assertTrue(readPersistedAutoUpdateEnabled(settings))

            persistAutoUpdateEnabled(settings, false)
            assertFalse(readPersistedAutoUpdateEnabled(settings))
        }
    }

    @Test
    fun `the stored key is the one the app reads back`() {
        withTestSettings { settings ->
            persistAutoUpdateEnabled(settings, true)

            assertEquals(true, settings.getBoolean("autoUpdateEnabled", false))
        }
    }

    /**
     * Nó descartável e removido no `finally`: o mesmo desenho dos outros testes
     * de preferência do projeto, que não sujam o registro de quem roda a suíte.
     *
     * Sexta cópia deste helper em `desktopTest` — extraí-lo para um arquivo de
     * apoio é uma limpeza legítima, e de propósito **não** entra neste commit,
     * que é sobre a preferência de atualização.
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

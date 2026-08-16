package com.usagemonitor

import androidx.compose.ui.window.WindowPlacement
import com.russhwolf.settings.PreferencesSettings
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TeamPresenceWindowPreferencesTest {

    @Test
    fun `sem preferencias gravadas a geometria cai nos defaults`() {
        withTestSettings { settings ->
            val state = readPersistedTeamPresenceWindowState(settings)

            assertEquals(null, state.widthDp)
            assertEquals(null, state.heightDp)
            assertEquals(null, state.xDp)
            assertEquals(null, state.yDp)
            assertEquals(PersistedWindowPlacement.FLOATING, state.placement)
        }
    }

    @Test
    fun `tamanho e posicao sobrevivem ao round trip`() {
        withTestSettings { settings ->
            persistTeamPresenceWindowState(
                settings = settings,
                snapshot = TeamPresenceWindowSnapshot(
                    widthDp = 880f,
                    heightDp = 640f,
                    xDp = 210f,
                    yDp = 130f,
                    placement = WindowPlacement.Floating
                )
            )

            val state = readPersistedTeamPresenceWindowState(settings)

            assertEquals(880, state.widthDp)
            assertEquals(640, state.heightDp)
            assertEquals(210, state.xDp)
            assertEquals(130, state.yDp)
            assertEquals(PersistedWindowPlacement.FLOATING, state.placement)
        }
    }

    @Test
    fun `maximizada preserva os limites flutuantes anteriores`() {
        withTestSettings { settings ->
            persistTeamPresenceWindowState(
                settings = settings,
                snapshot = TeamPresenceWindowSnapshot(
                    widthDp = 900f,
                    heightDp = 660f,
                    xDp = 64f,
                    yDp = 48f,
                    placement = WindowPlacement.Floating
                )
            )

            persistTeamPresenceWindowState(
                settings = settings,
                snapshot = TeamPresenceWindowSnapshot(
                    widthDp = 1920f,
                    heightDp = 1080f,
                    xDp = 0f,
                    yDp = 0f,
                    placement = WindowPlacement.Maximized
                )
            )

            // Guardar a área da tela inteira faria a janela restaurada nascer do
            // tamanho do monitor.
            val state = readPersistedTeamPresenceWindowState(settings)
            assertEquals(900, state.widthDp)
            assertEquals(660, state.heightDp)
            assertEquals(PersistedWindowPlacement.MAXIMIZED, state.placement)
        }
    }

    @Test
    fun `as chaves sao proprias e nao colidem com as da janela de consumo`() {
        withTestSettings { settings ->
            persistTeamPresenceWindowState(
                settings = settings,
                snapshot = TeamPresenceWindowSnapshot(
                    widthDp = 880f,
                    heightDp = 640f,
                    xDp = 210f,
                    yDp = 130f,
                    placement = WindowPlacement.Floating
                )
            )

            assertEquals("880", settings.getStringOrNull("teamPresenceWindowWidth"))
            // Chaves compartilhadas fariam uma janela nascer em cima da outra.
            assertNull(settings.getStringOrNull("teamUsageWindowWidth"))
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

package com.usagemonitor

import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import com.russhwolf.settings.PreferencesSettings
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A geometria gravada das quatro janelas redimensionáveis, por uma implementação
 * só. As chaves gravadas são contrato: mudar uma faria quem já usa o app perder
 * onde deixou a janela.
 */
class WindowGeometryPreferencesTest {

    private val specs = listOf(
        HISTORY_WINDOW_GEOMETRY,
        CLI_SESSIONS_WINDOW_GEOMETRY,
        TEAM_USAGE_WINDOW_GEOMETRY,
        TEAM_PRESENCE_WINDOW_GEOMETRY
    )

    @Test
    fun `stored keys keep the names each window always used`() {
        assertEquals(
            listOf(
                listOf("historyWindowWidth", "historyWindowHeight", "historyWindowX", "historyWindowY", "historyWindowPlacement"),
                listOf("cliSessionsWindowWidth", "cliSessionsWindowHeight", "cliSessionsWindowX", "cliSessionsWindowY", "cliSessionsWindowPlacement"),
                listOf("teamUsageWindowWidth", "teamUsageWindowHeight", "teamUsageWindowX", "teamUsageWindowY", "teamUsageWindowPlacement"),
                listOf("teamPresenceWindowWidth", "teamPresenceWindowHeight", "teamPresenceWindowX", "teamPresenceWindowY", "teamPresenceWindowPlacement")
            ),
            specs.map { spec -> listOf(spec.widthKey, spec.heightKey, spec.xKey, spec.yKey, spec.placementKey) }
        )
        // Tamanhos padrão de cada janela, os mesmos de antes da unificação.
        assertEquals(listOf(860 to 760, 960 to 780, 960 to 780, 1_030 to 620), specs.map { it.defaultWidthDp to it.defaultHeightDp })
    }

    @Test
    fun `nothing stored falls back to defaults`() = withTestSettings { settings ->
        for (spec in specs) {
            assertEquals(PersistedWindowGeometry(), readPersistedWindowGeometry(settings, spec))
        }
    }

    @Test
    fun `floating size and position survive a round trip`() = withTestSettings { settings ->
        persistWindowGeometry(settings, TEAM_PRESENCE_WINDOW_GEOMETRY, WindowGeometrySnapshot(972.4f, 711.6f, 128f, -96f, WindowPlacement.Floating))

        val state = readPersistedWindowGeometry(settings, TEAM_PRESENCE_WINDOW_GEOMETRY)

        assertEquals(PersistedWindowGeometry(972, 712, 128, -96, PersistedWindowPlacement.FLOATING), state)
        assertEquals(WindowPlacement.Floating, state.composePlacement)
    }

    @Test
    fun `maximized keeps the previous floating bounds`() = withTestSettings { settings ->
        persistWindowGeometry(settings, HISTORY_WINDOW_GEOMETRY, WindowGeometrySnapshot(1040f, 810f, 240f, 120f, WindowPlacement.Floating))
        persistWindowGeometry(settings, HISTORY_WINDOW_GEOMETRY, WindowGeometrySnapshot(1600f, 1200f, 0f, 0f, WindowPlacement.Maximized))

        assertEquals("1040", settings.getStringOrNull("historyWindowWidth"))
        assertEquals("810", settings.getStringOrNull("historyWindowHeight"))
        assertEquals("240", settings.getStringOrNull("historyWindowX"))
        assertEquals("120", settings.getStringOrNull("historyWindowY"))
        assertEquals("MAXIMIZED", settings.getStringOrNull("historyWindowPlacement"))
        assertEquals(WindowPlacement.Maximized, readPersistedWindowGeometry(settings, HISTORY_WINDOW_GEOMETRY).composePlacement)
    }

    @Test
    fun `windows never inherit each other's position`() = withTestSettings { settings ->
        persistWindowGeometry(settings, TEAM_USAGE_WINDOW_GEOMETRY, WindowGeometrySnapshot(900f, 700f, 10f, 20f, WindowPlacement.Floating))

        assertEquals(PersistedWindowGeometry(), readPersistedWindowGeometry(settings, CLI_SESSIONS_WINDOW_GEOMETRY))
        assertEquals(PersistedWindowGeometry(), readPersistedWindowGeometry(settings, TEAM_PRESENCE_WINDOW_GEOMETRY))
    }

    @Test
    fun `invalid stored values are ignored`() = withTestSettings { settings ->
        settings.putString("cliSessionsWindowWidth", "0")
        settings.putString("cliSessionsWindowHeight", "-5")
        settings.putString("cliSessionsWindowX", "abc")
        settings.putString("cliSessionsWindowPlacement", "FULLSCREEN")

        assertEquals(PersistedWindowGeometry(), readPersistedWindowGeometry(settings, CLI_SESSIONS_WINDOW_GEOMETRY))
    }

    @Test
    fun `non finite sizes and an unset position are not written`() = withTestSettings { settings ->
        persistWindowGeometry(settings, TEAM_USAGE_WINDOW_GEOMETRY, WindowGeometrySnapshot(Float.NaN, Float.POSITIVE_INFINITY, null, null, WindowPlacement.Floating))

        assertNull(settings.getStringOrNull("teamUsageWindowWidth"))
        assertNull(settings.getStringOrNull("teamUsageWindowHeight"))
        assertNull(settings.getStringOrNull("teamUsageWindowX"))
        assertEquals("FLOATING", settings.getStringOrNull("teamUsageWindowPlacement"))
    }

    @Test
    fun `window frame becomes a snapshot, with no position when unspecified`() {
        val placed = windowGeometrySnapshotOf(WindowPosition(30.dp, 40.dp), DpSize(800.dp, 600.dp), WindowPlacement.Floating)
        val centered = windowGeometrySnapshotOf(WindowPosition(Alignment.Center), DpSize(800.dp, 600.dp), WindowPlacement.Maximized)

        assertEquals(WindowGeometrySnapshot(800f, 600f, 30f, 40f, WindowPlacement.Floating), placed)
        assertEquals(WindowGeometrySnapshot(800f, 600f, null, null, WindowPlacement.Maximized), centered)
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

package com.usagemonitor

import androidx.compose.ui.window.WindowPlacement
import com.russhwolf.settings.PreferencesSettings
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertEquals

class HistoryWindowPreferencesTest {

    @Test
    fun `read persisted history window state uses defaults when preferences are absent`() {
        withTestSettings { settings ->
            val state = readPersistedHistoryWindowState(settings)

            assertEquals(null, state.widthDp)
            assertEquals(null, state.heightDp)
            assertEquals(null, state.xDp)
            assertEquals(null, state.yDp)
            assertEquals(PersistedWindowPlacement.FLOATING, state.placement)
        }
    }

    @Test
    fun `read persisted history window state restores floating size and position`() {
        withTestSettings { settings ->
            persistHistoryWindowState(
                settings = settings,
                snapshot = HistoryWindowSnapshot(
                    widthDp = 972f,
                    heightDp = 711f,
                    xDp = 128f,
                    yDp = 96f,
                    placement = WindowPlacement.Floating
                )
            )

            val state = readPersistedHistoryWindowState(settings)

            assertEquals(972, state.widthDp)
            assertEquals(711, state.heightDp)
            assertEquals(128, state.xDp)
            assertEquals(96, state.yDp)
            assertEquals(PersistedWindowPlacement.FLOATING, state.placement)
        }
    }

    @Test
    fun `read persisted history window state restores maximized placement`() {
        withTestSettings { settings ->
            persistHistoryWindowState(
                settings = settings,
                snapshot = HistoryWindowSnapshot(
                    widthDp = 860f,
                    heightDp = 760f,
                    xDp = 44f,
                    yDp = 55f,
                    placement = WindowPlacement.Floating
                )
            )
            persistHistoryWindowState(
                settings = settings,
                snapshot = HistoryWindowSnapshot(
                    widthDp = 1920f,
                    heightDp = 1080f,
                    xDp = 0f,
                    yDp = 0f,
                    placement = WindowPlacement.Maximized
                )
            )

            val state = readPersistedHistoryWindowState(settings)

            assertEquals(860, state.widthDp)
            assertEquals(760, state.heightDp)
            assertEquals(44, state.xDp)
            assertEquals(55, state.yDp)
            assertEquals(PersistedWindowPlacement.MAXIMIZED, state.placement)
        }
    }

    @Test
    fun `persist history window state keeps previous floating bounds when maximized`() {
        withTestSettings { settings ->
            persistHistoryWindowState(
                settings = settings,
                snapshot = HistoryWindowSnapshot(
                    widthDp = 1040f,
                    heightDp = 810f,
                    xDp = 240f,
                    yDp = 120f,
                    placement = WindowPlacement.Floating
                )
            )

            persistHistoryWindowState(
                settings = settings,
                snapshot = HistoryWindowSnapshot(
                    widthDp = 1600f,
                    heightDp = 1200f,
                    xDp = 0f,
                    yDp = 0f,
                    placement = WindowPlacement.Maximized
                )
            )

            assertEquals("1040", settings.getStringOrNull("historyWindowWidth"))
            assertEquals("810", settings.getStringOrNull("historyWindowHeight"))
            assertEquals("240", settings.getStringOrNull("historyWindowX"))
            assertEquals("120", settings.getStringOrNull("historyWindowY"))
            assertEquals("MAXIMIZED", settings.getStringOrNull("historyWindowPlacement"))
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

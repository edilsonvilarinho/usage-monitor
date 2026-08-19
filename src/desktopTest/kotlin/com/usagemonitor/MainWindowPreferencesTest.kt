package com.usagemonitor

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import com.russhwolf.settings.PreferencesSettings
import java.util.UUID
import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MainWindowPreferencesTest {

    @Test
    fun `read persisted main window state uses defaults when preferences are absent`() {
        withTestSettings { settings ->
            val state = readPersistedMainWindowState(settings)

            assertEquals(null, state.widthDp)
            assertEquals(null, state.heightDp)
            assertEquals(PersistedWindowPlacement.FLOATING, state.placement)
        }
    }

    @Test
    fun `read persisted main window state restores floating size`() {
        withTestSettings { settings ->
            persistMainWindowState(
                settings = settings,
                snapshot = MainWindowSnapshot(
                    widthDp = 372f,
                    heightDp = 1024f,
                    placement = WindowPlacement.Floating
                )
            )

            val state = readPersistedMainWindowState(settings)

            assertEquals(372, state.widthDp)
            assertEquals(1024, state.heightDp)
            assertEquals(PersistedWindowPlacement.FLOATING, state.placement)
        }
    }

    @Test
    fun `read persisted main window state restores maximized placement`() {
        withTestSettings { settings ->
            persistMainWindowState(
                settings = settings,
                snapshot = MainWindowSnapshot(
                    widthDp = 480f,
                    heightDp = 900f,
                    placement = WindowPlacement.Floating
                )
            )
            persistMainWindowState(
                settings = settings,
                snapshot = MainWindowSnapshot(
                    widthDp = 1920f,
                    heightDp = 1080f,
                    placement = WindowPlacement.Maximized
                )
            )

            val state = readPersistedMainWindowState(settings)

            assertEquals(480, state.widthDp)
            assertEquals(900, state.heightDp)
            assertEquals(PersistedWindowPlacement.MAXIMIZED, state.placement)
        }
    }

    @Test
    fun `persist main window state updates floating size and placement`() {
        withTestSettings { settings ->
            persistMainWindowState(
                settings = settings,
                snapshot = MainWindowSnapshot(
                    widthDp = 361.6f,
                    heightDp = 777.2f,
                    placement = WindowPlacement.Floating
                )
            )

            assertEquals("362", settings.getStringOrNull("windowWidth"))
            assertEquals("777", settings.getStringOrNull("windowHeight"))
            assertEquals("FLOATING", settings.getStringOrNull("windowPlacement"))
        }
    }

    @Test
    fun `persist main window state keeps previous floating size when maximized`() {
        withTestSettings { settings ->
            persistMainWindowState(
                settings = settings,
                snapshot = MainWindowSnapshot(
                    widthDp = 420f,
                    heightDp = 840f,
                    placement = WindowPlacement.Floating
                )
            )

            persistMainWindowState(
                settings = settings,
                snapshot = MainWindowSnapshot(
                    widthDp = 1600f,
                    heightDp = 1200f,
                    placement = WindowPlacement.Maximized
                )
            )

            assertEquals("420", settings.getStringOrNull("windowWidth"))
            assertEquals("840", settings.getStringOrNull("windowHeight"))
            assertEquals("MAXIMIZED", settings.getStringOrNull("windowPlacement"))
        }
    }

    @Test
    fun `scaled window size applies the ratio between the two scales`() {
        val scaled = scaledWindowSize(
            current = DpSize(400.dp, 800.dp),
            fromPercent = 100,
            toPercent = 150,
            maxSize = DpSize(4000.dp, 4000.dp)
        )

        assertEquals(600.dp, scaled.width)
        assertEquals(1200.dp, scaled.height)
    }

    @Test
    fun `scaled window size shrinks when the new scale is smaller`() {
        val scaled = scaledWindowSize(
            current = DpSize(600.dp, 1200.dp),
            fromPercent = 150,
            toPercent = 100,
            maxSize = DpSize(4000.dp, 4000.dp)
        )

        assertEquals(400.dp, scaled.width)
        assertEquals(800.dp, scaled.height)
    }

    @Test
    fun `scaled window size returns the current size when the scale did not change`() {
        val current = DpSize(400.dp, 800.dp)

        assertEquals(
            current,
            scaledWindowSize(current, fromPercent = 115, toPercent = 115, maxSize = DpSize(4000.dp, 4000.dp))
        )
    }

    @Test
    fun `scaled window size never exceeds the available screen area`() {
        val scaled = scaledWindowSize(
            current = DpSize(1000.dp, 900.dp),
            fromPercent = 100,
            toPercent = 150,
            maxSize = DpSize(1280.dp, 1000.dp)
        )

        assertEquals(1280.dp, scaled.width)
        assertEquals(1000.dp, scaled.height)
    }

    @Test
    fun `scaled window size keeps unspecified dimensions untouched`() {
        val scaled = scaledWindowSize(
            current = DpSize(Dp.Unspecified, 800.dp),
            fromPercent = 100,
            toPercent = 150,
            maxSize = DpSize(Dp.Unspecified, Dp.Unspecified)
        )

        assertTrue(scaled.width.value.isNaN())
        assertEquals(1200.dp, scaled.height)
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

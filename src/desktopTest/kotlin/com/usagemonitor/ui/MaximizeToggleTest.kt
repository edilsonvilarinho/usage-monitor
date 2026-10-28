package com.usagemonitor.ui

import androidx.compose.ui.window.WindowPlacement
import com.usagemonitor.presentation.ui.maximizeButtonLabel
import com.usagemonitor.presentation.ui.maximizeToggled
import kotlin.test.Test
import kotlin.test.assertEquals

/** O botão de maximizar da barra de título das janelas, sem janela (regra de `DesktopWindowFrame`). */
class MaximizeToggleTest {

    @Test
    fun `maximize restores a maximized window and maximizes any other`() {
        assertEquals(WindowPlacement.Floating, maximizeToggled(WindowPlacement.Maximized))
        assertEquals(WindowPlacement.Maximized, maximizeToggled(WindowPlacement.Floating))
        assertEquals(WindowPlacement.Maximized, maximizeToggled(WindowPlacement.Fullscreen))
    }

    @Test
    fun `the glyph says restore while maximized and maximize otherwise`() {
        assertEquals("❐", maximizeButtonLabel(WindowPlacement.Maximized))
        assertEquals("□", maximizeButtonLabel(WindowPlacement.Floating))
    }
}

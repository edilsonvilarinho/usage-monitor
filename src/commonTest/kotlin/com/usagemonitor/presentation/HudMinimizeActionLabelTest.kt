package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.hudMinimizeActionLabel
import kotlin.test.Test
import kotlin.test.assertEquals

class HudMinimizeActionLabelTest {
    @Test
    fun `conta visivel oferece minimizar em portugues e ingles`() {
        assertEquals("Minimizar", hudMinimizeActionLabel(minimized = false, language = AppLanguage.PT))
        assertEquals("Minimize", hudMinimizeActionLabel(minimized = false, language = AppLanguage.EN))
    }

    @Test
    fun `conta minimizada oferece restaurar em portugues e ingles`() {
        assertEquals("Restaurar", hudMinimizeActionLabel(minimized = true, language = AppLanguage.PT))
        assertEquals("Restore", hudMinimizeActionLabel(minimized = true, language = AppLanguage.EN))
    }
}

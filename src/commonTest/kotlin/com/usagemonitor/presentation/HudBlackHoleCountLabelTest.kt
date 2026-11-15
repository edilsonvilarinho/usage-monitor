package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.hudBlackHoleCountLabel
import kotlin.test.Test
import kotlin.test.assertEquals

class HudBlackHoleCountLabelTest {
    @Test
    fun `contagem singular e plural em portugues`() {
        assertEquals("1 minimizada", hudBlackHoleCountLabel(1, AppLanguage.PT))
        assertEquals("4 minimizadas", hudBlackHoleCountLabel(4, AppLanguage.PT))
    }

    @Test
    fun `contagem singular e plural em ingles`() {
        assertEquals("1 minimized", hudBlackHoleCountLabel(1, AppLanguage.EN))
        assertEquals("4 minimized", hudBlackHoleCountLabel(4, AppLanguage.EN))
    }
}

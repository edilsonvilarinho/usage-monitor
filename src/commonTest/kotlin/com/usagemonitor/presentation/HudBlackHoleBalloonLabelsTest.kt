package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.hudBlackHoleAutoBadge
import com.usagemonitor.presentation.ui.hudBlackHoleAutoNote
import com.usagemonitor.presentation.ui.hudBlackHoleBackLabel
import kotlin.test.Test
import kotlin.test.assertEquals

/** Rótulos do balão do buraco negro (#600, A05): volta, selo auto e texto da auto, em PT e EN. */
class HudBlackHoleBalloonLabelsTest {

    @Test
    fun `volta ao buraco com a contagem em portugues e ingles`() {
        assertEquals("← 4 no buraco", hudBlackHoleBackLabel(4, AppLanguage.PT))
        assertEquals("← 4 in the black hole", hudBlackHoleBackLabel(4, AppLanguage.EN))
    }

    @Test
    fun `selo auto e texto da auto em portugues e ingles`() {
        assertEquals("auto", hudBlackHoleAutoBadge(AppLanguage.PT))
        assertEquals("auto", hudBlackHoleAutoBadge(AppLanguage.EN))
        assertEquals("volta sozinha quando couber", hudBlackHoleAutoNote(AppLanguage.PT))
        assertEquals("returns on its own when there is room", hudBlackHoleAutoNote(AppLanguage.EN))
    }
}

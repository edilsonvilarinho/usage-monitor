package com.usagemonitor.screenshots

import kotlin.test.Test
import kotlin.test.assertEquals

/** Fixa o ritmo das demos de vídeo (#633): o dono pediu mais lento e sem corte. */
class VideoDemoPaceTest {

    @Test
    fun `o ritmo das demos de video e o combinado com o dono`() {
        assertEquals(2_500L, VideoDemoPace.INTRO_HOLD)
        assertEquals(1_400L, VideoDemoPace.CURSOR_TRAVEL)
        assertEquals(900L, VideoDemoPace.DWELL_BEFORE_CLICK)
        assertEquals(2_500L, VideoDemoPace.STEP_HOLD)
        assertEquals(3_000L, VideoDemoPace.OUTRO_HOLD)
        assertEquals(1_600L, VideoDemoPace.MIN_ANIMATION)
        assertEquals(48, VideoDemoPace.SCENE_MARGIN_DP)
    }
}

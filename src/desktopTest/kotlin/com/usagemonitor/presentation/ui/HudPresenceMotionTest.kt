package com.usagemonitor.presentation.ui

import com.usagemonitor.presentation.ui.theme.AppGargantuaTokens
import kotlin.test.Test
import kotlin.test.assertEquals

/** Quanto cada conta da HUD espera antes de a presença dela assentar. */
class HudPresenceMotionTest {

    @Test
    fun `a shown account has nothing to wait for`() {
        assertEquals(0, hudPresenceMillis(HudPresence.SHOWN, birthOrder = 3))
    }

    @Test
    fun `an entering account waits for its place in the birth queue and then for the birth`() {
        assertEquals(AppGargantuaTokens.birthMillis, hudPresenceMillis(HudPresence.ENTERING, birthOrder = 0))
        assertEquals(2 * HUD_BIRTH_STAGGER_MILLIS + AppGargantuaTokens.birthMillis, hudPresenceMillis(HudPresence.ENTERING, birthOrder = 2))
        // Sem lugar na fila nasce junto com a primeira.
        assertEquals(AppGargantuaTokens.birthMillis, hudPresenceMillis(HudPresence.ENTERING, birthOrder = null))
    }

    @Test
    fun `a leaving account stays until the collapse and the closing gap are over`() {
        assertEquals(
            AppGargantuaTokens.collapseMillis + AppGargantuaTokens.departureSettleMillis,
            hudPresenceMillis(HudPresence.LEAVING, birthOrder = null)
        )
    }
}

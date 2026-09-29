package com.usagemonitor.presentation.ui.components

import com.usagemonitor.presentation.ui.theme.AppMotionPolicy
import kotlin.test.Test
import kotlin.test.assertEquals

/** A onda final da coleta (R1) toca só no fim dela e nunca com "Reduzir animações". */
class GargantuaRefreshWaveTest {
    @Test
    fun `a onda toca quando a coleta termina`() {
        assertEquals(true, shouldPlayRefreshWave(wasRefreshing = true, refreshing = false, policy = AppMotionPolicy.Live))
        assertEquals(true, shouldPlayRefreshWave(wasRefreshing = true, refreshing = false, policy = AppMotionPolicy.Static))
    }

    @Test
    fun `nem no inicio da coleta nem sem coleta`() {
        assertEquals(false, shouldPlayRefreshWave(wasRefreshing = false, refreshing = true, policy = AppMotionPolicy.Live))
        assertEquals(false, shouldPlayRefreshWave(wasRefreshing = false, refreshing = false, policy = AppMotionPolicy.Live))
    }

    @Test
    fun `com reduzir animacoes nao ha onda`() {
        assertEquals(false, shouldPlayRefreshWave(wasRefreshing = true, refreshing = false, policy = AppMotionPolicy.Reduced))
    }
}

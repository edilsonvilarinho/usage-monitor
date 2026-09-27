package com.usagemonitor.ui

import com.usagemonitor.presentation.ui.shouldPulseProviderMark
import com.usagemonitor.presentation.ui.theme.AppMotionPolicy
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * O pulso da marca do fornecedor (issue #322) só no fim da coleta da conta, e
 * nunca com "Reduzir animações".
 */
class HudMarkPulseTest {

    @Test
    fun `a marca pulsa quando a coleta termina`() {
        assertEquals(true, shouldPulseProviderMark(wasRefreshing = true, refreshing = false, policy = AppMotionPolicy.Live))
        assertEquals(true, shouldPulseProviderMark(wasRefreshing = true, refreshing = false, policy = AppMotionPolicy.Static))
    }

    @Test
    fun `nem no inicio da coleta nem sem coleta`() {
        assertEquals(false, shouldPulseProviderMark(wasRefreshing = false, refreshing = true, policy = AppMotionPolicy.Live))
        assertEquals(false, shouldPulseProviderMark(wasRefreshing = false, refreshing = false, policy = AppMotionPolicy.Live))
    }

    @Test
    fun `com reduzir animacoes nao ha pulso`() {
        assertEquals(false, shouldPulseProviderMark(wasRefreshing = true, refreshing = false, policy = AppMotionPolicy.Reduced))
    }
}

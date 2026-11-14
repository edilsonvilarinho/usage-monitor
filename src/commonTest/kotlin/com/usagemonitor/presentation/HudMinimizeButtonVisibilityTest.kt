package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.presentation.ui.HudAccount
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.hudShowsMinimizeButton
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Botão de minimizar/restaurar no balão da HUD (#596): some na conta auto-minimizada. */
class HudMinimizeButtonVisibilityTest {

    @Test
    fun `conta auto-minimizada nao mostra o botao de restaurar`() {
        assertFalse(hudShowsMinimizeButton(conta(minimized = true, autoMinimized = true)))
    }

    @Test
    fun `conta minimizada pelo usuario mostra o botao de restaurar`() {
        assertTrue(hudShowsMinimizeButton(conta(minimized = true, autoMinimized = false)))
    }

    @Test
    fun `conta completa mostra o botao de minimizar`() {
        assertTrue(hudShowsMinimizeButton(conta(minimized = false, autoMinimized = false)))
    }

    private fun conta(minimized: Boolean, autoMinimized: Boolean): HudAccount = HudAccount(
        targetKey = UsageTargetKey(ApiSource.ANTHROPIC, "A"),
        label = "A",
        statusLabel = "Em dia",
        tone = AppTone.OK,
        quotas = emptyList(),
        focusIndex = 0,
        minimized = minimized,
        autoMinimized = autoMinimized
    )
}

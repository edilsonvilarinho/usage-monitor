package com.usagemonitor

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.AppUpdateInfo
import com.usagemonitor.presentation.ui.HudUpdateIndicator
import com.usagemonitor.presentation.ui.updateBannerContent
import com.usagemonitor.presentation.viewmodel.AppUpdateUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** As decisões da janela da HUD que não dependem da janela: aviso de atualização, tecla, texto e escala. */
class HudWindowDecisionsTest {

    private val update = AppUpdateInfo(
        version = "42.0.0",
        releasePageUrl = "https://github.com/edilsonvilarinho/usage-monitor/releases/tag/v42.0.0"
    )

    /** O ponto da engrenagem e o banner do balão dizem o que a faixa diria, com o mesmo tom (#225, #291). */
    @Test
    fun `the gear update dot says what the update banner says`() {
        val states = listOf(AppUpdateUiState.Available(update), AppUpdateUiState.Downloading(update, 42))
        for (language in AppLanguage.entries) {
            for (state in states) {
                val content = updateBannerContent(state = state, language = language)

                assertEquals(
                    HudUpdateIndicator(
                        tone = content.tone,
                        description = content.title,
                        actionLabel = content.actionLabel,
                        headline = content.headline,
                        detail = content.detail
                    ),
                    hudUpdateIndicatorOf(state, language)
                )
            }
        }
    }

    @Test
    fun `F1 pressed opens the help and is consumed`() {
        var opened = 0

        assertTrue(handleHudWindowKey(KeyEventType.KeyDown, Key.F1) { opened++ })
        assertEquals(1, opened)
    }

    /** Soltar o F1 não reabre a ajuda, e as outras teclas seguem para a composição. */
    @Test
    fun `releasing F1 or pressing another key does not open the help`() {
        var opened = 0

        assertFalse(handleHudWindowKey(KeyEventType.KeyUp, Key.F1) { opened++ })
        assertFalse(handleHudWindowKey(KeyEventType.KeyDown, Key.Escape) { opened++ })
        assertEquals(0, opened)
    }

    @Test
    fun `the gear names the app actions to the screen reader in each language`() {
        assertEquals("Ações do Usage Monitor", hudGearDescription(AppLanguage.PT))
        assertEquals("Usage Monitor actions", hudGearDescription(AppLanguage.EN))
        assertNotEquals(hudGearDescription(AppLanguage.PT), hudGearDescription(AppLanguage.EN))
    }

    /** A geometria trabalha em dp de composição: a área do monitor desce à escala da interface. */
    @Test
    fun `the screen work area is brought down to the composition scale`() {
        val area = ScreenWorkArea(x = 1920.dp, y = 40.dp, size = DpSize(2560.dp, 1400.dp))

        assertEquals(ScreenWorkArea(x = 960.dp, y = 20.dp, size = DpSize(1280.dp, 700.dp)), area.inCompositionDp(2f))
        assertEquals(area, area.inCompositionDp(1f))
    }
}

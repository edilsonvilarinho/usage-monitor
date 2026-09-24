package com.usagemonitor.presentation.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertTrue

class AppSurfaceLadderTest {

    /**
     * O hover tem de ser **visto**. O defeito que abriu esta regra era o card indo
     * de `#1B1818` a `#211E1E` no hover — 1,05:1, que ninguém enxerga. A camada
     * somada precisa abrir pelo menos [MIN_STATE_STEP] em todos os 26 presets.
     */
    @Test
    fun `hover e pressao sao distinguiveis da superficie em todo preset`() {
        for (preset in AppThemePreset.entries) {
            val ladder = AppSurfaceLadder.of(preset)
            val hovered = ladder.hoverLayer.compositeOver(preset.surface)
            val pressed = ladder.pressedLayer.compositeOver(preset.surface)
            assertTrue(
                contrastRatio(hovered, preset.surface) >= MIN_STATE_STEP,
                "Hover invisível no preset ${preset.name}: ${contrastRatio(hovered, preset.surface)}"
            )
            assertTrue(
                contrastRatio(pressed, preset.surface) > contrastRatio(hovered, preset.surface),
                "Pressão não passa do hover no preset ${preset.name}."
            )
        }
    }

    /**
     * A camada não pode custar legibilidade: texto sobre linha pressionada
     * continua no mesmo piso que `AppThemePresetTest` exige sobre a superfície.
     */
    @Test
    fun `texto continua legivel sobre a camada de pressao`() {
        for (preset in AppThemePreset.entries) {
            val pressed = AppSurfaceLadder.of(preset).pressedLayer.compositeOver(preset.surface)
            assertTrue(
                contrastRatio(preset.foreground, pressed) >= 4.5,
                "Texto principal ilegível sobre a pressão no preset ${preset.name}."
            )
            assertTrue(
                contrastRatio(preset.muted, pressed) >= 3.0,
                "Texto secundário ilegível sobre a pressão no preset ${preset.name}."
            )
        }
    }

    /**
     * No escuro a borda é mais clara em cima — a luz que dá volume onde a sombra
     * não aparece —; no claro ela escurece embaixo, que é onde a sombra assenta.
     */
    @Test
    fun `a borda recebe luz em cima no escuro e peso embaixo no claro`() {
        for (preset in AppThemePreset.entries) {
            val ladder = AppSurfaceLadder.of(preset)
            if (preset.isDark) {
                assertTrue(
                    ladder.borderTop.luminance() > ladder.borderBottom.luminance(),
                    "Borda sem luz no topo no preset ${preset.name}."
                )
            } else {
                assertTrue(
                    ladder.borderBottom.luminance() < ladder.borderTop.luminance(),
                    "Borda sem peso embaixo no preset ${preset.name}."
                )
            }
        }
    }

    @Test
    fun `os patamares de profundidade crescem sem pular`() {
        val depths = AppDepth.entries
        for (index in 1 until depths.size) {
            assertTrue(depths[index].key > depths[index - 1].key)
            assertTrue(depths[index].ambient > depths[index - 1].ambient)
        }
        // O vão da grade do dashboard é 12dp: sombra de card maior que a metade
        // dele se sobrepõe à do vizinho.
        assertTrue(AppDepth.CARD.ambient.value <= 6f)
    }

    private fun contrastRatio(a: Color, b: Color): Double {
        val first = a.luminance().toDouble()
        val second = b.luminance().toDouble()
        return (maxOf(first, second) + 0.05) / (minOf(first, second) + 0.05)
    }

    private companion object {
        const val MIN_STATE_STEP = 1.06
    }
}

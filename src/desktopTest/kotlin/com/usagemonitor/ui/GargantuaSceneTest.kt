package com.usagemonitor.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.usagemonitor.presentation.ui.components.GargantuaScene
import com.usagemonitor.presentation.ui.components.gargantuaScene
import com.usagemonitor.presentation.ui.theme.AppTheme
import com.usagemonitor.presentation.ui.theme.AppThemePreset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * No tema claro a luz do cenário vira tinta: parede, reflexos e detritos
 * brancos sumiam sobre a superfície clara. A escolha é pela superfície de cada
 * preset, então os 26 entram.
 */
@OptIn(ExperimentalTestApi::class)
class GargantuaSceneTest {

    @Test
    fun `dark presets keep the white glass and light presets draw it in the theme ink`() = runDesktopComposeUiTest {
        val failures = mutableListOf<String>()
        setContent {
            AppThemePreset.entries.forEach { preset ->
                AppTheme(preset = preset) {
                    val scene = gargantuaScene()
                    if (preset.isDark) {
                        if (scene !== GargantuaScene.Dark) failures += "$preset: escuro sem a cena escura"
                    } else if (scene.ink != MaterialTheme.colorScheme.onSurface || scene.ink == Color.White) {
                        failures += "$preset: claro sem a tinta do tema"
                    }
                }
            }
        }
        waitForIdle()
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun `the dark scene keeps the original values`() {
        assertEquals(Color.White, GargantuaScene.Dark.ink)
        assertEquals(0.06f, GargantuaScene.Dark.wallAlpha)
        assertEquals(0.6f, GargantuaScene.Dark.plasmaHighlight)
    }
}

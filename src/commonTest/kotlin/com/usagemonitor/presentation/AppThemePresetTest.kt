package com.usagemonitor.presentation

import androidx.compose.ui.graphics.Color
import com.usagemonitor.presentation.ui.theme.AppThemePreset
import com.usagemonitor.presentation.ui.theme.darkAppAccents
import com.usagemonitor.presentation.ui.theme.lightAppAccents
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppThemePresetTest {

    @Test
    fun `registry has thirteen dark and eight light presets`() {
        assertEquals(21, AppThemePreset.entries.size)
        assertEquals(13, AppThemePreset.dark.size)
        assertEquals(8, AppThemePreset.light.size)
    }

    @Test
    fun `storage round trip and legacy fallback are deterministic`() {
        for (preset in AppThemePreset.entries) {
            assertEquals(preset, AppThemePreset.fromStorage(preset.storageName))
        }
        assertNull(AppThemePreset.fromStorage(null))
        assertEquals(AppThemePreset.OBSIDIANA_DARK, AppThemePreset.fromLegacyMode(true))
        assertEquals(AppThemePreset.PORCELANA_LIGHT, AppThemePreset.fromLegacyMode(false))
    }

    @Test
    fun `every preset keeps foreground and semantic accents readable`() {
        for (preset in AppThemePreset.entries) {
            val accents = if (preset.isDark) darkAppAccents else lightAppAccents
            assertTrue(
                contrastRatio(preset.foreground, preset.surface) >= MINIMUM_RATIO,
                "Texto principal ilegível no preset ${preset.name}."
            )
            assertTrue(
                contrastRatio(preset.muted, preset.surface) >= 3.0,
                "Texto secundário ilegível no preset ${preset.name}."
            )
            assertTrue(
                contrastRatio(preset.primary, preset.surface) >= 3.0,
                "Acento estrutural sem contraste suficiente no preset ${preset.name}."
            )
            for ((name, color) in semanticColors(accents)) {
                assertTrue(
                    contrastRatio(color, preset.surface) >= MINIMUM_RATIO,
                    "O acento $name ficou ilegível no preset ${preset.name}."
                )
            }
        }
    }

    private fun semanticColors(accents: com.usagemonitor.presentation.ui.theme.AppAccents) = listOf(
        "input" to accents.input,
        "output" to accents.output,
        "cacheRead" to accents.cacheRead,
        "cacheWrite" to accents.cacheWrite,
        "savings" to accents.savings,
        "saturated" to accents.saturated,
        "neutral" to accents.neutral,
        "anthropic" to accents.anthropic,
        "minimax" to accents.minimax,
        "codex" to accents.codex,
        "deepseek" to accents.deepseek,
        "opencode" to accents.opencode,
        "kilo" to accents.kilo
    )

    private fun contrastRatio(a: Color, b: Color): Double {
        val first = relativeLuminance(a)
        val second = relativeLuminance(b)
        val lighter = maxOf(first, second)
        val darker = minOf(first, second)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun relativeLuminance(color: Color): Double {
        fun linearize(channel: Double): Double {
            return if (channel <= 0.03928) {
                channel / 12.92
            } else {
                ((channel + 0.055) / 1.055).pow(2.4)
            }
        }
        return 0.2126 * linearize(color.red.toDouble()) +
            0.7152 * linearize(color.green.toDouble()) +
            0.0722 * linearize(color.blue.toDouble())
    }

    private companion object {
        const val MINIMUM_RATIO = 4.5
    }
}

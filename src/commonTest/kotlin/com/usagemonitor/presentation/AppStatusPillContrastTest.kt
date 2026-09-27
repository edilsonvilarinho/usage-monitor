package com.usagemonitor.presentation

import androidx.compose.ui.graphics.Color
import com.usagemonitor.presentation.ui.components.statusPillBackground
import com.usagemonitor.presentation.ui.theme.darkAppAccents
import com.usagemonitor.presentation.ui.theme.lightAppAccents
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A pílula de estado (issue #322) escreve a palavra no próprio tom sobre um
 * fundo tingido desse tom. Tingir aproxima fundo e texto: este teste garante que
 * o texto continua em AA (4,5:1) sobre o fundo **composto**, nos dois temas, para
 * os três tons que a HUD usa.
 *
 * Superfícies e crítico copiados de `AppTheme.kt`, como em `AppAccentsContrastTest`.
 */
class AppStatusPillContrastTest {

    private val darkSurface = Color(0xFF1B1818)
    private val lightSurface = Color(0xFFFFFCFC)
    private val darkCritical = Color(0xFFE86A6A)
    private val lightCritical = Color(0xFFB3261E)

    @Test
    fun `texto da pilula passa em AA sobre o fundo tingido no tema escuro`() {
        assertReadable(
            listOf("OK" to darkAppAccents.cacheRead, "WARNING" to darkAppAccents.cacheWrite, "CRITICAL" to darkCritical),
            darkSurface,
            "escuro"
        )
    }

    @Test
    fun `texto da pilula passa em AA sobre o fundo tingido no tema claro`() {
        assertReadable(
            listOf("OK" to lightAppAccents.cacheRead, "WARNING" to lightAppAccents.cacheWrite, "CRITICAL" to lightCritical),
            lightSurface,
            "claro"
        )
    }

    private fun assertReadable(tones: List<Pair<String, Color>>, surface: Color, theme: String) {
        tones.forEach { (name, tone) ->
            val ratio = contrastRatio(tone, statusPillBackground(tone, surface))
            assertTrue(ratio >= 4.5, "Tema $theme, tom $name: ${"%.2f".format(ratio)}:1 sobre a pílula")
        }
    }

    private fun contrastRatio(a: Color, b: Color): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun relativeLuminance(color: Color): Double {
        return 0.2126 * linearize(color.red.toDouble()) +
            0.7152 * linearize(color.green.toDouble()) +
            0.0722 * linearize(color.blue.toDouble())
    }

    private fun linearize(channel: Double): Double {
        if (channel <= 0.03928) {
            return channel / 12.92
        }
        return ((channel + 0.055) / 1.055).pow(2.4)
    }
}

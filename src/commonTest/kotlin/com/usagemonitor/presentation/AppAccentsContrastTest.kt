package com.usagemonitor.presentation

import androidx.compose.ui.graphics.Color
import com.usagemonitor.presentation.ui.theme.AppAccents
import com.usagemonitor.presentation.ui.theme.darkAppAccents
import com.usagemonitor.presentation.ui.theme.lightAppAccents
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Guarda o contraste da paleta de acentos.
 *
 * A app passou a vida com uma paleta só para os dois temas, e os acentos são
 * usados como **cor de texto**. Contra a `surface` clara todos reprovavam o mínimo
 * de 4,5:1 da WCAG AA — `#4CAF50` dava 2,64:1, ilegível. O acento de contexto
 * saturado reprovava até no tema escuro (4,06:1).
 *
 * O teste é puro: nada de Compose além do tipo `Color`. Qualquer valor novo em
 * `AppAccents.kt` passa por aqui antes de chegar à tela.
 */
class AppAccentsContrastTest {

    // As superfícies de referência de `AppTheme.kt`: `surface` de cada tema, e não
    // `background` — o texto acentuado cai sobre um painel, não sobre a moldura da
    // janela.
    //
    // Não é `surfaceVariant` (o degrau "raised", #211E1E / #EFEAEA), que seria o
    // pior caso aritmético: contra o claro `cacheRead` e `opencode` dão 4,30:1.
    // Adotá-lo obrigaria a mexer nos acentos, e o protótipo aprovado os congela
    // justamente porque já satisfazem a regra contra `surface`. Medir contra
    // `surface` é a regra que este teste sempre teve; o que mudou foram os valores.
    private val darkSurface = Color(0xFF1B1818)
    private val lightSurface = Color(0xFFFFFCFC)

    private val minimumRatio = 4.5

    @Test
    fun `todo acento escuro passa em AA sobre a superficie escura`() {
        assertPaletteReadable(darkAppAccents, darkSurface, "escuro")
    }

    @Test
    fun `todo acento claro passa em AA sobre a superficie clara`() {
        assertPaletteReadable(lightAppAccents, lightSurface, "claro")
    }

    /**
     * A codificação de cor tem de sobreviver à troca de tema: o verde de cache
     * lido continua verde, o azul de custo continua azul. Sem isto, "rebaixar até
     * passar" acabaria virando cinza para tudo.
     */
    @Test
    fun `a variante clara preserva a matiz da escura`() {
        val pairs = namedAccents(darkAppAccents).zip(namedAccents(lightAppAccents))
        for ((dark, light) in pairs) {
            val delta = hueDistanceDegrees(hue(dark.second), hue(light.second))
            assertTrue(
                delta <= 30.0,
                "O acento ${dark.first} muda de matiz entre os temas: ${delta.toInt()}°."
            )
        }
    }

    /**
     * As seis cores de fonte são um conjunto categórico: se duas ficarem perto
     * demais, o gráfico do histórico deixa de distinguir as séries.
     */
    @Test
    fun `as cores de fonte permanecem distinguiveis entre si`() {
        for (palette in listOf(darkAppAccents to "escuro", lightAppAccents to "claro")) {
            val sources = sourceAccents(palette.first)
            for (i in sources.indices) {
                for (j in i + 1 until sources.size) {
                    val delta = hueDistanceDegrees(hue(sources[i].second), hue(sources[j].second))
                    assertTrue(
                        delta >= 20.0,
                        "No tema ${palette.second}, ${sources[i].first} e ${sources[j].first} " +
                            "estão a ${delta.toInt()}° de matiz — perto demais."
                    )
                }
            }
        }
    }

    private fun assertPaletteReadable(palette: AppAccents, surface: Color, themeName: String) {
        for ((name, color) in namedAccents(palette)) {
            val ratio = contrastRatio(color, surface)
            assertTrue(
                ratio >= minimumRatio,
                "O acento $name do tema $themeName tem contraste " +
                    "${(ratio * 100).toInt() / 100.0}:1, abaixo de $minimumRatio:1."
            )
        }
    }

    private fun namedAccents(palette: AppAccents): List<Pair<String, Color>> {
        return listOf(
            "input" to palette.input,
            "output" to palette.output,
            "cacheRead" to palette.cacheRead,
            "cacheWrite" to palette.cacheWrite,
            "savings" to palette.savings,
            "saturated" to palette.saturated,
            "neutral" to palette.neutral
        ) + sourceAccents(palette)
    }

    private fun sourceAccents(palette: AppAccents): List<Pair<String, Color>> {
        return listOf(
            "anthropic" to palette.anthropic,
            "minimax" to palette.minimax,
            "codex" to palette.codex,
            "deepseek" to palette.deepseek,
            "opencode" to palette.opencode,
            "kilo" to palette.kilo
        )
    }

    // ── WCAG 2.1, seção 1.4.3 ────────────────────────────────────────────────

    private fun contrastRatio(a: Color, b: Color): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        val lighter = maxOf(la, lb)
        val darker = minOf(la, lb)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun relativeLuminance(color: Color): Double {
        val r = linearize(color.red.toDouble())
        val g = linearize(color.green.toDouble())
        val b = linearize(color.blue.toDouble())
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun linearize(channel: Double): Double {
        if (channel <= 0.03928) {
            return channel / 12.92
        }
        return ((channel + 0.055) / 1.055).pow(2.4)
    }

    /** Matiz em graus (0–360). Cinza devolve 0 e nunca chega aqui na paleta. */
    private fun hue(color: Color): Double {
        val r = color.red.toDouble()
        val g = color.green.toDouble()
        val b = color.blue.toDouble()
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val span = max - min
        if (span == 0.0) {
            return 0.0
        }
        val raw = when (max) {
            r -> 60.0 * (((g - b) / span) % 6.0)
            g -> 60.0 * (((b - r) / span) + 2.0)
            else -> 60.0 * (((r - g) / span) + 4.0)
        }
        return (raw + 360.0) % 360.0
    }

    /** Distância circular: 350° e 10° estão a 20°, não a 340°. */
    private fun hueDistanceDegrees(a: Double, b: Double): Double {
        val raw = kotlin.math.abs(a - b)
        return minOf(raw, 360.0 - raw)
    }
}

package com.usagemonitor.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import com.usagemonitor.countdownWidth
import com.usagemonitor.HUD_COUNTDOWN_GAP
import com.usagemonitor.HUD_COUNTDOWN_ICON
import com.usagemonitor.percentWidth
import com.usagemonitor.presentation.ui.components.formatRefreshCountdown
import com.usagemonitor.presentation.ui.theme.AppTheme
import com.usagemonitor.wordWidth
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A estimativa de largura da geometria contra o texto que o Skia desenha.
 *
 * A geometria do notch não mede nada — estima pelo avanço da Plex Mono, e a
 * janela nasce desse número. Em densidade fracionária (a escala de 115% sobre
 * os 125% do Windows dá 1,4375) o avanço do glifo é arredondado para cima em
 * pixel, o texto real sai mais largo que a conta, e o último item da faixa — a
 * contagem — era o que ficava espremido: "04:5" na borda de cima. Na densidade
 * 1 dos testes de componente a diferença não existia, e por isso nenhum deles
 * pegou o defeito.
 */
@OptIn(ExperimentalTestApi::class)
class HudNotchTextFitTest {

    private val scales = (100..200 step 5).toList() + listOf(144, 172)

    private val words = listOf("Sem projeção", "No forecast", "Crítico", "Atenção", "Carregando", "Loading")
    private val percents = listOf("100%", "88%", "\$2.27")
    private val countdowns = listOf(formatRefreshCountdown(59 * 60 + 59), formatRefreshCountdown(10 * 60))

    @Test
    fun `a estimativa da geometria cobre o texto desenhado em qualquer escala`() = runDesktopComposeUiTest {
        val failures = mutableSetOf<String>()
        var scale by mutableStateOf(scales.first())
        setContent {
                AppTheme(isDark = true, uiScalePercent = scale) {
                    val measurer = rememberTextMeasurer()
                    val density = LocalDensity.current
                    val small = MaterialTheme.typography.labelSmall
                    val medium = MaterialTheme.typography.labelMedium
                    fun drawn(text: String, style: TextStyle): Dp {
                        val px = measurer.measure(text, style).size.width
                        return with(density) { px.toDp() }
                    }
                    words.forEach { word ->
                        val real = drawn(word, small)
                        if (real > wordWidth(word)) failures += "$scale%: \"$word\" desenhado $real > estimado ${wordWidth(word)}"
                    }
                    percents.forEach { percent ->
                        val real = drawn(percent, medium)
                        if (real > percentWidth(percent)) failures += "$scale%: \"$percent\" desenhado $real > estimado ${percentWidth(percent)}"
                    }
                    countdowns.forEach { text ->
                        // Ícone + vão de 4dp + texto: o mesmo `Row` do `HudCountdown`.
                        val textBudget = countdownWidth() - HUD_COUNTDOWN_ICON - HUD_COUNTDOWN_GAP
                        val real = drawn(text, small)
                        if (real > textBudget) failures += "$scale%: contagem \"$text\" desenhada $real > estimada $textBudget"
                    }
                }
        }
        for (next in scales) {
            scale = next
            waitForIdle()
        }
        assertTrue(failures.isEmpty(), failures.sorted().joinToString("\n"))
    }
}

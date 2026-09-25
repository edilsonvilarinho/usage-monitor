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
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.usagemonitor.HUD_APP_BALLOON_MODE_ROW
import com.usagemonitor.HUD_APP_BALLOON_UPDATE_TITLE
import com.usagemonitor.HUD_APP_BALLOON_UPDATE_TITLE_LINES
import com.usagemonitor.HUD_BALLOON_PADDING
import com.usagemonitor.HUD_BALLOON_WIDTH
import com.usagemonitor.domain.entity.ActiveSessionAlert
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CliSessionHealth
import com.usagemonitor.domain.entity.SessionPulse
import com.usagemonitor.domain.entity.StalledCliSession
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.presentation.ui.hudSessionSignals
import kotlinx.datetime.Instant
import com.usagemonitor.domain.entity.AppUpdateInfo
import com.usagemonitor.presentation.ui.updateBannerContent
import com.usagemonitor.presentation.viewmodel.AppUpdateUiState
import com.usagemonitor.countdownWidth
import com.usagemonitor.HUD_COUNTDOWN_GAP
import com.usagemonitor.HUD_COUNTDOWN_ICON
import com.usagemonitor.percentWidth
import com.usagemonitor.presentation.ui.HudStripLine
import com.usagemonitor.stripLineWidth
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

    private val words = listOf("Sem projeção", "No forecast", "Crítico", "Atenção", "Carregando", "Loading", "Nenhuma API", "No APIs")
    private val percents = listOf("100%", "88%", "\$2.27")

    /** As linhas com a janela (#286), em `labelSmall`: o pior caso de cada rótulo. */
    private val stripLines = listOf(
        HudStripLine("7d", "100%"), HudStripLine("5h", "100%"), HudStripLine("30d", "100%"),
        HudStripLine("Créditos", "100%"), HudStripLine("5h", "<1%")
    )
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
                    stripLines.forEach { line ->
                        val real = drawn(line.text, small)
                        if (real > stripLineWidth(line)) failures += "$scale%: linha \"${line.text}\" desenhada $real > estimada ${stripLineWidth(line)}"
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

    /**
     * Os sinais de sessão (issue #265) cabem numa linha do balão em qualquer
     * escala, no pior caso de contagem e de tempo: a linha é `maxLines = 1` com
     * reticências, e cortada ela perderia justamente o número.
     */
    @Test
    fun `os sinais de sessao cabem numa linha do balao em qualquer escala`() = runDesktopComposeUiTest {
        val failures = mutableSetOf<String>()
        var scale by mutableStateOf(scales.first())
        val work = UsageTargetKey(ApiSource.ANTHROPIC, "work")
        val now = Instant.parse("2026-09-25T12:00:00Z")
        val pulse = SessionPulse(
            List(99) { index -> ActiveSessionAlert("s$index", CliSessionHealth.SATURATED, now) } +
                List(99) { index -> ActiveSessionAlert("a$index", CliSessionHealth.ATTENTION, now) }
        )
        val stalled = List(99) { index ->
            StalledCliSession("t$index", null, "work", now, pendingMillis = (23 * 60 + 59) * 60_000L)
        }
        val texts = AppLanguage.entries.flatMap { language ->
            hudSessionSignals(work, pulse, stalled, language) +
                hudSessionSignals(work, null, stalled.take(1), language)
        }.map { signal -> signal.text }
        setContent {
            AppTheme(isDark = true, uiScalePercent = scale) {
                val measurer = rememberTextMeasurer()
                val density = LocalDensity.current
                val column = HUD_BALLOON_WIDTH - HUD_BALLOON_PADDING * 2
                texts.forEach { text ->
                    val result = measurer.measure(
                        text = text,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        constraints = Constraints(maxWidth = with(density) { column.roundToPx() })
                    )
                    if (result.hasVisualOverflow) failures += "$scale%: \"$text\" não cabe em $column"
                }
            }
        }
        for (next in scales) {
            scale = next
            waitForIdle()
        }
        assertTrue(texts.size >= 8, "sinais do pior caso: $texts")
        assertTrue(failures.isEmpty(), failures.sorted().joinToString("\n"))
    }

    /**
     * A frase e a ação da atualização no balão da engrenagem cabem, inteiras, nas
     * linhas que a geometria reserva (issue #274): a frase em duas, a ação numa. As
     * duas passaram a dizer o que reinicia, e cortadas perderiam justamente essa
     * parte. Foi este teste que reprovou "…it will be applied when Usage Monitor
     * closes", três linhas no balão. Versão com três dígitos por campo é o pior
     * caso de largura.
     */
    @Test
    fun `a frase e a acao da atualizacao cabem no balao em qualquer escala`() = runDesktopComposeUiTest {
        val failures = mutableSetOf<String>()
        var scale by mutableStateOf(scales.first())
        val update = AppUpdateInfo(version = "138.100.100", releasePageUrl = "https://example.com")
        val contents = AppLanguage.entries.map { language ->
            updateBannerContent(AppUpdateUiState.Ready(update), language)
        }
        setContent {
            AppTheme(isDark = true, uiScalePercent = scale) {
                val measurer = rememberTextMeasurer()
                val density = LocalDensity.current
                val small = MaterialTheme.typography.labelSmall
                val medium = MaterialTheme.typography.labelMedium
                // A coluna do balão; a linha de ação ainda desconta 4dp de padding de cada lado.
                val column = HUD_BALLOON_WIDTH - HUD_BALLOON_PADDING * 2
                fun overflow(text: String, style: TextStyle, width: Dp, lines: Int, height: Dp): String? {
                    val result = measurer.measure(
                        text = text,
                        style = style,
                        maxLines = lines,
                        constraints = Constraints(maxWidth = with(density) { width.roundToPx() })
                    )
                    val drawnHeight = with(density) { result.size.height.toDp() }
                    return when {
                        result.hasVisualOverflow -> "\"$text\" não cabe em $lines linhas de $width"
                        drawnHeight > height -> "\"$text\" mede $drawnHeight > $height"
                        else -> null
                    }
                }
                contents.forEach { content ->
                    overflow(content.title, small, column, HUD_APP_BALLOON_UPDATE_TITLE_LINES, HUD_APP_BALLOON_UPDATE_TITLE)
                        ?.let { failure -> failures += "$scale%: $failure" }
                    val action = "${content.actionLabel} →"
                    overflow(action, medium, column - 8.dp, 1, HUD_APP_BALLOON_MODE_ROW)
                        ?.let { failure -> failures += "$scale%: $failure" }
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

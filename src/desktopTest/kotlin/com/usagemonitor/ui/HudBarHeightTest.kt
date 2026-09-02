package com.usagemonitor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.usagemonitor.hudWindowSize
import com.usagemonitor.presentation.ui.HUD_CONTENT_TEST_TAG
import com.usagemonitor.presentation.ui.HudBar
import com.usagemonitor.presentation.ui.HudSourceStatus
import com.usagemonitor.presentation.ui.HudTopLine
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.theme.AppTheme
import kotlin.test.Test

/**
 * A janela HUD é dimensionada por `hudWindowSize` **antes** de existir
 * composição para medir — é essa a razão de a geometria ser função pura. O preço
 * é que as duas contas podem divergir sem nada reclamar, e foi o que aconteceu:
 * o rodapé tem o mesmo padding vertical da lista, a geometria contava só a
 * divisória mais a linha, e a janela nascia 8dp mais curta que o conteúdo. Na
 * tela isso é o texto do rodapé cortado ao meio na borda de baixo.
 *
 * Nenhum teste pegava: os de geometria conferiam a conta com ela mesma, e os de
 * componente rodam numa cena de altura fixa, onde sobra espaço. Este arquivo é a
 * costura entre os dois — mede o que o Compose realmente dispôs e compara com o
 * que a geometria prometeu.
 */
@OptIn(ExperimentalTestApi::class)
class HudBarHeightTest {

    private fun source(label: String, percent: String, reset: String?) = HudSourceStatus(
        label = label,
        statusLabel = "Crítico",
        tone = AppTone.CRITICAL,
        percentLabel = percent,
        resetLabel = reset
    )

    private val sources = listOf(
        source("Anthropic — INFORMATA2", "92%", "Ter 22h59"),
        source("Anthropic — Padrão", "41%", "4h12"),
        source("Codex", "12%", null)
    )

    private val topLine = HudTopLine(
        statusLabel = "Crítico",
        tone = AppTone.CRITICAL,
        label = "Padrão",
        quotaSummary = "5h 88% · 7d 9%"
    )

    private fun assertMeasuredHeightMatchesGeometry(
        sources: List<HudSourceStatus>,
        expanded: Boolean,
        dotOnly: Boolean = false
    ) = runDesktopComposeUiTest {
        val expected = hudWindowSize(
            topLine = topLine,
            sources = sources,
            fallbackLabel = "Carregando",
            dotOnly = dotOnly,
            expanded = expanded
        ).height

        setContent {
            AppTheme(isDark = true) {
                // A cena é bem mais alta que o conteúdo: o que se mede é o
                // bloco marcado, não o quanto a janela deu.
                Box(modifier = Modifier.width(500.dp).height(400.dp)) {
                    HudBar(
                        statusTone = AppTone.CRITICAL,
                        topLine = topLine,
                        sources = sources,
                        fallbackLabel = "Carregando",
                        dotOnly = dotOnly,
                        expanded = expanded,
                        onOpenFull = {}
                    )
                }
            }
        }

        onNodeWithTag(HUD_CONTENT_TEST_TAG).assertHeightIsEqualTo(expected)
    }

    @Test
    fun `a altura calculada bate com a composta parada`() {
        assertMeasuredHeightMatchesGeometry(sources = sources, expanded = false)
    }

    /** O caso que estava cortando na tela era este, com a lista aberta. */
    @Test
    fun `a altura calculada bate com a composta expandida`() {
        assertMeasuredHeightMatchesGeometry(sources = sources, expanded = true)
    }

    @Test
    fun `a altura calculada bate com a composta em uma cota so`() {
        assertMeasuredHeightMatchesGeometry(sources = sources.take(1), expanded = true)
    }

    @Test
    fun `a altura calculada bate com a composta na linha de carregamento`() {
        assertMeasuredHeightMatchesGeometry(sources = emptyList(), expanded = true)
    }

    @Test
    fun `a altura calculada bate com a composta recolhida ao ponto`() {
        assertMeasuredHeightMatchesGeometry(sources = sources, expanded = false, dotOnly = true)
    }
}

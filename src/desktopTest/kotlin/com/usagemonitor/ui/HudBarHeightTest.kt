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
import com.usagemonitor.presentation.ui.HudQuotaChip
import com.usagemonitor.presentation.ui.HudSourceStatus
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.theme.AppTheme
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
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

    private fun source(
        label: String,
        statusLabel: String,
        tone: AppTone,
        vararg quotas: Pair<String, AppTone>
    ) = HudSourceStatus(
        label = label,
        statusLabel = statusLabel,
        tone = tone,
        quotas = quotas.map { (text, chipTone) -> HudQuotaChip(text = text, tone = chipTone) }
    )

    private val sources = listOf(
        source(
            "INFORMATA2", "Crítico", AppTone.CRITICAL,
            "5h 28%" to AppTone.OK,
            "7d 9%" to AppTone.CRITICAL
        ),
        source(
            "Padrão", "Atenção", AppTone.WARNING,
            "5h 88%" to AppTone.WARNING,
            "7d 41%" to AppTone.OK
        ),
        source("Codex", "Normal", AppTone.OK, "mensal 75%" to AppTone.OK)
    )

    private fun assertMeasuredHeightMatchesGeometry(
        sources: List<HudSourceStatus>,
        expanded: Boolean,
        dotOnly: Boolean = false,
        showsCountdown: Boolean = false
    ) = runDesktopComposeUiTest {
        val expected = hudWindowSize(
            sources = sources,
            fallbackLabel = "Carregando",
            dotOnly = dotOnly,
            expanded = expanded,
            showsCountdown = showsCountdown
        ).height

        setContent {
            AppTheme(isDark = true) {
                // A cena é bem mais alta que o conteúdo: o que se mede é o
                // bloco marcado, não o quanto a janela deu.
                Box(modifier = Modifier.width(500.dp).height(400.dp)) {
                    HudBar(
                        statusTone = AppTone.CRITICAL,
                        sources = sources,
                        fallbackLabel = "Carregando",
                        dotOnly = dotOnly,
                        expanded = expanded,
                        nextRefreshAt = if (showsCountdown) NEXT_REFRESH_AT else null,
                        countdownDescription = if (showsCountdown) "Próxima coleta" else null,
                        nowProvider = { NOW },
                        countdownUpdatesEnabled = false,
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

    /**
     * A contagem da issue #185 entra na linha que já existe, então a altura não
     * pode mudar — e é aqui que se afirma isso, não por leitura do código: as
     * duas contas são independentes e já divergiram uma vez, quando o antigo
     * rodapé fez a janela nascer 8dp mais curta que o conteúdo.
     */
    @Test
    fun `a altura calculada bate com a composta com a contagem`() {
        assertMeasuredHeightMatchesGeometry(
            sources = sources,
            expanded = false,
            showsCountdown = true
        )
    }

    @Test
    fun `a altura calculada bate com a composta aberta com a contagem`() {
        assertMeasuredHeightMatchesGeometry(
            sources = sources,
            expanded = true,
            showsCountdown = true
        )
    }

    @Test
    fun `a altura calculada bate com a composta na linha de carregamento com a contagem`() {
        assertMeasuredHeightMatchesGeometry(
            sources = emptyList(),
            expanded = true,
            showsCountdown = true
        )
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-09-02T12:00:00Z")
        val NEXT_REFRESH_AT: Instant = NOW + 2.minutes + 5.seconds
    }
}

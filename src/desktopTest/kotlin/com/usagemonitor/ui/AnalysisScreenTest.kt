package com.usagemonitor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AnalysisPeriod
import com.usagemonitor.domain.entity.AnalysisPeriodBar
import com.usagemonitor.domain.entity.AnalysisPeriodSummary
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.SessionAnalysis
import com.usagemonitor.presentation.AnalysisFixture
import com.usagemonitor.presentation.ui.ANALYSIS_HOURS_CHART_TAG
import com.usagemonitor.presentation.ui.ANALYSIS_HOURS_TOOLTIP_TAG
import com.usagemonitor.presentation.ui.ANALYSIS_LIVE_TAG
import com.usagemonitor.presentation.ui.ANALYSIS_SPEED_STRIP_TAG
import com.usagemonitor.presentation.ui.PACE_DIAGONAL_TAG
import com.usagemonitor.presentation.ui.AnalysisContent
import com.usagemonitor.presentation.viewmodel.AnalysisUiState
import com.usagemonitor.presentation.viewmodel.CliExportOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

/** Análise do período escolhido no seletor: a fixture com a semanal do período e as barras dele. */
private fun analysisFor(period: AnalysisPeriod): SessionAnalysis = AnalysisFixture.analysis.copy(
    period = period,
    weeklySummary = if (period == AnalysisPeriod.DAYS_30) AnalysisPeriodSummary(windowCount = 4, exhaustedCount = 1, meanPeakPercent = 62) else null,
    periodBars = List(4) { index -> AnalysisPeriodBar(start = AnalysisFixture.now - ((4 - index) * 7).days,peakPercent = 40 + index, current = index == 3) }
)

/** Janela "Análise": painel único ao vivo (rodada AD, AD1) e o PDF (B21b). */
@OptIn(ExperimentalTestApi::class)
class AnalysisScreenTest {

    /** #538, A05b: o seletor pede o período à tela; o título das barras e o resumo vêm do período escolhido. */
    @Test
    fun `choosing 30 d asks for that period and shows its bar title and summary`() = runDesktopComposeUiTest(width = 1100, height = 900) {
        val requested = mutableListOf<AnalysisPeriod>()
        var period by mutableStateOf(AnalysisPeriod.NOW)
        setContent {
            ScreenTestTheme {
                Box(Modifier.size(1000.dp, 860.dp)) {
                    AnalysisContent(
                        state = AnalysisUiState.Success(analysisFor(period)),
                        accountLabel = "Anthropic · Padrão",
                        language = AppLanguage.PT,
                        period = period,
                        onSelectPeriod = { chosen ->
                            requested += chosen
                            period = chosen
                        }
                    )
                }
            }
        }
        onNodeWithText("30 d").performClick()
        assertEquals(listOf(AnalysisPeriod.DAYS_30), requested)
        onNodeWithText("Pico por semana · 30 d").assertIsDisplayed()
        onNodeWithText("4 semanas anteriores · 1 de 4 esgotaram · pico médio 62 %").assertIsDisplayed()
    }

    @Test
    fun `panel shows both windows on the pace diagonal, the table and the live time`() = runDesktopComposeUiTest(width = 1100, height = 900) {
        setContent {
            ScreenTestTheme {
                Box(Modifier.size(1000.dp, 860.dp)) {
                    AnalysisContent(
                        state = AnalysisUiState.Success(analysisFor(AnalysisPeriod.HOURS_24)),
                        accountLabel = "Anthropic · Padrão",
                        language = AppLanguage.PT
                    )
                }
            }
        }

        onNodeWithText("Análise · Anthropic · Padrão").assertIsDisplayed()
        // 12:30:00 BRT da fixture; sem o botão "Atualizar".
        onNodeWithTag(ANALYSIS_LIVE_TAG).assertIsDisplayed()
        onNodeWithText("Ao vivo · 12:30:00").assertIsDisplayed()
        onNodeWithText("Atualizar").assertDoesNotExist()
        // #463, AH1: a diagonal da 5h com a frase; a semanal da fixture não tem início, só a frase.
        onAllNodesWithTag(PACE_DIAGONAL_TAG).assertCountEquals(2)
        // #538, A05a: a frase do veredito fica abaixo da pílula; a frase da diagonal é só o contentDescription.
        onNodeWithText("Projeção de 83 % no reinício, abaixo dos 100 %.", substring = true).assertIsDisplayed()
        onNodeWithText("Projeção de 55 % no reinício, abaixo dos 100 %.", substring = true).assertIsDisplayed()
        // #538, A05a: o marcador "agora" e a legenda da diagonal, nas duas janelas.
        onAllNodesWithText("agora 12:30").assertCountEquals(2)
        onAllNodesWithText("seu uso até agora").assertCountEquals(2)
        onNodeWithText("acima da linha: esgota antes").assertDoesNotExist() // desenhado no Canvas, não é nó de texto
        onNodeWithText("Ritmo por intervalo · 24 h").performScrollTo().assertIsDisplayed()
        // As anteriores ficam desenhadas atrás da aberta: a tabela "antes × agora" saiu.
        onNodeWithText("Esta janela × anterior").assertDoesNotExist()
        onNodeWithText("tok/s na última hora: claude-sonnet 71 · claude-opus 48").performScrollTo().assertIsDisplayed()
    }

    /** A hora sob o ponteiro: pontos e vazão numa bolha; a hora sem leitura diz que não foi medida. */
    @Test
    fun `hovering an hour shows its points and throughput`() = runDesktopComposeUiTest(width = 1100, height = 900) {
        setContent {
            ScreenTestTheme {
                Box(Modifier.size(1000.dp, 860.dp)) {
                    AnalysisContent(AnalysisUiState.Success(analysisFor(AnalysisPeriod.HOURS_24)), "Anthropic · Padrão", AppLanguage.PT)
                }
            }
        }
        // Hora 2 da fixture: 17% da 5h e 48 tok/s. As horas são as barras do período "24 h" (#538, A05b).
        onNodeWithTag(ANALYSIS_HOURS_CHART_TAG).performScrollTo()
        onNodeWithTag(ANALYSIS_HOURS_CHART_TAG).performMouseInput { moveTo(androidx.compose.ui.geometry.Offset(width * 2.5f / 24f, height / 2f)) }
        waitForIdle()
        onNodeWithTag(ANALYSIS_HOURS_TOOLTIP_TAG).assertIsDisplayed()
        onNodeWithText("17,0% · 48 tok/s", substring = true).assertIsDisplayed()
        // A vazão tem faixa própria, com a escala dela.
        onNodeWithTag(ANALYSIS_SPEED_STRIP_TAG).assertExists()
    }

    /** Releitura falha: os números ficam e o selo diz a hora da falha e a da leitura na tela. */
    @Test
    fun `a failed live reload keeps the panel and says which reading is shown`() = runDesktopComposeUiTest(width = 1100, height = 900) {
        setContent {
            ScreenTestTheme {
                Box(Modifier.size(1000.dp, 860.dp)) {
                    AnalysisContent(
                        state = AnalysisUiState.Success(AnalysisFixture.analysis, liveFailedAt = AnalysisFixture.now + 1.minutes),
                        accountLabel = "Codex",
                        language = AppLanguage.EN
                    )
                }
            }
        }
        onNodeWithText("Failed at 12:31:00 · reading from 12:30:00").assertIsDisplayed()
        // #538, A05a: a frase "X% used with Y% gone" saiu da tela (é só o contentDescription); a tela mostra o veredito.
        onAllNodesWithText("Fits until the reset", substring = true).assertCountEquals(2)
    }

    /** B21b: com exportação, o botão "Relatório PDF" aparece e o resultado vira faixa com o caminho. */
    @Test
    fun `pdf button exports and the saved path shows in a banner`() = runDesktopComposeUiTest(width = 1100, height = 900) {
        var exported = 0
        setContent {
            ScreenTestTheme {
                Box(Modifier.size(1000.dp, 860.dp)) {
                    AnalysisContent(
                        state = AnalysisUiState.Success(AnalysisFixture.analysis, exportOutcome = CliExportOutcome.Saved("C:/tmp/analise.pdf")),
                        accountLabel = "Codex",
                        language = AppLanguage.PT,
                        onExportPdf = { exported++ }
                    )
                }
            }
        }
        onNodeWithText("Relatório PDF").performClick()
        assertEquals(1, exported)
        onNodeWithText("C:/tmp/analise.pdf").assertIsDisplayed()
    }

    @Test
    fun `empty state explains that the history is missing`() = runDesktopComposeUiTest {
        setContent {
            ScreenTestTheme {
                AnalysisContent(AnalysisUiState.Empty, "Codex", AppLanguage.EN)
            }
        }
        onNodeWithText("No history for this account yet. The analysis shows up after a few collections.").assertIsDisplayed()
    }
}

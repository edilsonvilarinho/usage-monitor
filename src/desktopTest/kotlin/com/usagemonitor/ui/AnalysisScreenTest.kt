package com.usagemonitor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.AnalysisFixture
import com.usagemonitor.presentation.ui.ANALYSIS_HOURS_CHART_TAG
import com.usagemonitor.presentation.ui.ANALYSIS_HOURS_TOOLTIP_TAG
import com.usagemonitor.presentation.ui.ANALYSIS_LIVE_TAG
import com.usagemonitor.presentation.ui.AnalysisContent
import com.usagemonitor.presentation.viewmodel.AnalysisUiState
import com.usagemonitor.presentation.viewmodel.CliExportOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

/** Janela "Análise": painel único ao vivo (rodada AD, AD1) e o PDF (B21b). */
@OptIn(ExperimentalTestApi::class)
class AnalysisScreenTest {

    @Test
    fun `panel shows both windows, the tables and the live time`() = runDesktopComposeUiTest(width = 1100, height = 900) {
        setContent {
            ScreenTestTheme {
                Box(Modifier.size(1000.dp, 860.dp)) {
                    AnalysisContent(
                        state = AnalysisUiState.Success(AnalysisFixture.analysis),
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
        onNodeWithText("62% usado").assertIsDisplayed()
        onNodeWithText("≈83% no reinício").assertIsDisplayed()
        onNodeWithText("41%").assertIsDisplayed()
        onNodeWithText("Ritmo por intervalo").assertIsDisplayed()
        onNodeWithText("Esta janela × anterior").assertIsDisplayed()
        onNodeWithText("tok/s na última hora: claude-sonnet 71 · claude-opus 48").assertIsDisplayed()
    }

    /** A hora sob o ponteiro: pontos e vazão numa bolha; a hora sem leitura diz que não foi medida. */
    @Test
    fun `hovering an hour shows its points and throughput`() = runDesktopComposeUiTest(width = 1100, height = 900) {
        setContent {
            ScreenTestTheme {
                Box(Modifier.size(1000.dp, 860.dp)) {
                    AnalysisContent(AnalysisUiState.Success(AnalysisFixture.analysis), "Anthropic · Padrão", AppLanguage.PT)
                }
            }
        }
        // Hora 2 da fixture: 17 pp e 48 tok/s.
        onNodeWithTag(ANALYSIS_HOURS_CHART_TAG).performMouseInput { moveTo(androidx.compose.ui.geometry.Offset(width * 2.5f / 24f, height / 2f)) }
        waitForIdle()
        onNodeWithTag(ANALYSIS_HOURS_TOOLTIP_TAG).assertIsDisplayed()
        onNodeWithText("17,0 pp · 48 tok/s", substring = true).assertIsDisplayed()
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
        onNodeWithText("62% used").assertIsDisplayed()
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

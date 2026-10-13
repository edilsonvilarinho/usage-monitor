package com.usagemonitor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AnalysisView
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.AnalysisFixture
import com.usagemonitor.presentation.ui.ANALYSIS_BODY_TAG
import com.usagemonitor.presentation.ui.AnalysisContent
import com.usagemonitor.presentation.viewmodel.AnalysisUiState
import kotlin.test.Test
import kotlin.test.assertEquals

/** Janela "Análise" (rodada AB, B20): abas das vistas do bot e o texto sem as marcas do Telegram. */
@OptIn(ExperimentalTestApi::class)
class AnalysisScreenTest {

    @Test
    fun `panel tab shows the plain text and another tab asks for its view`() = runDesktopComposeUiTest(width = 1100, height = 800) {
        var selected: AnalysisView? = null
        setContent {
            ScreenTestTheme {
                Box(Modifier.size(1000.dp, 700.dp)) {
                    AnalysisContent(
                        state = AnalysisUiState.Success(AnalysisFixture.analysis, AnalysisView.PANEL),
                        accountLabel = "Anthropic · Padrão",
                        language = AppLanguage.PT,
                        onSelect = { view -> selected = view },
                        onRefresh = {}
                    )
                }
            }
        }

        onNodeWithText("Análise · Anthropic · Padrão").assertIsDisplayed()
        onNodeWithTag(ANALYSIS_BODY_TAG).assertTextContains("JANELA 5h", substring = true)
        onNodeWithTag(ANALYSIS_BODY_TAG).assertTextContains("no reset ≈ 83%", substring = true)
        onNodeWithText("Intervalos").performClick()
        assertEquals(AnalysisView.INTERVALS, selected)
    }

    @Test
    fun `empty state explains that the history is missing`() = runDesktopComposeUiTest {
        setContent {
            ScreenTestTheme {
                AnalysisContent(AnalysisUiState.Empty, "Codex", AppLanguage.EN, onSelect = {}, onRefresh = {})
            }
        }
        onNodeWithText("No history for this account yet. The analysis shows up after a few collections.").assertIsDisplayed()
    }
}

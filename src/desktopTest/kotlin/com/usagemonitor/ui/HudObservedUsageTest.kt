package com.usagemonitor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.usagemonitor.HUD_BALLOON_PADDING
import com.usagemonitor.HUD_BALLOON_WIDTH
import com.usagemonitor.HUD_OBSERVED_MODEL_HEIGHT
import com.usagemonitor.HudEdge
import com.usagemonitor.ScreenWorkArea
import com.usagemonitor.hudBalloonHeight
import com.usagemonitor.hudDockedWindowBounds
import com.usagemonitor.hudNotchSizes
import com.usagemonitor.hudObservedViewportHeight
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.presentation.ui.HUD_BALLOON_CONTENT_TEST_TAG
import com.usagemonitor.presentation.ui.HUD_BALLOON_TEST_TAG
import com.usagemonitor.presentation.ui.HudAccount
import com.usagemonitor.presentation.ui.HudAccountBalloonContent
import com.usagemonitor.presentation.ui.HudNotch
import com.usagemonitor.presentation.ui.HudObservedModel
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.theme.AppTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class HudObservedUsageTest {
    @Test
    fun `as tres fontes mostram unidade e modelos sem cotas em PT e EN`() {
        for (source in listOf(ApiSource.OPENCODE, ApiSource.KILO, ApiSource.GEMINI)) {
            for (language in AppLanguage.entries) {
                runDesktopComposeUiTest {
                    val account = fixture(source, language)
                    setContent {
                        AppTheme(isDark = language == AppLanguage.PT) {
                            Box(Modifier.size(HUD_BALLOON_WIDTH, hudBalloonHeight(account)).padding(HUD_BALLOON_PADDING)) {
                                HudAccountBalloonContent(account, language)
                            }
                        }
                    }
                    val noun = if (source == ApiSource.GEMINI) "tokens" else if (language == AppLanguage.PT) "requisições" else "requests"
                    onNodeWithText("Model A").assertIsDisplayed()
                    onNodeWithText("${if (language == AppLanguage.PT) "Últimas 5h" else "Last 5h"} — 18 $noun").assertIsDisplayed()
                    onNodeWithText("${if (language == AppLanguage.PT) "Últimos 7 dias" else "Last 7 days"} — 25 $noun").assertIsDisplayed()
                    for (text in listOf("0%", "Sessão 5h", "Semanal", "Sem projeção", "Reinicia")) {
                        onAllNodesWithText(text, substring = true).assertCountEquals(0)
                    }
                    assertEquals(hudBalloonHeight(account) - HUD_BALLOON_PADDING * 2,
                        onNodeWithTag(HUD_BALLOON_CONTENT_TEST_TAG).getUnclippedBoundsInRoot().height)
                }
            }
        }
    }

    @Test
    fun `lista longa rola e mantem as acoes fixas na tela pequena`() = runDesktopComposeUiTest {
        val account = fixture(ApiSource.OPENCODE, AppLanguage.PT, count = 8)
        val limit = 300.dp
        assertEquals(HUD_OBSERVED_MODEL_HEIGHT * 4, hudObservedViewportHeight(account))
        assertTrue(hudBalloonHeight(account, limit) <= limit)
        setContent {
            AppTheme(isDark = true) {
                Box(Modifier.size(HUD_BALLOON_WIDTH, hudBalloonHeight(account, limit)).padding(HUD_BALLOON_PADDING)) {
                    HudAccountBalloonContent(account, AppLanguage.PT, actions = {
                        Text("Histórico", modifier = Modifier.testTag("observedActions"))
                    }, maxBodyHeight = limit)
                }
            }
        }
        onNodeWithText("Model H").performScrollTo().assertIsDisplayed()
        onNodeWithTag("observedActions").assertIsDisplayed()
        onNodeWithText("Contagem local; limite oficial indisponível").assertIsDisplayed()
        assertEquals(limit - HUD_BALLOON_PADDING * 2,
            onNodeWithTag(HUD_BALLOON_CONTENT_TEST_TAG).getUnclippedBoundsInRoot().height)
    }

    @Test
    fun `balao observado cabe nas quatro bordas em tela baixa e modo compacto`() {
        val account = fixture(ApiSource.OPENCODE, AppLanguage.PT, count = 8)
        val screen = ScreenWorkArea(0.dp, 0.dp, DpSize(800.dp, 400.dp))
        for (edge in HudEdge.entries) {
            runDesktopComposeUiTest {
                val sizes = hudNotchSizes(listOf(account), edge, "Carregando", false, maxAlong = 1.dp, maxWindowHeight = screen.size.height)
                assertTrue(sizes.compact)
                val window = hudDockedWindowBounds(edge, 0.5f, sizes, screen)
                setContent {
                    AppTheme(isDark = false) {
                        Box(Modifier.size(window.size)) {
                            HudNotch(listOf(account), edge, sizes, "Carregando", expanded = true,
                                notchCenter = window.notchCenterInWindow, initialBalloonIndex = 0, modifier = Modifier.fillMaxSize())
                        }
                    }
                }
                onNodeWithText(account.focusLine.text).assertIsDisplayed()
                val box = onNodeWithTag(HUD_BALLOON_TEST_TAG).getUnclippedBoundsInRoot()
                assertTrue(box.top >= 0.dp && box.bottom <= window.size.height, "$edge: $box em ${window.size}")
                assertTrue(window.size.height <= screen.size.height)
            }
        }
    }

    private fun fixture(source: ApiSource, language: AppLanguage, count: Int = 1): HudAccount = HudAccount(
        targetKey = UsageTargetKey.forSource(source), label = source.name,
        statusLabel = if (language == AppLanguage.PT) "Atividade local" else "Local activity",
        tone = AppTone.NEUTRAL, quotas = emptyList(), focusIndex = 0,
        observedModels = List(count) { index -> HudObservedModel("Model ${'A' + index}",
            if (source == ApiSource.GEMINI) UsageUnit.TOKENS else UsageUnit.REQUESTS, 18, 25) }
    )
}

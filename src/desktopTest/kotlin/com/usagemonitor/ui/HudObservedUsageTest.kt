package com.usagemonitor.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
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
import com.usagemonitor.presentation.ui.HUD_OBSERVED_TABLE_HEADER_TAG
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
                    onNodeWithText("Model A", useUnmergedTree = true).assertIsDisplayed()
                    onNodeWithTag("hudObservedFiveHours:Model A", useUnmergedTree = true).assertIsDisplayed()
                    onNodeWithTag("hudObservedSevenDays:Model A", useUnmergedTree = true).assertIsDisplayed()
                    onNodeWithTag("hudObservedRow:Model A").assertContentDescriptionContains(
                        "Model A. ${if (language == AppLanguage.PT) "Últimas 5h" else "Last 5h"}: 18 $noun. " +
                            "${if (language == AppLanguage.PT) "Últimos 7 dias" else "Last 7 days"}: 25 $noun."
                    )
                    onNodeWithText(if (language == AppLanguage.PT) "ÚLTIMAS\n5H" else "LAST\n5H").assertIsDisplayed()
                    onNodeWithText(if (language == AppLanguage.PT) "ÚLTIMOS\n7 DIAS" else "LAST\n7 DAYS").assertIsDisplayed()
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
        val headerBefore = onNodeWithTag(HUD_OBSERVED_TABLE_HEADER_TAG).getUnclippedBoundsInRoot()
        onNodeWithTag("hudObservedRow:Model H").performScrollTo().assertIsDisplayed()
        assertEquals(headerBefore, onNodeWithTag(HUD_OBSERVED_TABLE_HEADER_TAG).getUnclippedBoundsInRoot())
        onNodeWithTag("observedActions").assertIsDisplayed()
        onNodeWithText("Contagem local; limite oficial indisponível").assertIsDisplayed()
        assertEquals(limit - HUD_BALLOON_PADDING * 2,
            onNodeWithTag(HUD_BALLOON_CONTENT_TEST_TAG).getUnclippedBoundsInRoot().height)
    }

    @Test
    fun `nomes e contagens mantem contraste nos dois temas`() {
        for (dark in listOf(true, false)) {
            runDesktopComposeUiTest {
                val account = fixture(ApiSource.OPENCODE, AppLanguage.PT)
                var foreground = Color.Unspecified
                setContent {
                    AppTheme(isDark = dark) {
                        foreground = MaterialTheme.colorScheme.onSurface
                        Box(Modifier.size(HUD_BALLOON_WIDTH, hudBalloonHeight(account))
                            .background(MaterialTheme.colorScheme.surface).padding(HUD_BALLOON_PADDING)) {
                            HudAccountBalloonContent(account, AppLanguage.PT)
                        }
                    }
                }
                for (tag in listOf("hudObservedName:Model A", "hudObservedFiveHours:Model A", "hudObservedSevenDays:Model A")) {
                    val pixels = onNodeWithTag(tag, useUnmergedTree = true).captureToImage().toPixelMap()
                    var inkFound = false
                    for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                        val pixel = pixels[x, y]
                        if (kotlin.math.abs(pixel.red - foreground.red) < 0.03f &&
                            kotlin.math.abs(pixel.green - foreground.green) < 0.03f &&
                            kotlin.math.abs(pixel.blue - foreground.blue) < 0.03f) inkFound = true
                    }
                    assertTrue(inkFound, "$tag não usa a cor do texto no tema dark=$dark")
                }
            }
        }
    }

    @Test
    fun `tabela preserva nomes completos na semantica e alinha numeros grandes com escala`() {
        for (scale in listOf(100, 115, 150)) {
            runDesktopComposeUiTest {
                val name = "Modelo gratuito com identificação extensa para testar leitura"
                val account = fixture(ApiSource.OPENCODE, AppLanguage.PT).copy(
                    observedModels = listOf(HudObservedModel(name, UsageUnit.REQUESTS, 123_456, 1_234_567))
                )
                setContent {
                    AppTheme(isDark = true, uiScalePercent = scale) {
                        Box(Modifier.size(HUD_BALLOON_WIDTH, hudBalloonHeight(account)).padding(HUD_BALLOON_PADDING)) {
                            HudAccountBalloonContent(account, AppLanguage.PT)
                        }
                    }
                }
                onNodeWithTag("hudObservedRow:$name").assertContentDescriptionContains(
                    "$name. Últimas 5h: 123.456 requisições. Últimos 7 dias: 1.234.567 requisições."
                ).assertIsDisplayed()
                val first = onNodeWithTag("hudObservedFiveHours:$name", useUnmergedTree = true).getUnclippedBoundsInRoot()
                val second = onNodeWithTag("hudObservedSevenDays:$name", useUnmergedTree = true).getUnclippedBoundsInRoot()
                assertTrue(first.right < second.left)
                assertTrue(second.right <= HUD_BALLOON_WIDTH - HUD_BALLOON_PADDING)
                onNodeWithText("123.456", useUnmergedTree = true).assertIsDisplayed()
                onNodeWithText("1.234.567", useUnmergedTree = true).assertIsDisplayed()
                for (tag in listOf("hudObservedFiveHours:$name", "hudObservedSevenDays:$name")) {
                    val layouts = mutableListOf<TextLayoutResult>()
                    onNodeWithTag(tag, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
                        it(layouts)
                    }
                    assertTrue(layouts.isNotEmpty())
                    assertTrue(layouts.none { it.hasVisualOverflow }, "$scale%: $tag cortado")
                }
            }
        }
    }

    @Test
    fun `zero e uma requisicao conservam contagem e unidade sem percentual`() = runDesktopComposeUiTest {
        val account = fixture(ApiSource.OPENCODE, AppLanguage.PT).copy(
            observedModels = listOf(HudObservedModel("Big Pickle", UsageUnit.REQUESTS, 0, 1))
        )
        setContent {
            AppTheme(isDark = true) {
                Box(Modifier.size(HUD_BALLOON_WIDTH, hudBalloonHeight(account)).padding(HUD_BALLOON_PADDING)) {
                    HudAccountBalloonContent(account, AppLanguage.PT)
                }
            }
        }
        onNodeWithTag("hudObservedRow:Big Pickle").assertContentDescriptionContains(
            "Big Pickle. Últimas 5h: 0 requisições. Últimos 7 dias: 1 requisição."
        )
        onNodeWithText("0", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("1", useUnmergedTree = true).assertIsDisplayed()
        onAllNodesWithText("%", substring = true).assertCountEquals(0)
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

package com.usagemonitor.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.usagemonitor.HudEdge
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.hudNotchSizes
import com.usagemonitor.presentation.ui.HUD_RING_ITEM_TEST_TAG
import com.usagemonitor.presentation.ui.HudAccount
import com.usagemonitor.presentation.ui.HudQuota
import com.usagemonitor.presentation.ui.HudRingStrip
import com.usagemonitor.presentation.ui.components.AppTone
import kotlin.test.Test

/**
 * Buraco negro (#600, A04): as contas minimizadas na lateral viram uma célula só, com a contagem;
 * na horizontal a faixa segue como antes. Tag `hudBlackHole` (literal, o nó ainda não existe).
 */
@OptIn(ExperimentalTestApi::class)
class HudBlackHoleCellTest {

    private val fourMinimized = listOf("A", "B", "C", "D").map { label -> account(label, minimized = true) }
    private val fourFull = listOf("A", "B", "C", "D").map { label -> account(label, minimized = false) }

    @Test
    fun `vertical com quatro minimizadas mostra o buraco com a contagem e nenhuma celula por conta`() = runDesktopComposeUiTest {
        setStrip(fourMinimized, HudEdge.LEFT)

        onAllNodesWithTag("hudBlackHole").assertCountEquals(1)
        onNodeWithText("4 minimizadas").assertExists()
        onAllNodesWithTag(HUD_RING_ITEM_TEST_TAG).assertCountEquals(0)
    }

    @Test
    fun `vertical sem minimizada nao mostra buraco e mantem uma celula por conta`() = runDesktopComposeUiTest {
        setStrip(fourFull, HudEdge.LEFT)

        onAllNodesWithTag("hudBlackHole").assertCountEquals(0)
        onAllNodesWithTag(HUD_RING_ITEM_TEST_TAG).assertCountEquals(4)
    }

    @Test
    fun `horizontal com minimizadas nao mostra buraco e mantem as celulas de hoje`() = runDesktopComposeUiTest {
        setStrip(fourMinimized, HudEdge.TOP)

        onAllNodesWithTag("hudBlackHole").assertCountEquals(0)
        onAllNodesWithTag(HUD_RING_ITEM_TEST_TAG).assertCountEquals(4)
    }

    private fun ComposeUiTest.setStrip(accounts: List<HudAccount>, edge: HudEdge) {
        setContent {
            ScreenTestTheme(isDark = true) {
                HudRingStrip(
                    accounts = accounts,
                    edge = edge,
                    fallbackLabel = "Carregando",
                    fallbackTone = AppTone.NEUTRAL,
                    size = hudNotchSizes(accounts, edge, "Carregando", false).collapsed,
                    compact = false,
                    language = AppLanguage.PT,
                    onRingHovered = {},
                    onHoleHovered = {},
                    onRingRefresh = {},
                    onItemPlaced = { _, _ -> },
                    onRingPlaced = { _, _ -> }
                )
            }
        }
    }

    private fun account(label: String, minimized: Boolean): HudAccount {
        val quota = HudQuota("5h", "28%", 0.28f, AppTone.OK, resetText = null, hasForecast = false)
        return HudAccount(
            targetKey = UsageTargetKey(ApiSource.ANTHROPIC, label),
            label = label,
            statusLabel = "Ok",
            tone = AppTone.OK,
            quotas = listOf(quota),
            focusIndex = 0,
            minimized = minimized
        )
    }
}

package com.usagemonitor.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
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
import com.usagemonitor.presentation.ui.theme.AppMotionPolicy
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Buraco negro (#600, A04): as contas minimizadas na lateral viram uma célula só, com a contagem;
 * na horizontal a faixa segue como antes. Tag `hudBlackHole` (literal, o nó ainda não existe).
 * Órbita (#602, A06): um glifo por conta do buraco, o em execução com semântica própria.
 */
@OptIn(ExperimentalTestApi::class)
class HudBlackHoleCellTest {

    private val fourMinimized = listOf("A", "B", "C", "D").map { label -> account(label, minimized = true) }
    private val fourFull = listOf("A", "B", "C", "D").map { label -> account(label, minimized = false) }

    @Test
    fun `vertical com quatro minimizadas mostra o buraco com a contagem e nenhuma celula por conta`() = runDesktopComposeUiTest {
        setStrip(fourMinimized, HudEdge.LEFT)

        onAllNodesWithTag("hudBlackHole").assertCountEquals(1)
        onAllNodesWithText("4 minimizadas").assertCountEquals(1)
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

    @Test
    fun `vertical com conta a meio caminho conta so as que chegaram e desenha a em voo`() = runDesktopComposeUiTest {
        setStrip(fourMinimized, HudEdge.LEFT, minimizeProgress = mapOf(fourMinimized[3].targetKey to 0.5f))

        onAllNodesWithText("3 minimizadas").assertCountEquals(1)
        onAllNodesWithText("4 minimizadas").assertCountEquals(0)
        onAllNodesWithTag("hudBlackHoleFlying").assertCountEquals(1)
    }

    @Test
    fun `vertical com conta que chegou ao buraco conta as quatro e nao desenha voo`() = runDesktopComposeUiTest {
        setStrip(fourMinimized, HudEdge.LEFT, minimizeProgress = mapOf(fourMinimized[3].targetKey to 1f))

        onAllNodesWithText("4 minimizadas").assertCountEquals(1)
        onAllNodesWithTag("hudBlackHoleFlying").assertCountEquals(0)
    }

    @Test
    fun `horizontal com conta a meio caminho nao desenha buraco nem voo`() = runDesktopComposeUiTest {
        setStrip(fourMinimized, HudEdge.TOP, minimizeProgress = mapOf(fourMinimized[3].targetKey to 0.5f))

        onAllNodesWithTag("hudBlackHoleFlying").assertCountEquals(0)
        onAllNodesWithTag("hudBlackHole").assertCountEquals(0)
    }

    @Test
    fun `buraco com quatro minimizadas desenha um glifo em orbita por conta`() = runDesktopComposeUiTest {
        setStrip(fourMinimized, HudEdge.LEFT)

        onAllNodesWithTag("hudBlackHoleOrbitGlyph", useUnmergedTree = true).assertCountEquals(4)
    }

    @Test
    fun `so o glifo da conta em execucao leva a semantica de em execucao`() = runDesktopComposeUiTest {
        val accounts = listOf(
            account("A", minimized = true, sessionActive = true),
            account("B", minimized = true),
            account("C", minimized = true),
            account("D", minimized = true)
        )
        setStrip(accounts, HudEdge.LEFT)

        onAllNodesWithContentDescription("em execução", substring = true).assertCountEquals(1)
    }

    @Test
    fun `movimento continuo desenha um filamento por conta em execucao e nenhum nas ociosas`() = runDesktopComposeUiTest {
        val accounts = listOf(
            account("A", minimized = true, sessionActive = true),
            account("B", minimized = true),
            account("C", minimized = true, sessionActive = true),
            account("D", minimized = true)
        )
        mainClock.autoAdvance = false
        setStrip(accounts, HudEdge.LEFT, motion = AppMotionPolicy.Live)

        onAllNodesWithTag("hudBlackHoleFilament", useUnmergedTree = true).assertCountEquals(2)
    }

    @Test
    fun `movimento continuo sem conta em execucao nao desenha filamento`() = runDesktopComposeUiTest {
        mainClock.autoAdvance = false
        setStrip(fourMinimized, HudEdge.LEFT, motion = AppMotionPolicy.Live)

        onAllNodesWithTag("hudBlackHoleFilament", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun `movimento reduzido nao desenha filamento mesmo com conta em execucao`() = runDesktopComposeUiTest {
        val accounts = listOf(
            account("A", minimized = true, sessionActive = true),
            account("B", minimized = true),
            account("C", minimized = true),
            account("D", minimized = true)
        )
        setStrip(accounts, HudEdge.LEFT, motion = AppMotionPolicy.Reduced)

        onAllNodesWithTag("hudBlackHoleFilament", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun `glifo em orbita e disco de 32 dp com marca de 20 dp`() = runDesktopComposeUiTest {
        setStrip(fourMinimized, HudEdge.LEFT)

        onAllNodesWithTag("hudBlackHoleOrbitMark", useUnmergedTree = true).assertCountEquals(4)
        onAllNodesWithTag("hudBlackHoleOrbitMark", useUnmergedTree = true).onFirst().assertWidthIsEqualTo(20.dp)
        onAllNodesWithTag("hudBlackHoleOrbitGlyph", useUnmergedTree = true).onFirst().assertWidthIsEqualTo(32.dp)
    }

    @Test
    fun `movimento reduzido mantem os glifos na mesma posicao em dois quadros e em posicoes distintas`() = runDesktopComposeUiTest {
        setStrip(fourMinimized, HudEdge.LEFT)
        mainClock.autoAdvance = false

        onAllNodesWithTag("hudBlackHoleOrbitGlyph", useUnmergedTree = true).assertCountEquals(4)
        val primeiroQuadro = onAllNodesWithTag("hudBlackHoleOrbitGlyph", useUnmergedTree = true).fetchSemanticsNodes().map { no -> no.positionInRoot }
        mainClock.advanceTimeByFrame()
        waitForIdle()
        val segundoQuadro = onAllNodesWithTag("hudBlackHoleOrbitGlyph", useUnmergedTree = true).fetchSemanticsNodes().map { no -> no.positionInRoot }

        assertEquals(primeiroQuadro, segundoQuadro)
        assertEquals(4, primeiroQuadro.distinct().size)
    }

    private fun ComposeUiTest.setStrip(
        accounts: List<HudAccount>,
        edge: HudEdge,
        minimizeProgress: Map<UsageTargetKey, Float> = emptyMap(),
        motion: AppMotionPolicy = AppMotionPolicy.Reduced,
    ) {
        setContent {
            ScreenTestTheme(isDark = true, motion = motion) {
                HudRingStrip(
                    accounts = accounts,
                    edge = edge,
                    fallbackLabel = "Carregando",
                    fallbackTone = AppTone.NEUTRAL,
                    size = hudNotchSizes(accounts, edge, "Carregando", false).collapsed,
                    compact = false,
                    minimizeProgress = minimizeProgress,
                    language = AppLanguage.PT,
                    onRingHovered = {},
                    onHoleClicked = {},
                    onRingRefresh = {},
                    onItemPlaced = { _, _ -> },
                    onRingPlaced = { _, _ -> }
                )
            }
        }
    }

    private fun account(label: String, minimized: Boolean, sessionActive: Boolean = false): HudAccount {
        val quota = HudQuota("5h", "28%", 0.28f, AppTone.OK, resetText = null, hasForecast = false)
        return HudAccount(
            targetKey = UsageTargetKey(ApiSource.ANTHROPIC, label),
            label = label,
            statusLabel = "Ok",
            tone = AppTone.OK,
            quotas = listOf(quota),
            focusIndex = 0,
            sessionActive = sessionActive,
            minimized = minimized
        )
    }
}

package com.usagemonitor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.usagemonitor.HUD_RING_GAP
import com.usagemonitor.HUD_RING_SIZE
import com.usagemonitor.HUD_RING_STROKE
import com.usagemonitor.HudEdge
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.hudNotchSizes
import com.usagemonitor.presentation.ui.HUD_CONTENT_TEST_TAG
import com.usagemonitor.presentation.ui.HudNotch
import com.usagemonitor.presentation.ui.components.AppGargantuaRing
import com.usagemonitor.presentation.ui.components.AppRingArc
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.hudRingDescription
import com.usagemonitor.presentation.ui.hudRingMarkSize
import com.usagemonitor.presentation.ui.theme.AppMotionPolicy
import com.usagemonitor.presentation.ui.theme.AppTheme
import com.usagemonitor.screenshots.GargantuaPreviewFixtures
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class GargantuaHudTest {
    @Test
    fun `o disco se move mesmo em zero mas fica estavel sem movimento continuo`() {
        for (fraction in listOf(0f, 0.67f)) {
            val live = frames(AppMotionPolicy.Live, fraction)
            assertTrue(differs(live.first, live.second), "disco parado em $fraction com política Live")
            val static = frames(AppMotionPolicy.Static, fraction)
            assertFalse(differs(static.first, static.second), "animação contínua escapou de sua política em $fraction")
        }
    }

    @Test
    fun `reduzir animacoes entrega o valor final no primeiro quadro mesmo em atencao e coleta`() {
        val reduced = frames(AppMotionPolicy.Reduced, 0.94f, settle = false, signals = true)
        assertFalse(differs(reduced.first, reduced.second), "Reduzir animações deve desligar entrada, órbita e coleta")
    }

    @Test
    fun `ativar reduzir animacoes interrompe a entrada e o disco em andamento`() = runDesktopComposeUiTest {
        var policy by mutableStateOf(AppMotionPolicy.Live)
        mainClock.autoAdvance = false
        setContent {
            AppTheme(isDark = true, motion = policy) {
                Box(Modifier.testTag(FRAME).background(Color.Black).padding(8.dp)) {
                    AppGargantuaRing(
                        arcs = listOf(AppRingArc(0.94f, AppTone.WARNING)),
                        description = "Conta · 7d 94%",
                        active = true,
                        attention = true,
                        refreshing = true
                    )
                }
            }
        }
        mainClock.advanceTimeBy(80)
        policy = AppMotionPolicy.Reduced
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        val first = onNodeWithTag(FRAME).captureToImage().toPixelMap()
        mainClock.advanceTimeBy(650)
        val second = onNodeWithTag(FRAME).captureToImage().toPixelMap()
        assertFalse(differs(first, second), "movimento continuou depois de ativar Reduzir animações")
    }

    @Test
    fun `o fluxo do plasma corre dentro do arco sem alterar seu comprimento`() {
        val fraction = 0.67f
        val (first, second) = frames(AppMotionPolicy.Live, fraction)
        val frameSize = HUD_RING_SIZE.value + 16f
        val scale = first.width / frameSize
        val centerX = first.width / 2f
        val centerY = first.height / 2f
        // Com dois arcos, da linha central do interno até fora do Canvas são
        // só dados. O disco e sua luz ficam no miolo, antes dessa região.
        val dataRadius = (HUD_RING_SIZE.value / 2 - HUD_RING_STROKE.value / 2 -
            HUD_RING_STROKE.value - HUD_RING_GAP.value) * scale
        // Folga angular para o antialiasing da ponta; o fluxo tem ponta reta.
        val margin = 6f
        var checked = 0
        var flowed = false
        for (y in 0 until first.height) {
            for (x in 0 until first.width) {
                val dx = x + 0.5f - centerX
                val dy = y + 0.5f - centerY
                if (hypot(dx, dy) < dataRadius) continue
                // Ângulo no sentido horário a partir das 12h, como o arco.
                val angle = (Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble())).toFloat() + 360f) % 360f
                if (angle > fraction * 360f + margin && angle < 360f - margin) {
                    assertEquals(first[x, y], second[x, y], "o arco cresceu com a animação em ($x, $y)")
                    checked++
                } else if (first[x, y] != second[x, y]) {
                    flowed = true
                }
            }
        }
        assertTrue(checked > 0)
        assertTrue(flowed, "a luz de plasma deve correr dentro do arco")
        assertTrue(differs(first, second), "o disco deve mover enquanto o comprimento fica imóvel")
    }

    @Test
    fun `arco em zero nao ganha fluxo de plasma`() {
        val (first, second) = frames(AppMotionPolicy.Live, 0f)
        val frameSize = HUD_RING_SIZE.value + 16f
        val scale = first.width / frameSize
        val dataRadius = (HUD_RING_SIZE.value / 2 - HUD_RING_STROKE.value / 2 -
            HUD_RING_STROKE.value - HUD_RING_GAP.value) * scale
        for (y in 0 until first.height) {
            for (x in 0 until first.width) {
                if (hypot(x + 0.5f - first.width / 2f, y + 0.5f - first.height / 2f) >= dataRadius) {
                    assertEquals(first[x, y], second[x, y], "cota zerada ganhou luz em ($x, $y)")
                }
            }
        }
    }

    @Test
    fun `sem projecao os detritos orbitam na trilha e param sem movimento continuo`() {
        val (first, second) = frames(AppMotionPolicy.Live, 0f, hasForecast = false)
        val frameSize = HUD_RING_SIZE.value + 16f
        val scale = first.width / frameSize
        val dataRadius = (HUD_RING_SIZE.value / 2 - HUD_RING_STROKE.value / 2 -
            HUD_RING_STROKE.value - HUD_RING_GAP.value) * scale
        var orbited = false
        for (y in 0 until first.height) {
            for (x in 0 until first.width) {
                val outside = hypot(x + 0.5f - first.width / 2f, y + 0.5f - first.height / 2f) >= dataRadius
                if (outside && first[x, y] != second[x, y]) orbited = true
            }
        }
        // Com projeção a mesma cota zerada fica imóvel (teste acima): o que anda aqui é o detrito.
        assertTrue(orbited, "o anel de detritos deve orbitar na trilha da cota sem projeção")
        val static = frames(AppMotionPolicy.Static, 0f, hasForecast = false)
        assertFalse(differs(static.first, static.second), "detritos orbitando fora da política de movimento")
    }

    @Test
    fun `a animacao nao altera os valores nem a descricao acessivel das janelas`() = runDesktopComposeUiTest {
        val account = GargantuaPreviewFixtures.showcase.first()
        val description = hudRingDescription(account, AppLanguage.PT)
        mainClock.autoAdvance = false
        setContent {
            AppTheme(isDark = true, motion = AppMotionPolicy.Live) {
                HudNotch(
                    accounts = listOf(account),
                    edge = HudEdge.TOP,
                    sizes = hudNotchSizes(listOf(account), HudEdge.TOP, "Carregando", false),
                    fallbackLabel = "Carregando",
                    expanded = false
                )
            }
        }
        mainClock.advanceTimeBy(3_000)
        onNodeWithText("7d 94%").assertIsDisplayed()
        onNodeWithText("5h 51%").assertIsDisplayed()
        onNodeWithContentDescription(description).assertIsDisplayed()
        mainClock.advanceTimeBy(1_100)
        onNodeWithText("7d 94%").assertIsDisplayed()
        onNodeWithText("5h 51%").assertIsDisplayed()
        onNodeWithContentDescription(description).assertIsDisplayed()
    }

    @Test
    fun `os onze provedores cabem na geometria compacta sem perder identificacao`() {
        val accounts = GargantuaPreviewFixtures.accounts
        assertEquals(ApiSource.entries.toSet(), accounts.map { account -> account.source }.toSet())
        assertTrue(hudRingMarkSize(3) >= 14.dp, "três cotas não podem tornar a marca ilegível")
        for (edge in listOf(HudEdge.TOP, HudEdge.RIGHT)) {
            val sizes = hudNotchSizes(accounts, edge, "Carregando", false, maxAlong = 1.dp)
            assertTrue(sizes.compact)
            runDesktopComposeUiTest(width = 1280, height = 1400) {
                setContent {
                    AppTheme(isDark = true, motion = AppMotionPolicy.Reduced) {
                        Box(Modifier.size(sizes.expanded)) {
                            HudNotch(
                                accounts = accounts,
                                edge = edge,
                                sizes = sizes,
                                fallbackLabel = "Carregando",
                                expanded = false
                            )
                        }
                    }
                }
                accounts.forEach { account ->
                    onNodeWithContentDescription(hudRingDescription(account, AppLanguage.PT)).assertIsDisplayed()
                }
                val bounds = onNodeWithTag(HUD_CONTENT_TEST_TAG).getUnclippedBoundsInRoot()
                assertEquals(sizes.collapsed.width, bounds.width)
                assertEquals(sizes.collapsed.height, bounds.height)
            }
        }
    }

    private fun frames(
        policy: AppMotionPolicy,
        fraction: Float,
        settle: Boolean = true,
        signals: Boolean = false,
        hasForecast: Boolean = true
    ): Pair<PixelMap, PixelMap> {
        lateinit var first: PixelMap
        lateinit var second: PixelMap
        runDesktopComposeUiTest {
            mainClock.autoAdvance = false
            setContent {
                AppTheme(isDark = true, motion = policy) {
                    // Fundo opaco evita acumular alfa de antialiasing entre frames.
                    Box(Modifier.testTag(FRAME).background(Color.Black).padding(8.dp)) {
                        AppGargantuaRing(
                            arcs = listOf(
                                AppRingArc(fraction, AppTone.WARNING, hasForecast = hasForecast),
                                AppRingArc(0f, AppTone.OK)
                            ),
                            description = "Conta · 7d · 5h",
                            size = HUD_RING_SIZE,
                            stroke = HUD_RING_STROKE,
                            gap = HUD_RING_GAP,
                            active = signals,
                            attention = signals,
                            refreshing = signals
                        )
                    }
                }
            }
            if (settle) mainClock.advanceTimeBy(3_000) else mainClock.advanceTimeByFrame()
            first = onNodeWithTag(FRAME).captureToImage().toPixelMap()
            mainClock.advanceTimeBy(650)
            second = onNodeWithTag(FRAME).captureToImage().toPixelMap()
        }
        return first to second
    }

    private fun differs(first: PixelMap, second: PixelMap): Boolean {
        for (y in 0 until first.height) {
            for (x in 0 until first.width) {
                if (first[x, y] != second[x, y]) return true
            }
        }
        return false
    }

    private companion object {
        const val FRAME = "gargantua-frame"
    }
}

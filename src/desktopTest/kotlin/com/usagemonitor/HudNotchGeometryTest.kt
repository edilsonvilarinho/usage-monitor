package com.usagemonitor

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.presentation.ui.HudAccount
import com.usagemonitor.presentation.ui.HudQuota
import com.usagemonitor.presentation.ui.components.AppTone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HudNotchGeometryTest {

    private val screen = ScreenWorkArea(x = 0.dp, y = 0.dp, size = DpSize(1920.dp, 1080.dp))

    @Test
    fun `colado em cima a janela encosta no topo e so tem margem nos outros tres lados`() {
        val content = DpSize(200.dp, 46.dp)
        val bounds = hudWindowBounds(HudEdge.TOP, 0.5f, content, screen)

        assertEquals(0.dp, bounds.y)
        assertEquals(DpSize(200.dp + HUD_SHADOW_MARGIN * 2, 46.dp + HUD_SHADOW_MARGIN), bounds.size)
        assertEquals(960.dp, bounds.x + bounds.notchCenterInWindow)
    }

    @Test
    fun `cada borda encosta no lado dela`() {
        val content = DpSize(60.dp, 200.dp)
        val right = hudWindowBounds(HudEdge.RIGHT, 0.5f, content, screen)
        assertEquals(1920.dp, right.x + right.size.width)
        assertEquals(540.dp, right.y + right.notchCenterInWindow)

        val left = hudWindowBounds(HudEdge.LEFT, 0.25f, content, screen)
        assertEquals(0.dp, left.x)
        assertEquals(270.dp, left.y + left.notchCenterInWindow)

        val bottom = hudWindowBounds(HudEdge.BOTTOM, 0.5f, DpSize(200.dp, 46.dp), screen)
        assertEquals(1080.dp, bottom.y + bottom.size.height)
    }

    /** Perto do canto a janela fica dentro da tela e o notch desliza para dentro dela. */
    @Test
    fun `no canto a janela e presa e o notch continua inteiro dentro dela`() {
        val content = DpSize(300.dp, 46.dp)
        val bounds = hudWindowBounds(HudEdge.TOP, 1f, content, screen)

        assertEquals(1920.dp, bounds.x + bounds.size.width)
        assertTrue(bounds.notchCenterInWindow + 150.dp <= bounds.size.width - HUD_SHADOW_MARGIN)
    }

    @Test
    fun `o notch aberto cresce para dentro da tela e nunca encolhe`() {
        val accounts = listOf(account("Padrão", "Crítico", listOf("5h" to "88%", "7d" to "9%")))
        for (edge in HudEdge.entries) {
            val sizes = hudNotchSizes(accounts, edge, "Carregando", showsCountdown = true, hasUpdateIndicator = false)
            assertTrue(sizes.expanded.width >= sizes.collapsed.width, "$edge: largura")
            assertTrue(sizes.expanded.height >= sizes.collapsed.height, "$edge: altura")
            if (edge.isHorizontal) {
                assertTrue(sizes.expanded.height > sizes.collapsed.height, "$edge: o painel desce/sobe")
            } else {
                assertTrue(sizes.expanded.width > sizes.collapsed.width, "$edge: o painel vai para o lado")
            }
        }
    }

    /**
     * O percentual novo não pode mudar o tamanho do notch a cada coleta, senão a
     * janela pularia de dez em dez minutos: a largura é pela palavra ou pelo
     * percentual, o que for maior, e `9%` e `88%` cabem na mesma palavra.
     */
    @Test
    fun `mudar o percentual dentro da mesma palavra nao muda o notch`() {
        val low = hudNotchSizes(listOf(account("Padrão", "Normal", listOf("5h" to "9%"))), HudEdge.TOP, "", true, false)
        val high = hudNotchSizes(listOf(account("Padrão", "Normal", listOf("5h" to "88%"))), HudEdge.TOP, "", true, false)
        assertEquals(low.collapsed, high.collapsed)
    }

    @Test
    fun `a contagem e a atualizacao ocupam espaco so quando existem`() {
        val accounts = listOf(account("Padrão", "Normal", listOf("5h" to "9%")))
        val bare = hudNotchSizes(accounts, HudEdge.TOP, "", showsCountdown = false, hasUpdateIndicator = false)
        val full = hudNotchSizes(accounts, HudEdge.TOP, "", showsCountdown = true, hasUpdateIndicator = true)
        assertTrue(full.collapsed.width > bare.collapsed.width)
        assertEquals(bare.collapsed.height, full.collapsed.height)
    }

    @Test
    fun `sem contas sobra a linha de carregamento`() {
        val sizes = hudNotchSizes(emptyList(), HudEdge.TOP, "Carregando", showsCountdown = true, hasUpdateIndicator = false)
        assertTrue(sizes.collapsed.width > HUD_RING_SIZE)
        assertEquals(sizes.collapsed, sizes.expanded)
    }

    @Test
    fun `a palavra longa quebra em duas linhas so na coluna vertical`() {
        assertEquals(2, verticalWordLines("Sem projeção"))
        assertEquals(1, verticalWordLines("Crítico"))
        val single = hudNotchSizes(listOf(account("A", "Normal", listOf("5h" to "9%"))), HudEdge.LEFT, "", false, false)
        val double = hudNotchSizes(listOf(account("A", "Sem projeção", listOf("5h" to "9%"))), HudEdge.LEFT, "", false, false)
        assertEquals(HUD_WORD_LINE, double.collapsed.height - single.collapsed.height)
    }

    @Test
    fun `soltar o notch gruda na borda mais proxima`() {
        assertEquals(HudPlacement(HudEdge.TOP, 0.5f), nearestHudPlacement(960.dp, 40.dp, screen))
        assertEquals(HudEdge.BOTTOM, nearestHudPlacement(960.dp, 1050.dp, screen).edge)
        assertEquals(HudEdge.LEFT, nearestHudPlacement(30.dp, 540.dp, screen).edge)
        val right = nearestHudPlacement(1900.dp, 270.dp, screen)
        assertEquals(HudEdge.RIGHT, right.edge)
        assertEquals(0.25f, right.offsetFraction)
    }

    @Test
    fun `tela sem medida devolve a posicao de estreia`() {
        assertEquals(HudPlacement.Default, nearestHudPlacement(100.dp, 100.dp, ScreenWorkArea.Unknown))
    }

    private fun account(label: String, word: String, quotas: List<Pair<String, String>>): HudAccount {
        return HudAccount(
            targetKey = UsageTargetKey(ApiSource.ANTHROPIC, label),
            label = label,
            statusLabel = word,
            tone = AppTone.OK,
            quotas = quotas.map { (short, percent) ->
                HudQuota(short, percent, 0.5f, AppTone.OK, resetText = null, hasForecast = true)
            },
            focusIndex = 0
        )
    }
}

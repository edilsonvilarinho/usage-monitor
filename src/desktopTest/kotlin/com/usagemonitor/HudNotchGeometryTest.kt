package com.usagemonitor

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.presentation.ui.HudAccount
import com.usagemonitor.presentation.ui.HudQuota
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.components.appUsageRingOrbitReach
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
    fun `aberto o balao fica do lado de dentro da tela e o notch nao cresce`() {
        val accounts = listOf(account("Padrão", "Crítico", listOf("5h" to "88%", "7d" to "9%")))
        for (edge in HudEdge.entries) {
            val sizes = hudNotchSizes(accounts, edge, "Carregando", showsCountdown = true, hasUpdateIndicator = false)
            if (edge.isHorizontal) {
                assertEquals(sizes.collapsed.height + HUD_BALLOON_GAP + sizes.balloon.height, sizes.expanded.height, "$edge: altura")
                assertTrue(sizes.expanded.width >= sizes.balloon.width, "$edge: largura")
            } else {
                assertEquals(sizes.collapsed.width + HUD_BALLOON_GAP + sizes.balloon.width, sizes.expanded.width, "$edge: largura")
                assertTrue(sizes.expanded.height >= sizes.balloon.height, "$edge: altura")
            }
        }
    }

    /** A janela não pode mudar de tamanho ao passar de um anel para outro: o balão é o da conta mais alta. */
    @Test
    fun `o balao reservado e o da conta mais alta`() {
        val short = account("A", "Normal", listOf("5h" to "9%"))
        val tall = account("B", "Normal", listOf("5h" to "9%", "7d" to "1%", "30d" to "2%"))
        val sizes = hudNotchSizes(listOf(short, tall), HudEdge.RIGHT, "", true, false)

        assertEquals(hudBalloonHeight(tall), sizes.balloon.height)
        assertTrue(hudBalloonHeight(tall) > hudBalloonHeight(short))
    }

    /**
     * Perto do canto a janela aberta é presa na tela, e centrar nela o notch o
     * faria andar ao abrir. O notch fica onde estava; quem se ajusta é o balão.
     */
    @Test
    fun `abrir perto do canto nao move o notch na tela`() {
        val accounts = listOf(account("Padrão", "Crítico", listOf("5h" to "88%", "7d" to "9%")))
        for (edge in HudEdge.entries) {
            for (fraction in listOf(0f, 0.02f, 0.5f, 0.98f, 1f)) {
                val sizes = hudNotchSizes(accounts, edge, "", true, false)
                val closed = hudRestWindowBounds(edge, fraction, sizes, screen)
                val open = hudOpenWindowBounds(edge, fraction, sizes, screen)
                val closedCenter = (if (edge.isHorizontal) closed.x else closed.y) + closed.notchCenterInWindow
                val openCenter = (if (edge.isHorizontal) open.x else open.y) + open.notchCenterInWindow
                assertEquals(closedCenter, openCenter, "$edge em $fraction")
                // As alças cabem na janela aberta, mesmo no canto.
                val openAlong = if (edge.isHorizontal) open.size.width else open.size.height
                val handlesHalf = (if (edge.isHorizontal) sizes.withHandles.width else sizes.withHandles.height) / 2
                assertTrue(open.notchCenterInWindow - handlesHalf >= 0.dp, "$edge em $fraction: mão fora")
                assertTrue(open.notchCenterInWindow + handlesHalf <= openAlong, "$edge em $fraction: engrenagem fora")
            }
        }
    }

    /**
     * Janela transparente que muda de origem mostra um quadro do conteúdo antigo
     * no lugar novo — o notch pulava ao passar o ponteiro. Parada e aberta, ela
     * tem o mesmo começo e o mesmo comprimento ao longo da borda; em cima e à
     * esquerda a origem inteira fica, e a janela só cresce para dentro da tela.
     */
    @Test
    fun `abrir nao muda a origem da janela ao longo da borda`() {
        val accounts = listOf(account("Padrão", "Crítico", listOf("5h" to "88%", "7d" to "9%")))
        for (edge in HudEdge.entries) {
            for (fraction in listOf(0f, 0.02f, 0.5f, 0.82f, 1f)) {
                val sizes = hudNotchSizes(accounts, edge, "", true, false)
                val rest = hudRestWindowBounds(edge, fraction, sizes, screen)
                val open = hudOpenWindowBounds(edge, fraction, sizes, screen)
                if (edge.isHorizontal) {
                    assertEquals(rest.x, open.x, "$edge em $fraction: começo")
                    assertEquals(rest.size.width, open.size.width, "$edge em $fraction: comprimento")
                } else {
                    assertEquals(rest.y, open.y, "$edge em $fraction: começo")
                    assertEquals(rest.size.height, open.size.height, "$edge em $fraction: comprimento")
                }
                if (edge == HudEdge.TOP || edge == HudEdge.LEFT) {
                    assertEquals(rest.x to rest.y, open.x to open.y, "$edge em $fraction: origem")
                }
            }
        }
    }

    /**
     * Issue #288: o arrasto começava da origem da janela aberta com o tamanho da
     * de arrasto, e embaixo e à direita o notch saltava o tamanho do balão para
     * longe do ponteiro. O notch tem de estar no mesmo ponto nos três estados.
     */
    @Test
    fun `comecar o arrasto nao tira o notch do lugar em nenhuma borda`() {
        val accounts = listOf(account("Padrão", "Crítico", listOf("5h" to "88%", "7d" to "9%")))
        for (edge in HudEdge.entries) {
            for (fraction in listOf(0f, 0.02f, 0.5f, 0.82f, 1f)) {
                val sizes = hudNotchSizes(accounts, edge, "", true, false)
                val drag = hudDragWindowBounds(edge, fraction, sizes, screen)
                val atDrag = notchOrigin(edge, drag, sizes)
                assertEquals(notchOrigin(edge, hudOpenWindowBounds(edge, fraction, sizes, screen), sizes), atDrag, "$edge em $fraction: aberta")
                assertEquals(notchOrigin(edge, hudRestWindowBounds(edge, fraction, sizes, screen), sizes), atDrag, "$edge em $fraction: parada")
                // Simétrica: o centro da janela é o do notch, que é o que o encaixe lê.
                val along = if (edge.isHorizontal) drag.size.width else drag.size.height
                assertEquals(along / 2, drag.notchCenterInWindow, "$edge em $fraction: centro")
            }
        }
    }

    /**
     * Issue #288: o notch mora na área útil. Com a barra de tarefas embaixo o
     * notch de baixo encosta nela, e não passa por baixo; com a barra em cima ou
     * à esquerda, a origem deslocada é respeitada.
     */
    @Test
    fun `o notch encosta na barra de tarefas e nao passa por baixo dela`() {
        val accounts = listOf(account("Padrão", "Crítico", listOf("5h" to "88%", "7d" to "9%")))
        val taskbarBottom = ScreenWorkArea(0.dp, 0.dp, DpSize(1920.dp, 1032.dp))
        val bottomSizes = hudNotchSizes(accounts, HudEdge.BOTTOM, "", true, false)
        for (bounds in listOf(
            hudRestWindowBounds(HudEdge.BOTTOM, 0.5f, bottomSizes, taskbarBottom),
            hudOpenWindowBounds(HudEdge.BOTTOM, 0.5f, bottomSizes, taskbarBottom),
            hudDragWindowBounds(HudEdge.BOTTOM, 0.5f, bottomSizes, taskbarBottom)
        )) {
            assertEquals(1032.dp, bounds.y + bounds.size.height)
        }

        val taskbarTopLeft = ScreenWorkArea(48.dp, 40.dp, DpSize(1872.dp, 1040.dp))
        val top = hudRestWindowBounds(HudEdge.TOP, 0.5f, hudNotchSizes(accounts, HudEdge.TOP, "", true, false), taskbarTopLeft)
        assertEquals(40.dp, top.y)
        val left = hudRestWindowBounds(HudEdge.LEFT, 0.5f, hudNotchSizes(accounts, HudEdge.LEFT, "", true, false), taskbarTopLeft)
        assertEquals(48.dp, left.x)
    }

    /** O canto de cima à esquerda do notch na tela, como `HudNotch` o posiciona na janela. */
    private fun notchOrigin(edge: HudEdge, bounds: HudWindowBounds, sizes: HudNotchSizes): Pair<Float, Float> {
        val notch = sizes.collapsed
        val alongStart = bounds.notchCenterInWindow - (if (edge.isHorizontal) notch.width else notch.height) / 2
        val origin = when (edge) {
            HudEdge.TOP -> bounds.x + alongStart to bounds.y
            HudEdge.BOTTOM -> bounds.x + alongStart to bounds.y + bounds.size.height - notch.height
            HudEdge.LEFT -> bounds.x to bounds.y + alongStart
            HudEdge.RIGHT -> bounds.x + bounds.size.width - notch.width to bounds.y + alongStart
        }
        return origin.first.value to origin.second.value
    }

    /** A órbita de sessão ativa gira por fora do anel e cabe no respiro do notch e entre dois anéis. */
    @Test
    fun `a orbita de sessao ativa cabe em volta do anel`() {
        val reach = appUsageRingOrbitReach(HUD_RING_STROKE, HUD_RING_GAP)
        assertTrue(reach <= HUD_NOTCH_PADDING_ACROSS, "passa do respiro do notch")
        assertTrue(reach * 2 <= HUD_ITEM_GAP, "encosta no anel vizinho")
    }

    @Test
    fun `cotas vizinhas do mesmo grupo formam uma caixa so`() {
        fun quota(group: String?) = HudQuota("7d", "5%", 0.05f, AppTone.OK, resetText = null, hasForecast = true, group = group)
        val runs = hudQuotaRuns(listOf(quota(null), quota("Gemini"), quota("Gemini"), quota("Claude/GPT")))

        assertEquals(listOf(null, "Gemini", "Claude/GPT"), runs.map { run -> run.group })
        assertEquals(listOf(1, 2, 1), runs.map { run -> run.quotas.size })
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
        // Sem conta sobram as alças e o balão da engrenagem: é a saída do modo.
        assertEquals(hudAppBalloonHeight(hasUpdateIndicator = false), sizes.balloon.height)
        assertEquals(sizes.collapsed.height + HUD_BALLOON_GAP + sizes.balloon.height, sizes.expanded.height)
        assertEquals(sizes.collapsed.height, sizes.withHandles.height)
        assertEquals(sizes.collapsed.width + (HUD_HANDLE_GAP + HUD_HANDLE_SIZE) * 2, sizes.withHandles.width)
    }

    /** A ação da atualização é uma linha a mais no balão da engrenagem, e a janela aberta a reserva. */
    @Test
    fun `a acao da atualizacao cresce o balao da engrenagem`() {
        val indicatorOnly = hudNotchSizes(emptyList(), HudEdge.TOP, "Carregando", showsCountdown = true, hasUpdateIndicator = true)
        val withAction = hudNotchSizes(
            emptyList(), HudEdge.TOP, "Carregando", showsCountdown = true, hasUpdateIndicator = true, hasUpdateAction = true
        )
        assertEquals(hudAppBalloonHeight(hasUpdateIndicator = true, hasUpdateAction = true), withAction.balloon.height)
        assertTrue(withAction.balloon.height > indicatorOnly.balloon.height)
        assertTrue(withAction.expanded.height > indicatorOnly.expanded.height)
        // Parado nada muda: a ação só existe no balão.
        assertEquals(indicatorOnly.collapsed, withAction.collapsed)
    }

    @Test
    fun `a palavra longa quebra em duas linhas so na coluna vertical`() {
        assertEquals(2, verticalWordLines("Sem projeção"))
        assertEquals(1, verticalWordLines("Crítico"))
        val single = hudNotchSizes(listOf(account("A", "Normal", listOf("5h" to "9%"))), HudEdge.LEFT, "", false, false)
        val double = hudNotchSizes(listOf(account("A", "Sem projeção", listOf("5h" to "9%"))), HudEdge.LEFT, "", false, false)
        assertEquals(HUD_WORD_LINE, double.collapsed.height - single.collapsed.height)
    }

    /**
     * Sete APIs numa tela de notebook: a faixa completa atravessava a borda. Acima
     * do comprimento permitido ela vira a célula do Codenotch — anel e percentual
     * embaixo, sem a palavra —, e com poucas contas continua completa.
     */
    @Test
    fun `contas demais para a borda deixam a faixa compacta`() {
        val many = (1..7).map { index -> account("Conta $index", "Sem projeção", listOf("5h" to "3%")) }
        val few = many.take(2)
        for (edge in HudEdge.entries) {
            val budget = if (edge.isHorizontal) 1366.dp * HUD_MAX_ALONG_FRACTION else 768.dp * HUD_MAX_ALONG_FRACTION
            val unbounded = hudNotchSizes(many, edge, "", showsCountdown = true, hasUpdateIndicator = false)
            val bounded = hudNotchSizes(many, edge, "", showsCountdown = true, hasUpdateIndicator = false, maxAlong = budget)
            val along = { size: DpSize -> if (edge.isHorizontal) size.width else size.height }

            assertTrue(!unbounded.compact && bounded.compact, "$edge: sete contas deviam compactar")
            // Deitada sai a palavra ao lado do anel e a faixa cai para menos da metade;
            // em pé sai só a linha da palavra embaixo, e o ganho é menor.
            val ratio = if (edge.isHorizontal) 0.6f else 0.8f
            assertTrue(along(bounded.collapsed) < along(unbounded.collapsed) * ratio, "$edge: compacta devia encolher")
            assertTrue(!hudNotchSizes(few, edge, "", true, false, maxAlong = budget).compact, "$edge: duas contas cabem completas")
        }
    }

    @Test
    fun `sem contas a linha de carregamento nunca compacta`() {
        assertTrue(!hudNotchSizes(emptyList(), HudEdge.TOP, "Carregando", true, false, maxAlong = 10.dp).compact)
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

    /**
     * Uma linha por janela (#286): o notch engrossa só o que as linhas pedem.
     * Com uma cota a espessura é a do anel, como antes.
     */
    @Test
    fun `cada janela a mais engrossa o notch de cima em uma linha`() {
        val quotas = listOf("5h" to "88%", "7d" to "9%", "30d" to "40%")
        val thickness = (1..3).map { count ->
            val sizes = hudNotchSizes(listOf(account("Padrão", "Ok", quotas.take(count))), HudEdge.TOP, "Carregando", false, false)
            sizes.collapsed.height - HUD_NOTCH_PADDING_ACROSS * 2
        }

        assertEquals(listOf(HUD_RING_SIZE, HUD_STRIP_LINE * 2 + HUD_WORD_LINE, HUD_STRIP_LINE * 3 + HUD_WORD_LINE), thickness)
    }

    @Test
    fun `na borda lateral as linhas somam na altura e a mais larga decide a coluna`() {
        val one = hudNotchSizes(listOf(account("Padrão", "Ok", listOf("5h" to "88%"))), HudEdge.RIGHT, "Carregando", false, false)
        val two = hudNotchSizes(listOf(account("Padrão", "Ok", listOf("5h" to "88%", "7d" to "100%"))), HudEdge.RIGHT, "Carregando", false, false)

        assertEquals(HUD_STRIP_LINE * 2 - HUD_PERCENT_LINE, two.collapsed.height - one.collapsed.height)
        assertEquals(maxOf(HUD_RING_SIZE, wordWidth("7d 100%")), two.collapsed.width - HUD_NOTCH_PADDING_ACROSS * 2)
    }

    /** Compacta, a célula é a cota em foco com a janela — não uma linha por anel. */
    @Test
    fun `compacta a celula mede so a linha em foco`() {
        val accounts = (1..7).map { index -> account("Conta $index", "Ok", listOf("5h" to "88%", "7d" to "9%")) }
        val sizes = hudNotchSizes(accounts, HudEdge.TOP, "Carregando", false, false, maxAlong = 300.dp)

        assertTrue(sizes.compact)
        assertEquals(HUD_RING_SIZE + HUD_STRIP_LINE, sizes.collapsed.height - HUD_NOTCH_PADDING_ACROSS * 2)
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

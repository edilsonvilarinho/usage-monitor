package com.usagemonitor

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.usagemonitor.presentation.ui.HudSourceStatus
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.theme.AppChrome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A geometria da barra HUD (issue #164) decide o tamanho e a posição de uma
 * janela AWT sem que exista janela nenhuma — é por isso que ela é função pura, e
 * é aqui que se afirma o que ela faz. O comportamento observável no app
 * (arrastar, grudar, expandir) é a composição destas quatro decisões.
 */
class HudWindowGeometryTest {

    private val laptop = ScreenWorkArea(
        x = 0.dp,
        y = 0.dp,
        size = DpSize(1366.dp, 728.dp)
    )

    private val sources = listOf(
        HudSourceStatus(label = "Anthropic — pessoal", statusLabel = "Crítico", tone = AppTone.CRITICAL),
        HudSourceStatus(label = "Anthropic — empresa", statusLabel = "Atenção", tone = AppTone.WARNING),
        HudSourceStatus(label = "OpenCode Go", statusLabel = "Normal", tone = AppTone.OK)
    )

    // ---------------------------------------------------------------- largura

    @Test
    fun `a pilula recolhida ao ponto ocupa quase nada`() {
        val width = hudPillWidth(
            statusLabel = "Normal",
            sourceLabel = "Anthropic — pessoal",
            resetLabel = "Reinício: Ter 22h59 BRT",
            dotOnly = true
        )

        // O ponto de 6dp mais 8dp de padding de cada lado. O texto não entra:
        // recolhido, ele não é composto.
        assertEquals(HUD_PILL_DOT_ONLY_PADDING * 2 + 6.dp, width)
    }

    @Test
    fun `a pilula sem fonte nem reset e mais estreita que a pilula cheia`() {
        val loading = hudPillWidth(statusLabel = "Carregando", sourceLabel = null, resetLabel = null)
        val full = hudPillWidth(
            statusLabel = "Carregando",
            sourceLabel = "Anthropic — pessoal",
            resetLabel = "Reinício: Ter 22h59 BRT"
        )

        assertTrue(loading < full, "esperava $loading < $full")
    }

    /**
     * O caso que abriu esta passada: 320dp fixos capturando clique de quem está
     * atrás para mostrar uma palavra. Medido pelo conteúdo, "Normal" não chega
     * perto do teto.
     */
    @Test
    fun `estado normal sem fonte nao chega ao teto de largura`() {
        val width = hudPillWidth(statusLabel = "Normal", sourceLabel = null, resetLabel = null)

        assertTrue(width < HUD_PILL_MAX_WIDTH / 2, "esperava $width bem abaixo do teto")
    }

    @Test
    fun `rotulo longo demais para no teto`() {
        val width = hudPillWidth(
            statusLabel = "Crítico",
            sourceLabel = "Anthropic — conta corporativa da empresa inteira",
            resetLabel = "Reinício: Ter 22h59 BRT"
        )

        assertEquals(HUD_PILL_MAX_WIDTH, width)
    }

    @Test
    fun `o painel sem fonte nenhuma nao tem largura`() {
        assertEquals(0.dp, hudPanelWidth(emptyList()))
    }

    @Test
    fun `o painel e medido pela linha mais larga`() {
        val one = hudPanelWidth(listOf(sources[2]))
        val all = hudPanelWidth(sources)

        assertTrue(one < all, "esperava $one < $all")
        assertTrue(all <= HUD_PILL_MAX_WIDTH, "esperava $all dentro do teto")
    }

    // ----------------------------------------------------------------- altura

    @Test
    fun `colapsada a janela tem a altura do token do cromo`() {
        val size = hudWindowSize(
            pillWidth = 200.dp,
            panelWidth = 280.dp,
            sourceCount = 3,
            expanded = false
        )

        assertEquals(AppChrome.hud, size.height)
        assertEquals(200.dp, size.width)
    }

    @Test
    fun `expandida a janela cresce uma linha por fonte`() {
        val two = hudWindowSize(pillWidth = 200.dp, panelWidth = 280.dp, sourceCount = 2, expanded = true)
        val three = hudWindowSize(pillWidth = 200.dp, panelWidth = 280.dp, sourceCount = 3, expanded = true)

        assertEquals(HUD_SOURCE_ROW_HEIGHT, three.height - two.height)
        assertTrue(three.height > AppChrome.hud)
    }

    /**
     * Expandir não pode estreitar a faixa que está debaixo do ponteiro, nem
     * encolher abaixo da pílula quando o painel for mais estreito que ela.
     */
    @Test
    fun `expandida a largura e o maximo entre pilula e painel`() {
        val painelLargo = hudWindowSize(pillWidth = 120.dp, panelWidth = 280.dp, sourceCount = 3, expanded = true)
        val pilulaLarga = hudWindowSize(pillWidth = 300.dp, panelWidth = 180.dp, sourceCount = 3, expanded = true)

        assertEquals(280.dp, painelLargo.width)
        assertEquals(300.dp, pilulaLarga.width)
    }

    /** Sem fonte nenhuma não há painel: expandir não muda nada. */
    @Test
    fun `expandida sem fontes continua colapsada`() {
        val size = hudWindowSize(pillWidth = 120.dp, panelWidth = 0.dp, sourceCount = 0, expanded = true)

        assertEquals(AppChrome.hud, size.height)
        assertEquals(120.dp, size.width)
    }

    // --------------------------------------------------------------- expansão

    @Test
    fun `no topo da tela o painel cresce para baixo`() {
        val collapsed = DpSize(200.dp, AppChrome.hud)
        val expanded = DpSize(280.dp, 120.dp)

        val position = hudExpandedPosition(
            collapsedX = 400.dp,
            collapsedY = 0.dp,
            collapsedSize = collapsed,
            expandedSize = expanded,
            workArea = laptop
        )

        assertEquals(0.dp, position.y)
    }

    /**
     * Grudada acima da barra de tarefas — o encaixe pedido —, crescer para baixo
     * jogaria a lista para fora da tela.
     */
    @Test
    fun `na borda de baixo o painel cresce para cima`() {
        val collapsed = DpSize(200.dp, AppChrome.hud)
        val expanded = DpSize(280.dp, 120.dp)
        val bottom = laptop.size.height - AppChrome.hud

        val position = hudExpandedPosition(
            collapsedX = 400.dp,
            collapsedY = bottom,
            collapsedSize = collapsed,
            expandedSize = expanded,
            workArea = laptop
        )

        // O rodapé da janela expandida coincide com o rodapé da pílula.
        assertEquals(bottom + AppChrome.hud - 120.dp, position.y)
    }

    @Test
    fun `encostada a direita o painel alarga para a esquerda`() {
        val collapsed = DpSize(200.dp, AppChrome.hud)
        val expanded = DpSize(300.dp, 120.dp)
        val right = laptop.size.width - 200.dp

        val position = hudExpandedPosition(
            collapsedX = right,
            collapsedY = 0.dp,
            collapsedSize = collapsed,
            expandedSize = expanded,
            workArea = laptop
        )

        assertEquals(laptop.size.width - 300.dp, position.x)
    }

    @Test
    fun `encostada a esquerda o painel alarga para a direita`() {
        val collapsed = DpSize(200.dp, AppChrome.hud)
        val expanded = DpSize(300.dp, 120.dp)

        val position = hudExpandedPosition(
            collapsedX = 0.dp,
            collapsedY = 0.dp,
            collapsedSize = collapsed,
            expandedSize = expanded,
            workArea = laptop
        )

        assertEquals(0.dp, position.x)
    }

    @Test
    fun `sem medida da tela a expansao nao move a janela`() {
        val position = hudExpandedPosition(
            collapsedX = 400.dp,
            collapsedY = 10.dp,
            collapsedSize = DpSize(200.dp, AppChrome.hud),
            expandedSize = DpSize(280.dp, 120.dp),
            workArea = ScreenWorkArea.Unknown
        )

        assertEquals(400.dp, position.x)
        assertEquals(10.dp, position.y)
    }

    // ----------------------------------------------------------------- encaixe

    @Test
    fun `solta perto da borda esquerda gruda nela`() {
        val position = snapHudPosition(
            x = 9.dp,
            y = 300.dp,
            size = DpSize(200.dp, AppChrome.hud),
            workArea = laptop
        )

        assertEquals(0.dp, position.x)
        assertEquals(300.dp, position.y)
    }

    @Test
    fun `solta perto da borda direita gruda rente a ela`() {
        val size = DpSize(200.dp, AppChrome.hud)

        val position = snapHudPosition(
            x = laptop.size.width - 200.dp - 10.dp,
            y = 300.dp,
            size = size,
            workArea = laptop
        )

        assertEquals(laptop.size.width - 200.dp, position.x)
    }

    /**
     * A borda de baixo da área útil é o encaixe "acima da barra de tarefas":
     * `maximumWindowBounds` já desconta a barra.
     */
    @Test
    fun `solta perto da borda de baixo gruda acima da barra de tarefas`() {
        val size = DpSize(200.dp, AppChrome.hud)
        val bottom = laptop.size.height - AppChrome.hud

        val position = snapHudPosition(
            x = 400.dp,
            y = bottom - 12.dp,
            size = size,
            workArea = laptop
        )

        assertEquals(bottom, position.y)
    }

    @Test
    fun `solta longe de qualquer borda fica onde foi solta`() {
        val position = snapHudPosition(
            x = 500.dp,
            y = 300.dp,
            size = DpSize(200.dp, AppChrome.hud),
            workArea = laptop
        )

        assertEquals(500.dp, position.x)
        assertEquals(300.dp, position.y)
    }

    /** Arrastar para fora da tela é o caminho curto para uma janela irrecuperável. */
    @Test
    fun `solta fora da area util volta para dentro`() {
        val position = snapHudPosition(
            x = 2000.dp,
            y = (-80).dp,
            size = DpSize(200.dp, AppChrome.hud),
            workArea = laptop
        )

        assertEquals(laptop.size.width - 200.dp, position.x)
        assertEquals(0.dp, position.y)
    }

    @Test
    fun `origem deslocada por barra de tarefas no topo e respeitada no encaixe`() {
        val topBar = ScreenWorkArea(x = 0.dp, y = 48.dp, size = DpSize(1366.dp, 720.dp))

        val position = snapHudPosition(
            x = 400.dp,
            y = 52.dp,
            size = DpSize(200.dp, AppChrome.hud),
            workArea = topBar
        )

        assertEquals(48.dp, position.y)
    }

    @Test
    fun `sem medida da tela nada gruda`() {
        val position = snapHudPosition(
            x = 4.dp,
            y = 4.dp,
            size = DpSize(200.dp, AppChrome.hud),
            workArea = ScreenWorkArea.Unknown
        )

        assertEquals(4.dp, position.x)
        assertEquals(4.dp, position.y)
    }

    @Test
    fun `o teto de largura continua sendo o valor do contrato`() {
        assertEquals(320.dp, HUD_PILL_MAX_WIDTH)
        assertEquals(20.dp, HUD_SOURCE_ROW_HEIGHT)
    }
}

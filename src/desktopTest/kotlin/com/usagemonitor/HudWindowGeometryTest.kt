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
 * (arrastar, grudar, recolher) é a composição destas decisões.
 */
class HudWindowGeometryTest {

    private val laptop = ScreenWorkArea(
        x = 0.dp,
        y = 0.dp,
        size = DpSize(1366.dp, 728.dp)
    )

    private val sources = listOf(
        source("Anthropic — pessoal", "Crítico", AppTone.CRITICAL, "92%", "Ter 22h59"),
        source("Anthropic — empresa", "Atenção", AppTone.WARNING, "41%", "4h12"),
        source("OpenCode Go", "Normal", AppTone.OK, "12%", null)
    )

    private fun source(
        label: String,
        statusLabel: String,
        tone: AppTone,
        percentLabel: String,
        resetLabel: String?
    ) = HudSourceStatus(
        label = label,
        statusLabel = statusLabel,
        tone = tone,
        percentLabel = percentLabel,
        resetLabel = resetLabel
    )

    private fun size(
        sources: List<HudSourceStatus> = this.sources,
        footerLabel: String? = null,
        fallbackLabel: String = "Carregando",
        dotOnly: Boolean = false
    ) = hudWindowSize(
        sources = sources,
        footerLabel = footerLabel,
        fallbackLabel = fallbackLabel,
        dotOnly = dotOnly
    )

    // ------------------------------------------------------------------ ponto

    @Test
    fun `recolhida ao ponto a janela ocupa quase nada`() {
        val recolhida = size(dotOnly = true)

        assertEquals(HUD_PILL_DOT_ONLY_PADDING * 2 + 6.dp, recolhida.width)
        assertEquals(AppChrome.hud, recolhida.height)
    }

    /**
     * O caso que abriu esta passada: 320dp fixos capturando clique de quem está
     * atrás para mostrar uma palavra. Recolhida, a janela é o ponto.
     */
    @Test
    fun `recolhida ao ponto e muito menor que o painel`() {
        assertTrue(size(dotOnly = true).width < size().width / 5)
    }

    // ---------------------------------------------------------------- largura

    @Test
    fun `a largura e a da linha mais larga, nao a da primeira`() {
        val soAMaisEstreita = size(sources = listOf(sources[2]))
        val todas = size()

        assertTrue(soAMaisEstreita.width < todas.width, "esperava ${soAMaisEstreita.width} < ${todas.width}")
    }

    @Test
    fun `rotulo longo demais para no teto`() {
        val longa = size(
            sources = listOf(
                source(
                    "Anthropic — conta corporativa da empresa inteira e mais um pouco",
                    "Crítico",
                    AppTone.CRITICAL,
                    "92%",
                    "Ter 22h59"
                )
            )
        )

        assertEquals(HUD_PILL_MAX_WIDTH, longa.width)
    }

    /**
     * O teto subiu de 320 para 420 quando a linha passou a ter quatro colunas.
     * Com 320, um rótulo de conta típico não cabia e toda linha truncava.
     */
    @Test
    fun `uma linha tipica cabe sem truncar`() {
        assertTrue(size().width < HUD_PILL_MAX_WIDTH, "esperava ${size().width} abaixo do teto")
    }

    @Test
    fun `o rodape alarga a janela quando e a linha mais larga`() {
        val comRodapeCurto = size(footerLabel = "1 sessão")
        val comRodapeLongo = size(
            footerLabel = "3 sessões · \$12.34 · 1.2M tok · e mais um bocado de texto aqui"
        )

        assertEquals(size().width, comRodapeCurto.width)
        assertTrue(comRodapeLongo.width > comRodapeCurto.width)
    }

    // ----------------------------------------------------------------- altura

    @Test
    fun `a janela cresce uma linha por fonte`() {
        val duas = size(sources = sources.take(2))
        val tres = size()

        assertEquals(HUD_SOURCE_ROW_HEIGHT, tres.height - duas.height)
    }

    /**
     * O rodapé é um **bloco**, não uma linha solta: ele tem o mesmo padding
     * vertical da lista. Contar só a divisória mais a linha deixava a janela 8dp
     * mais curta que o conteúdo, e o texto do rodapé aparecia cortado ao meio na
     * borda de baixo.
     */
    @Test
    fun `o rodape acrescenta divisoria, padding e linha`() {
        val semRodape = size()
        val comRodape = size(footerLabel = "2 sessões")

        assertEquals(
            1.dp + HUD_PANEL_VERTICAL_PADDING * 2 + HUD_SOURCE_ROW_HEIGHT,
            comRodape.height - semRodape.height
        )
    }

    /** Zero linhas dariam altura nula, que o usuário leria como o app ter sumido. */
    @Test
    fun `sem fonte nenhuma sobra a linha de carregamento`() {
        val vazia = size(sources = emptyList())

        assertEquals(HUD_PANEL_VERTICAL_PADDING * 2 + HUD_SOURCE_ROW_HEIGHT, vazia.height)
        assertTrue(vazia.width > 0.dp)
    }

    // ------------------------------------------------------------- ancoragem

    @Test
    fun `no topo da tela a janela cresce para baixo`() {
        val position = hudWindowPosition(
            anchorX = 400.dp,
            anchorY = 0.dp,
            anchorSize = DpSize(280.dp, AppChrome.hud),
            windowSize = DpSize(280.dp, 120.dp),
            workArea = laptop
        )

        assertEquals(0.dp, position.y)
    }

    /**
     * Grudada acima da barra de tarefas — o encaixe pedido —, crescer para baixo
     * jogaria as linhas de baixo para fora da tela.
     */
    @Test
    fun `na borda de baixo a janela cresce para cima`() {
        val anchorHeight = AppChrome.hud
        val bottom = laptop.size.height - anchorHeight

        val position = hudWindowPosition(
            anchorX = 400.dp,
            anchorY = bottom,
            anchorSize = DpSize(280.dp, anchorHeight),
            windowSize = DpSize(280.dp, 120.dp),
            workArea = laptop
        )

        assertEquals(bottom + anchorHeight - 120.dp, position.y)
    }

    @Test
    fun `encostada a direita a janela alarga para a esquerda`() {
        val right = laptop.size.width - 200.dp

        val position = hudWindowPosition(
            anchorX = right,
            anchorY = 0.dp,
            anchorSize = DpSize(200.dp, AppChrome.hud),
            windowSize = DpSize(300.dp, 120.dp),
            workArea = laptop
        )

        assertEquals(laptop.size.width - 300.dp, position.x)
    }

    @Test
    fun `encostada a esquerda a janela alarga para a direita`() {
        val position = hudWindowPosition(
            anchorX = 0.dp,
            anchorY = 0.dp,
            anchorSize = DpSize(200.dp, AppChrome.hud),
            windowSize = DpSize(300.dp, 120.dp),
            workArea = laptop
        )

        assertEquals(0.dp, position.x)
    }

    /**
     * Recolher ao ponto é a janela **encolhendo**, e a regra é a mesma: a quina
     * mais próxima da borda fica onde estava. Sem isso o ponto saltaria para
     * dentro da tela toda vez que o risco baixasse.
     */
    @Test
    fun `ao recolher no canto inferior direito o ponto fica na mesma quina`() {
        val panel = DpSize(300.dp, 120.dp)
        val dot = DpSize(22.dp, AppChrome.hud)
        val anchorX = laptop.size.width - panel.width
        val anchorY = laptop.size.height - panel.height

        val position = hudWindowPosition(
            anchorX = anchorX,
            anchorY = anchorY,
            anchorSize = panel,
            windowSize = dot,
            workArea = laptop
        )

        assertEquals(laptop.size.width - dot.width, position.x)
        assertEquals(laptop.size.height - dot.height, position.y)
    }

    @Test
    fun `sem medida da tela a ancoragem nao move a janela`() {
        val position = hudWindowPosition(
            anchorX = 400.dp,
            anchorY = 10.dp,
            anchorSize = DpSize(200.dp, AppChrome.hud),
            windowSize = DpSize(280.dp, 120.dp),
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
        val position = snapHudPosition(
            x = laptop.size.width - 200.dp - 10.dp,
            y = 300.dp,
            size = DpSize(200.dp, AppChrome.hud),
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
    fun `os valores de contrato continuam onde estavam`() {
        assertEquals(420.dp, HUD_PILL_MAX_WIDTH)
        assertEquals(20.dp, HUD_SOURCE_ROW_HEIGHT)
    }
}

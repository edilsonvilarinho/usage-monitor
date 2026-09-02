package com.usagemonitor

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.usagemonitor.presentation.ui.HudQuotaChip
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

    private fun source(
        label: String,
        statusLabel: String,
        tone: AppTone,
        vararg quotas: Pair<String, AppTone>
    ) = HudSourceStatus(
        label = label,
        statusLabel = statusLabel,
        tone = tone,
        quotas = quotas.map { (text, chipTone) -> HudQuotaChip(text = text, tone = chipTone) }
    )

    private val sources = listOf(
        source(
            "INFORMATA2", "Crítico", AppTone.CRITICAL,
            "5h 28%" to AppTone.OK,
            "7d 9%" to AppTone.CRITICAL
        ),
        source(
            "Padrão", "Atenção", AppTone.WARNING,
            "5h 88%" to AppTone.WARNING,
            "7d 41%" to AppTone.OK
        ),
        source("Codex", "Normal", AppTone.OK, "mensal 75%" to AppTone.OK)
    )

    private fun size(
        sources: List<HudSourceStatus> = this.sources,
        fallbackLabel: String = "Carregando",
        dotOnly: Boolean = false,
        expanded: Boolean = true,
        showsCountdown: Boolean = false
    ) = hudWindowSize(
        sources = sources,
        fallbackLabel = fallbackLabel,
        dotOnly = dotOnly,
        expanded = expanded,
        showsCountdown = showsCountdown
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
                    "5h 92%" to AppTone.CRITICAL,
                    "7d 88%" to AppTone.CRITICAL
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

    /**
     * Parada, a barra é **uma** linha: listar tudo o tempo todo virou conteúdo
     * demais — dez linhas na tela para dizer o que cabe em uma.
     */
    @Test
    fun `parada a janela tem uma linha so`() {
        val parada = size(expanded = false)

        assertEquals(HUD_PANEL_VERTICAL_PADDING * 2 + HUD_SOURCE_ROW_HEIGHT, parada.height)
        assertTrue(parada.height < size().height)
    }

    /** A largura parada sai da linha de topo, não da lista que ela esconde. */
    /**
     * Parada, a largura sai da **primeira** linha só: medir pela lista escondida
     * deixaria a barra larga sem nada para mostrar ali.
     */
    @Test
    fun `parada a largura ignora as linhas escondidas`() {
        val larga = source(
            "Uma conta com nome bem comprido", "Crítico", AppTone.CRITICAL,
            "5h 92%" to AppTone.CRITICAL
        )
        val estreita = source("Ana", "Normal", AppTone.OK, "5h 1%" to AppTone.OK)

        val parada = size(sources = listOf(estreita, larga), expanded = false)
        val aberta = size(sources = listOf(estreita, larga), expanded = true)

        assertTrue(parada.width < aberta.width, "esperava ${parada.width} < ${aberta.width}")
    }

    // ------------------------------------------------------ coluna da contagem

    /**
     * A contagem até a próxima coleta (issue #185) é uma coluna a mais na linha,
     * e a janela é medida pelo conteúdo: sem reservar a largura dela, o texto
     * nasceria fora da janela.
     */
    @Test
    fun `a coluna da contagem alarga a janela`() {
        val sem = size(expanded = false)
        val com = size(expanded = false, showsCountdown = true)

        assertTrue(sem.width < com.width, "esperava ${sem.width} < ${com.width}")
        assertEquals(sem.height, com.height)
    }

    /**
     * **O caso que separa "uma coluna" de "uma por linha".** A primeira linha é a
     * estreita e a segunda é larga o bastante para continuar mandando na largura
     * mesmo depois de a primeira ganhar a contagem. Se a coluna entrasse em todas
     * as linhas, a janela cresceria; entrando só na primeira, ela não muda.
     */
    @Test
    fun `a coluna da contagem entra so na primeira linha`() {
        val estreita = source("Ana", "Normal", AppTone.OK, "5h 1%" to AppTone.OK)
        val larga = source(
            "Uma conta com nome bem comprido", "Crítico", AppTone.CRITICAL,
            "5h 92%" to AppTone.CRITICAL
        )
        val lista = listOf(estreita, larga)

        val sem = size(sources = lista, showsCountdown = false)
        val com = size(sources = lista, showsCountdown = true)

        assertEquals(sem.width, com.width)
    }

    /** Aberta, a primeira linha continua sendo a que carrega a coluna. */
    @Test
    fun `aberta a contagem alarga quando a primeira linha e a mais larga`() {
        val sem = size(showsCountdown = false)
        val com = size(showsCountdown = true)

        assertTrue(sem.width < com.width, "esperava ${sem.width} < ${com.width}")
    }

    /**
     * Enquanto nada foi coletado, "quando é a próxima tentativa" é a informação
     * mais útil que a barra tem — e a linha de carregamento é a primeira linha.
     */
    @Test
    fun `a linha de carregamento tambem reserva a contagem`() {
        val sem = size(sources = emptyList(), showsCountdown = false)
        val com = size(sources = emptyList(), showsCountdown = true)

        assertTrue(sem.width < com.width, "esperava ${sem.width} < ${com.width}")
    }

    /** Recolhida ao ponto não há texto nenhum, e portanto não há o que reservar. */
    @Test
    fun `recolhida ao ponto a contagem nao muda nada`() {
        assertEquals(
            size(dotOnly = true, showsCountdown = false),
            size(dotOnly = true, showsCountdown = true)
        )
    }

    /**
     * **O caso que fez o teto subir de 420 para 484.** Medidas com as contas
     * reais: `Anthropic — Padrão` pedia 356,9dp sem a contagem e 420,9dp com ela.
     * Com o teto antigo, a coluna nova seria paga pelo nome — exatamente o que o
     * salto anterior, de 320 para 420, existiu para evitar.
     */
    @Test
    fun `uma conta de nome realista cabe com a contagem`() {
        val realista = source(
            "Anthropic — Padrão", "Atenção", AppTone.WARNING,
            "5h 68%" to AppTone.WARNING,
            "7d 41%" to AppTone.OK
        )

        val largura = size(sources = listOf(realista), showsCountdown = true).width

        assertTrue(largura < HUD_PILL_MAX_WIDTH, "esperava $largura abaixo do teto")
    }

    /** O teto é do painel inteiro, e a coluna nova não o fura. */
    @Test
    fun `com a contagem o rotulo longo continua preso ao teto`() {
        val longa = size(
            sources = listOf(
                source(
                    "Anthropic — conta corporativa da empresa inteira e mais um pouco",
                    "Crítico",
                    AppTone.CRITICAL,
                    "5h 92%" to AppTone.CRITICAL,
                    "7d 88%" to AppTone.CRITICAL
                )
            ),
            showsCountdown = true
        )

        assertEquals(HUD_PILL_MAX_WIDTH, longa.width)
    }

    // ----------------------------------------------------------------- altura

    @Test
    fun `a janela cresce uma linha por fonte`() {
        val duas = size(sources = sources.take(2))
        val tres = size()

        assertEquals(HUD_SOURCE_ROW_HEIGHT, tres.height - duas.height)
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
        // 484 = o teto anterior (420) mais a largura da coluna da contagem. Este
        // teste reprovou a mudança, que é o trabalho dele: o valor só se move com
        // a razão escrita junto, e ela está em `HUD_PILL_MAX_WIDTH`.
        assertEquals(484.dp, HUD_PILL_MAX_WIDTH)
        assertEquals(20.dp, HUD_SOURCE_ROW_HEIGHT)
    }
}

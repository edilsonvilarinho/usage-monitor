package com.usagemonitor

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.usagemonitor.presentation.ui.HudAccount
import com.usagemonitor.presentation.ui.HudQuota
import kotlin.math.abs
import kotlin.math.ceil

/**
 * Geometria do notch da HUD: em que borda ele mora, o tamanho recolhido e o
 * aberto, e onde a janela fica.
 *
 * Função pura sobre [Dp], como [WindowScreenFit]: é a única forma de afirmar por
 * teste o que a janela vai fazer sem abrir uma janela. **É também a dona do
 * tamanho do conteúdo**: o notch composto usa estes mesmos números como tamanho
 * do contêiner, e não mede nada. Medir e devolver para a janela fecharia o laço
 * `redimensionar → recompor → medir → redimensionar`; estimar pela métrica da
 * fonte só funciona porque a escala `label*` é **mono**.
 *
 * As medidas saem em dp de composição; quem aplica à janela multiplica por
 * `uiScaleFactor(...)`, como a HUD anterior já fazia.
 */

/** A borda da tela em que o notch está colado. Enum novo, não valor novo num existente. */
internal enum class HudEdge {
    TOP, BOTTOM, LEFT, RIGHT;

    /** Colado em cima ou embaixo: as contas ficam lado a lado. */
    val isHorizontal: Boolean
        get() = this == TOP || this == BOTTOM
}

/**
 * Anel de uma conta: 36dp, três arcos concêntricos no máximo e a marca do
 * fornecedor no miolo. Era 28dp sem marca; com dois arcos o miolo de 28dp ficava
 * com 10dp, pouco para reconhecer o asterisco do Claude.
 */
internal val HUD_RING_SIZE = 36.dp

/** Espessura de cada arco e o vão entre dois arcos concêntricos. */
internal val HUD_RING_STROKE = 2.5.dp
internal val HUD_RING_GAP = 1.5.dp

/**
 * Os "ombros" côncavos que ligam o notch à borda da tela, um de cada lado. É o
 * que faz o notch ler como parte da borda — a silhueta do Codenotch — em vez de
 * uma pílula flutuando rente a ela.
 */
internal val HUD_NOTCH_SHOULDER = 8.dp

/** Raio dos cantos do lado de dentro do notch. Isento do teto de 10dp: é forma, não painel. */
internal val HUD_NOTCH_RADIUS = 14.dp

/** Margem transparente para a sombra nos três lados que não encostam na borda. */
internal val HUD_SHADOW_MARGIN = 16.dp

internal val HUD_NOTCH_PADDING_ALONG = 12.dp
internal val HUD_NOTCH_PADDING_ACROSS = 8.dp
internal val HUD_ITEM_GAP = 12.dp
internal val HUD_RING_TEXT_GAP = 6.dp

/** Linha de texto `labelMedium` (percentual) e `labelSmall` (palavra, contagem). */
internal val HUD_PERCENT_LINE = 16.dp
internal val HUD_WORD_LINE = 14.dp

/** Avanço por caractere: 12sp × 0,6 da Plex Mono, e 10sp × 0,6 + o espaçamento de 0,7. */
private const val PERCENT_ADVANCE_DP = 7.2f
private const val WORD_ADVANCE_DP = 6.7f

/** Palavra mais longa que cabe numa linha da coluna vertical antes de quebrar. */
private const val VERTICAL_WORD_LINE_CHARS = 8

/** A contagem é medida sobre `00:00` fixo: medir o relógio mudaria a largura a cada segundo. */
private const val COUNTDOWN_CHARS = 5
internal val HUD_COUNTDOWN_ICON = 12.dp

/** Vão entre o ícone e o texto da contagem, o mesmo `spacedBy` do `HudCountdown`. */
internal val HUD_COUNTDOWN_GAP = 4.dp

/**
 * Tamanho do notch e da área aberta.
 *
 * [collapsed] é o **notch**, parado ou aberto: ele não cresce mais. [expanded]
 * é o que a janela precisa com o ponteiro em cima — o notch, a folga da cauda e
 * o **maior** balão possível ao lado dele, do lado de dentro da tela. O maior, e
 * não o da conta em foco: passar de um anel para outro não pode redimensionar a
 * janela AWT, que é o tranco que a HUD já teve.
 */
internal data class HudNotchSizes(
    val collapsed: DpSize,
    val expanded: DpSize,
    /** O balão maior, com a cauda; é o teto de todos. */
    val balloon: DpSize = DpSize(0.dp, 0.dp),
    /**
     * O notch com as duas alças, uma além de cada ponta. É o tamanho da janela
     * **durante o arrasto**: simétrico ao longo da borda, o centro da janela
     * continua sendo o centro do notch, que é o que o encaixe lê.
     */
    val withHandles: DpSize = collapsed,
    /**
     * A faixa compacta do Codenotch: por conta só o anel e o percentual
     * embaixo, sem a palavra. Liga sozinha quando a faixa completa passa do
     * comprimento que a borda comporta ([hudNotchSizes], `maxAlong`).
     */
    val compact: Boolean = false
)

/**
 * O tamanho do notch e da área aberta.
 *
 * O notch mostra, por conta, o anel, o percentual da cota em foco e a palavra do
 * estado — a palavra porque cor nunca informa sozinha. O detalhe é o balão de
 * **uma** conta, a do anel sob o ponteiro, como no Codenotch.
 *
 * **Com contas demais para a borda, o notch fica compacto**: se a faixa completa
 * passa de [maxAlong], cada conta vira só anel e percentual embaixo — a célula do
 * Codenotch —, e a palavra sai. Com seis ou sete APIs numa tela de notebook a
 * faixa completa atravessava a tela inteira; compacta ela mede menos da metade.
 * A palavra não some da HUD: continua no cabeçalho do balão e na descrição do
 * anel, e o tom do anel segue dizendo o estado de relance.
 *
 * Sem contas é a linha de carregamento: a palavra [fallbackLabel] no lugar dos
 * anéis, e nenhum balão.
 */
internal fun hudNotchSizes(
    accounts: List<HudAccount>,
    edge: HudEdge,
    fallbackLabel: String,
    showsCountdown: Boolean,
    hasUpdateIndicator: Boolean,
    /** O comprimento que a faixa completa pode ter antes de virar compacta. */
    maxAlong: Dp = Dp.Infinity
): HudNotchSizes {
    val full = if (edge.isHorizontal) {
        horizontalCollapsed(accounts, fallbackLabel, showsCountdown, hasUpdateIndicator, compact = false)
    } else {
        verticalCollapsed(accounts, fallbackLabel, showsCountdown, hasUpdateIndicator, compact = false)
    }
    val fullAlong = if (edge.isHorizontal) full.width else full.height
    val compact = accounts.isNotEmpty() && fullAlong > maxAlong
    val collapsed = when {
        !compact -> full
        edge.isHorizontal -> horizontalCollapsed(accounts, fallbackLabel, showsCountdown, hasUpdateIndicator, compact = true)
        else -> verticalCollapsed(accounts, fallbackLabel, showsCountdown, hasUpdateIndicator, compact = true)
    }
    val handlesReach = (HUD_HANDLE_GAP + HUD_HANDLE_SIZE) * 2
    val withHandles = if (edge.isHorizontal) {
        DpSize(collapsed.width + handlesReach, collapsed.height)
    } else {
        DpSize(collapsed.width, collapsed.height + handlesReach)
    }
    // O balão da engrenagem existe mesmo sem conta nenhuma: é a saída do modo.
    val tallest = maxOf(
        accounts.maxOfOrNull { account -> hudBalloonHeight(account) } ?: 0.dp,
        hudAppBalloonHeight(hasUpdateIndicator)
    )
    // O balão não gira com a borda: é texto, e fica sempre de pé.
    val balloon = DpSize(HUD_BALLOON_WIDTH, tallest)
    val expanded = if (edge.isHorizontal) {
        DpSize(maxOf(withHandles.width, balloon.width), collapsed.height + HUD_BALLOON_GAP + balloon.height)
    } else {
        DpSize(collapsed.width + HUD_BALLOON_GAP + balloon.width, maxOf(withHandles.height, balloon.height))
    }
    return HudNotchSizes(collapsed = collapsed, expanded = expanded, balloon = balloon, withHandles = withHandles, compact = compact)
}

/**
 * As alças do notch aberto, uma além de cada ponta, como o `MoveHandle` e o
 * `SettingsOrb` do Codenotch: a mão na ponta de perto (em cima, à esquerda) e a
 * engrenagem na de longe. Um disco de 32dp é o alvo de clique que o resto do app
 * já usa; paradas elas são só um arco na margem da sombra, que a janela recolhida
 * já tem, e não custam área de clique engolida.
 */
internal val HUD_HANDLE_SIZE = 32.dp
internal val HUD_HANDLE_GAP = 6.dp

/**
 * A fração da borda que a faixa completa pode ocupar antes de virar compacta.
 * Menos da metade: o notch divide a borda com títulos e abas de outras janelas.
 */
internal const val HUD_MAX_ALONG_FRACTION = 0.45f

/** Largura do balão: o teto do card do Codenotch (246px), com folga para "Reinicia ter 21h00". */
internal val HUD_BALLOON_WIDTH = 264.dp
internal val HUD_BALLOON_PADDING = 12.dp

/** A distância entre o notch e o balão, que a cauda atravessa. */
internal val HUD_BALLOON_GAP = 10.dp

/** Base da cauda, ao longo da borda. */
internal val HUD_BALLOON_TAIL_BASE = 26.dp

/** Linhas do balão, todas de altura fixa: é o que torna a altura calculável sem medir. */
internal val HUD_BALLOON_HEADER = 24.dp
internal val HUD_BALLOON_SECTION_GAP = 10.dp
internal val HUD_BALLOON_QUOTA_TITLE = 16.dp
internal val HUD_BALLOON_BAR_ROW = 12.dp
internal val HUD_BALLOON_QUOTA_DETAIL = 14.dp
internal val HUD_BALLOON_GROUP_HEADER = 16.dp
internal val HUD_BALLOON_GROUP_PADDING = 8.dp
internal val HUD_BALLOON_FOOTER = 14.dp

/** A fileira de botões: o alvo de 28dp do rodapé. */
internal val HUD_BALLOON_ACTIONS = 28.dp

private val HUD_BALLOON_QUOTA_BLOCK = HUD_BALLOON_QUOTA_TITLE + HUD_BALLOON_BAR_ROW + HUD_BALLOON_QUOTA_DETAIL

/**
 * A altura do balão de uma conta: cabeçalho, uma seção por cota — rótulo e
 * reinício, barra, "usado · restante" —, as de mesmo grupo numa caixa sob o nome
 * dele, a linha de plano e origem e a fileira de botões do card. É a mesma sequência que `HudBalloon`
 * compõe, e `HudNotchTest` afirma que as duas batem.
 */
internal fun hudBalloonHeight(account: HudAccount): Dp {
    var height = HUD_BALLOON_PADDING * 2 + HUD_BALLOON_HEADER
    hudQuotaRuns(account.quotas).forEach { run ->
        height += if (run.group == null) {
            (HUD_BALLOON_SECTION_GAP + HUD_BALLOON_QUOTA_BLOCK) * run.quotas.size
        } else {
            HUD_BALLOON_SECTION_GAP + HUD_BALLOON_GROUP_HEADER + HUD_BALLOON_GROUP_PADDING * 2 +
                HUD_BALLOON_QUOTA_BLOCK * run.quotas.size + HUD_BALLOON_SECTION_GAP * (run.quotas.size - 1)
        }
    }
    if (account.detailLine != null) {
        height += HUD_BALLOON_SECTION_GAP + HUD_BALLOON_FOOTER
    }
    // A fileira de botões do card — o histórico existe em toda conta.
    height += HUD_BALLOON_SECTION_GAP + HUD_BALLOON_ACTIONS
    return height
}

/** As linhas do balão da engrenagem. */
internal val HUD_APP_BALLOON_CAPTION = 16.dp
internal val HUD_APP_BALLOON_MODE_ROW = 24.dp
internal val HUD_APP_BALLOON_ACTIONS = HUD_BALLOON_ACTIONS

/** Quantos modos de janela o balão da engrenagem lista: os três do rodapé. */
internal const val HUD_APP_BALLOON_MODES = 3

/**
 * A altura do balão da engrenagem: título com a contagem, os modos de janela, a
 * fileira de ações do rodapé e, quando há atualização pendente, a linha dela.
 */
internal fun hudAppBalloonHeight(hasUpdateIndicator: Boolean): Dp {
    var height = HUD_BALLOON_PADDING * 2 + HUD_BALLOON_HEADER +
        HUD_BALLOON_SECTION_GAP + HUD_APP_BALLOON_CAPTION + HUD_APP_BALLOON_MODE_ROW * HUD_APP_BALLOON_MODES +
        HUD_BALLOON_SECTION_GAP + HUD_APP_BALLOON_ACTIONS
    if (hasUpdateIndicator) {
        height += HUD_BALLOON_SECTION_GAP + HUD_BALLOON_FOOTER
    }
    return height
}

/** Cotas vizinhas do mesmo grupo, na ordem da API; grupo nulo é cota solta. */
internal data class HudQuotaRun(val group: String?, val quotas: List<HudQuota>)

internal fun hudQuotaRuns(quotas: List<HudQuota>): List<HudQuotaRun> {
    val runs = mutableListOf<HudQuotaRun>()
    quotas.forEach { quota ->
        val last = runs.lastOrNull()
        if (last != null && last.group != null && last.group == quota.group) {
            runs[runs.lastIndex] = last.copy(quotas = last.quotas + quota)
        } else {
            runs += HudQuotaRun(quota.group, listOf(quota))
        }
    }
    return runs
}

private fun horizontalCollapsed(
    accounts: List<HudAccount>,
    fallbackLabel: String,
    showsCountdown: Boolean,
    hasUpdateIndicator: Boolean,
    compact: Boolean
): DpSize {
    val items = if (accounts.isEmpty()) {
        listOf(HUD_RING_SIZE + HUD_RING_TEXT_GAP + wordWidth(fallbackLabel))
    } else {
        accounts.map { account ->
            val percent = percentWidth(account.focus?.percentText.orEmpty())
            if (compact) {
                // Compacta: o percentual embaixo do anel, sem a palavra.
                maxOf(HUD_RING_SIZE, percent)
            } else {
                HUD_RING_SIZE + HUD_RING_TEXT_GAP + maxOf(percent, wordWidth(account.statusLabel))
            }
        }
    }
    val extras = buildList {
        if (hasUpdateIndicator) add(HUD_COUNTDOWN_ICON)
        if (showsCountdown) add(countdownWidth())
    }
    val all = items + extras
    val along = all.fold(0.dp) { sum, width -> sum + width } + HUD_ITEM_GAP * (all.size - 1).coerceAtLeast(0)
    val across = if (compact) {
        HUD_RING_SIZE + HUD_PERCENT_LINE
    } else {
        maxOf(HUD_RING_SIZE, HUD_PERCENT_LINE + HUD_WORD_LINE)
    }
    return DpSize(
        width = along + HUD_NOTCH_PADDING_ALONG * 2 + HUD_NOTCH_SHOULDER * 2,
        height = across + HUD_NOTCH_PADDING_ACROSS * 2
    )
}

private fun verticalCollapsed(
    accounts: List<HudAccount>,
    fallbackLabel: String,
    showsCountdown: Boolean,
    hasUpdateIndicator: Boolean,
    compact: Boolean
): DpSize {
    val words = when {
        accounts.isEmpty() -> listOf(fallbackLabel)
        compact -> emptyList()
        else -> accounts.map { account -> account.statusLabel }
    }
    val itemHeights = if (accounts.isEmpty()) {
        listOf(HUD_RING_SIZE + HUD_WORD_LINE * verticalWordLines(fallbackLabel))
    } else {
        accounts.map { account ->
            val word = if (compact) 0.dp else HUD_WORD_LINE * verticalWordLines(account.statusLabel)
            HUD_RING_SIZE + HUD_PERCENT_LINE + word
        }
    }
    val extras = buildList {
        if (hasUpdateIndicator) add(HUD_COUNTDOWN_ICON)
        if (showsCountdown) add(HUD_COUNTDOWN_ICON + HUD_WORD_LINE)
    }
    val all = itemHeights + extras
    val along = all.fold(0.dp) { sum, height -> sum + height } + HUD_ITEM_GAP * (all.size - 1).coerceAtLeast(0)
    val widestWord = words.maxOfOrNull { word -> verticalWordLineWidth(word) } ?: 0.dp
    val widestPercent = accounts.maxOfOrNull { account -> percentWidth(account.focus?.percentText.orEmpty()) } ?: 0.dp
    val across = maxOf(HUD_RING_SIZE, widestWord, widestPercent, charWidth(COUNTDOWN_CHARS, WORD_ADVANCE_DP))
    return DpSize(
        width = across + HUD_NOTCH_PADDING_ACROSS * 2,
        height = along + HUD_NOTCH_PADDING_ALONG * 2 + HUD_NOTCH_SHOULDER * 2
    )
}

internal fun percentWidth(text: String): Dp = charWidth(text.length, PERCENT_ADVANCE_DP)

internal fun wordWidth(text: String): Dp = charWidth(text.length, WORD_ADVANCE_DP)

internal fun countdownWidth(): Dp = HUD_COUNTDOWN_ICON + HUD_COUNTDOWN_GAP + charWidth(COUNTDOWN_CHARS, WORD_ADVANCE_DP)

/**
 * Largura de [chars] caracteres mais a folga do arredondamento em pixel.
 *
 * O Skia devolve a largura da linha arredondada para cima em pixel inteiro, e
 * em densidade fracionária (115% sobre os 125% do Windows) isso passa da conta
 * em até um pixel — que nunca é mais que 1dp com densidade ≥ 1. Sem a folga a
 * faixa somava a diferença de cada texto, e o último item, a contagem, ficava
 * espremido e quebrava em "04:5" (`HudNotchTextFitTest`).
 */
private fun charWidth(chars: Int, advance: Float): Dp = ceil(chars * advance).dp + TEXT_PIXEL_ROUNDING_SLACK

private val TEXT_PIXEL_ROUNDING_SLACK = 1.dp

/** "Sem projeção" quebra em duas linhas na coluna estreita; palavra curta, uma. */
internal fun verticalWordLines(word: String): Int {
    return if (word.length > VERTICAL_WORD_LINE_CHARS && word.contains(' ')) 2 else 1
}

private fun verticalWordLineWidth(word: String): Dp {
    val longestLine = if (verticalWordLines(word) == 2) {
        word.split(' ').maxOf { part -> part.length }
    } else {
        word.length
    }
    return charWidth(longestLine, WORD_ADVANCE_DP)
}

/** Onde a janela da HUD fica e onde, dentro dela, está o centro do notch. */
internal data class HudWindowBounds(
    val x: Dp,
    val y: Dp,
    val size: DpSize,
    /** Centro do notch ao longo da borda, em coordenadas da janela. */
    val notchCenterInWindow: Dp
)

/**
 * A janela para um conteúdo de [contentSize] colado em [edge], com o centro ao
 * longo da borda na fração [offsetFraction] da tela.
 *
 * A borda da janela que encosta na tela **não** tem margem — o notch é reto ali
 * e rente —; as outras três têm [HUD_SHADOW_MARGIN] para a sombra. Perto de um
 * canto o centro do notch é preso para que [reserveAlong] caiba inteiro na tela,
 * e a janela desliza em volta dele; é por isso que o centro volta junto: quem
 * compõe o posiciona por ele.
 *
 * [reserveAlong] é o que precisa caber em volta do centro. Parado e aberto ele é
 * o notch **com as alças** (`HudNotchSizes.withHandles`): com o mesmo recorte
 * nos dois estados o notch não anda na tela ao abrir, e as alças nunca ficam
 * fora dela.
 */
internal fun hudWindowBounds(
    edge: HudEdge,
    offsetFraction: Float,
    contentSize: DpSize,
    area: ScreenWorkArea,
    margin: Dp = HUD_SHADOW_MARGIN,
    reserveAlong: Dp = if (edge.isHorizontal) contentSize.width else contentSize.height
): HudWindowBounds {
    val fraction = offsetFraction.coerceIn(0f, 1f)
    val alongContent = if (edge.isHorizontal) contentSize.width else contentSize.height
    val acrossContent = if (edge.isHorizontal) contentSize.height else contentSize.width
    val alongWindow = alongContent + margin * 2
    val acrossWindow = acrossContent + margin

    val areaStart = if (edge.isHorizontal) area.x else area.y
    val areaLength = if (edge.isHorizontal) area.size.width else area.size.height
    val lowest = areaStart + margin + reserveAlong / 2
    val highest = (areaStart + areaLength - margin - reserveAlong / 2).coerceAtLeast(lowest)
    val center = (areaStart + areaLength * fraction).coerceIn(lowest, highest)
    val start = (center - alongWindow / 2)
        .coerceAtMost(areaStart + areaLength - alongWindow)
        .coerceAtLeast(areaStart)
    val centerInWindow = center - start

    return when (edge) {
        HudEdge.TOP -> HudWindowBounds(start, area.y, DpSize(alongWindow, acrossWindow), centerInWindow)
        HudEdge.BOTTOM -> HudWindowBounds(
            start, area.y + area.size.height - acrossWindow, DpSize(alongWindow, acrossWindow), centerInWindow
        )
        HudEdge.LEFT -> HudWindowBounds(area.x, start, DpSize(acrossWindow, alongWindow), centerInWindow)
        HudEdge.RIGHT -> HudWindowBounds(
            area.x + area.size.width - acrossWindow, start, DpSize(acrossWindow, alongWindow), centerInWindow
        )
    }
}

/**
 * A janela parada: a espessura do notch e, ao longo da borda, **o comprimento da
 * aberta**, com o centro preso como o dela.
 *
 * É o que deixa a origem da janela no mesmo ponto ao abrir e ao fechar. Janela
 * transparente que muda de origem mostra um ou dois quadros do conteúdo antigo
 * no lugar novo — medido no Windows 11: o notch pulava 60px e voltava, com o
 * redimensionamento do Compose, com `setBounds` numa chamada só e com ele antes
 * do estado. Crescendo só para dentro da tela, nenhum quadro fora do lugar. O
 * preço são as duas faixas transparentes onde as alças aparecem, que engolem
 * clique também parado (C11).
 *
 * Em cima e à esquerda a origem não muda; embaixo e à direita a janela aberta
 * sobe o balão para dentro da tela e a origem anda na espessura — ali ainda sobra
 * um quadro em branco ao abrir.
 */
internal fun hudRestWindowBounds(
    edge: HudEdge,
    offsetFraction: Float,
    sizes: HudNotchSizes,
    area: ScreenWorkArea
): HudWindowBounds {
    val content = if (edge.isHorizontal) {
        DpSize(sizes.expanded.width, sizes.collapsed.height)
    } else {
        DpSize(sizes.collapsed.width, sizes.expanded.height)
    }
    return hudWindowBounds(edge, offsetFraction, content, area, reserveAlong = sizes.handlesAlong(edge))
}

/**
 * A janela aberta, com o notch **no mesmo ponto da tela** em que estava parado.
 *
 * As duas prendem o centro do notch pela mesma reserva — o notch com as alças —,
 * então abrir perto de um canto não o faz andar; quem se ajusta ao canto é o
 * balão, que o composable prende dentro da janela.
 */
internal fun hudOpenWindowBounds(
    edge: HudEdge,
    offsetFraction: Float,
    sizes: HudNotchSizes,
    area: ScreenWorkArea
): HudWindowBounds = hudWindowBounds(edge, offsetFraction, sizes.expanded, area, reserveAlong = sizes.handlesAlong(edge))

private fun HudNotchSizes.handlesAlong(edge: HudEdge): Dp = if (edge.isHorizontal) withHandles.width else withHandles.height

/** Uma posição de notch gravada: a borda e o centro ao longo dela, em fração da tela. */
internal data class HudPlacement(val edge: HudEdge, val offsetFraction: Float) {
    companion object {
        /**
         * Estreia no topo, perto da direita — onde a pílula antiga nascia —, e não
         * no centro: o centro do topo é onde fica o título das janelas
         * maximizadas, que o notch cobriria na primeira abertura.
         */
        val Default = HudPlacement(HudEdge.TOP, 0.82f)
    }
}

/**
 * Onde o notch gruda quando é solto com o centro em ([centerX], [centerY]): na
 * borda mais próxima da tela, e na fração correspondente ao longo dela. Tela sem
 * medida devolve a posição de estreia.
 */
internal fun nearestHudPlacement(centerX: Dp, centerY: Dp, area: ScreenWorkArea): HudPlacement {
    val width = area.size.width.value
    val height = area.size.height.value
    if (!width.isFinite() || !height.isFinite() || width <= 0f || height <= 0f ||
        !centerX.value.isFinite() || !centerY.value.isFinite()
    ) {
        return HudPlacement.Default
    }
    val fromLeft = (centerX - area.x).value
    val fromTop = (centerY - area.y).value
    val distances = mapOf(
        HudEdge.TOP to abs(fromTop),
        HudEdge.BOTTOM to abs(height - fromTop),
        HudEdge.LEFT to abs(fromLeft),
        HudEdge.RIGHT to abs(width - fromLeft)
    )
    val edge = distances.minByOrNull { (_, distance) -> distance }!!.key
    val fraction = if (edge.isHorizontal) fromLeft / width else fromTop / height
    return HudPlacement(edge, fraction.coerceIn(0f, 1f))
}

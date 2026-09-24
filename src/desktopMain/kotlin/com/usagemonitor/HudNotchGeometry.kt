package com.usagemonitor

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.usagemonitor.presentation.ui.HudAccount
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

/** Largura do painel aberto: rótulo, barra cápsula, percentual e reset cabem numa linha. */
internal val HUD_PANEL_WIDTH = 320.dp
internal val HUD_PANEL_PADDING = 12.dp
internal val HUD_PANEL_ROW_HEIGHT = 20.dp
internal val HUD_PANEL_BLOCK_GAP = 8.dp

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

/** Tamanho recolhido e aberto do conteúdo do notch, com os ombros. */
internal data class HudNotchSizes(val collapsed: DpSize, val expanded: DpSize)

/**
 * O tamanho do notch parado e aberto.
 *
 * Recolhido ele mostra, por conta, o anel, o percentual da cota em foco e a
 * palavra do estado — **a palavra sempre**: cor nunca informa sozinha, e no
 * notch recolhido é só isso que existe na tela. Aberto ele acrescenta o painel
 * do lado de dentro da tela, com uma linha por cota.
 *
 * Sem contas é a linha de carregamento: a palavra [fallbackLabel] no lugar dos
 * anéis.
 */
internal fun hudNotchSizes(
    accounts: List<HudAccount>,
    edge: HudEdge,
    fallbackLabel: String,
    showsCountdown: Boolean,
    hasUpdateIndicator: Boolean
): HudNotchSizes {
    val collapsed = if (edge.isHorizontal) {
        horizontalCollapsed(accounts, fallbackLabel, showsCountdown, hasUpdateIndicator)
    } else {
        verticalCollapsed(accounts, fallbackLabel, showsCountdown, hasUpdateIndicator)
    }
    val panel = panelSize(accounts)
    val expanded = if (edge.isHorizontal) {
        DpSize(maxOf(collapsed.width, panel.width + HUD_NOTCH_SHOULDER * 2), collapsed.height + panel.height)
    } else {
        DpSize(collapsed.width + panel.width, maxOf(collapsed.height, panel.height + HUD_NOTCH_SHOULDER * 2))
    }
    return HudNotchSizes(collapsed = collapsed, expanded = expanded)
}

private fun horizontalCollapsed(
    accounts: List<HudAccount>,
    fallbackLabel: String,
    showsCountdown: Boolean,
    hasUpdateIndicator: Boolean
): DpSize {
    val items = if (accounts.isEmpty()) {
        listOf(HUD_RING_SIZE + HUD_RING_TEXT_GAP + wordWidth(fallbackLabel))
    } else {
        accounts.map { account ->
            val percent = percentWidth(account.focus?.percentText.orEmpty())
            HUD_RING_SIZE + HUD_RING_TEXT_GAP + maxOf(percent, wordWidth(account.statusLabel))
        }
    }
    val extras = buildList {
        if (hasUpdateIndicator) add(HUD_COUNTDOWN_ICON)
        if (showsCountdown) add(countdownWidth())
    }
    val all = items + extras
    val along = all.fold(0.dp) { sum, width -> sum + width } + HUD_ITEM_GAP * (all.size - 1).coerceAtLeast(0)
    val across = maxOf(HUD_RING_SIZE, HUD_PERCENT_LINE + HUD_WORD_LINE)
    return DpSize(
        width = along + HUD_NOTCH_PADDING_ALONG * 2 + HUD_NOTCH_SHOULDER * 2,
        height = across + HUD_NOTCH_PADDING_ACROSS * 2
    )
}

private fun verticalCollapsed(
    accounts: List<HudAccount>,
    fallbackLabel: String,
    showsCountdown: Boolean,
    hasUpdateIndicator: Boolean
): DpSize {
    val words = if (accounts.isEmpty()) listOf(fallbackLabel) else accounts.map { account -> account.statusLabel }
    val itemHeights = if (accounts.isEmpty()) {
        listOf(HUD_RING_SIZE + HUD_WORD_LINE * verticalWordLines(fallbackLabel))
    } else {
        accounts.map { account ->
            HUD_RING_SIZE + HUD_PERCENT_LINE + HUD_WORD_LINE * verticalWordLines(account.statusLabel)
        }
    }
    val extras = buildList {
        if (hasUpdateIndicator) add(HUD_COUNTDOWN_ICON)
        if (showsCountdown) add(HUD_COUNTDOWN_ICON + HUD_WORD_LINE)
    }
    val all = itemHeights + extras
    val along = all.fold(0.dp) { sum, height -> sum + height } + HUD_ITEM_GAP * (all.size - 1).coerceAtLeast(0)
    val widestWord = words.maxOf { word -> verticalWordLineWidth(word) }
    val widestPercent = accounts.maxOfOrNull { account -> percentWidth(account.focus?.percentText.orEmpty()) } ?: 0.dp
    val across = maxOf(HUD_RING_SIZE, widestWord, widestPercent, charWidth(COUNTDOWN_CHARS, WORD_ADVANCE_DP))
    return DpSize(
        width = across + HUD_NOTCH_PADDING_ACROSS * 2,
        height = along + HUD_NOTCH_PADDING_ALONG * 2 + HUD_NOTCH_SHOULDER * 2
    )
}

/** O painel aberto: um bloco por conta, cabeçalho mais uma linha por cota. */
private fun panelSize(accounts: List<HudAccount>): DpSize {
    if (accounts.isEmpty()) {
        return DpSize(0.dp, 0.dp)
    }
    val rows = accounts.sumOf { account -> 1 + account.quotas.size }
    val height = HUD_PANEL_ROW_HEIGHT * rows +
        HUD_PANEL_BLOCK_GAP * (accounts.size - 1) +
        HUD_PANEL_PADDING * 2
    return DpSize(HUD_PANEL_WIDTH + HUD_PANEL_PADDING * 2, height)
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
 * canto a janela é presa dentro da tela e o notch desliza para dentro dela, e é
 * por isso que o centro volta junto: quem compõe o posiciona por ele.
 */
internal fun hudWindowBounds(
    edge: HudEdge,
    offsetFraction: Float,
    contentSize: DpSize,
    area: ScreenWorkArea,
    margin: Dp = HUD_SHADOW_MARGIN
): HudWindowBounds {
    val fraction = offsetFraction.coerceIn(0f, 1f)
    val alongContent = if (edge.isHorizontal) contentSize.width else contentSize.height
    val acrossContent = if (edge.isHorizontal) contentSize.height else contentSize.width
    val alongWindow = alongContent + margin * 2
    val acrossWindow = acrossContent + margin

    val areaStart = if (edge.isHorizontal) area.x else area.y
    val areaLength = if (edge.isHorizontal) area.size.width else area.size.height
    val center = areaStart + areaLength * fraction
    val start = (center - alongWindow / 2)
        .coerceAtMost(areaStart + areaLength - alongWindow)
        .coerceAtLeast(areaStart)
    val centerInWindow = (center - start)
        .coerceAtLeast(margin + alongContent / 2)
        .coerceAtMost(alongWindow - margin - alongContent / 2)

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

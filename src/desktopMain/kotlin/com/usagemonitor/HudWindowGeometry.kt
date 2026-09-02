package com.usagemonitor

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import com.usagemonitor.presentation.ui.HudSourceStatus
import com.usagemonitor.presentation.ui.components.STATUS_DOT_SIZE
import com.usagemonitor.presentation.ui.theme.AppChrome
import com.usagemonitor.presentation.ui.theme.AppSpacing
import kotlin.math.abs

/**
 * Geometria da barra HUD (issue #164): largura pelo conteúdo, altura do painel
 * expandido, encaixe nas bordas e o canto de onde o painel cresce.
 *
 * Tudo aqui é função pura sobre [Dp] — sem AWT, sem Compose, sem estado —, pelo
 * mesmo motivo de [WindowScreenFit]: é a única forma de afirmar por teste o que
 * a janela vai fazer sem abrir uma janela. `Main.kt` é quem aplica o resultado.
 *
 * **As medidas saem em dp de composição, não em dp de janela.** `mainWindowState`
 * mede na densidade do sistema e a composição roda na densidade escalada
 * (`AppTheme(uiScalePercent = …)`), então quem chama multiplica por
 * `uiScaleFactor(...)` — exatamente como o código de ancoragem já fazia com o
 * antigo `HUD_PILL_WIDTH_DP`.
 */

/**
 * Teto da largura da pílula.
 *
 * Era largura **fixa** (`HUD_PILL_WIDTH_DP`, 320): a faixa media 320dp mesmo
 * mostrando "Normal", e o retângulo inteiro captura clique de quem estiver
 * atrás — a queixa que abriu esta passada. Virou teto: a pílula mede o conteúdo
 * e para aqui. O papel que a largura fixa cumpria — não mudar de tamanho a cada
 * coleta — continua sendo cumprido por este limite mais as reticências de
 * `TextOverflow.Ellipsis` que a faixa já usava.
 */
internal val HUD_PILL_MAX_WIDTH = 320.dp

/**
 * Distância em que a pílula solta gruda na borda da área útil.
 *
 * A área útil é a de [availableWindowAreaDp], que sai de `maximumWindowBounds` e
 * **já desconta a barra de tarefas**: grudar na borda de baixo põe a pílula
 * imediatamente acima dela, que é o encaixe pedido. Sobrepor a barra exigiria os
 * limites físicos da tela e disputa de ordem-z com uma janela que também é
 * topmost — fora de escopo, declaradamente.
 */
internal val HUD_SNAP_THRESHOLD = 16.dp

/**
 * Altura de uma linha de fonte no painel expandido.
 *
 * **Não é `AppDataRow`**, e a recusa é deliberada: aquela primitiva tem piso de
 * 32dp mais 8dp de padding vertical, e seis fontes dariam um painel de ~288dp —
 * uma janela, não um HUD. É a mesma exceção que `AppChrome.hud` já abre ao furar
 * o piso de 28dp do cromo: aqui não há alvo de clique próprio nem célula de
 * tabela, só ponto, palavra e rótulo.
 */
internal val HUD_SOURCE_ROW_HEIGHT = 20.dp

/** Padding horizontal da pílula com texto. */
internal val HUD_PILL_PADDING = AppSpacing.md

/**
 * Padding horizontal da pílula recolhida ao ponto.
 *
 * Com `AppSpacing.md` dos dois lados, um ponto de 6dp viraria uma janela de
 * 38dp — quase seis vezes o que ela mostra.
 */
internal val HUD_PILL_DOT_ONLY_PADDING = AppSpacing.sm

/** Padding vertical do painel expandido, acima e abaixo da lista. */
internal val HUD_PANEL_VERTICAL_PADDING = AppSpacing.xs

/**
 * Avanço de caractere da IBM Plex Mono, em fração do corpo.
 *
 * A escala `label*` deste sistema é **mono** (`AppTheme.kt`), e é isso que torna
 * a largura do texto calculável sem compor nada: numa fonte proporcional este
 * número não existiria. 0,6em é o avanço da família; a folga de
 * [HUD_TEXT_SAFETY_MARGIN] cobre o arredondamento.
 *
 * **Estimativa, não medição.** Medir a composição e devolver a largura para a
 * janela fecha o laço `redimensionar → recompor → medir → redimensionar`. O
 * preço de errar aqui é um caractere truncado a mais, que as reticências já
 * tratam; o preço do laço seria uma janela oscilando.
 */
private const val MONO_ADVANCE_RATIO = 0.6f

/** Folga contra o arredondamento da estimativa acima. */
private val HUD_TEXT_SAFETY_MARGIN = 8.dp

// Corpo e espaçamento das duas escalas que a faixa usa, espelhados de
// `appTypography`: `labelSmall` no indicador de estado, `labelMedium` no rótulo
// da fonte e no tempo até o reset.
private const val LABEL_SMALL_FONT_SIZE = 10f
private const val LABEL_SMALL_LETTER_SPACING = 0.7f
private const val LABEL_MEDIUM_FONT_SIZE = 12f

/** Largura do ponto mais o vão até a palavra, dentro de `AppStatusIndicator`. */
private val STATUS_INDICATOR_DOT_WIDTH = STATUS_DOT_SIZE + AppSpacing.xs

/**
 * Largura da pílula colapsada, medida pelo conteúdo e presa em
 * [HUD_PILL_MAX_WIDTH].
 *
 * [dotOnly] é o estado em que todas as fontes estão em `ON_TRACK`: some o texto
 * e resta o ponto, porque um dado que diz "está tudo bem" não precisa ocupar
 * tela até deixar de ser verdade.
 */
internal fun hudPillWidth(
    statusLabel: String,
    sourceLabel: String?,
    resetLabel: String?,
    dotOnly: Boolean = false
): Dp {
    if (dotOnly) {
        return HUD_PILL_DOT_ONLY_PADDING * 2 + STATUS_DOT_SIZE
    }

    var width = HUD_PILL_PADDING * 2 + STATUS_INDICATOR_DOT_WIDTH + labelSmallWidth(statusLabel)
    if (sourceLabel != null) {
        width += AppSpacing.md + labelMediumWidth(sourceLabel)
    }
    if (resetLabel != null) {
        width += AppSpacing.md + labelMediumWidth(resetLabel)
    }

    return (width + HUD_TEXT_SAFETY_MARGIN).coerceAtMost(HUD_PILL_MAX_WIDTH)
}

/**
 * Largura do painel expandido: a linha mais larga da lista, presa no mesmo teto.
 *
 * Lista vazia devolve zero — quem chama resolve com `max` contra a pílula, e o
 * painel sem linha nenhuma não é composto.
 */
internal fun hudPanelWidth(sources: List<HudSourceStatus>): Dp {
    if (sources.isEmpty()) {
        return 0.dp
    }

    val widest = sources.maxOf { source ->
        labelMediumWidth(source.label).value +
            AppSpacing.md.value +
            STATUS_INDICATOR_DOT_WIDTH.value +
            labelSmallWidth(source.statusLabel).value
    }

    return (HUD_PILL_PADDING * 2 + widest.dp + HUD_TEXT_SAFETY_MARGIN)
        .coerceAtMost(HUD_PILL_MAX_WIDTH)
}

/**
 * Tamanho da janela HUD nos dois estados.
 *
 * Expandida ela nunca encolhe na horizontal: a largura é o máximo entre a pílula
 * e o painel, senão passar o mouse estreitaria a faixa que está debaixo do
 * ponteiro.
 */
internal fun hudWindowSize(
    pillWidth: Dp,
    panelWidth: Dp,
    sourceCount: Int,
    expanded: Boolean
): DpSize {
    if (!expanded || sourceCount <= 0) {
        return DpSize(pillWidth, AppChrome.hud)
    }

    val panelHeight = HUD_DIVIDER_HEIGHT +
        HUD_PANEL_VERTICAL_PADDING * 2 +
        HUD_SOURCE_ROW_HEIGHT * sourceCount

    return DpSize(
        width = maxOf(pillWidth, panelWidth),
        height = AppChrome.hud + panelHeight
    )
}

/** A divisória de 1dp entre a pílula e a lista, do mesmo `AppDivider` de sempre. */
private val HUD_DIVIDER_HEIGHT = 1.dp

/**
 * Posição da janela expandida, a partir do canto da pílula colapsada.
 *
 * **Cresce para baixo quando cabe e para cima quando não cabe.** Grudada na
 * borda de baixo — o encaixe pedido, logo acima da barra de tarefas — crescer
 * para baixo jogaria a lista inteira para fora da tela.
 *
 * **Na horizontal, o lado que fica preso é o mais próximo da borda.** Com a
 * pílula encostada à direita, alargar a partir do `x` a empurraria para fora;
 * quem tem de andar é a borda esquerda.
 */
internal fun hudExpandedPosition(
    collapsedX: Dp,
    collapsedY: Dp,
    collapsedSize: DpSize,
    expandedSize: DpSize,
    workArea: ScreenWorkArea
): WindowPosition {
    val measured = collapsedX.value.isFinite() &&
        collapsedY.value.isFinite() &&
        workArea.x.value.isFinite() &&
        workArea.y.value.isFinite() &&
        workArea.size.width.value.isFinite() &&
        workArea.size.height.value.isFinite()

    if (!measured) {
        return WindowPosition(collapsedX, collapsedY)
    }

    val bottomLimit = workArea.y + workArea.size.height
    val y = if (collapsedY + expandedSize.height <= bottomLimit) {
        collapsedY
    } else {
        collapsedY + collapsedSize.height - expandedSize.height
    }

    val rightLimit = workArea.x + workArea.size.width
    val gapToRight = rightLimit - (collapsedX + collapsedSize.width)
    val gapToLeft = collapsedX - workArea.x
    val x = if (gapToRight < gapToLeft) {
        collapsedX + collapsedSize.width - expandedSize.width
    } else {
        collapsedX
    }

    return fitWindowPosition(x = x, y = y, size = expandedSize, workArea = workArea)
}

/**
 * A pílula solta gruda na borda mais próxima e nunca termina fora da tela.
 *
 * O encaixe é por eixo e independente: soltar no canto inferior direito gruda
 * nos dois. O resultado sempre passa por [fitWindowPosition] — arrastar para
 * fora da área útil é o caminho mais curto para uma janela sem barra de título e
 * sem como voltar (issue #72).
 */
internal fun snapHudPosition(
    x: Dp,
    y: Dp,
    size: DpSize,
    workArea: ScreenWorkArea,
    threshold: Dp = HUD_SNAP_THRESHOLD
): WindowPosition {
    return fitWindowPosition(
        x = snapAxis(x, size.width, workArea.x, workArea.size.width, threshold),
        y = snapAxis(y, size.height, workArea.y, workArea.size.height, threshold),
        size = size,
        workArea = workArea
    )
}

// `Dp.Unspecified` é `NaN`: quem decide é `isFinite`, como em `WindowScreenFit`.
private fun snapAxis(
    start: Dp,
    extent: Dp,
    origin: Dp,
    available: Dp,
    threshold: Dp
): Dp {
    if (!start.value.isFinite() || !origin.value.isFinite() || !available.value.isFinite()) {
        return start
    }

    val end = origin + available
    if (abs((start - origin).value) <= threshold.value) {
        return origin
    }
    if (abs((start + extent - end).value) <= threshold.value) {
        return end - extent
    }

    return start
}

private fun labelSmallWidth(text: String): Dp {
    val advance = LABEL_SMALL_FONT_SIZE * MONO_ADVANCE_RATIO + LABEL_SMALL_LETTER_SPACING
    return (text.length * advance).dp
}

private fun labelMediumWidth(text: String): Dp {
    return (text.length * LABEL_MEDIUM_FONT_SIZE * MONO_ADVANCE_RATIO).dp
}

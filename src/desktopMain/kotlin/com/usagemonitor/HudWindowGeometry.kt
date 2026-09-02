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
 * Teto da largura do painel.
 *
 * Era largura **fixa** (`HUD_PILL_WIDTH_DP`, 320): a faixa media 320dp mesmo
 * mostrando "Normal", e o retângulo inteiro captura clique de quem estiver
 * atrás — a queixa que abriu esta passada. Virou teto: o HUD mede o conteúdo e
 * para aqui. O papel que a largura fixa cumpria — não mudar de tamanho a cada
 * coleta — continua sendo cumprido por este limite mais as reticências de
 * `TextOverflow.Ellipsis` que a faixa já usava.
 *
 * **Subiu de 320 para 420** quando a linha passou a carregar quatro colunas —
 * estado, nome da fonte, percentual e reset. Com 320 sobravam ~96dp para o
 * nome, treze caracteres: toda conta virava "Anthropic — I…", que é justamente
 * o que a lista existe para distinguir. Continua bem abaixo do piso da janela
 * normal (240dp de card × n).
 */
internal val HUD_PILL_MAX_WIDTH = 420.dp

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
 * Largura da janela recolhida ao ponto.
 *
 * [dotOnly] é o estado em que todas as fontes estão em `ON_TRACK`: some o texto
 * e resta o ponto, porque um dado que diz "está tudo bem" não precisa ocupar
 * tela até deixar de ser verdade.
 */
private fun hudDotOnlyWidth(): Dp = HUD_PILL_DOT_ONLY_PADDING * 2 + STATUS_DOT_SIZE

/**
 * Largura de uma linha de fonte: estado, nome, percentual e reset.
 *
 * O nome entra **inteiro** na conta e é ele que a composição encolhe quando a
 * soma passa do teto: as outras três colunas são curtas e de largura previsível,
 * e truncar o percentual ou o horário do reset não deixaria nada legível.
 */
private fun hudSourceRowWidth(source: HudSourceStatus): Dp {
    var width = HUD_PILL_PADDING * 2 +
        STATUS_INDICATOR_DOT_WIDTH +
        labelSmallWidth(source.statusLabel) +
        AppSpacing.md +
        labelMediumWidth(source.label) +
        AppSpacing.md +
        labelMediumWidth(source.percentLabel)

    if (source.resetLabel != null) {
        width += AppSpacing.md + labelMediumWidth(source.resetLabel)
    }

    return width + HUD_TEXT_SAFETY_MARGIN
}

/** Largura da linha única de carregamento: ponto, palavra e nada mais. */
private fun hudFallbackRowWidth(fallbackLabel: String): Dp {
    return HUD_PILL_PADDING * 2 +
        STATUS_INDICATOR_DOT_WIDTH +
        labelSmallWidth(fallbackLabel) +
        HUD_TEXT_SAFETY_MARGIN
}

/** Largura do rodapé de sessão, que é uma linha de texto só. */
private fun hudFooterWidth(footerLabel: String): Dp {
    return HUD_PILL_PADDING * 2 + labelMediumWidth(footerLabel) + HUD_TEXT_SAFETY_MARGIN
}

/**
 * Tamanho da janela HUD, medido pelo que ela vai mostrar.
 *
 * **A largura é a da linha mais larga, presa em [HUD_PILL_MAX_WIDTH]**, e não a
 * da primeira: com linhas de larguras diferentes, dimensionar pela primeira
 * truncaria todas as outras sem motivo. **A altura é uma linha por fonte** —
 * mostrar só a pior era a queixa que abriu esta passada; as outras contas não
 * tinham sinal nenhum de que existiam.
 *
 * Lista vazia rende **uma** linha, a de carregamento: zero linhas dariam uma
 * janela de altura nula, que o AWT não sabe desenhar e o usuário leria como o
 * app ter sumido.
 */
internal fun hudWindowSize(
    sources: List<HudSourceStatus>,
    footerLabel: String?,
    fallbackLabel: String,
    dotOnly: Boolean
): DpSize {
    if (dotOnly) {
        return DpSize(hudDotOnlyWidth(), AppChrome.hud)
    }

    val rowWidths = if (sources.isEmpty()) {
        listOf(hudFallbackRowWidth(fallbackLabel))
    } else {
        sources.map { source -> hudSourceRowWidth(source) }
    }
    val footerWidth = footerLabel?.let { label -> hudFooterWidth(label) } ?: 0.dp
    val width = maxOf(rowWidths.maxOf { rowWidth -> rowWidth.value }, footerWidth.value)
        .dp
        .coerceAtMost(HUD_PILL_MAX_WIDTH)

    val rowCount = maxOf(sources.size, 1)
    var height = HUD_PANEL_VERTICAL_PADDING * 2 + HUD_SOURCE_ROW_HEIGHT * rowCount
    if (footerLabel != null) {
        // **O rodapé é um bloco, não uma linha solta.** Ele tem o mesmo padding
        // vertical da lista, e contar só a divisória mais a linha deixava a
        // janela 8dp mais curta que o conteúdo: o texto do rodapé aparecia
        // cortado ao meio na borda de baixo. `HudBarHeightTest` compara esta
        // conta com o que o Compose realmente mede, que é o teste que faltava.
        height += HUD_DIVIDER_HEIGHT + HUD_PANEL_VERTICAL_PADDING * 2 + HUD_SOURCE_ROW_HEIGHT
    }

    return DpSize(width, height)
}

/** A divisória de 1dp entre a pílula e a lista, do mesmo `AppDivider` de sempre. */
private val HUD_DIVIDER_HEIGHT = 1.dp

/**
 * Posição da janela, a partir da âncora.
 *
 * A âncora é o canto superior esquerdo do painel **completo** — a forma normal
 * da janela. Ela é o que o arrasto move e o que fica gravado; o estado
 * recolhido ao ponto é derivado dela, nunca o contrário. Guardar a âncora do
 * ponto faria a janela saltar toda vez que uma fonte saísse de `ON_TRACK`.
 *
 * **Nos dois eixos, o lado que fica preso é o mais próximo da borda da tela.**
 * Encostado à direita, alargar a partir do `x` empurraria a janela para fora;
 * quem tem de andar é a borda esquerda. Encostado embaixo — o encaixe pedido,
 * logo acima da barra de tarefas — crescer para baixo jogaria as linhas de
 * baixo para fora da tela. A regra vale igual quando a janela **encolhe** (o
 * recolhimento ao ponto), e é ela que mantém o ponto na mesma quina em que o
 * painel estava.
 */
internal fun hudWindowPosition(
    anchorX: Dp,
    anchorY: Dp,
    anchorSize: DpSize,
    windowSize: DpSize,
    workArea: ScreenWorkArea
): WindowPosition {
    val measured = anchorX.value.isFinite() &&
        anchorY.value.isFinite() &&
        workArea.x.value.isFinite() &&
        workArea.y.value.isFinite() &&
        workArea.size.width.value.isFinite() &&
        workArea.size.height.value.isFinite()

    if (!measured) {
        return WindowPosition(anchorX, anchorY)
    }

    val x = alignToNearestEdge(
        anchorStart = anchorX,
        anchorExtent = anchorSize.width,
        windowExtent = windowSize.width,
        origin = workArea.x,
        available = workArea.size.width
    )
    val y = alignToNearestEdge(
        anchorStart = anchorY,
        anchorExtent = anchorSize.height,
        windowExtent = windowSize.height,
        origin = workArea.y,
        available = workArea.size.height
    )

    return fitWindowPosition(x = x, y = y, size = windowSize, workArea = workArea)
}

private fun alignToNearestEdge(
    anchorStart: Dp,
    anchorExtent: Dp,
    windowExtent: Dp,
    origin: Dp,
    available: Dp
): Dp {
    val gapToStart = anchorStart - origin
    val gapToEnd = (origin + available) - (anchorStart + anchorExtent)

    return if (gapToEnd < gapToStart) {
        anchorStart + anchorExtent - windowExtent
    } else {
        anchorStart
    }
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

package com.usagemonitor

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import com.usagemonitor.presentation.ui.HudSourceStatus
import com.usagemonitor.presentation.ui.HudQuotaChip
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
 *
 * **E de 420 para 484 quando a contagem entrou** (issue #185), que é exatamente
 * o teto anterior mais [hudCountdownWidth] — não um número escolhido por ser
 * redondo. O 420 foi calibrado para a linha **sem** a coluna, e mantê-lo faria a
 * coluna nova ser paga pelo nome da conta. Medido com as contas reais: sem a
 * contagem, `Anthropic — Padrão` pedia 356,9dp e `OpenCode Go` 361,6dp, os dois
 * abaixo do teto; com ela passavam a 420,9 e 425,6 e começavam a truncar. É a
 * mesma razão do salto anterior, e a aritmética preserva exatamente a
 * capacidade de nome que a barra já tinha.
 */
internal val HUD_PILL_MAX_WIDTH = 484.dp

/**
 * Largura de uma coluna de reset (issue #189): o vão até o percentual mais o
 * rótulo mais longo que `resetShortLabel` imprime.
 *
 * O mais longo é o da janela semanal ou mensal — `Ter 21h00`, nove caracteres —,
 * e não o da intradiária (`22h59`, cinco). A conta é a mesma de
 * [hudCountdownWidth]: vão mais avanço mono por caractere.
 */
private val HUD_RESET_COLUMN_WIDTH =
    AppSpacing.xs + (9 * LABEL_MEDIUM_FONT_SIZE * MONO_ADVANCE_RATIO).dp

/**
 * Teto da largura do painel **expandido** (issue #189).
 *
 * [HUD_PILL_MAX_WIDTH] foi calibrado para a linha **sem** a coluna de reset, e
 * mantê-lo aqui faria a coluna nova ser paga pelo nome da conta — exatamente o
 * erro que os saltos 320 → 420 → 484 já recusaram duas vezes. O painel expandido
 * só existe enquanto o ponteiro está sobre ele, então a área que ele ocupa não é
 * a que fica capturando clique de quem está atrás: a razão que prende a pílula
 * parada a um teto baixo não se aplica ao painel.
 *
 * **A aritmética é o teto da pílula mais três colunas de reset**, e não um número
 * escolhido por ser redondo: três é a maior contagem de cotas numa fonte só
 * (OpenCode Go, com `5h`, `semanal` e `mensal`).
 */
internal val HUD_PANEL_MAX_WIDTH = HUD_PILL_MAX_WIDTH + HUD_RESET_COLUMN_WIDTH * 3

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
 * Ícone da contagem regressiva até a próxima coleta (issue #185).
 *
 * **O ícone é o único portador de significado disponível aqui.** No HUD não cabe
 * tooltip — popup no Compose Desktop é camada *dentro* da janela e sai recortado
 * sobre o próprio alvo, como a #164 já pagou —, e um `02:05` solto ao lado dos
 * percentuais não diria de que tempo se trata.
 *
 * 12dp cabe na linha de 20dp e é o menor tamanho em que a seta circular ainda se
 * lê; o ícone do rodapé, que tem 30dp de barra, usa 16.
 */
internal val HUD_COUNTDOWN_ICON_SIZE = 12.dp

/**
 * Texto que a geometria mede no lugar da contagem corrente.
 *
 * **A largura é estimada sobre um placeholder, nunca sobre o valor do relógio.**
 * A janela é dimensionada pelo conteúdo, então medir o texto corrente faria a
 * janela mudar de tamanho junto com o relógio. Com o poll de 10 minutos o
 * `%02d:%02d` de `formatRefreshCountdown` dá sempre cinco caracteres, e a escala
 * `label*` é mono: o placeholder tem exatamente a largura de qualquer valor que
 * a barra vá imprimir.
 */
private const val HUD_COUNTDOWN_PLACEHOLDER = "00:00"

/**
 * Largura da coluna da contagem: o vão que a separa das cotas, o ícone, o vão
 * interno e o texto.
 */
private fun hudCountdownWidth(): Dp {
    return AppSpacing.md +
        HUD_COUNTDOWN_ICON_SIZE +
        AppSpacing.xs +
        labelMediumWidth(HUD_COUNTDOWN_PLACEHOLDER)
}

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
 * Largura das cotas de uma linha: um ponto e um texto por cota, com vão entre
 * elas.
 *
 * [showsReset] é o estado expandido (issue #189): a hora do reinício só é
 * desenhada com o ponteiro em cima, então medi-la sempre deixaria a pílula
 * parada larga para mostrar o que ela não mostra. Cota sem reset a exibir —
 * saldo que não expira, janela sem reset conhecido — chega com `resetText` nulo
 * e não reserva nada.
 */
private fun hudChipsWidth(quotas: List<HudQuotaChip>, showsReset: Boolean): Dp {
    if (quotas.isEmpty()) {
        return 0.dp
    }

    val chips = quotas.fold(0f) { total, chip ->
        val reset = chip.resetText
        val resetWidth = if (showsReset && reset != null) {
            AppSpacing.xs.value + labelMediumWidth(reset).value
        } else {
            0f
        }
        total + STATUS_DOT_SIZE.value + AppSpacing.xs.value + labelMediumWidth(chip.text).value + resetWidth
    }
    val gaps = AppSpacing.sm.value * (quotas.size - 1)

    return (chips + gaps).dp
}

/**
 * Largura de uma linha: estado da fonte, nome da conta e as cotas dela.
 *
 * O nome entra **inteiro** na conta e é ele que a composição encolhe quando a
 * soma passa do teto: as outras colunas são curtas e de largura previsível, e
 * truncar um percentual não deixaria nada legível.
 */
private fun hudSourceRowWidth(source: HudSourceStatus, showsReset: Boolean): Dp {
    return HUD_PILL_PADDING * 2 +
        STATUS_INDICATOR_DOT_WIDTH +
        labelSmallWidth(source.statusLabel) +
        AppSpacing.md +
        labelMediumWidth(source.label) +
        AppSpacing.md +
        hudChipsWidth(source.quotas, showsReset) +
        HUD_TEXT_SAFETY_MARGIN
}

/** Largura da linha única de carregamento: ponto, palavra e nada mais. */
private fun hudFallbackRowWidth(fallbackLabel: String): Dp {
    return HUD_PILL_PADDING * 2 +
        STATUS_INDICATOR_DOT_WIDTH +
        labelSmallWidth(fallbackLabel) +
        HUD_TEXT_SAFETY_MARGIN
}

/**
 * Tamanho da janela HUD, medido pelo que ela vai mostrar.
 *
 * **Parada é a primeira fonte; com o ponteiro em cima, todas.** Listar tudo o
 * tempo todo virou conteúdo demais — dez linhas na tela para dizer o que, na
 * maior parte do tempo, cabe em uma.
 *
 * **A largura é a da linha mais larga entre as visíveis, presa em
 * [HUD_PILL_MAX_WIDTH]**, e não a da primeira: com linhas de larguras
 * diferentes, dimensionar pela primeira truncaria todas as outras sem motivo.
 * **Parada, ela é a da primeira linha só** — medir pela lista escondida deixaria
 * a barra larga sem nada para mostrar ali.
 *
 * Sem fonte nenhuma sobra a linha de carregamento: zero linhas dariam uma janela
 * de altura nula, que o AWT não sabe desenhar e o usuário leria como o app ter
 * sumido.
 *
 * **A coluna de reset entra só no estado expandido** (issue #189), e com ela o
 * teto passa a ser [HUD_PANEL_MAX_WIDTH]. Parada, a pílula mede exatamente o que
 * media antes: ela é a que fica na tela o tempo todo, e a área dela é a que
 * captura clique de quem está atrás.
 *
 * **A contagem regressiva ([showsCountdown]) mede só na primeira linha.** O
 * polling é um só — dez minutos para o app inteiro, não por conta —, e repeti-la
 * em cada linha afirmaria que cada conta tem coleta própria. Recolhida ao ponto
 * ela não existe: ali não há texto nenhum.
 */
internal fun hudWindowSize(
    sources: List<HudSourceStatus>,
    fallbackLabel: String,
    dotOnly: Boolean,
    expanded: Boolean,
    showsCountdown: Boolean = false
): DpSize {
    if (dotOnly) {
        return DpSize(hudDotOnlyWidth(), AppChrome.hud)
    }

    val countdownWidth = if (showsCountdown) hudCountdownWidth() else 0.dp
    val visible = if (expanded) sources else sources.take(1)
    // O teto é do estado, não do componente (issue #189): expandido o painel
    // carrega uma coluna a mais por cota, e prendê-lo ao teto da pílula faria
    // essa coluna ser paga pelo nome da conta.
    val maxWidth = if (expanded) HUD_PANEL_MAX_WIDTH else HUD_PILL_MAX_WIDTH

    if (visible.isEmpty()) {
        // A linha de carregamento é a primeira linha, e enquanto nada foi
        // coletado "quando é a próxima tentativa" é o que a barra tem a dizer.
        return DpSize(
            width = (hudFallbackRowWidth(fallbackLabel) + countdownWidth)
                .coerceAtMost(maxWidth),
            height = HUD_PANEL_VERTICAL_PADDING * 2 + HUD_SOURCE_ROW_HEIGHT
        )
    }

    val width = visible
        .mapIndexed { index, source ->
            val row = hudSourceRowWidth(source, showsReset = expanded)
            if (index == 0) (row + countdownWidth).value else row.value
        }
        .max()
        .dp
        .coerceAtMost(maxWidth)

    return DpSize(
        width = width,
        height = HUD_PANEL_VERTICAL_PADDING * 2 + HUD_SOURCE_ROW_HEIGHT * visible.size
    )
}

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

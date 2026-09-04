package com.usagemonitor.presentation.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.WindowState
import java.awt.Cursor
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import com.usagemonitor.HUD_COUNTDOWN_ICON_SIZE
import com.usagemonitor.HUD_PANEL_VERTICAL_PADDING
import com.usagemonitor.HUD_PILL_DOT_ONLY_PADDING
import com.usagemonitor.HUD_PILL_PADDING
import com.usagemonitor.HUD_SOURCE_ROW_HEIGHT
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.components.AppDivider
import com.usagemonitor.presentation.ui.components.AppStatusDot
import com.usagemonitor.presentation.ui.components.AppStatusIndicator
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.components.WindowMode
import com.usagemonitor.presentation.ui.components.WindowModeMenuButton
import com.usagemonitor.presentation.ui.components.formatRefreshCountdown
import com.usagemonitor.presentation.ui.theme.AppChrome
import com.usagemonitor.presentation.ui.theme.AppMotion
import com.usagemonitor.presentation.ui.theme.AppShapes
import com.usagemonitor.presentation.ui.theme.AppSpacing

// 10dp é o teto da escala de raios; 16 vinha da escala antiga, que ia até 28.
// `applyWindowShape` continua reagindo a `componentResized`, senão a máscara
// ficaria com o tamanho da janela anterior depois de qualquer redimensionamento.
private val WindowCornerRadius = 10.dp

/** Descrição semântica do botão que devolve a moldura completa da janela. */
internal const val COMPACT_EXIT_DESCRIPTION = "Sair do modo somente cards"

/** Descrição semântica da faixa HUD inteira: é o único alvo de clique dela. */
internal const val HUD_BAR_OPEN_DESCRIPTION = "Abrir Usage Monitor"

/**
 * O conteúdo do HUD, sem o preenchimento da janela.
 *
 * A raiz usa `fillMaxSize` para a superfície pintar a janela inteira, e por isso
 * ela mede o que a cena der — inútil para conferir altura. Esta marca é do bloco
 * que **envolve o conteúdo**, e é sobre ela que `HudBarHeightTest` compara o que
 * o Compose mede com o que `hudWindowSize` calcula.
 */
internal const val HUD_CONTENT_TEST_TAG = "hudContent"

/**
 * Altura da barra de título das seis janelas.
 *
 * Sai de [AppChrome], onde os cinco patamares do cromo vivem: era um literal
 * aqui e um `private val` em `AppStructure`, os dois com o mesmo 34 e nenhum
 * sabendo do outro.
 */
private val TITLE_BAR_HEIGHT = AppChrome.titleBar

private fun WindowScope.applyWindowShape(density: androidx.compose.ui.unit.Density, cornerRadius: Dp) {
    val arcDiameter = with(density) { cornerRadius.toPx() * 2 }
    window.shape = java.awt.geom.RoundRectangle2D.Float(
        0f, 0f,
        window.width.toFloat(),
        window.height.toFloat(),
        arcDiameter,
        arcDiameter
    )
}

@Composable
fun WindowScope.DesktopWindowFrame(
    title: String,
    iconPainter: Painter?,
    windowState: WindowState,
    onCloseRequest: () -> Unit,
    /**
     * Modo somente cards: a barra de título sai do fluxo e vira uma faixa
     * revelada ao passar o mouse no topo da janela.
     */
    compact: Boolean = false,
    /** Volta ao modo normal; `null` esconde o botão correspondente na faixa. */
    onExitCompact: (() -> Unit)? = null,
    /**
     * Barra HUD (issue #164): terceiro chrome, ainda mais discreto que
     * [compact] — sem título, sem [content], só [HudBar]. Mutuamente
     * exclusivo com `compact` por regra de negócio de quem chama esta
     * função (`Main.kt`), não por tipo.
     */
    hud: Boolean = false,
    /** Conteúdo da barra HUD; ignorado quando [hud] é `false`. */
    hudContent: (@Composable () -> Unit)? = null,
    /**
     * Idioma do menu de modos revelado no modo "Somente cards"
     * ([onWindowModeChange]). Sem efeito quando ele é `null`.
     */
    language: AppLanguage = AppLanguage.PT,
    /** A moldura em que a janela está agora — marcada no menu, quando existe. */
    windowMode: WindowMode = WindowMode.STANDARD,
    /**
     * Troca de moldura direto da faixa revelada do modo "Somente cards", sem
     * passar pelo Padrão primeiro (issue #215).
     *
     * `null` esconde o controle — mesmo padrão de [onExitCompact]: os
     * geradores de captura montam a moldura sem despachar nada.
     */
    onWindowModeChange: ((WindowMode) -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val isMaximized = windowState.placement == WindowPlacement.Maximized

    // Cantos arredondados pressupõem a janela flutuando sobre o desktop; a
    // faixa HUD, encostada na borda superior da tela, sai reta como uma
    // janela maximizada.
    DisposableEffect(isMaximized, hud, density) {
        if (isMaximized || hud) {
            window.shape = null
        } else {
            applyWindowShape(density, WindowCornerRadius)
            val listener = object : java.awt.event.ComponentAdapter() {
                override fun componentResized(e: java.awt.event.ComponentEvent) {
                    applyWindowShape(density, WindowCornerRadius)
                }
            }
            window.addComponentListener(listener)
            return@DisposableEffect onDispose { window.removeComponentListener(listener) }
        }
        onDispose {}
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (!compact && !hud) {
                DesktopTitleBar(
                    title = title,
                    iconPainter = iconPainter,
                    windowState = windowState,
                    onCloseRequest = onCloseRequest
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Em modo HUD, content() não compõe: a faixa não mostra cards,
                // só o resumo que hudContent traz. A janela real, redimensionada
                // por quem chama esta função, mal tem altura para mais que isso.
                if (hud) {
                    hudContent?.invoke()
                    return@Box
                }

                content()

                if (compact) {
                    CompactTitleBarOverlay(
                        title = title,
                        iconPainter = iconPainter,
                        windowState = windowState,
                        onCloseRequest = onCloseRequest,
                        onExitCompact = onExitCompact,
                        language = language,
                        windowMode = windowMode,
                        onWindowModeChange = onWindowModeChange,
                        modifier = Modifier.align(Alignment.TopStart)
                    )
                }
            }
        }
    }
}

/**
 * A barra de título do modo somente cards: existe só enquanto o mouse está nela.
 *
 * **Sobreposta, e não linha da `Column`.** Entrando no fluxo, revelar a barra
 * empurraria os cards para baixo a cada passagem do mouse pelo topo.
 *
 * **Só é composta quando o ponteiro está dentro.** A `WindowDraggableArea` que ela
 * carrega usa arrasto imediato, e o card usa arrasto **depois de pressão longa**:
 * com a faixa sempre presente, o arrasto da janela venceria a pressão longa e
 * reordenar o primeiro card ficaria impossível na faixa superior. Fora do hover
 * não há área de arrasto nenhuma; dentro dela, mover a janela é o que se espera.
 *
 * A transição é única, não um laço: animação infinita trava o `waitForIdle`.
 */
@Composable
private fun WindowScope.CompactTitleBarOverlay(
    title: String,
    iconPainter: Painter?,
    windowState: WindowState,
    onCloseRequest: () -> Unit,
    onExitCompact: (() -> Unit)?,
    language: AppLanguage,
    windowMode: WindowMode,
    onWindowModeChange: ((WindowMode) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val hoverInteraction = remember { MutableInteractionSource() }
    val isHovered by hoverInteraction.collectIsHoveredAsState()
    val revealAlpha by animateFloatAsState(
        targetValue = if (isHovered) 1f else 0f,
        animationSpec = tween(durationMillis = AppMotion.fast, easing = AppMotion.enterEasing),
        label = "compactTitleBarAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(TITLE_BAR_HEIGHT)
            .hoverable(hoverInteraction)
    ) {
        // Invisível não pode continuar clicável: um botão de fechar transparente
        // no canto superior direito é pior que botão nenhum.
        if (revealAlpha <= 0.01f) {
            return@Box
        }

        Column(modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = revealAlpha }) {
            DesktopTitleBar(
                title = title,
                iconPainter = iconPainter,
                windowState = windowState,
                onCloseRequest = onCloseRequest,
                onExitCompact = onExitCompact,
                language = language,
                windowMode = windowMode,
                onWindowModeChange = onWindowModeChange
            )
        }
    }
}

/**
 * Uma fonte monitorada como a barra HUD a mostra: quem é, em que estado está e
 * de que tom é esse estado.
 *
 * Existe porque a lista de fontes deixou de ser texto de tooltip e passou a ser
 * conteúdo da própria janela — e porque a geometria (`HudWindowGeometry`) mede a
 * janela a partir destes rótulos antes de a composição existir. Carrega o tom
 * além da palavra: no painel cada linha é um `AppStatusIndicator`, ponto **e**
 * palavra, porque cor sozinha não informa estado neste sistema.
 */
/**
 * Uma cota dentro da linha da fonte: o rótulo curto, o percentual e o tom.
 *
 * **Ponto sem palavra, e isto tem precedente exato.** É o mesmo desenho que o
 * card já usa: `RiskSemaphoreDot` por cota, só ponto, mais **um** badge com
 * ponto e palavra resumindo o pior. A palavra da linha (`HudSourceStatus`)
 * cumpre aqui o papel do badge — a cor não informa estado sozinha, ela detalha
 * um estado que a linha já disse por escrito.
 */
internal data class HudQuotaChip(
    /** `5h 28%` — rótulo curto e percentual, montados por `hudQuotaChipText`. */
    val text: String,
    val tone: AppTone,
    /**
     * Quando esta cota reinicia, em forma curta — `22h59`, `Ter 21h00` (issue
     * #189).
     *
     * Sai de `resetShortLabel`, a mesma função que a linha do card usa recortada:
     * sem prefixo, sem fuso e sem a data do dia quando a janela é intradiária.
     * Nenhum formato de data novo.
     *
     * **`null` é "não há reset a mostrar"** — saldo que não expira, janela sem
     * reset conhecido — e nesse caso nada é impresso, em vez de um traço que não
     * informa nada. É o "caso item tenha" do título da issue.
     *
     * **Só é desenhado com o painel expandido.** A pílula parada fica na tela o
     * tempo todo e o retângulo dela captura o clique de quem está atrás; o reset
     * é detalhe sob demanda, e o ponteiro em cima já é o gesto que revela o
     * resto da lista.
     */
    val resetText: String? = null
)

/**
 * Uma fonte monitorada como a barra HUD a mostra: **uma linha por conta**, com
 * o estado da pior cota e o percentual de todas elas.
 *
 * Foi uma linha por cota antes disso, e a conta com janela de 5h e de 7d
 * ocupava duas linhas seguidas repetindo o próprio nome. Com dez cotas em cinco
 * contas, a lista virou parede de texto para dizer o que cabe em cinco linhas.
 */
internal data class HudSourceStatus(
    /** Perfil ou nome da fonte, sem rótulo de cota. */
    val label: String,
    /** Palavra da **pior** cota desta fonte — o papel do badge do card. */
    val statusLabel: String,
    val tone: AppTone,
    /** Todas as cotas da fonte, na ordem em que a API as devolve. */
    val quotas: List<HudQuotaChip> = emptyList()
)

/**
 * Conteúdo da barra HUD (issue #164): **uma linha parada, a lista inteira no
 * hover**.
 *
 * Cada linha é uma **fonte**: ponto e palavra da pior cota dela, o nome da
 * conta, e um ponto por cota ao lado do percentual. Parada, a barra mostra a
 * primeira fonte da ordem de cards do usuário; com o ponteiro em cima, todas.
 *
 * **O ponto por cota sem palavra tem precedente exato**: é o desenho do card —
 * `RiskSemaphoreDot` por cota, só ponto, mais um badge com ponto e palavra
 * resumindo o pior. A palavra da linha faz o papel do badge, então a cor nunca
 * informa um estado que a linha não tenha dito por escrito.
 *
 * **O caminho até aqui foi por tentativa, e cada volta corrigiu a anterior.**
 * (1) Uma linha com a fonte de pior risco: as outras contas não tinham sinal de
 * que existiam. (2) As outras num `HoverTooltipBox`: o popup piscava. (3) Uma
 * linha por fonte, sempre visível: a conta com 5h e 7d mostrava um limite só.
 * (4) Uma linha por cota, sempre visível: dez linhas na tela para dizer o que
 * cabe em uma. (5) Uma linha por cota no hover: a conta com 5h e 7d ocupava duas
 * linhas seguidas repetindo o próprio nome. O que sobrou junta as metades certas
 * — uma linha por conta, o resumo cabe numa, e o detalhe fica a um movimento de
 * mouse.
 *
 * **A lista é conteúdo da janela, nunca `Popup`.** Ela saía por
 * `HoverTooltipBox` → `TooltipBox` → `Popup`, e popup no Compose Desktop é
 * camada **dentro** da janela (`compose.layers.type` não está definido neste
 * projeto, e o default recorta pelos limites): numa janela de 24dp, um balão
 * com piso de 180dp de largura e uma linha por fonte não cabe — ele era
 * recortado sobre o próprio alvo, o ponteiro passava a estar sobre o balão, a
 * faixa recebia `Exit` e a tooltip fechava, para reabrir no quadro seguinte.
 *
 * **Cada linha carrega ponto E palavra** (`AppStatusIndicator`), e não só o
 * ponto colorido. O percentual ao lado descreve o consumo, não o risco: 40% às
 * onze da manhã pode ser pior que 80% dez minutos antes do reinício, e é a
 * palavra que diz qual dos dois é o caso. Sem ela a cor informaria estado
 * sozinha, que é justamente o que este sistema visual não faz.
 *
 * **A hora do reinício sai só no painel expandido** (issue #189). A pílula
 * parada fica na tela o tempo todo e o retângulo dela captura o clique de quem
 * está atrás — a queixa que fez a largura virar teto na #164 —, então o reset é
 * detalhe sob demanda: o ponteiro em cima já é o gesto que revela o resto da
 * lista. Ele vai **ao lado de cada cota**, e não em coluna própria à direita da
 * linha: a linha é por conta e as cotas são várias, e uma coluna única teria de
 * escolher qual delas descrever.
 *
 * **O hover mora no container inteiro.** Ele só serve ao estado recolhido
 * ([dotOnly]) — passar o mouse devolve a lista —, mas preso a uma linha só,
 * mover o ponteiro para dentro do painel tiraria o hover e a janela
 * colapsaria debaixo dele.
 *
 * **O painel inteiro é o alvo de clique** que devolve a janela completa — não
 * há botão próprio, e por isso a semântica vai no container, não só em
 * `onClickLabel` (armadilha nº2 do design system: sem
 * `Modifier.semantics { contentDescription = ... }`,
 * `onNodeWithContentDescription` não encontra o nó).
 *
 * **Com [WindowScope] seria mais curto e é justamente o que não se pode
 * fazer.** `WindowDraggableArea` arrasta a partir do `down`, e um `clickable`
 * dentro dela consome esse `down` antes: sobraria o clique e o arrasto nunca
 * começaria. Aqui o gesto é **um só**, e o que separa clique de arrasto é o
 * limiar de deslocamento — abaixo dele, [onOpenFull]; acima,
 * [onDragStart]/[onDragMove]/[onDragEnd], que `Main.kt` traduz em movimento da
 * janela AWT. Manter o AWT fora daqui é também o que deixa esta função
 * exercitável em `runDesktopComposeUiTest`, que não fornece janela nenhuma.
 *
 * **Largura de quem chama, não fixa aqui.** `Main.kt` dimensiona a janela pelo
 * conteúdo (`hudWindowSize`); `HudBar` só sabe preencher o que recebe, e por
 * isso o nome da fonte trunca com reticências em vez de estourar o container.
 */
@Composable
internal fun HudBar(
    /** Tom do ponto no estado recolhido; nas linhas, cada fonte traz o seu. */
    statusTone: AppTone,
    /**
     * Uma entrada por fonte, na ordem de card do usuário. Parada, a barra mostra
     * só a **primeira**; com o ponteiro em cima, todas.
     */
    sources: List<HudSourceStatus> = emptyList(),
    /** Palavra da linha única enquanto nenhuma fonte foi coletada. */
    fallbackLabel: String,
    /** O ponteiro está sobre a barra: as demais fontes aparecem abaixo da primeira. */
    expanded: Boolean = false,
    /**
     * Todas as fontes em `ON_TRACK` e sem o ponteiro em cima: recolhe ao ponto.
     *
     * O dado não some — ele para de ocupar tela enquanto diz que está tudo bem,
     * e o hover devolve o painel inteiro. É o mesmo princípio do ponto de risco
     * da bandeja, que não acende nada em `ON_TRACK`.
     */
    dotOnly: Boolean = false,
    /**
     * Quando a próxima coleta automática acontece (issue #185).
     *
     * `null` esconde a coluna inteira — é o default porque os geradores de
     * captura e os testes de geometria montam a barra sem relógio nenhum.
     */
    nextRefreshAt: Instant? = null,
    /**
     * O que o ícone da contagem significa, por extenso.
     *
     * Vem pronto de quem chama, como `statusLabel` e `fallbackLabel`: a barra não
     * conhece idioma. É o `contentDescription` do ícone — no HUD não cabe tooltip
     * (popup aqui é camada dentro da janela e sai recortado sobre o próprio
     * alvo), então esta é a única frase que explica o número.
     */
    countdownDescription: String? = null,
    onHoverChange: (Boolean) -> Unit = {},
    onDragStart: () -> Unit = {},
    onDragMove: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    onOpenFull: () -> Unit,
    /**
     * Botão direito na barra: troca direto para "Somente cards", sem passar
     * pelo Padrão primeiro (issue #215).
     *
     * Não há popup aqui — a razão é a mesma que já tirou a lista de fontes de
     * um `HoverTooltipBox`: um menu de opções seria recortado pelos limites
     * desta janela, que mal tem altura para si mesma. O botão esquerdo já leva
     * ao Padrão (de onde os três modos são alcançáveis pelo rodapé); faltava
     * só o atalho direto ao outro modo reduzido, e é só isso que o direito
     * oferece. `{}` por default: quem monta a barra sem trocar de modo (os
     * geradores de captura, os testes) não precisa saber que o botão existe.
     */
    onSwitchToCardsOnly: () -> Unit = {},
    modifier: Modifier = Modifier,
    /** Injetáveis pela mesma razão do `FooterBar`: afirmar o decremento sem esperar segundos reais. */
    nowProvider: () -> Instant = { Clock.System.now() },
    waitNextTick: suspend () -> Unit = { delay(1_000L) },
    /**
     * Desliga o laço do relógio, deixando a contagem parada no valor de entrada.
     *
     * **Não é preferência de usuário, é o mesmo interruptor do `FooterBar`.** Sob
     * o relógio dos testes de componente o `delay` avança sozinho, e com o
     * `nowProvider` fixo o laço nunca chega a zero: ele gira para sempre e o
     * `waitForIdle` não retorna. Quem exercita o decremento injeta
     * [waitNextTick]; quem só quer o número na tela desliga aqui.
     */
    countdownUpdatesEnabled: Boolean = true
) {
    val hoverInteraction = remember { MutableInteractionSource() }
    val isHovered by hoverInteraction.collectIsHoveredAsState()

    LaunchedEffect(isHovered) {
        onHoverChange(isHovered)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .hoverable(hoverInteraction)
            // **O cursor é o que diz que a barra se move.** Sem barra de título
            // e sem pegador visível, nada na tela informava que ela é arrastável
            // — a pergunta "como eu consigo mover?" veio de quem já estava com
            // ela na tela. O cursor de mover é a afordância que o cromo de
            // janela normalmente dá de graça.
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.MOVE_CURSOR)))
            .hudPressGesture(
                onDragStart = onDragStart,
                onDragMove = onDragMove,
                onDragEnd = onDragEnd,
                onClick = onOpenFull,
                onSecondaryClick = onSwitchToCardsOnly
            )
            // A ação de clique é **declarada** na semântica, não instalada por
            // `clickable`: aquele consumiria o `down` e o arrasto nunca
            // começaria. Sem a declaração, o único caminho para a janela
            // completa deixaria de existir para leitor de tela.
            .semantics {
                contentDescription = HUD_BAR_OPEN_DESCRIPTION
                onClick(label = HUD_BAR_OPEN_DESCRIPTION) {
                    onOpenFull()
                    true
                }
            }
    ) {
        if (dotOnly) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(AppChrome.hud)
                    .padding(horizontal = HUD_PILL_DOT_ONLY_PADDING)
                    .testTag(HUD_CONTENT_TEST_TAG),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppStatusDot(tone = statusTone)
            }
            return@Column
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(HUD_CONTENT_TEST_TAG)
                .padding(vertical = HUD_PANEL_VERTICAL_PADDING)
        ) {
            val visible = if (expanded) sources else sources.take(1)
            // A contagem é do app inteiro — o polling é um só —, então ela sai
            // **uma vez**, na primeira linha. Uma por linha afirmaria que cada
            // conta tem coleta própria.
            val countdown: (@Composable () -> Unit)? =
                if (nextRefreshAt == null || countdownDescription == null) {
                    null
                } else {
                    {
                        HudCountdown(
                            nextRefreshAt = nextRefreshAt,
                            description = countdownDescription,
                            nowProvider = nowProvider,
                            waitNextTick = waitNextTick,
                            updatesEnabled = countdownUpdatesEnabled
                        )
                    }
                }

            if (visible.isEmpty()) {
                HudPanelRow {
                    // O `weight` mora no indicador, e não num `Spacer` próprio: a
                    // `HudPanelRow` espaça os filhos, e um terceiro filho traria
                    // um vão que `hudFallbackRowWidth` não conta — a janela
                    // nasceria estreita e o texto sairia comprimido.
                    AppStatusIndicator(
                        label = fallbackLabel,
                        tone = statusTone,
                        modifier = Modifier.weight(1f)
                    )
                    countdown?.invoke()
                }
                return@Column
            }

            visible.forEachIndexed { index, source ->
                HudPanelRow {
                    AppStatusIndicator(label = source.statusLabel, tone = source.tone)
                    Text(
                        text = source.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                    ) {
                        source.quotas.forEach { chip ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
                            ) {
                                AppStatusDot(tone = chip.tone)
                                Text(
                                    text = chip.text,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                                // A hora do reinício (issue #189), só com o
                                // ponteiro em cima. Tom secundário e sem
                                // separador impresso: o reset não é consumo, e é
                                // a diferença de tom que diz isso — a mesma que
                                // a contagem regressiva usa na ponta da linha.
                                // Um `·` entre os dois gastaria largura para
                                // repetir o que o tom já informou.
                                val resetText = chip.resetText
                                if (expanded && resetText != null) {
                                    Text(
                                        text = resetText,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    if (index == 0) {
                        countdown?.invoke()
                    }
                }
            }
        }
    }
}

/**
 * A contagem regressiva até a próxima coleta automática (issue #185).
 *
 * **O tique mora aqui, não em quem chama.** Em `Main.kt` ele recomporia `main()`
 * inteiro a cada segundo, e aquele composable já está no limite do backend JVM.
 * As duas esperas são injetáveis pela mesma razão do `FooterBar`: é o que deixa o
 * decremento ser afirmado sem esperar segundos reais.
 *
 * **O laço para em zero**, e não é animação: `waitNextTick` é uma suspensão, não
 * um quadro pendente, então o `waitForIdle` dos testes de componente não trava.
 *
 * **Nenhum formato novo** — `formatRefreshCountdown` é a mesma do rodapé.
 */
@Composable
private fun HudCountdown(
    nextRefreshAt: Instant,
    description: String,
    nowProvider: () -> Instant,
    waitNextTick: suspend () -> Unit,
    updatesEnabled: Boolean
) {
    val remainingOf = { (nextRefreshAt - nowProvider()).inWholeSeconds.coerceAtLeast(0).toInt() }
    var secondsUntilRefresh by remember(nextRefreshAt) { mutableStateOf(remainingOf()) }

    LaunchedEffect(nextRefreshAt, updatesEnabled) {
        secondsUntilRefresh = remainingOf()
        if (!updatesEnabled) {
            return@LaunchedEffect
        }

        while (true) {
            val remaining = remainingOf()
            secondsUntilRefresh = remaining
            if (remaining <= 0) {
                break
            }
            waitNextTick()
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
    ) {
        // O ícone é o que diz de que tempo se trata: aqui não cabe tooltip, e um
        // `02:05` solto ao lado dos percentuais não se explica. A descrição vai
        // na semântica, que é o caminho do leitor de tela e dos asserts.
        Icon(
            imageVector = Icons.Rounded.Refresh,
            contentDescription = description,
            modifier = Modifier.size(HUD_COUNTDOWN_ICON_SIZE),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = formatRefreshCountdown(secondsUntilRefresh),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

/**
 * Uma linha do painel.
 *
 * **Não é `AppDataRow`**: aquela primitiva tem piso de 32dp mais 8dp de padding
 * vertical, e seis fontes dariam um painel de ~288dp — uma janela, não um HUD.
 * É a mesma exceção que `AppChrome.hud` já abre ao furar o piso de 28dp do
 * cromo, e pela mesma razão: aqui não há alvo de clique por linha nem célula de
 * tabela.
 */
@Composable
private fun HudPanelRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(HUD_SOURCE_ROW_HEIGHT)
            .padding(horizontal = HUD_PILL_PADDING),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
        content = content
    )
}

/**
 * Um gesto só para as três ações da pílula: mover a janela, abrir a completa e
 * — botão direito, issue #215 — trocar direto para "Somente cards".
 *
 * `clickable` empilhado com um detector de arrasto não resolve — o `clickable`
 * consome o `down` e o arrasto nunca começa. Aqui o `down` inicia a espera, o
 * deslocamento acumulado decide o que o gesto é, e o `up` despacha: abaixo do
 * limiar foi clique, acima foi arrasto que terminou.
 *
 * **O botão direito é ramo à parte, decidido no próprio `down`, e nunca chega
 * ao clique/arrasto de esquerda.** Um `AppMenu` aqui seria um `Popup`, e popup
 * no Compose Desktop é recortado pelos limites da própria janela — numa faixa
 * de 24-200dp o menu de três opções saía cortado sobre o próprio alvo, o mesmo
 * problema que já descartou a tooltip nesta barra (issue #164). Sem popup não
 * há o que desenhar: o botão direito despacha [onSecondaryClick] direto, sem
 * confirmação nem lista — a única troca que faz sentido sem menu é a que já
 * está a um clique de distância pelo caminho normal (clicar abre o Padrão), e
 * o que faltava era ir direto ao outro modo reduzido.
 *
 * **Nenhuma coordenada sai daqui.** `positionChange` é relativo a um componente
 * que, durante o arrasto, se move junto com a janela — como deslocamento ele
 * acumularia erro. Serve para medir se o limiar foi cruzado, e nada mais:
 * `Main.kt` lê a posição absoluta do ponteiro na tela a cada [onDragMove], que
 * é o que o `WindowDraggableArea` do Compose também faz por dentro.
 */
@Composable
private fun Modifier.hudPressGesture(
    onDragStart: () -> Unit,
    onDragMove: () -> Unit,
    onDragEnd: () -> Unit,
    onClick: () -> Unit,
    onSecondaryClick: () -> Unit = {}
): Modifier {
    // **A chave do `pointerInput` é `Unit`, e os callbacks entram por
    // `rememberUpdatedState`.** `Main.kt` declara as lambdas de arrasto como
    // objetos comuns, recriados a cada recomposição, e cada movimento recompõe
    // (ele move a âncora, que é estado). Com elas como chave, o handler de
    // ponteiro é desmontado e remontado a cada quadro do arrasto.
    //
    // **Isto não é correção de defeito, e a diferença foi medida, não deduzida**
    // — o arrasto sobrevive à troca de chave no Compose 1.7.1: o teste de
    // recomposição abaixo passa com as duas versões. É o padrão canônico e
    // evita reinstalar o handler à toa; nada além disso.
    val currentDragStart by rememberUpdatedState(onDragStart)
    val currentDragMove by rememberUpdatedState(onDragMove)
    val currentDragEnd by rememberUpdatedState(onDragEnd)
    val currentClick by rememberUpdatedState(onClick)
    val currentSecondaryClick by rememberUpdatedState(onSecondaryClick)

    return pointerInput(Unit) {
        awaitEachGesture {
            // `awaitFirstDown` não serve aqui: por contrato ela só reage ao
            // botão primário do mouse ("If it was down caused by
            // PointerType.Mouse, this function reacts only on primary
            // button") e um `down` de botão direito nunca a satisfaria — ela
            // ficaria esperando para sempre, e o botão direito não dispararia
            // nada. O laço abaixo é o mesmo, sem esse filtro.
            var down: PointerInputChange
            while (true) {
                val event = awaitPointerEvent()
                val candidate = event.changes.firstOrNull { it.changedToDownIgnoreConsumed() }
                if (candidate != null) {
                    down = candidate
                    break
                }
            }

            if (currentEvent.buttons.isSecondaryPressed) {
                down.consume()
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { candidate -> candidate.id == down.id }
                        ?: break
                    change.consume()
                    if (!change.pressed) break
                }
                currentSecondaryClick()
                return@awaitEachGesture
            }

            var travelled = 0f
            var dragging = false

            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { candidate -> candidate.id == down.id }
                    ?: break

                if (!change.pressed) {
                    if (dragging) currentDragEnd() else currentClick()
                    break
                }

                travelled += change.positionChange().getDistance()
                if (!dragging && travelled > viewConfiguration.touchSlop) {
                    dragging = true
                    currentDragStart()
                }
                if (dragging) {
                    change.consume()
                    currentDragMove()
                }
            }
        }
    }
}

@Composable
fun WindowScope.DesktopDialogFrame(
    title: String,
    iconPainter: Painter?,
    windowState: WindowState? = null,
    onCloseRequest: () -> Unit,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val isMaximized = windowState?.placement == WindowPlacement.Maximized

    DisposableEffect(density, isMaximized) {
        if (isMaximized) {
            window.shape = null
        } else {
            applyWindowShape(density, WindowCornerRadius)
            val listener = object : java.awt.event.ComponentAdapter() {
                override fun componentResized(e: java.awt.event.ComponentEvent) {
                    applyWindowShape(density, WindowCornerRadius)
                }
            }
            window.addComponentListener(listener)
            return@DisposableEffect onDispose { window.removeComponentListener(listener) }
        }
        onDispose {}
    }

    var entered by remember(title) { mutableStateOf(false) }

    LaunchedEffect(title) {
        entered = false
        entered = true
    }

    val frameScale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.94f,
        animationSpec = tween(durationMillis = AppMotion.normal, easing = AppMotion.enterEasing),
        label = "dialogFrameScale"
    )
    val frameOffsetY by animateDpAsState(
        targetValue = if (entered) 0.dp else 14.dp,
        animationSpec = tween(durationMillis = AppMotion.normal, easing = AppMotion.enterEasing),
        label = "dialogFrameOffsetY"
    )

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = frameScale
                scaleY = frameScale
                translationY = frameOffsetY.toPx()
                transformOrigin = TransformOrigin(0.5f, 0.3f)
            },
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            DesktopDialogTitleBar(
                title = title,
                iconPainter = iconPainter,
                windowState = windowState,
                onCloseRequest = onCloseRequest
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun WindowScope.DesktopTitleBar(
    title: String,
    iconPainter: Painter?,
    windowState: WindowState,
    onCloseRequest: () -> Unit,
    /** Presente só na faixa do modo somente cards. */
    onExitCompact: (() -> Unit)? = null,
    /**
     * Os três a seguir existem só para o menu de modos (issue #215) — sem
     * efeito na barra de título normal, que não os recebe.
     */
    language: AppLanguage = AppLanguage.PT,
    windowMode: WindowMode = WindowMode.STANDARD,
    onWindowModeChange: ((WindowMode) -> Unit)? = null
) {
    WindowDraggableArea {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 34dp: a barra é cromo, e oito dp a menos por janela são oito
                // dp a mais de dado em seis janelas.
                .height(TITLE_BAR_HEIGHT)
                .background(MaterialTheme.colorScheme.surface)
                .padding(start = AppSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (iconPainter != null) {
                    Icon(
                        painter = iconPainter,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.Unspecified
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Primeiro do grupo, mesmo lugar do rodapé: as demais agem
                // sobre o conteúdo da janela, esta troca a moldura dela — e é
                // a saída direta para a Barra HUD que a issue #215 pedia (sem
                // o botão ▣ ao lado, a única volta seria pelo Padrão).
                if (onWindowModeChange != null) {
                    WindowModeMenuButton(
                        language = language,
                        windowMode = windowMode,
                        onWindowModeChange = onWindowModeChange
                    )
                }
                if (onExitCompact != null) {
                    TitleBarButton(
                        label = "▣",
                        description = COMPACT_EXIT_DESCRIPTION,
                        onClick = onExitCompact
                    )
                }
                TitleBarButton(
                    label = "—",
                    onClick = { windowState.isMinimized = true }
                )
                TitleBarButton(
                    label = if (windowState.placement == WindowPlacement.Maximized) "❐" else "□",
                    onClick = {
                        windowState.placement = if (windowState.placement == WindowPlacement.Maximized) {
                            WindowPlacement.Floating
                        } else {
                            WindowPlacement.Maximized
                        }
                    }
                )
                TitleBarButton(
                    label = "×",
                    hoverColor = MaterialTheme.colorScheme.error,
                    onClick = onCloseRequest
                )
            }
        }
    }

    // `AppDivider`, e não o `HorizontalDivider` do Material com meia opacidade:
    // o sistema tem uma divisória só, de 1dp em `outlineVariant`, e a moldura da
    // janela não é exceção.
    AppDivider()
}

@Composable
private fun WindowScope.DesktopDialogTitleBar(
    title: String,
    iconPainter: Painter?,
    windowState: WindowState?,
    onCloseRequest: () -> Unit
) {
    WindowDraggableArea {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 34dp: a barra é cromo, e oito dp a menos por janela são oito
                // dp a mais de dado em seis janelas.
                .height(TITLE_BAR_HEIGHT)
                .background(MaterialTheme.colorScheme.surface)
                .padding(start = AppSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (iconPainter != null) {
                    Icon(
                        painter = iconPainter,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.Unspecified
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (windowState != null) {
                    TitleBarButton(
                        label = if (windowState.placement == WindowPlacement.Maximized) "❐" else "□",
                        onClick = {
                            windowState.placement = if (windowState.placement == WindowPlacement.Maximized) {
                                WindowPlacement.Floating
                            } else {
                                WindowPlacement.Maximized
                            }
                        }
                    )
                }

                TitleBarButton(
                    label = "×",
                    hoverColor = MaterialTheme.colorScheme.error,
                    onClick = onCloseRequest
                )
            }
        }
    }

    AppDivider()
}

@Composable
internal fun TitleBarButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Descrição semântica de ações cujo glifo não se explica.
     *
     * Minimizar, maximizar e fechar são o vocabulário de janela que todo sistema
     * desenha igual; o quadrado do modo somente cards, não.
     */
    description: String? = null,
    hoverColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
) {
    val defaultColor = Color.Transparent
    val textColor = MaterialTheme.colorScheme.onSurface
    val hoverInteraction = remember { MutableInteractionSource() }
    val isHovered by hoverInteraction.collectIsHoveredAsState()

    Box(
        modifier = modifier
            // Retângulo que preenche a altura da barra, como numa janela de
            // sistema: o botão arredondado flutuando dentro dela era o único
            // lugar do app onde um controle não encostava na própria moldura.
            .width(40.dp)
            .height(TITLE_BAR_HEIGHT - 1.dp)
            .clip(AppShapes.extraSmall)
            .background(if (isHovered) hoverColor else defaultColor)
            .hoverable(hoverInteraction)
            .clickable(onClick = onClick)
            .then(
                if (description == null) {
                    Modifier
                } else {
                    Modifier.semantics { contentDescription = description }
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center
        )
    }
}

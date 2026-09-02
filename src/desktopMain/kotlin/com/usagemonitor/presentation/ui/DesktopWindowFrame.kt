package com.usagemonitor.presentation.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import com.usagemonitor.HUD_PANEL_VERTICAL_PADDING
import com.usagemonitor.HUD_PILL_DOT_ONLY_PADDING
import com.usagemonitor.HUD_PILL_PADDING
import com.usagemonitor.HUD_SOURCE_ROW_HEIGHT
import com.usagemonitor.presentation.ui.components.AppDivider
import com.usagemonitor.presentation.ui.components.AppStatusDot
import com.usagemonitor.presentation.ui.components.AppStatusIndicator
import com.usagemonitor.presentation.ui.components.AppTone
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
                onExitCompact = onExitCompact
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
internal data class HudSourceStatus(
    val label: String,
    val statusLabel: String,
    val tone: AppTone,
    /** `compactPercentageLabel` da cota que determinou o estado — nenhum formato novo. */
    val percentLabel: String,
    /** `resetShortLabel` da mesma cota; `null` esconde a coluna em vez de imprimir traço. */
    val resetLabel: String? = null
)

/**
 * Conteúdo da barra HUD (issue #164): uma linha por fonte monitorada, com
 * estado, nome, percentual e reset, mais um rodapé opcional de sessão.
 *
 * **Todas as fontes, não só a pior.** A primeira versão mostrava uma linha só —
 * a fonte que perdia — e as outras contas não tinham sinal nenhum de que
 * existiam sem abrir a janela completa. A segunda pôs as outras num hover. Nas
 * duas, o dado que o usuário queria estava escondido atrás de um gesto; agora a
 * lista **é** o conteúdo.
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
    /** Todas as fontes, pior primeiro. Vazia rende a linha de carregamento. */
    sources: List<HudSourceStatus> = emptyList(),
    /** Palavra da linha única enquanto nenhuma fonte foi coletada. */
    fallbackLabel: String,
    /** Resumo de sessão do rodapé; `null` não desenha divisória nem linha. */
    footerLabel: String? = null,
    /**
     * Todas as fontes em `ON_TRACK` e sem o ponteiro em cima: recolhe ao ponto.
     *
     * O dado não some — ele para de ocupar tela enquanto diz que está tudo bem,
     * e o hover devolve o painel inteiro. É o mesmo princípio do ponto de risco
     * da bandeja, que não acende nada em `ON_TRACK`.
     */
    dotOnly: Boolean = false,
    onHoverChange: (Boolean) -> Unit = {},
    onDragStart: () -> Unit = {},
    onDragMove: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    onOpenFull: () -> Unit,
    modifier: Modifier = Modifier
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
            .hudPressGesture(
                onDragStart = onDragStart,
                onDragMove = onDragMove,
                onDragEnd = onDragEnd,
                onClick = onOpenFull
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

        Column(modifier = Modifier.fillMaxWidth().testTag(HUD_CONTENT_TEST_TAG)) {
        Column(modifier = Modifier.padding(vertical = HUD_PANEL_VERTICAL_PADDING)) {
            if (sources.isEmpty()) {
                HudPanelRow {
                    AppStatusIndicator(label = fallbackLabel, tone = statusTone)
                }
            } else {
                sources.forEach { source ->
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
                        Text(
                            text = source.percentLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        if (source.resetLabel != null) {
                            Text(
                                text = source.resetLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        if (footerLabel != null) {
            AppDivider()
            Column(modifier = Modifier.padding(vertical = HUD_PANEL_VERTICAL_PADDING)) {
                HudPanelRow {
                    Text(
                        text = footerLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        }
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
 * Um gesto só para as duas ações da pílula: mover a janela e abrir a completa.
 *
 * `clickable` empilhado com um detector de arrasto não resolve — o `clickable`
 * consome o `down` e o arrasto nunca começa. Aqui o `down` inicia a espera, o
 * deslocamento acumulado decide o que o gesto é, e o `up` despacha: abaixo do
 * limiar foi clique, acima foi arrasto que terminou.
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
    onClick: () -> Unit
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

    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
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
    onExitCompact: (() -> Unit)? = null
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

package com.usagemonitor.presentation.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import kotlinx.coroutines.CoroutineScope
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideIn
import androidx.compose.animation.slideOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.geometry.Rect
import com.usagemonitor.HudOrbitParams
import com.usagemonitor.domain.entity.UsageTargetKey
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.usagemonitor.HUD_HANDLE_GAP
import com.usagemonitor.HUD_HANDLE_SIZE
import com.usagemonitor.HUD_SHADOW_MARGIN
import com.usagemonitor.HudEdge
import com.usagemonitor.HudNotchSizes
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.hudBalloonHeight
import com.usagemonitor.hudColumnArrangement
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.components.appDepth
import com.usagemonitor.presentation.ui.components.gargantuaHorizonBody
import com.usagemonitor.presentation.ui.components.color
import com.usagemonitor.presentation.ui.components.rememberLatestNonNull
import com.usagemonitor.presentation.ui.components.GargantuaJetFrame
import com.usagemonitor.presentation.ui.components.gargantuaJetCloseFrame
import com.usagemonitor.presentation.ui.components.gargantuaJetOpenFrame
import com.usagemonitor.presentation.ui.theme.AppDepth
import com.usagemonitor.presentation.ui.theme.AppGargantuaTokens
import com.usagemonitor.presentation.ui.theme.LocalAppMotionPolicy
import com.usagemonitor.presentation.ui.theme.appTweenSpec
import com.usagemonitor.presentation.ui.theme.AppMotion
import com.usagemonitor.presentation.ui.theme.appSpring
import com.usagemonitor.presentation.ui.theme.appTween
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** Descrição do corpo do notch — é por ela que leitor de tela e testes o acham. */
internal const val HUD_NOTCH_DESCRIPTION = "Barra HUD do Usage Monitor"

/** "Atualizar Anthropic — Padrão": a ação do clique num anel, na semântica dele. */
internal fun hudRefreshAccountLabel(account: HudAccount, language: AppLanguage): String =
    if (language == AppLanguage.PT) "Atualizar ${account.label}" else "Refresh ${account.label}"

/** O corpo do notch, cujo tamanho a geometria afirma (`HudNotchTest`). */
internal const val HUD_CONTENT_TEST_TAG = "hudContent"

/** O selo do emoji da conta no anel (issue #287). */
internal const val HUD_ACCOUNT_EMOJI_TEST_TAG = "hudAccountEmoji"

/**
 * Atualização pendente (issues #225 e #291). No notch ela é **só o ponto da
 * engrenagem**, com a frase inteira ([description]) na descrição dela; o aviso
 * mesmo — [headline] e [detail] num banner, mais a ação [actionLabel] como botão
 * — mora no balão da engrenagem, e só o botão dele reinicia: o balão abre no
 * hover (#317), mas o reinício continua sendo um clique no rótulo que diz o que
 * ele faz. Nunca um clique no notch: seria clique de rotina reiniciando o app.
 */
internal data class HudUpdateIndicator(
    val tone: AppTone,
    val description: String,
    val actionLabel: String? = null,
    val headline: String = description,
    val detail: String? = null
)

/**
 * O notch da HUD: colado numa borda da tela, com um anel por conta.
 *
 * **O notch não cresce.** Ele mostra por conta o anel (um arco por cota), o
 * percentual da cota em foco e a palavra do estado — a palavra sempre, porque é
 * só isso que existe na tela e cor nunca informa sozinha. Com o ponteiro em cima
 * ([expanded]), o **balão** de uma conta só — a do anel sob o ponteiro, como no
 * Codenotch — aparece ao lado dele, do lado de dentro da tela, com a cauda
 * apontando para o anel. Passar para outro anel desliza o balão até ele.
 *
 * O contêiner ocupa o espaço que recebe (a janela inteira, no app): o notch fica
 * rente à borda, centrado em [notchCenter] ao longo dela, e o balão é preso
 * dentro do contêiner. **Os tamanhos são da geometria** ([sizes], de
 * `hudNotchSizes`): a janela é dimensionada antes de existir composição, e medir
 * aqui para devolver lá fecharia o laço de redimensionamento.
 *
 * Sem AWT: os gestos saem como eventos, e é por isso que ele é exercitável em
 * `runDesktopComposeUiTest`. Nenhuma coordenada de tela sai daqui — quem move a
 * janela lê o ponteiro na tela.
 */
@Composable
internal fun HudNotch(
    accounts: List<HudAccount>,
    edge: HudEdge,
    sizes: HudNotchSizes,
    fallbackLabel: String,
    fallbackTone: AppTone = AppTone.NEUTRAL,
    /**
     * O tamanho sem as contas que estão saindo (K1). Enquanto a vaga fecha, o
     * notch é desenhado recolhendo de [sizes] até ele, dentro da janela, que só
     * ajusta o tamanho no fim; `null` mantém [sizes] até a conta sair.
     */
    settledSizes: HudNotchSizes? = null,
    /** O estado de minimizar vindo do host (uma instância por janela); `null` lê aqui. */
    minimize: HudMinimizeProgress? = null,
    expanded: Boolean = false,
    dragging: Boolean = false,
    updateIndicator: HudUpdateIndicator? = null,
    /** Centro do notch ao longo da borda, no contêiner; `null` centra. */
    notchCenter: Dp? = null,
    language: AppLanguage = AppLanguage.PT,
    /** O balão já aberto nesta conta — para a demo da ajuda, que não tem ponteiro. */
    initialBalloonIndex: Int? = null,
    onHoverChange: (Boolean) -> Unit = {},
    onDragStart: () -> Unit = {},
    onDragMove: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    /** Clique num anel: recoleta aquela conta, como no Codenotch. */
    onRefreshAccount: (UsageTargetKey) -> Unit = {},
    /**
     * Pressão longa sobre um anel e arrasto: ao soltar, a ordem nova das contas (só se mudou). Esc e soltar no
     * mesmo lugar não chamam.
     */
    onReorderAccounts: (List<UsageTargetKey>) -> Unit = {},
    /** Os botões do card de cada conta, na fileira de baixo do balão dela. */
    accountActions: (@Composable (HudAccount) -> Unit)? = null,
    /**
     * O conteúdo do balão da engrenagem. Com ele, o ponteiro sobre a engrenagem
     * abre o balão, e o clique também; sem ele, o clique vai a [onGearClick].
     */
    appBalloon: (@Composable () -> Unit)? = null,
    appBalloonHeight: Dp = 0.dp,
    /** O clique na engrenagem quando não há [appBalloon]. */
    onGearClick: () -> Unit = {},
    gearDescription: String = "",
    /** Modo "recolher quando parada" (#400): parado, só a faixa fica à vista. */
    autoRetract: Boolean = false,
    /** O alfinete ao lado da mão, que alterna [autoRetract]; `null` não o mostra. */
    onToggleAutoRetract: (() -> Unit)? = null,
    /** Só Windows (#619): a alça de soltar/encaixar a barra na borda. */
    floatingSupported: Boolean = false,
    floating: Boolean = false,
    onToggleFloating: () -> Unit = {},
    /** Modo flutuante (#619, A10b): a coluna solta, com cantos nos 4 lados; o repouso é o buraco. */
    detached: Boolean = false,
    /** Deslocamento da coluna aberta ao longo da borda, a partir do buraco (A10c); só com [detached]. */
    detachedAlongShift: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    val hover = rememberHudHover(onHoverChange)
    val gearHovered by hover.gear.collectIsHoveredAsState()

    val open = expanded && !dragging
    // As alças ficam durante o arrasto: é a mão que está sendo carregada, e
    // tirá-la da composição cancelaria o gesto no meio.
    val showHandles = open || dragging
    // Z2 · íris do eclipse: recolhido, o notch some para dentro da faixa.
    val iris = rememberHudIrisFrame(revealed = !autoRetract || showHandles)
    // O balão é da conta do último anel sob o ponteiro, ou da engrenagem
    // ([APP_BALLOON]). Fechado, ele é esquecido: a próxima abertura começa pelo
    // anel em que o ponteiro entrar.
    val balloon = rememberHudNotchBalloonState(initialBalloonIndex)
    // Buraco negro da coluna (#600): as contas minimizadas. `balloon.holeSelection` é a conta aberta a partir
    // da lista do buraco; `null` mostra a lista. Sentinela do buraco = `accounts.size`.
    val holeMembers = remember(accounts) { hudColumnArrangement(accounts).holeMembers }
    val holeIndex = accounts.size
    LaunchedEffect(open) {
        if (!open) balloon.holeSelection = null
        if (!open && initialBalloonIndex == null) balloon.balloonIndex = null
    }
    // A conta aberta deixa o buraco (Restaurar ou outro caminho): a lista volta sem ela, e o buraco vazio fecha o balão.
    LaunchedEffect(holeMembers) {
        if (balloon.holeSelection != null && balloon.holeSelection !in holeMembers) balloon.holeSelection = null
        if (holeMembers.isEmpty() && balloon.balloonIndex == holeIndex) balloon.balloonIndex = null
    }
    // A engrenagem abre o balão dela no hover, como o anel abre o da conta
    // (#317). Só com o notch aberto: durante o arrasto as alças continuam na
    // tela, e o índice gravado ali reabriria o balão ao soltar.
    LaunchedEffect(gearHovered, open) {
        if (gearHovered && open && appBalloon != null) balloon.balloonIndex = APP_BALLOON
    }
    val shownIndex = balloon.balloonIndex?.takeIf { index ->
        index in accounts.indices ||
            (index == APP_BALLOON && appBalloon != null) ||
            (index == holeIndex && holeMembers.isNotEmpty())
    }
    // Durante a saída o balão continua desenhando a última conta.
    val lastShown = rememberLatestNonNull(shownIndex)
    // Centro de cada anel ao longo da borda, em px do contêiner.
    val ringCenters = remember { mutableStateMapOf<Int, Float>() }
    // A profundidade do centro de cada anel (e da engrenagem), de onde o feixe
    // do jato sai. Lida só no desenho do balão, então não precisa ser estado.
    val ringDepths = remember { HashMap<Int, Float>() }
    val balloonBox = remember { HudBalloonBox() }
    var rootCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    // A caixa de cada conta no corpo do notch, para achar o anel de um clique.
    // Não é estado: é lida no gesto, nunca na composição.
    val ringItemBounds = remember { HudRingHitBoxes() }
    val currentAccounts by rememberUpdatedState(accounts)
    val currentOnRefreshAccount by rememberUpdatedState(onRefreshAccount)
    val reorder = rememberHudRingReorder(ringItemBounds, currentAccounts, edge.isHorizontal, onReorderAccounts)

    val shape = remember(edge, detached) { hudNotchShape(edge, detached) }
    // Saída (K1): o notch recolhe junto com a vaga; o passo mais lento manda.
    val departures = rememberHudDepartures(accounts)
    val settle = departures.values.minOrNull()
    val minimizeState = minimize ?: rememberHudMinimizeProgress(accounts)
    // Minimizar (#576, A05b): o notch segura o maior tamanho até a transição acabar; sem AWT por quadro.
    val notchSize = rememberHudMinimizeNotchSize(
        moving = minimizeState.moving,
        current = if (settle != null && settledSizes != null) {
            lerp(sizes.collapsed, settledSizes.collapsed, settle)
        } else {
            sizes.collapsed
        },
    )

    // Ao longo da borda o balão segue o anel pela mola `GENTLE`. A primeira
    // posição de cada abertura é salto, senão ele entraria deslizando a partir
    // do anel da abertura anterior.
    val balloonAlong = remember { Animatable(0f) }
    val placement = remember { HudBalloonPlacement() }
    val alongSpec = appSpring(AppMotion.Springs.GENTLE, visibilityThreshold = BALLOON_ALONG_THRESHOLD_PX)
    // Abertura da coluna destacada (A10b): o deslocamento ao longo da borda acompanha este progresso.
    val openProgress by animateFloatAsState(
        if (open) 1f else 0f, animationSpec = if (LocalAppMotionPolicy.current.reduced) snap() else appSpring(AppMotion.Springs.GENTLE))
    val scope = rememberCoroutineScope()
    LaunchedEffect(open) {
        if (!open) placement.placed = false
    }

    Layout(
        modifier = modifier.onPlaced { coordinates -> rootCoordinates = coordinates },
        content = {
            // Repouso destacado (A10b): o buraco no lugar do corpo; aberto, a coluna de sempre.
            if (detached && !open) HudFloatingHole(params = HudOrbitParams(), update = updateIndicator,
                description = hudFloatingHoleDescription(language), modifier = Modifier.layoutId(HudNotchPart.NOTCH)) else Box(
                modifier = Modifier
                    .layoutId(HudNotchPart.NOTCH)
                    .requiredSize(notchSize)
                    .testTag(HUD_CONTENT_TEST_TAG)
                    .hudEclipseIris(edge, iris)
                    .appDepth(AppDepth.DIALOG, shape)
                    // Corpo = horizonte de eventos (M1): núcleo escuro e anel de fótons na borda.
                    .gargantuaHorizonBody(shape)
                    .hoverable(hover.notch)
                    .onPlaced { coordinates -> ringItemBounds.body = coordinates }
                    .hudReorderKeys(reorder)
                    .onKeyEvent { event ->
                        val closes = event.type == KeyEventType.KeyDown && event.key == Key.Escape && balloon.balloonIndex == holeIndex
                        if (closes) balloon.closeHole()
                        closes
                    }
                    // Só a mão move: arrastar pelo corpo tirava o notch ao clicar num anel.
                    .hudPressGesture(
                        draggable = false,
                        onDragStart = {},
                        onDragMove = {},
                        onDragEnd = {},
                        // Clique num anel recoleta a conta; na margem não faz nada.
                        // Clique fora do buraco com a lista aberta fecha a lista (#602, A02).
                        onClick = { position ->
                            val ring = hudRingKeyAt(ringItemBounds, currentAccounts, position)
                            if (ring != null) {
                                currentOnRefreshAccount(ring)
                            } else if (balloon.balloonIndex == holeIndex && ringItemBounds.boxes[holeIndex]?.contains(position) != true) {
                                balloon.closeHole()
                            }
                        },
                        onHoldStart = reorder::start, onHoldMove = reorder::move, onHoldEnd = reorder::end
                    )
                    .semantics { contentDescription = HUD_NOTCH_DESCRIPTION }
            ) {
                HudStarfieldCanvas(rings = { ringItemBounds.rings() }, running = { hudStarRunningRings(currentAccounts) }, forbidden = { hudStarfieldForbidden(ringItemBounds.boxes, ringItemBounds.rings()) }, holeIndex = holeIndex, modifier = Modifier.matchParentSize().clip(shape))
                HudRingStrip(
                    accounts = accounts,
                    edge = edge,
                    fallbackLabel = fallbackLabel, fallbackTone = fallbackTone,
                    size = notchSize,
                    compact = sizes.compact,
                    settledCompact = settledSizes?.compact ?: sizes.compact,
                    departures = departures,
                    settle = settle,
                    minimizeProgress = minimizeState.values,
                    reorderDrag = reorder.drag, reorderDrop = reorder.drop,
                    onRingHovered = balloon::ringHovered,
                    onHoleClicked = { balloon.holeClicked(holeIndex) },
                    onHoleHovered = { balloon.holeHovered(holeIndex) },
                    language = language,
                    onRingRefresh = { index -> accounts.getOrNull(index)?.let { account -> onRefreshAccount(account.targetKey) } },
                    onItemPlaced = { index, coordinates ->
                        val body = ringItemBounds.body
                        if (body != null && body.isAttached && coordinates.isAttached) {
                            ringItemBounds.boxes[index] = body.localBoundingBoxOf(coordinates)
                        }
                    },
                    onRingPlaced = { index, coordinates ->
                        if (recordHudAnchor(rootCoordinates, coordinates, edge, index, ringCenters, ringDepths)) {
                            ringItemBounds.measureRing(index, coordinates)
                        }
                    }
                )
            }
            HudRetractParts(autoRetract, onToggleAutoRetract, showHandles, accounts, edge, notchSize, ringCenters, { rootCoordinates }, language, hover)
            HudHandleHints(visible = !showHandles && !detached, edge = edge, updateTone = updateIndicator?.tone)
            AnimatedVisibility(
                visible = showHandles,
                modifier = Modifier.layoutId(HudNotchPart.MOVE),
                enter = handleEnter(edge, atStart = true),
                exit = handleExit(edge, atStart = true)
            ) {
                HudMoveHandle(
                    language = language,
                    carrying = dragging,
                    onDragStart = onDragStart,
                    onDragMove = onDragMove,
                    onDragEnd = onDragEnd,
                    interaction = hover.move
                )
            }
            AnimatedVisibility(
                visible = showHandles,
                modifier = Modifier.layoutId(HudNotchPart.GEAR),
                enter = handleEnter(edge, atStart = false),
                exit = handleExit(edge, atStart = false)
            ) {
                HudGearHandle(
                    // Cor nunca informa sozinha: a frase da atualização vai junto
                    // da descrição da engrenagem que carrega o ponto.
                    description = updateIndicator?.let { indicator -> "$gearDescription · ${indicator.description}" }
                        ?: gearDescription,
                    badgeTone = updateIndicator?.tone,
                    // O clique só abre, nunca fecha: com o hover já abrindo o
                    // balão, alternar fecharia o que o próprio ponteiro abriu.
                    onClick = {
                        if (appBalloon == null) {
                            onGearClick()
                        } else {
                            balloon.balloonIndex = APP_BALLOON
                        }
                    },
                    interaction = hover.gear,
                    // A cauda do balão da engrenagem aponta para ela, como a do
                    // de conta aponta para o anel.
                    modifier = Modifier.onGloballyPositioned { coordinates ->
                        recordHudAnchor(rootCoordinates, coordinates, edge, APP_BALLOON, ringCenters, ringDepths)
                    }
                )
            }
            if (floatingSupported) {
                AnimatedVisibility(
                    visible = showHandles,
                    modifier = Modifier.layoutId(HudFloatPart.HANDLE),
                    enter = handleEnter(edge, atStart = false),
                    exit = handleExit(edge, atStart = false)
                ) {
                    HudFloatHandle(
                        floating = floating,
                        language = language,
                        onToggle = onToggleFloating,
                        interaction = hover.float
                    )
                }
            }
            HudBalloonVisibility(
                visible = open && shownIndex != null, shownIndex = lastShown, edge = edge,
                accounts = accounts, holeMembers = holeMembers, holeSelection = balloon.holeSelection?.takeIf { index -> index in holeMembers },
                onHoleSelect = { position -> balloon.holeSelection = position }, sizes = sizes,
                appBalloon = appBalloon, appBalloonHeight = appBalloonHeight, language = language,
                accountActions = accountActions, ringCenters = ringCenters, ringDepths = ringDepths,
                balloonAlong = balloonAlong, balloonBox = balloonBox, rootCoordinates = { rootCoordinates },
                hover = hover.balloon
            )
        },
        measurePolicy = hudNotchMeasurePolicy(
            edge = edge,
            notchCenter = notchCenter,
            ringCenters = ringCenters,
            shownIndex = lastShown,
            placement = placement,
            balloonAlong = balloonAlong,
            alongSpec = alongSpec,
            scope = scope,
            detachedAlongShift = if (detached) detachedAlongShift else 0.dp,
            openProgress = { openProgress }
        )

    )
}

/**
 * Registra o centro e a profundidade de um anel (ou da engrenagem, com [key] = `APP_BALLOON`) no contêiner: de lá
 * saem a cauda e o feixe do balão. Devolve `false`, sem gravar nada, enquanto o contêiner ou o anel não estão anexados.
 */
private fun recordHudAnchor(
    root: LayoutCoordinates?,
    coordinates: LayoutCoordinates,
    edge: HudEdge,
    key: Int,
    centers: MutableMap<Int, Float>,
    depths: MutableMap<Int, Float>
): Boolean {
    if (root == null || !root.isAttached || !coordinates.isAttached) return false
    val center = root.localPositionOf(coordinates, Offset(coordinates.size.width / 2f, coordinates.size.height / 2f))
    centers[key] = if (edge.isHorizontal) center.x else center.y
    depths[key] = if (edge.isHorizontal) center.y else center.x
    return true
}

/**
 * O balão aberto (B3): a conta do último anel sob o ponteiro, a engrenagem ou a coluna do buraco. Fica fora de
 * [HudNotch] para a função dele caber no limite; os valores chegam prontos, sem estado de composição.
 */
@Composable
private fun HudBalloonVisibility(
    visible: Boolean,
    shownIndex: Int?,
    edge: HudEdge,
    accounts: List<HudAccount>,
    holeMembers: List<Int>,
    holeSelection: Int?,
    onHoleSelect: (Int?) -> Unit,
    sizes: HudNotchSizes,
    appBalloon: (@Composable () -> Unit)?,
    appBalloonHeight: Dp,
    language: AppLanguage,
    accountActions: (@Composable (HudAccount) -> Unit)?,
    ringCenters: Map<Int, Float>,
    ringDepths: Map<Int, Float>,
    balloonAlong: Animatable<Float, AnimationVector1D>,
    balloonBox: HudBalloonBox,
    rootCoordinates: () -> LayoutCoordinates?,
    hover: MutableInteractionSource
) {
    val holeIndex = accounts.size
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.layoutId(HudNotchPart.BALLOON),
        // B3 · jato relativístico: a transição toda é o quadro abaixo.
        enter = EnterTransition.None,
        exit = ExitTransition.None
    ) {
        val index = shownIndex ?: 0
        val jet = gargantuaJetProgress(index)
        val account = accounts.getOrNull(index)
        val app = appBalloon.takeIf { index == APP_BALLOON }
        val inHole = index == holeIndex && holeMembers.isNotEmpty()
        if (inHole || account != null || app != null) {
            HudBalloon(
                edge = edge,
                bodyHeight = when {
                    inHole -> sizes.balloon.height
                    app == null && account != null -> hudBalloonHeight(account, sizes.balloon.height)
                    else -> appBalloonHeight
                },
                tailCenter = { (ringCenters[index] ?: 0f) - balloonAlong.value },
                reveal = jet,
                beamOrigin = { balloonBox.depthTo(edge, ringDepths[index]) },
                modifier = Modifier
                    .hoverable(hover)
                    .onPlaced { coordinates ->
                        val root = rootCoordinates()
                        if (root != null && root.isAttached && coordinates.isAttached) {
                            balloonBox.bounds = root.localBoundingBoxOf(coordinates)
                        }
                    },
                // Sem crossfade na troca: o jato repetido já é a transição,
                // e o conteúdo antigo apagando dentro do desdobrar novo sujava.
                content = {
                    when {
                        index == APP_BALLOON -> appBalloon?.invoke()
                        inHole -> HudBlackHoleBalloonContent(
                            accounts = accounts,
                            members = holeMembers,
                            selected = holeSelection,
                            language = language,
                            accountActions = accountActions,
                            bodyHeight = sizes.balloon.height,
                            onSelect = { position -> onHoleSelect(position) },
                            onBack = { onHoleSelect(null) }
                        )
                        account != null -> HudAccountBalloonContent(account, language, accountActions, sizes.balloon.height)
                    }
                }
            )
        }
    }
}

/** As dicas das pontas do notch, em repouso: somem quando as alças aparecem. */
@Composable
private fun HudHandleHints(visible: Boolean, edge: HudEdge, updateTone: AppTone?) {
    for ((part, atStart) in listOf(HudNotchPart.HINT_START to true, HudNotchPart.HINT_END to false)) {
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.layoutId(part),
            enter = fadeIn(appTween(AppMotion.normal, delayMillis = HINT_RETURN_DELAY_MS)),
            exit = fadeOut(appTween(AppMotion.exit, AppMotion.exitEasing))
        ) {
            // Ponto da atualização na ponta da engrenagem: sem ele só via quem abrisse o notch.
            HudHandleHint(
                edge = edge,
                atStart = atStart,
                accent = if (atStart) null else updateTone?.color()
            )
        }
    }
}

internal enum class HudNotchPart { NOTCH, BALLOON, HINT_START, HINT_END, MOVE, GEAR }

/**
 * As fontes de hover do notch. O ponteiro "está no notch" enquanto estiver no
 * corpo, no balão, numa alça ou na faixa recolhida: sair do anel para o balão
 * atravessa a cauda, que é do balão, e chegar a uma alça passa rente à ponta.
 */
internal class HudHoverSources {
    val notch = MutableInteractionSource()
    val balloon = MutableInteractionSource()
    val move = MutableInteractionSource()
    val gear = MutableInteractionSource()
    val retract = MutableInteractionSource()
    val strip = MutableInteractionSource()
    val float = MutableInteractionSource()
    val all: List<MutableInteractionSource> get() = listOf(notch, balloon, move, gear, retract, strip, float)
}

/** As fontes, e [onHoverChange] a cada troca da união delas. */
@Composable
private fun rememberHudHover(onHoverChange: (Boolean) -> Unit): HudHoverSources {
    val sources = remember { HudHoverSources() }
    val states = sources.all.map { source -> source.collectIsHoveredAsState() }
    val hovered = states.any { state -> state.value }
    LaunchedEffect(hovered) {
        onHoverChange(hovered)
    }
    return sources
}

/** A faixa recolhida fica por baixo do corpo, acima das alças que saem de trás dele. */
internal const val STRIP_Z_INDEX = -0.5f

/** A caixa de cada conta no corpo do notch; o gesto do corpo acha o anel clicado por ela. */
internal class HudRingHitBoxes {
    var body: LayoutCoordinates? by mutableStateOf(null)
    val boxes = mutableMapOf<Int, Rect>()
    private val ringCoordinates = mutableStateMapOf<Int, LayoutCoordinates>()

    fun indexAt(position: Offset): Int? = boxes.entries.firstOrNull { (_, box) -> box.contains(position) }?.key

    /** Os limites de cada conta ao longo da borda, em ordem de índice; `null` se alguma caixa ainda não foi medida. */
    fun spansAlong(count: Int, horizontal: Boolean): List<CardAxisSpan>? {
        val found = (0 until count).mapNotNull { index -> boxes[index] }
        if (found.size != count) return null
        return found.map { box -> if (horizontal) CardAxisSpan(box.left, box.right) else CardAxisSpan(box.top, box.bottom) }
    }

    /** Guarda as coordenadas do anel; o Rect sai do corpo só na leitura (#586 A03). */
    fun measureRing(index: Int, coordinates: LayoutCoordinates) {
        ringCoordinates[index] = coordinates
    }

    /** Rect de cada anel no espaço do corpo; vazio enquanto o corpo não foi posicionado. */
    fun rings(): Map<Int, Rect> {
        val current = body?.takeIf { it.isAttached } ?: return emptyMap()
        return ringCoordinates.filterValues { it.isAttached }.mapValues { (_, ring) -> current.localBoundingBoxOf(ring) }
    }
}

/** O "índice" do balão da engrenagem, fora do intervalo das contas. */
private const val APP_BALLOON = -1

internal fun Placeable.alongSize(edge: HudEdge): Int = if (edge.isHorizontal) width else height

internal fun Placeable.acrossSize(edge: HudEdge): Int = if (edge.isHorizontal) height else width

/**
 * As alças saem **de dentro do notch**: deslizam da ponta para fora crescendo e
 * clareando juntas, pela mola `GENTLE`. Com o rebote da `EXPRESSIVE` e o disco
 * aparecendo já no lugar, a entrada lia como tremor ao passar o ponteiro.
 */
@Composable
internal fun handleEnter(edge: HudEdge, atStart: Boolean): EnterTransition {
    val slide = with(LocalDensity.current) { HANDLE_SLIDE.roundToPx() }
    return fadeIn(appTween(AppMotion.slow, AppMotion.emphasizedEasing)) +
        scaleIn(
            appSpring(AppMotion.Springs.GENTLE),
            initialScale = HANDLE_ENTER_SCALE,
            transformOrigin = handleOrigin(edge, atStart)
        ) +
        slideIn(appSpring(AppMotion.Springs.GENTLE, visibilityThreshold = IntOffset.VisibilityThreshold)) {
            towardNotch(edge, atStart, slide)
        }
}

/** A saída volta para dentro do notch, curta: o que sai já não interessa. */
@Composable
internal fun handleExit(edge: HudEdge, atStart: Boolean): ExitTransition {
    val slide = with(LocalDensity.current) { HANDLE_SLIDE.roundToPx() }
    return fadeOut(appTween(AppMotion.fast, AppMotion.exitEasing)) +
        scaleOut(
            appTween(AppMotion.fast, AppMotion.exitEasing),
            targetScale = HANDLE_ENTER_SCALE,
            transformOrigin = handleOrigin(edge, atStart)
        ) +
        slideOut(appTween(AppMotion.fast, AppMotion.exitEasing)) { towardNotch(edge, atStart, slide) }
}

/** O deslocamento de uma alça em direção ao notch: a da ponta de perto anda para a frente, a outra para trás. */
private fun towardNotch(edge: HudEdge, atStart: Boolean, distance: Int): IntOffset {
    val along = if (atStart) distance else -distance
    return if (edge.isHorizontal) IntOffset(along, 0) else IntOffset(0, along)
}

/** A alça cresce do lado que encosta no notch. */
private fun handleOrigin(edge: HudEdge, atStart: Boolean): TransformOrigin {
    val side = if (atStart) 1f else 0f
    return if (edge.isHorizontal) TransformOrigin(side, 0.5f) else TransformOrigin(0.5f, side)
}

private const val HANDLE_ENTER_SCALE = 0.6f
private val HANDLE_SLIDE = 14.dp

/** Abaixo do notch: a alça que desliza de dentro dele sai de trás, não por cima. */
internal const val HANDLE_Z_INDEX = -1f

/** O arco parado volta depois de as alças saírem, não por cima delas. */
private const val HINT_RETURN_DELAY_MS = 120

/**
 * Onde o balão foi posto nesta abertura. Não é estado de composição: é lido e
 * escrito no passo de layout, e como estado cada escrita pediria outro layout.
 */
internal class HudBalloonPlacement {
    var placed = false
    var index: Int? = null
    var target = 0f
}

/** Meio pixel: abaixo disso o balão já está no anel. */
internal const val BALLOON_ALONG_THRESHOLD_PX = 0.5f

/**
 * Onde a caixa do balão ficou no contêiner, para o feixe saber de onde vem. Não
 * é estado: é escrita no `onPlaced` e lida no desenho.
 */
private class HudBalloonBox {
    var bounds: Rect? = null

    /** Da borda do balão que encosta no notch até [ringDepth], em direção ao notch. */
    fun depthTo(edge: HudEdge, ringDepth: Float?): Float {
        val box = bounds ?: return 0f
        val ring = ringDepth ?: return 0f
        return when (edge) {
            HudEdge.TOP -> box.top - ring
            HudEdge.BOTTOM -> ring - box.bottom
            HudEdge.LEFT -> box.left - ring
            HudEdge.RIGHT -> ring - box.right
        }.coerceAtLeast(0f)
    }
}

/**
 * O quadro do jato (B3) movido pela própria transição do `AnimatedVisibility`:
 * entrando, [gargantuaJetOpenFrame] em [AppGargantuaTokens.jetOpenMillis];
 * saindo, [gargantuaJetCloseFrame] em [AppGargantuaTokens.jetCloseMillis]. A
 * saída espera esta animação terminar. Com "Reduzir animações" o tween vira
 * `snap()` e o balão abre e fecha de uma vez.
 */
@Composable
private fun AnimatedVisibilityScope.gargantuaJetProgress(index: Int): () -> GargantuaJetFrame {
    val policy = LocalAppMotionPolicy.current
    val progress = transition.animateFloat(
        transitionSpec = {
            val millis = if (targetState == EnterExitState.Visible) AppGargantuaTokens.jetOpenMillis else AppGargantuaTokens.jetCloseMillis
            appTweenSpec(millis, policy, LinearEasing)
        },
        label = "gargantuaJet"
    ) { state -> if (state == EnterExitState.Visible) 1f else 0f }
    // Trocar de anel (ou ir para a engrenagem) com o balão aberto repete a
    // abertura a partir do anel novo. Nasce em zero já na composição da troca,
    // para o conteúdo novo não aparecer inteiro por um quadro.
    val previous = remember { HudShownIndex() }
    val switched = remember(index) { Animatable(if (previous.value != null && previous.value != index) 0f else 1f) }
    SideEffect { previous.value = index }
    LaunchedEffect(switched) {
        if (switched.value < 1f) {
            switched.animateTo(1f, appTweenSpec(AppGargantuaTokens.jetOpenMillis, policy, LinearEasing))
        }
    }
    return {
        when {
            transition.targetState != EnterExitState.Visible -> gargantuaJetCloseFrame(1f - progress.value)
            switched.value < 1f -> gargantuaJetOpenFrame(switched.value)
            else -> gargantuaJetOpenFrame(progress.value)
        }
    }
}

/** O último balão mostrado, lido na troca; não é estado, só memória entre composições. */
private class HudShownIndex {
    var value: Int? = null
}

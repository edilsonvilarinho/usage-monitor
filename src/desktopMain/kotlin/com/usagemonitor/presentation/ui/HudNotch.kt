package com.usagemonitor.presentation.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.presentation.ui.theme.LocalAppMotionPolicy
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.usagemonitor.HUD_COUNTDOWN_GAP
import com.usagemonitor.HUD_COUNTDOWN_ICON
import com.usagemonitor.HUD_HANDLE_GAP
import com.usagemonitor.HUD_ITEM_GAP
import com.usagemonitor.HUD_NOTCH_PADDING_ACROSS
import com.usagemonitor.HUD_NOTCH_PADDING_ALONG
import com.usagemonitor.HUD_NOTCH_RADIUS
import com.usagemonitor.HUD_NOTCH_SHOULDER
import com.usagemonitor.HUD_RING_GAP
import com.usagemonitor.HUD_RING_SIZE
import com.usagemonitor.HUD_RING_STROKE
import com.usagemonitor.HUD_RING_TEXT_GAP
import com.usagemonitor.HUD_SHADOW_MARGIN
import com.usagemonitor.HudEdge
import com.usagemonitor.HudNotchSizes
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.hudBalloonHeight
import com.usagemonitor.presentation.ui.components.AppProviderMark
import com.usagemonitor.presentation.ui.components.AppRingArc
import com.usagemonitor.presentation.ui.components.AppStateCrossfade
import com.usagemonitor.presentation.ui.components.AppStatusIndicator
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.components.AppUsageRing
import com.usagemonitor.presentation.ui.components.appDepth
import com.usagemonitor.presentation.ui.components.appSheen
import com.usagemonitor.presentation.ui.components.color
import com.usagemonitor.presentation.ui.components.formatRefreshCountdown
import com.usagemonitor.presentation.ui.components.rememberLatestNonNull
import com.usagemonitor.presentation.ui.theme.AppDepth
import com.usagemonitor.presentation.ui.theme.AppMotion
import com.usagemonitor.presentation.ui.theme.AppSurfaceLadders
import com.usagemonitor.presentation.ui.theme.appSpring
import com.usagemonitor.presentation.ui.theme.appTween
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/** Descrição do corpo do notch — é por ela que leitor de tela e testes o acham. */
internal const val HUD_NOTCH_DESCRIPTION = "Barra HUD do Usage Monitor"

/** "Atualizar Anthropic — Padrão": a ação do clique num anel, na semântica dele. */
internal fun hudRefreshAccountLabel(account: HudAccount, language: AppLanguage): String =
    if (language == AppLanguage.PT) "Atualizar ${account.label}" else "Refresh ${account.label}"

/** O corpo do notch, cujo tamanho a geometria afirma (`HudNotchTest`). */
internal const val HUD_CONTENT_TEST_TAG = "hudContent"

internal const val HUD_UPDATE_INDICATOR_TAG = "hudUpdateIndicator"

/** Atualização pendente: só ícone, a frase inteira na semântica (issue #225). */
internal data class HudUpdateIndicator(
    val tone: AppTone,
    val description: String
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
    expanded: Boolean = false,
    dragging: Boolean = false,
    updateIndicator: HudUpdateIndicator? = null,
    nextRefreshAt: Instant? = null,
    countdownDescription: String? = null,
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
    onSwitchToCardsOnly: () -> Unit = {},
    /** Os botões do card de cada conta, na fileira de baixo do balão dela. */
    accountActions: (@Composable (HudAccount) -> Unit)? = null,
    /**
     * O conteúdo do balão da engrenagem. Com ele, a engrenagem abre e fecha o
     * balão; sem ele, o clique vai a [onGearClick].
     */
    appBalloon: (@Composable () -> Unit)? = null,
    appBalloonHeight: Dp = 0.dp,
    /** O clique na engrenagem quando não há [appBalloon]. */
    onGearClick: () -> Unit = {},
    gearDescription: String = "",
    modifier: Modifier = Modifier,
    nowProvider: () -> Instant = { Clock.System.now() },
    waitNextTick: suspend () -> Unit = { delay(1_000L) },
    /** O interruptor do `FooterBar`: sob o relógio dos testes o laço giraria para sempre. */
    countdownUpdatesEnabled: Boolean = true
) {
    // O ponteiro "está no notch" enquanto estiver no corpo, no balão ou numa
    // alça: sair do anel para o balão atravessa a cauda, que é do balão, e
    // chegar a uma alça passa rente à ponta do notch.
    val notchHover = remember { MutableInteractionSource() }
    val balloonHover = remember { MutableInteractionSource() }
    val moveHover = remember { MutableInteractionSource() }
    val gearHover = remember { MutableInteractionSource() }
    val notchHovered by notchHover.collectIsHoveredAsState()
    val balloonHovered by balloonHover.collectIsHoveredAsState()
    val moveHovered by moveHover.collectIsHoveredAsState()
    val gearHovered by gearHover.collectIsHoveredAsState()
    val hovered = notchHovered || balloonHovered || moveHovered || gearHovered
    LaunchedEffect(hovered) {
        onHoverChange(hovered)
    }

    val open = expanded && !dragging
    // As alças ficam durante o arrasto: é a mão que está sendo carregada, e
    // tirá-la da composição cancelaria o gesto no meio.
    val showHandles = open || dragging
    // O balão é da conta do último anel sob o ponteiro, ou da engrenagem
    // ([APP_BALLOON]). Fechado, ele é esquecido: a próxima abertura começa pelo
    // anel em que o ponteiro entrar.
    var balloonIndex by remember { mutableStateOf(initialBalloonIndex) }
    LaunchedEffect(open) {
        if (!open && initialBalloonIndex == null) balloonIndex = null
    }
    val shownIndex = balloonIndex?.takeIf { index ->
        index in accounts.indices || (index == APP_BALLOON && appBalloon != null)
    }
    // Durante a saída o balão continua desenhando a última conta.
    val lastShown = rememberLatestNonNull(shownIndex)
    // Centro de cada anel ao longo da borda, em px do contêiner.
    val ringCenters = remember { mutableStateMapOf<Int, Float>() }
    var rootCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    // A caixa de cada conta no corpo do notch, para achar o anel de um clique.
    // Não é estado: é lida no gesto, nunca na composição.
    val ringItemBounds = remember { HudRingHitBoxes() }
    val currentAccounts by rememberUpdatedState(accounts)
    val currentOnRefreshAccount by rememberUpdatedState(onRefreshAccount)

    val shape = remember(edge) { HudNotchShape(edge) }
    val ladder = AppSurfaceLadders.current

    val countdown: (@Composable () -> Unit)? = if (nextRefreshAt == null || countdownDescription == null) {
        null
    } else {
        {
            HudCountdown(
                nextRefreshAt = nextRefreshAt,
                description = countdownDescription,
                vertical = !edge.isHorizontal,
                nowProvider = nowProvider,
                waitNextTick = waitNextTick,
                updatesEnabled = countdownUpdatesEnabled
            )
        }
    }

    // Ao longo da borda o balão segue o anel pela mola `GENTLE`. A primeira
    // posição de cada abertura é salto, senão ele entraria deslizando a partir
    // do anel da abertura anterior.
    val balloonAlong = remember { Animatable(0f) }
    val placement = remember { HudBalloonPlacement() }
    val alongSpec = appSpring(AppMotion.Springs.GENTLE, visibilityThreshold = BALLOON_ALONG_THRESHOLD_PX)
    val scope = rememberCoroutineScope()
    LaunchedEffect(open) {
        if (!open) placement.placed = false
    }

    Layout(
        modifier = modifier.onPlaced { coordinates -> rootCoordinates = coordinates },
        content = {
            Box(
                modifier = Modifier
                    .layoutId(HudNotchPart.NOTCH)
                    .requiredSize(sizes.collapsed)
                    .testTag(HUD_CONTENT_TEST_TAG)
                    .appDepth(AppDepth.DIALOG, shape)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surface)
                    .appSheen()
                    .border(1.dp, ladder.borderTop, shape)
                    .hoverable(notchHover)
                    .onPlaced { coordinates -> ringItemBounds.body = coordinates }
                    // Só a mão move. Arrastando pelo corpo o notch saía do lugar
                    // quando a intenção era clicar num anel, e o cursor de mover
                    // sobre a faixa inteira dizia que ela toda era alça.
                    .hudPressGesture(
                        draggable = false,
                        onDragStart = {},
                        onDragMove = {},
                        onDragEnd = {},
                        // Clique num anel recoleta aquela conta; fora dos anéis
                        // (contagem, margem) não faz nada. Abrir a janela padrão
                        // ficou com a engrenagem, a bandeja e `Ctrl+Shift+H`.
                        onClick = { position ->
                            ringItemBounds.indexAt(position)
                                ?.let { index -> currentAccounts.getOrNull(index) }
                                ?.let { account -> currentOnRefreshAccount(account.targetKey) }
                        },
                        onSecondaryClick = onSwitchToCardsOnly
                    )
                    .semantics { contentDescription = HUD_NOTCH_DESCRIPTION }
            ) {
                HudRingStrip(
                    accounts = accounts,
                    edge = edge,
                    fallbackLabel = fallbackLabel,
                    fallbackTone = fallbackTone,
                    updateIndicator = updateIndicator,
                    countdown = countdown,
                    size = sizes.collapsed,
                    compact = sizes.compact,
                    onRingHovered = { index -> balloonIndex = index },
                    language = language,
                    onRingRefresh = { index -> accounts.getOrNull(index)?.let { account -> onRefreshAccount(account.targetKey) } },
                    onItemPlaced = { index, coordinates ->
                        val body = ringItemBounds.body
                        if (body != null && body.isAttached && coordinates.isAttached) {
                            ringItemBounds.boxes[index] = body.localBoundingBoxOf(coordinates)
                        }
                    },
                    onRingPlaced = { index, coordinates ->
                        val root = rootCoordinates
                        if (root != null && root.isAttached && coordinates.isAttached) {
                            val center = root.localPositionOf(
                                coordinates,
                                Offset(coordinates.size.width / 2f, coordinates.size.height / 2f)
                            )
                            ringCenters[index] = if (edge.isHorizontal) center.x else center.y
                        }
                    }
                )
            }
            for ((part, atStart) in listOf(HudNotchPart.HINT_START to true, HudNotchPart.HINT_END to false)) {
                AnimatedVisibility(
                    visible = !showHandles,
                    modifier = Modifier.layoutId(part),
                    enter = fadeIn(appTween(AppMotion.normal, delayMillis = HINT_RETURN_DELAY_MS)),
                    exit = fadeOut(appTween(AppMotion.exit, AppMotion.exitEasing))
                ) {
                    HudHandleHint(edge = edge, atStart = atStart)
                }
            }
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
                    interaction = moveHover
                )
            }
            AnimatedVisibility(
                visible = showHandles,
                modifier = Modifier.layoutId(HudNotchPart.GEAR),
                enter = handleEnter(edge, atStart = false),
                exit = handleExit(edge, atStart = false)
            ) {
                HudGearHandle(
                    description = gearDescription,
                    onClick = {
                        if (appBalloon == null) {
                            onGearClick()
                        } else {
                            balloonIndex = if (balloonIndex == APP_BALLOON) null else APP_BALLOON
                        }
                    },
                    interaction = gearHover,
                    // A cauda do balão da engrenagem aponta para ela, como a do
                    // de conta aponta para o anel.
                    modifier = Modifier.onGloballyPositioned { coordinates ->
                        val root = rootCoordinates
                        if (root != null && root.isAttached && coordinates.isAttached) {
                            val center = root.localPositionOf(
                                coordinates,
                                Offset(coordinates.size.width / 2f, coordinates.size.height / 2f)
                            )
                            ringCenters[APP_BALLOON] = if (edge.isHorizontal) center.x else center.y
                        }
                    }
                )
            }
            AnimatedVisibility(
                visible = open && shownIndex != null,
                modifier = Modifier.layoutId(HudNotchPart.BALLOON),
                enter = balloonEnter(edge),
                exit = fadeOut(appTween(AppMotion.exit, AppMotion.exitEasing))
            ) {
                val index = lastShown ?: 0
                val account = accounts.getOrNull(index)
                val app = appBalloon.takeIf { index == APP_BALLOON }
                if (account != null || app != null) {
                    HudBalloon(
                        edge = edge,
                        bodyHeight = if (app != null) appBalloonHeight else hudBalloonHeight(account!!),
                        tailCenter = { (ringCenters[index] ?: 0f) - balloonAlong.value },
                        modifier = Modifier.hoverable(balloonHover),
                        content = {
                            AppStateCrossfade(state = index, key = { shown -> shown }) { shown ->
                                val shownAccount = accounts.getOrNull(shown)
                                when {
                                    shown == APP_BALLOON -> appBalloon?.invoke()
                                    shownAccount != null -> HudAccountBalloonContent(shownAccount, language, accountActions)
                                }
                            }
                        }
                    )
                }
            }
        }
    ) { measurables, constraints ->
        val notch = measurables.first { measurable -> measurable.layoutId == HudNotchPart.NOTCH }
            .measure(Constraints())
        val balloon = measurables.firstOrNull { measurable -> measurable.layoutId == HudNotchPart.BALLOON }
            ?.measure(Constraints())
        val extras = listOf(HudNotchPart.HINT_START, HudNotchPart.HINT_END, HudNotchPart.MOVE, HudNotchPart.GEAR)
            .associateWith { part ->
                measurables.firstOrNull { measurable -> measurable.layoutId == part }?.measure(Constraints())
            }
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else notch.width
        val height = if (constraints.hasBoundedHeight) constraints.maxHeight else notch.height
        val alongLength = if (edge.isHorizontal) width else height
        val notchAlong = if (edge.isHorizontal) notch.width else notch.height
        val center = notchCenter?.roundToPx() ?: (alongLength / 2)
        val notchStart = (center - notchAlong / 2).coerceIn(0, (alongLength - notchAlong).coerceAtLeast(0))

        // O balão centrado no anel e preso ao contêiner, com a margem da sombra.
        var balloonStart = 0
        if (balloon != null && balloon.width > 0) {
            val balloonAlongSize = if (edge.isHorizontal) balloon.width else balloon.height
            val ring = ringCenters[lastShown ?: 0] ?: (notchStart + notchAlong / 2f)
            val margin = HUD_SHADOW_MARGIN.roundToPx()
            val maxStart = (alongLength - margin - balloonAlongSize).coerceAtLeast(0).toFloat()
            val target = (ring - balloonAlongSize / 2f).coerceIn(margin.toFloat().coerceAtMost(maxStart), maxStart)
            if (!placement.placed) {
                placement.placed = true
                placement.target = target
                scope.launch { balloonAlong.snapTo(target) }
                balloonStart = target.roundToInt()
            } else {
                if (placement.target != target) {
                    placement.target = target
                    scope.launch { balloonAlong.animateTo(target, alongSpec) }
                }
                balloonStart = balloonAlong.value.roundToInt()
            }
        }

        val notchAcross = if (edge.isHorizontal) notch.height else notch.width
        val handleGap = HUD_HANDLE_GAP.roundToPx()

        layout(width, height) {
            // (ao longo, a partir da borda da tela) → posição na caixa de cada borda.
            fun Placeable.placeAt(along: Int, across: Int, zIndex: Float = 0f) {
                val acrossSize = if (edge.isHorizontal) this.height else this.width
                when (edge) {
                    HudEdge.TOP -> place(along, across, zIndex)
                    HudEdge.BOTTOM -> place(along, height - across - acrossSize, zIndex)
                    HudEdge.LEFT -> place(across, along, zIndex)
                    HudEdge.RIGHT -> place(width - across - acrossSize, along, zIndex)
                }
            }
            notch.placeAt(notchStart, 0)
            val notchEnd = notchStart + notchAlong
            extras[HudNotchPart.HINT_START]?.let { hint -> hint.placeAt(notchStart - hint.alongSize(edge), 0) }
            extras[HudNotchPart.HINT_END]?.let { hint -> hint.placeAt(notchEnd, 0) }
            // As alças centradas na espessura do notch, uma além de cada ponta, e
            // por baixo dele: entrando, elas saem de trás do notch.
            extras[HudNotchPart.MOVE]?.let { handle ->
                handle.placeAt(
                    notchStart - handleGap - handle.alongSize(edge),
                    (notchAcross - handle.acrossSize(edge)) / 2,
                    HANDLE_Z_INDEX
                )
            }
            extras[HudNotchPart.GEAR]?.let { handle ->
                handle.placeAt(notchEnd + handleGap, (notchAcross - handle.acrossSize(edge)) / 2, HANDLE_Z_INDEX)
            }
            if (balloon != null) {
                when (edge) {
                    HudEdge.TOP -> balloon.place(balloonStart, notch.height)
                    HudEdge.BOTTOM -> balloon.place(balloonStart, height - notch.height - balloon.height)
                    HudEdge.LEFT -> balloon.place(notch.width, balloonStart)
                    HudEdge.RIGHT -> balloon.place(width - notch.width - balloon.width, balloonStart)
                }
            }
        }
    }
}

private enum class HudNotchPart { NOTCH, BALLOON, HINT_START, HINT_END, MOVE, GEAR }

/** A caixa de cada conta no corpo do notch; o gesto do corpo acha o anel clicado por ela. */
private class HudRingHitBoxes {
    var body: LayoutCoordinates? = null
    val boxes = mutableMapOf<Int, Rect>()

    fun indexAt(position: Offset): Int? = boxes.entries.firstOrNull { (_, box) -> box.contains(position) }?.key
}

/** O "índice" do balão da engrenagem, fora do intervalo das contas. */
private const val APP_BALLOON = -1

private fun Placeable.alongSize(edge: HudEdge): Int = if (edge.isHorizontal) width else height

private fun Placeable.acrossSize(edge: HudEdge): Int = if (edge.isHorizontal) height else width

/**
 * As alças saem **de dentro do notch**: deslizam da ponta para fora crescendo e
 * clareando juntas, pela mola `GENTLE`. Com o rebote da `EXPRESSIVE` e o disco
 * aparecendo já no lugar, a entrada lia como tremor ao passar o ponteiro.
 */
@Composable
private fun handleEnter(edge: HudEdge, atStart: Boolean): EnterTransition {
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
private fun handleExit(edge: HudEdge, atStart: Boolean): ExitTransition {
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
private const val HANDLE_Z_INDEX = -1f

/**
 * O balão desce do notch: fade, escala a partir do lado do notch e um
 * deslizamento curto, todos pela mola `GENTLE` — sem o rebote, que somado ao
 * balão trocando de conta fazia a abertura tremer.
 */
@Composable
private fun balloonEnter(edge: HudEdge): EnterTransition {
    val slide = with(LocalDensity.current) { BALLOON_SLIDE.roundToPx() }
    return fadeIn(appTween(AppMotion.slow, AppMotion.emphasizedEasing)) +
        scaleIn(
            appSpring(AppMotion.Springs.GENTLE),
            initialScale = BALLOON_ENTER_SCALE,
            transformOrigin = balloonOrigin(edge)
        ) +
        slideIn(appSpring(AppMotion.Springs.GENTLE, visibilityThreshold = IntOffset.VisibilityThreshold)) {
            when (edge) {
                HudEdge.TOP -> IntOffset(0, -slide)
                HudEdge.BOTTOM -> IntOffset(0, slide)
                HudEdge.LEFT -> IntOffset(-slide, 0)
                HudEdge.RIGHT -> IntOffset(slide, 0)
            }
        }
}

private val BALLOON_SLIDE = 8.dp

/** O arco parado volta depois de as alças saírem, não por cima delas. */
private const val HINT_RETURN_DELAY_MS = 120

/**
 * Onde o balão foi posto nesta abertura. Não é estado de composição: é lido e
 * escrito no passo de layout, e como estado cada escrita pediria outro layout.
 */
private class HudBalloonPlacement {
    var placed = false
    var target = 0f
}

/** O balão cresce a partir do lado do notch. */
private fun balloonOrigin(edge: HudEdge): TransformOrigin = when (edge) {
    HudEdge.TOP -> TransformOrigin(0.5f, 0f)
    HudEdge.BOTTOM -> TransformOrigin(0.5f, 1f)
    HudEdge.LEFT -> TransformOrigin(0f, 0.5f)
    HudEdge.RIGHT -> TransformOrigin(1f, 0.5f)
}

private const val BALLOON_ENTER_SCALE = 0.94f

/** O anel "pressionado" enquanto a conta recoleta. */
private const val RING_REFRESH_SCALE = 0.9f

/** A marca gira uma volta por segundo, o ritmo do glifo de recarga do card. */
private const val RING_REFRESH_TURN_MILLIS = 1_000

/** Meio pixel: abaixo disso o balão já está no anel. */
private const val BALLOON_ALONG_THRESHOLD_PX = 0.5f

/** A faixa de anéis, do tamanho do notch. */
@Composable
private fun HudRingStrip(
    accounts: List<HudAccount>,
    edge: HudEdge,
    fallbackLabel: String,
    fallbackTone: AppTone,
    updateIndicator: HudUpdateIndicator?,
    countdown: (@Composable () -> Unit)?,
    size: DpSize,
    compact: Boolean,
    language: AppLanguage,
    onRingHovered: (Int) -> Unit,
    onRingRefresh: (Int) -> Unit,
    onItemPlaced: (Int, LayoutCoordinates) -> Unit,
    onRingPlaced: (Int, LayoutCoordinates) -> Unit
) {
    val items: @Composable () -> Unit = {
        if (accounts.isEmpty()) {
            AppStatusIndicator(label = fallbackLabel, tone = fallbackTone)
        } else {
            accounts.forEachIndexed { index, account ->
                HudRingItem(
                    account = account,
                    vertical = !edge.isHorizontal,
                    compact = compact,
                    language = language,
                    onHovered = { onRingHovered(index) },
                    onRefresh = { onRingRefresh(index) },
                    onItemPlaced = { coordinates -> onItemPlaced(index, coordinates) },
                    onPlaced = { coordinates -> onRingPlaced(index, coordinates) }
                )
            }
        }
        // Atualização e contagem são do app, não de uma conta: uma vez, no fim.
        updateIndicator?.let { indicator -> HudUpdateBadge(indicator) }
        countdown?.invoke()
    }
    if (edge.isHorizontal) {
        Row(
            modifier = Modifier
                .size(size)
                .padding(horizontal = HUD_NOTCH_SHOULDER + HUD_NOTCH_PADDING_ALONG, vertical = HUD_NOTCH_PADDING_ACROSS),
            horizontalArrangement = Arrangement.spacedBy(HUD_ITEM_GAP, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) { items() }
    } else {
        Column(
            modifier = Modifier
                .size(size)
                .padding(horizontal = HUD_NOTCH_PADDING_ACROSS, vertical = HUD_NOTCH_SHOULDER + HUD_NOTCH_PADDING_ALONG),
            verticalArrangement = Arrangement.spacedBy(HUD_ITEM_GAP, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) { items() }
    }
}

@Composable
private fun HudRingItem(
    account: HudAccount,
    vertical: Boolean,
    /** A célula do Codenotch: anel e percentual embaixo, sem a palavra. */
    compact: Boolean,
    language: AppLanguage,
    onHovered: () -> Unit,
    onRefresh: () -> Unit,
    onItemPlaced: (LayoutCoordinates) -> Unit,
    onPlaced: (LayoutCoordinates) -> Unit
) {
    // Coletando, o anel fica pressionado — o `refreshRing` do Codenotch — e a
    // marca gira, só com a política contínua.
    val pressScale by animateFloatAsState(
        targetValue = if (account.refreshing) RING_REFRESH_SCALE else 1f,
        animationSpec = appSpring(AppMotion.Springs.SNAPPY),
        label = "hudRingRefreshScale"
    )
    val policy = LocalAppMotionPolicy.current
    val markTurn = if (account.refreshing && policy.continuous) {
        val transition = rememberInfiniteTransition(label = "hudRingRefresh")
        val angle by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(RING_REFRESH_TURN_MILLIS, easing = LinearEasing)),
            label = "hudRingRefreshAngle"
        )
        angle
    } else {
        0f
    }
    val refreshLabel = hudRefreshAccountLabel(account, language)
    // A ação é **declarada** na semântica, não instalada: um `clickable` aqui
    // consumiria o `down` e o arrasto pelo corpo nunca começaria.
    val itemModifier = Modifier
        .onGloballyPositioned(onItemPlaced)
        .semantics {
            onClick(label = refreshLabel) {
                onRefresh()
                true
            }
        }
    // O anel sob o ponteiro escolhe a conta do balão.
    val hover = remember { MutableInteractionSource() }
    val isHovered by hover.collectIsHoveredAsState()
    val currentOnHovered by rememberUpdatedState(onHovered)
    LaunchedEffect(isHovered) {
        if (isHovered) currentOnHovered()
    }
    val focus = account.focus
    val description = buildString {
        append(account.label)
        account.planLabel?.let { plan -> append(" ($plan)") }
        append(" · ")
        append(account.statusLabel)
        account.quotas.forEach { quota -> append(" · ${quota.shortLabel} ${quota.percentText}") }
    }
    val ring: @Composable () -> Unit = {
        // A marca do fornecedor no centro do anel, como no Codenotch: a conta se
        // reconhece antes de ler o nome, que o notch recolhido nem mostra. Na
        // cor do texto e não no acento — em volta dela já estão os arcos, e o
        // acento ali competiria com a cor de risco deles.
        Box(
            modifier = Modifier
                .onGloballyPositioned(onPlaced)
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                },
            contentAlignment = Alignment.Center
        ) {
            AppUsageRing(
                arcs = account.rings.map { quota -> AppRingArc(quota.fraction, quota.tone, quota.hasForecast) },
                description = description,
                size = HUD_RING_SIZE,
                stroke = HUD_RING_STROKE,
                gap = HUD_RING_GAP,
                active = account.sessionActive,
                attention = account.needsAttention
            )
            AppProviderMark(
                source = account.source,
                tint = MaterialTheme.colorScheme.onSurface,
                size = hudRingMarkSize(account.rings.size),
                modifier = Modifier.graphicsLayer { rotationZ = markTurn }
            )
        }
    }
    val percent: @Composable () -> Unit = {
        Text(
            text = focus?.percentText.orEmpty(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
    val word: @Composable () -> Unit = {
        Text(
            text = account.statusLabel,
            style = MaterialTheme.typography.labelSmall,
            color = account.tone.color(),
            maxLines = if (vertical) 2 else 1,
            overflow = TextOverflow.Ellipsis
        )
    }
    if (vertical || compact) {
        Column(modifier = itemModifier.hoverable(hover), horizontalAlignment = Alignment.CenterHorizontally) {
            ring()
            percent()
            // Compacto, a palavra fica no balão e na descrição do anel: com
            // contas demais ela é o que fazia a faixa atravessar a tela.
            if (!compact) word()
        }
    } else {
        Row(
            modifier = itemModifier.hoverable(hover),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HUD_RING_TEXT_GAP)
        ) {
            ring()
            Column {
                percent()
                word()
            }
        }
    }
}

/**
 * A marca cabe no miolo que os arcos deixam livre: cada arco come um traço e um
 * vão de cada lado. 70% do miolo deixa ar entre a marca e o arco de dentro.
 *
 * A sessão ativa não entra na conta: a órbita dela gira por fora do anel. Quando
 * ela morava por dentro, a marca da conta trabalhando caía de 14dp para 8dp.
 */
internal fun hudRingMarkSize(arcs: Int): Dp {
    val used = (HUD_RING_STROKE + HUD_RING_GAP) * 2 * arcs.coerceIn(1, 3)
    return ((HUD_RING_SIZE - used) * 0.7f).coerceAtLeast(6.dp)
}

/**
 * A silhueta do notch: reta e rente na borda da tela, cantos redondos do lado de
 * dentro e **ombros côncavos** ligando os dois — o que faz ele ler como parte da
 * borda, como o notch de hardware que o Codenotch imita, e não como pílula
 * flutuando rente a ela. Isenta do teto de 10dp de raio: é forma, não painel.
 */
internal class HudNotchShape(private val edge: HudEdge) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val s = with(density) { HUD_NOTCH_SHOULDER.toPx() }
        val r = with(density) { HUD_NOTCH_RADIUS.toPx() }
        // Desenhado para a borda de cima em (along, across) e levado às outras
        // bordas por reflexão/rotação dos pontos — de controle inclusive, que é
        // afim e portanto preserva as curvas.
        val along = if (edge.isHorizontal) size.width else size.height
        val across = if (edge.isHorizontal) size.height else size.width
        val radius = minOf(r, (along - 2 * s) / 2, across / 2).coerceAtLeast(0f)
        val map: (Float, Float) -> Offset = when (edge) {
            HudEdge.TOP -> { a, c -> Offset(a, c) }
            HudEdge.BOTTOM -> { a, c -> Offset(a, size.height - c) }
            HudEdge.LEFT -> { a, c -> Offset(c, a) }
            HudEdge.RIGHT -> { a, c -> Offset(size.width - c, a) }
        }
        val path = Path()
        fun moveTo(a: Float, c: Float) = map(a, c).let { point -> path.moveTo(point.x, point.y) }
        fun lineTo(a: Float, c: Float) = map(a, c).let { point -> path.lineTo(point.x, point.y) }
        fun quadTo(ca: Float, cc: Float, a: Float, c: Float) {
            val control = map(ca, cc)
            val end = map(a, c)
            path.quadraticTo(control.x, control.y, end.x, end.y)
        }
        moveTo(0f, 0f)
        lineTo(along, 0f)
        // Ombro côncavo: da borda da tela até o corpo do notch.
        quadTo(along - s, 0f, along - s, s)
        lineTo(along - s, across - radius)
        quadTo(along - s, across, along - s - radius, across)
        lineTo(s + radius, across)
        quadTo(s, across, s, across - radius)
        lineTo(s, s)
        quadTo(s, 0f, 0f, 0f)
        path.close()
        return Outline.Generic(path)
    }

    override fun equals(other: Any?): Boolean = other is HudNotchShape && other.edge == edge

    override fun hashCode(): Int = edge.hashCode()
}

@Composable
private fun HudUpdateBadge(indicator: HudUpdateIndicator) {
    // Sem clique próprio (#225): o balão da engrenagem repete a frase, e o
    // reinício é oferecido na janela padrão. Reiniciar direto daqui faria um
    // clique de rotina reiniciar o app sem aviso.
    Icon(
        imageVector = Icons.Rounded.SystemUpdate,
        contentDescription = indicator.description,
        modifier = Modifier
            .size(HUD_COUNTDOWN_ICON)
            .testTag(HUD_UPDATE_INDICATOR_TAG),
        tint = indicator.tone.color()
    )
}

/**
 * A contagem até a próxima coleta (issue #185). O tique mora aqui e não em quem
 * chama, e para em zero — é suspensão, não quadro pendente.
 */
@Composable
internal fun HudCountdown(
    nextRefreshAt: Instant,
    description: String,
    vertical: Boolean,
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

    val icon: @Composable () -> Unit = {
        Icon(
            imageVector = Icons.Rounded.Refresh,
            contentDescription = description,
            modifier = Modifier.size(HUD_COUNTDOWN_ICON),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    val text: @Composable () -> Unit = {
        Text(
            text = formatRefreshCountdown(secondsUntilRefresh),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
    if (vertical) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            icon()
            text()
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HUD_COUNTDOWN_GAP)) {
            icon()
            text()
        }
    }
}

/**
 * Um gesto só para as ações do notch: mover (só pela mão), clicar num anel e —
 * com o botão direito, #215 — trocar direto para "Somente cards". O que separa
 * clique de arrasto é o limiar de deslocamento; o botão direito é decidido no
 * próprio `down` e nunca vira arrasto. Nenhuma coordenada sai daqui.
 *
 * Sem [draggable], passar do limiar só desiste do clique: o ponteiro que
 * escorregou não recoleta a conta, e o `move` não é consumido.
 */
@Composable
internal fun Modifier.hudPressGesture(
    draggable: Boolean = true,
    onDragStart: () -> Unit,
    onDragMove: () -> Unit,
    onDragEnd: () -> Unit,
    /** Recebe a posição do `down`, no nó do gesto: é por ela que o notch acha o anel. */
    onClick: (Offset) -> Unit,
    onSecondaryClick: () -> Unit = {}
): Modifier {
    val currentDragStart by rememberUpdatedState(onDragStart)
    val currentDragMove by rememberUpdatedState(onDragMove)
    val currentDragEnd by rememberUpdatedState(onDragEnd)
    val currentClick by rememberUpdatedState(onClick)
    val currentSecondaryClick by rememberUpdatedState(onSecondaryClick)

    return pointerInput(Unit) {
        awaitEachGesture {
            // `awaitFirstDown` só reage ao botão primário do mouse; o laço abaixo
            // é o mesmo, sem esse filtro, para o direito chegar aqui.
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
            var slipped = false
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { candidate -> candidate.id == down.id }
                    ?: break
                if (!change.pressed) {
                    when {
                        dragging -> currentDragEnd()
                        !slipped -> currentClick(down.position)
                    }
                    break
                }
                travelled += change.positionChange().getDistance()
                if (!dragging && !slipped && travelled > viewConfiguration.touchSlop) {
                    if (draggable) {
                        dragging = true
                        currentDragStart()
                    } else {
                        slipped = true
                    }
                }
                if (dragging) {
                    change.consume()
                    currentDragMove()
                }
            }
        }
    }
}

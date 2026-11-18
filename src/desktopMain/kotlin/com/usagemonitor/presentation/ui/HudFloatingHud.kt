package com.usagemonitor.presentation.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.usagemonitor.HUD_BALLOON_GAP
import com.usagemonitor.HUD_BALLOON_WIDTH
import com.usagemonitor.HUD_HANDLE_GAP
import com.usagemonitor.HUD_HANDLE_SIZE
import com.usagemonitor.HudEdge
import com.usagemonitor.HudOrbitParams
import com.usagemonitor.HudPxRect
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.hudColumnArrangement
import com.usagemonitor.hudFloatingBalloonTop
import com.usagemonitor.hudFloatingColumnParts
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.verticalCollapsed
import kotlin.math.roundToInt

/** Tag do vão de arrasto sobre o buraco, para os testes de tela. */
const val HUD_FLOATING_HOLE_DRAG_TAG = "hud-floating-hole-drag"

/** Tag da coluna de anéis da janela flutuante, para os testes de tela (A11e). */
const val HUD_FLOATING_COLUMN_TEST_TAG = "hud-floating-column"

/** Balão aberto na janela flutuante: de uma conta (pelo índice), da engrenagem ou da lista do buraco negro. */
internal sealed interface HudFloatingBalloon {
    data class Account(val index: Int) : HudFloatingBalloon
    data object App : HudFloatingBalloon

    /** Lista das contas no buraco negro; [selection] é a conta escolhida da lista, ou nenhuma (A11g). */
    data class Hole(val selection: Int?) : HudFloatingBalloon
}

/**
 * Janela flutuante da HUD (A11c-2). O buraco fica sempre visível; expandido mostra a coluna vertical de
 * anéis ao lado dele, as alças (engrenagem e encaixar) logo abaixo da coluna e o balão alinhado ao anel
 * em foco. Tamanho fixo, vindo do host. Arrastar o buraco chama início, fim e passos do arrasto. O
 * conteúdo dos balões vem de fora: o host monta o `HudBalloon` com a cauda apontada para a conta.
 * O hover é por parte visível (A11f): o vão entre o buraco e a coluna não conta como janela.
 */
@Composable
internal fun HudFloatingHud(
    accounts: List<HudAccount>,
    params: HudOrbitParams,
    language: AppLanguage,
    expanded: Boolean,
    balloon: HudFloatingBalloon?,
    balloonSide: HudEdge,
    update: HudUpdateIndicator?,
    gearDescription: String,
    fallbackLabel: String,
    fallbackTone: AppTone,
    holeScreenY: Dp,
    areaTop: Dp,
    areaBottom: Dp,
    onHoverChange: (Boolean) -> Unit,
    onAccountClick: (Int) -> Unit,
    onGearClick: () -> Unit,
    onToggleFloating: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: () -> Unit,
    onDragEnd: () -> Unit,
    balloonHeight: Dp,
    accountBalloon: @Composable (Int) -> Unit,
    appBalloon: @Composable () -> Unit,
    accountActions: @Composable (HudAccount) -> Unit,
    onHoleHover: () -> Unit,
    onHoleClick: () -> Unit,
    onHoleSelect: (Int) -> Unit,
    onHoleBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hitBoxes = remember { HudRingHitBoxes() }
    val columnSize = verticalCollapsed(accounts, fallbackLabel, compact = false)
    val hover = rememberHudFloatingHover(onHoverChange)
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val parts = hudFloatingColumnParts(
            windowSize = DpSize(maxWidth, maxHeight),
            expanded = expanded,
            columnSize = columnSize,
            holeDiameter = params.holeD.dp,
            holeCenterYPx = holeScreenY.value.roundToInt(),
            workArea = HudPxRect(
                xPx = 0,
                yPx = areaTop.value.roundToInt(),
                widthPx = 1,
                heightPx = (areaBottom - areaTop).value.roundToInt()
            ),
            balloonOpen = balloon != null,
            balloonSide = balloonSide,
            balloonSize = DpSize(HUD_BALLOON_WIDTH, balloonHeight),
            handleSize = HUD_HANDLE_SIZE,
            gap = HUD_HANDLE_GAP
        )
        // A coluna é composta sempre, no mesmo retângulo: a íris revela e recolhe dentro dele (A11e).
        // Recolhida, não recebe hover: o callback de anel só passa quando expandida.
        HudFloatingColumn(
            accounts = accounts,
            fallbackLabel = fallbackLabel,
            fallbackTone = fallbackTone,
            language = language,
            revealed = expanded,
            onRingHovered = { index -> if (expanded) onAccountClick(index) },
            onRingRefresh = {},
            onHoleHovered = { if (expanded) onHoleHover() },
            onHoleClicked = { if (expanded) onHoleClick() },
            hitBoxes = hitBoxes,
            modifier = Modifier
                .placedAt(parts.columnSlot)
                .testTag(HUD_FLOATING_COLUMN_TEST_TAG)
                .hoverable(hover.column, enabled = expanded)
        )
        // O buraco fica sempre visível, minimizado e expandido.
        HudFloatingHole(
            params = params,
            update = update,
            description = localized(language, "Buraco negro", "Black hole"),
            modifier = Modifier.placedAt(parts.hole)
        )
        HudFloatingButtons(
            rect = parts.buttonsSlot,
            expanded = expanded,
            gearDescription = gearDescription,
            update = update,
            language = language,
            onGearClick = onGearClick,
            onToggleFloating = onToggleFloating,
            hover = hover.handles
        )
        val balloonRect = parts.balloon
        if (balloonRect != null && balloon != null) {
            // Balão alinhado ao anel em foco (ou à sentinela do buraco, na lista); o da engrenagem fica centralizado.
            val ringCenterY = focusedRingCenterY(
                focusedRingIndex(balloon, accounts.size),
                hitBoxes,
                parts.columnSlot,
                density
            ) ?: (maxHeight / 2f)
            val balloonTop = hudFloatingBalloonTop(ringCenterY, balloonHeight, maxHeight)
            // A caixa do HudBalloon é HUD_BALLOON_GAP mais larga que o corpo; no lado esquerdo o corpo fica depois do vão.
            val boxLeft = if (balloonSide == HudEdge.LEFT) balloonRect.left - HUD_BALLOON_GAP else balloonRect.left
            HudBalloon(
                edge = balloonSide,
                tailCenter = { with(density) { (ringCenterY - balloonTop).toPx() } },
                bodyHeight = balloonHeight,
                modifier = Modifier.offset(x = boxLeft, y = balloonTop).hoverable(hover.balloon),
                content = {
                    HudFloatingBalloonContent(
                        balloon = balloon,
                        accounts = accounts,
                        language = language,
                        accountActions = accountActions,
                        balloonHeight = balloonHeight,
                        accountBalloon = accountBalloon,
                        appBalloon = appBalloon,
                        onHoleSelect = onHoleSelect,
                        onHoleBack = onHoleBack
                    )
                }
            )
        }
        HudFloatingDragArea(
            hole = parts.hole,
            hover = hover.hole,
            onDragStart = onDragStart,
            onDrag = onDrag,
            onDragEnd = onDragEnd
        )
    }
}

/** Fontes de hover do modo flutuante, uma por parte visível: buraco, coluna, alças e balão (A11f). */
private class HudFloatingHover {
    val hole = MutableInteractionSource()
    val column = MutableInteractionSource()
    val handles = MutableInteractionSource()
    val balloon = MutableInteractionSource()
    val all: List<MutableInteractionSource> get() = listOf(hole, column, handles, balloon)
}

/**
 * Hover por parte, como o da barra (`rememberHudHover`): [onHoverChange] recebe a união das partes a cada troca.
 * O atraso de recolher do host cobre a travessia entre o buraco e a coluna.
 */
@Composable
private fun rememberHudFloatingHover(onHoverChange: (Boolean) -> Unit): HudFloatingHover {
    val sources = remember { HudFloatingHover() }
    val currentHoverChange by rememberUpdatedState(onHoverChange)
    val states = sources.all.map { source -> source.collectIsHoveredAsState() }
    val hovered = states.any { state -> state.value }
    LaunchedEffect(hovered) {
        currentHoverChange(hovered)
    }
    return sources
}

/**
 * Índice do anel em foco no balão: a conta pelo índice, o buraco pela sentinela `accountCount` (a coluna mede o
 * buraco com esse índice), e `null` para o balão da engrenagem.
 */
private fun focusedRingIndex(balloon: HudFloatingBalloon, accountCount: Int): Int? = when (balloon) {
    is HudFloatingBalloon.Account -> balloon.index
    is HudFloatingBalloon.Hole -> accountCount
    HudFloatingBalloon.App -> null
}

/**
 * Centro Y, em dp de janela, do anel em foco. A caixa do anel é medida no corpo da coluna, então soma-se o topo
 * da coluna. `null` para o balão da engrenagem ou enquanto o anel ainda não foi medido.
 */
private fun focusedRingCenterY(
    ringIndex: Int?,
    hitBoxes: HudRingHitBoxes,
    column: DpRect?,
    density: Density
): Dp? {
    if (ringIndex == null || column == null) return null
    val ring = hitBoxes.rings()[ringIndex] ?: return null
    return with(density) { column.top + ring.center.y.toDp() }
}

/**
 * Alças logo abaixo da coluna: engrenagem em cima, encaixar embaixo. Entram e saem pelo mesmo
 * `handleEnter`/`handleExit` das alças do notch; recolhidas, não compõem e não recebem clique (A11e).
 */
@Composable
private fun HudFloatingButtons(
    rect: DpRect,
    expanded: Boolean,
    gearDescription: String,
    update: HudUpdateIndicator?,
    language: AppLanguage,
    onGearClick: () -> Unit,
    onToggleFloating: () -> Unit,
    hover: MutableInteractionSource
) {
    val gearInteraction = remember { MutableInteractionSource() }
    val floatInteraction = remember { MutableInteractionSource() }
    AnimatedVisibility(
        visible = expanded,
        modifier = Modifier.placedAt(rect).hoverable(hover, enabled = expanded),
        enter = handleEnter(HudEdge.LEFT, true),
        exit = handleExit(HudEdge.LEFT, true)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(HUD_HANDLE_GAP)) {
            // A frase da atualização entra na descrição da engrenagem, como no notch (HudNotch.kt).
            val gearText = update?.let { "$gearDescription · ${it.description}" } ?: gearDescription
            HudGearHandle(
                description = gearText,
                onClick = onGearClick,
                interaction = gearInteraction,
                badgeTone = update?.tone
            )
            HudFloatHandle(
                floating = true,
                language = language,
                onToggle = onToggleFloating,
                interaction = floatInteraction
            )
        }
    }
}

/** Conteúdo do balão aberto: o de conta pelo índice, o da engrenagem, ou a lista das minimizadas no buraco (A11g). */
@Composable
private fun HudFloatingBalloonContent(
    balloon: HudFloatingBalloon,
    accounts: List<HudAccount>,
    language: AppLanguage,
    accountActions: @Composable (HudAccount) -> Unit,
    balloonHeight: Dp,
    accountBalloon: @Composable (Int) -> Unit,
    appBalloon: @Composable () -> Unit,
    onHoleSelect: (Int) -> Unit,
    onHoleBack: () -> Unit
) {
    when (balloon) {
        is HudFloatingBalloon.Account -> accountBalloon(balloon.index)
        HudFloatingBalloon.App -> appBalloon()
        is HudFloatingBalloon.Hole -> HudBlackHoleBalloonContent(
            accounts = accounts,
            members = hudColumnArrangement(accounts).holeMembers,
            selected = balloon.selection,
            language = language,
            accountActions = accountActions,
            bodyHeight = balloonHeight,
            onSelect = onHoleSelect,
            onBack = onHoleBack
        )
    }
}

/**
 * Área de arrasto sobre o buraco. Os callbacks passam por `rememberUpdatedState`, para que o gesto
 * não seja reiniciado quando o host troca a lambda a cada recomposição.
 */
@Composable
private fun HudFloatingDragArea(
    hole: DpRect,
    hover: MutableInteractionSource,
    onDragStart: () -> Unit,
    onDrag: () -> Unit,
    onDragEnd: () -> Unit
) {
    val currentDragStart by rememberUpdatedState(onDragStart)
    val currentDrag by rememberUpdatedState(onDrag)
    val currentDragEnd by rememberUpdatedState(onDragEnd)
    Box(
        modifier = Modifier
            .placedAt(hole)
            .testTag(HUD_FLOATING_HOLE_DRAG_TAG)
            .hoverable(hover)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { currentDragStart() },
                    onDrag = { _, _ -> currentDrag() },
                    onDragEnd = { currentDragEnd() },
                    onDragCancel = { currentDragEnd() }
                )
            }
    )
}

/** Posiciona e dimensiona um filho pelo retângulo calculado por [hudFloatingColumnParts]. */
private fun Modifier.placedAt(rect: DpRect): Modifier =
    offset(x = rect.left, y = rect.top).size(rect.right - rect.left, rect.bottom - rect.top)

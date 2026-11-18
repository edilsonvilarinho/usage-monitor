package com.usagemonitor.presentation.ui

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
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
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.hudFloatingParts

/** Tag do vão de arrasto sobre o buraco, para os testes de tela. */
const val HUD_FLOATING_HOLE_DRAG_TAG = "hud-floating-hole-drag"

/** Balão aberto na janela flutuante: de uma conta (pelo índice) ou da engrenagem. */
internal sealed interface HudFloatingBalloon {
    data class Account(val index: Int) : HudFloatingBalloon
    data object App : HudFloatingBalloon
}

/**
 * Janela flutuante da HUD (A08b). O tamanho é fixo e vem do host; minimizado mostra só o buraco;
 * expandido mostra campo, coluna de botões e o balão do lado [balloonSide]. Arrastar o buraco chama
 * início, fim e passos do arrasto. O conteúdo dos balões vem de fora: o host monta o `HudBalloon`
 * com a cauda apontada para a conta.
 */
@OptIn(ExperimentalComposeUiApi::class)
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
    modifier: Modifier = Modifier
) {
    val fieldDiameter = rememberHudOrbitFieldDiameter(accounts, params, language)
    val hoverModifier = modifier
        .fillMaxSize()
        .onPointerEvent(PointerEventType.Enter) { onHoverChange(true) }
        .onPointerEvent(PointerEventType.Exit) { onHoverChange(false) }
    BoxWithConstraints(modifier = hoverModifier) {
        val density = LocalDensity.current
        val parts = hudFloatingParts(
            windowSize = DpSize(maxWidth, maxHeight),
            expanded = expanded,
            fieldDiameter = fieldDiameter,
            holeDiameter = params.holeD.dp,
            balloonOpen = balloon != null,
            balloonSide = balloonSide,
            balloonWidth = HUD_BALLOON_WIDTH,
            balloonHeight = balloonHeight,
            handleSize = HUD_HANDLE_SIZE,
            gap = HUD_HANDLE_GAP
        )
        // Minimizado: o buraco é o próprio desenho. Expandido: o campo já desenha o seu buraco.
        if (!expanded) {
            HudFloatingHole(
                params = params,
                update = update,
                description = localized(language, "Buraco negro", "Black hole"),
                modifier = Modifier.placedAt(parts.hole)
            )
        }
        val field = parts.field
        if (field != null) {
            HudOrbitField(
                accounts = accounts,
                params = params,
                language = language,
                onAccountClick = onAccountClick,
                modifier = Modifier.placedAt(field)
            )
        }
        val buttons = parts.buttons
        if (buttons != null) {
            HudFloatingButtons(
                buttons = buttons,
                gearDescription = gearDescription,
                update = update,
                language = language,
                onGearClick = onGearClick,
                onToggleFloating = onToggleFloating
            )
        }
        val balloonRect = parts.balloon
        if (balloonRect != null && balloon != null) {
            // A caixa do HudBalloon é HUD_BALLOON_GAP mais larga que o corpo; no lado esquerdo o corpo fica depois do vão.
            val boxLeft = if (balloonSide == HudEdge.LEFT) balloonRect.left - HUD_BALLOON_GAP else balloonRect.left
            val windowCenterY = maxHeight / 2f
            HudBalloon(
                edge = balloonSide,
                tailCenter = { with(density) { (windowCenterY - balloonRect.top).toPx() } },
                bodyHeight = balloonHeight,
                modifier = Modifier.offset(x = boxLeft, y = balloonRect.top),
                content = { HudFloatingBalloonContent(balloon, accountBalloon, appBalloon) }
            )
        }
        HudFloatingDragArea(
            hole = parts.hole,
            onDragStart = onDragStart,
            onDrag = onDrag,
            onDragEnd = onDragEnd
        )
    }
}

/** Coluna à direita do campo: engrenagem em cima, encaixar na borda embaixo. */
@Composable
private fun HudFloatingButtons(
    buttons: DpRect,
    gearDescription: String,
    update: HudUpdateIndicator?,
    language: AppLanguage,
    onGearClick: () -> Unit,
    onToggleFloating: () -> Unit
) {
    val gearInteraction = remember { MutableInteractionSource() }
    val floatInteraction = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier.placedAt(buttons),
        verticalArrangement = Arrangement.spacedBy(HUD_HANDLE_GAP)
    ) {
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

/** Conteúdo do balão aberto: o de conta pelo índice, ou o da engrenagem. */
@Composable
private fun HudFloatingBalloonContent(
    balloon: HudFloatingBalloon,
    accountBalloon: @Composable (Int) -> Unit,
    appBalloon: @Composable () -> Unit
) {
    when (balloon) {
        is HudFloatingBalloon.Account -> accountBalloon(balloon.index)
        HudFloatingBalloon.App -> appBalloon()
    }
}

/**
 * Área de arrasto sobre o buraco. Os callbacks passam por `rememberUpdatedState`, para que o gesto
 * não seja reiniciado quando o host troca a lambda a cada recomposição.
 */
@Composable
private fun HudFloatingDragArea(
    hole: DpRect,
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

/** Posiciona e dimensiona um filho pelo retângulo calculado por [hudFloatingParts]. */
private fun Modifier.placedAt(rect: DpRect): Modifier =
    offset(x = rect.left, y = rect.top).size(rect.right - rect.left, rect.bottom - rect.top)

package com.usagemonitor.presentation.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.usagemonitor.HUD_HANDLE_SIZE
import com.usagemonitor.HudEdge
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.components.appDepth
import com.usagemonitor.presentation.ui.components.color
import com.usagemonitor.presentation.ui.theme.AppDepth
import com.usagemonitor.presentation.ui.theme.AppMotion
import com.usagemonitor.presentation.ui.theme.AppSurfaceLadders
import com.usagemonitor.presentation.ui.theme.appSpring
import com.usagemonitor.presentation.ui.theme.appTween
import java.awt.Cursor
import kotlin.math.atan2

internal const val HUD_MOVE_HANDLE_TAG = "hudMoveHandle"
internal const val HUD_GEAR_HANDLE_TAG = "hudGearHandle"

internal fun hudMoveHandleDescription(language: AppLanguage): String =
    if (language == AppLanguage.PT) "Mover a barra HUD" else "Move the HUD bar"

/**
 * A alça de mover: pressionar e arrastar leva o notch a qualquer borda. É a
 * resposta à pergunta "como eu movo?" — o cursor de mover sobre o notch inteiro
 * existia, e ninguém o descobria. Uma mão e não setas, como no Codenotch: o
 * gesto é carregar, não empurrar.
 *
 * O arrasto é o mesmo [hudPressGesture] do corpo do notch, com o clique vazio:
 * é o host quem lê o ponteiro na tela. Carregando, o disco ganha a borda de
 * informação — está na mão, não oferecido.
 */
@Composable
internal fun HudMoveHandle(
    language: AppLanguage,
    carrying: Boolean,
    onDragStart: () -> Unit,
    onDragMove: () -> Unit,
    onDragEnd: () -> Unit,
    interaction: MutableInteractionSource,
    modifier: Modifier = Modifier
) {
    val description = hudMoveHandleDescription(language)
    HudHandleDisc(
        carrying = carrying,
        interaction = interaction,
        modifier = modifier
            .testTag(HUD_MOVE_HANDLE_TAG)
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.MOVE_CURSOR)))
            .hudPressGesture(
                onDragStart = onDragStart,
                onDragMove = onDragMove,
                onDragEnd = onDragEnd,
                onClick = {}
            )
            .semantics { contentDescription = description }
    ) { tint ->
        Icon(Icons.Rounded.PanTool, contentDescription = null, tint = tint, modifier = Modifier.size(HANDLE_ICON_SIZE))
    }
}

/** A engrenagem na outra ponta: um clique abre o que ela oferece. */
@Composable
internal fun HudGearHandle(
    description: String,
    onClick: () -> Unit,
    interaction: MutableInteractionSource,
    modifier: Modifier = Modifier
) {
    HudHandleDisc(
        carrying = false,
        interaction = interaction,
        modifier = modifier
            .testTag(HUD_GEAR_HANDLE_TAG)
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR)))
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = description,
                onClick = onClick
            )
            .semantics { contentDescription = description }
    ) { tint ->
        Icon(Icons.Rounded.Settings, contentDescription = null, tint = tint, modifier = Modifier.size(HANDLE_ICON_SIZE))
    }
}

/** O disco das duas alças: superfície em `RAISED`, hover que acende o glifo e pressão que encolhe. */
@Composable
private fun HudHandleDisc(
    carrying: Boolean,
    modifier: Modifier,
    interaction: MutableInteractionSource,
    glyph: @Composable (androidx.compose.ui.graphics.Color) -> Unit
) {
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val ladder = AppSurfaceLadders.current
    val scale by animateFloatAsState(
        targetValue = if (pressed) PRESSED_SCALE else 1f,
        animationSpec = appSpring(AppMotion.Springs.SNAPPY),
        label = "hudHandleScale"
    )
    val tint by animateColorAsState(
        targetValue = if (hovered || carrying) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = appTween(AppMotion.fast),
        label = "hudHandleTint"
    )
    val border = if (carrying) AppTone.INFO.color() else ladder.borderTop
    Box(
        modifier = modifier
            .requiredSize(HUD_HANDLE_SIZE)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .appDepth(AppDepth.RAISED, CircleShape)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(if (carrying) 2.dp else 1.dp, border, CircleShape)
            .hoverable(interaction),
        contentAlignment = Alignment.Center
    ) {
        glyph(tint)
    }
}

/**
 * Parado, cada alça é só um arco de um quarto na margem da sombra, rente à borda
 * da tela e concêntrico com o canto do notch — o `h-rest` do Codenotch. Diz que
 * há algo ali sem ocupar área de clique: a janela recolhida já tem essa margem.
 *
 * [atStart] é a ponta de perto (a da mão). O arco sai da borda da tela e vai até
 * o lado de fora do notch.
 */
@Composable
internal fun HudHandleHint(edge: HudEdge, atStart: Boolean, modifier: Modifier = Modifier) {
    // `outline` e não a borda de luz do notch: o arco fica sobre o que estiver
    // atrás da janela, e a luz de 1dp some contra papel de parede escuro.
    val color = MaterialTheme.colorScheme.outline
    Canvas(modifier = modifier.requiredSize(HUD_HANDLE_HINT_SIZE)) {
        val side = size.width
        // O canto do arco: onde o notch encontra a borda da tela, do lado da alça.
        val alongCorner = if (atStart) side else 0f
        val corner = mapAlongAcross(edge, alongCorner, 0f, size)
        val inward = mapAlongAcross(edge, alongCorner, side, size) - corner
        val away = mapAlongAcross(edge, if (atStart) 0f else side, 0f, size) - corner
        val start = angleOf(inward)
        var sweep = angleOf(away) - start
        while (sweep > 180f) sweep -= 360f
        while (sweep < -180f) sweep += 360f
        val radius = side * HINT_RADIUS_FRACTION
        val stroke = HINT_STROKE.toPx()
        drawArc(
            color = color,
            startAngle = start,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Offset(corner.x - radius, corner.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
    }
}

/** (ao longo, a partir da borda da tela) → coordenadas da caixa de cada borda. */
private fun mapAlongAcross(edge: HudEdge, along: Float, across: Float, size: Size): Offset = when (edge) {
    HudEdge.TOP -> Offset(along, across)
    HudEdge.BOTTOM -> Offset(along, size.height - across)
    HudEdge.LEFT -> Offset(across, along)
    HudEdge.RIGHT -> Offset(size.width - across, along)
}

private fun angleOf(vector: Offset): Float = Math.toDegrees(atan2(vector.y, vector.x).toDouble()).toFloat()

/** Cabe na margem de 16dp da sombra, que a janela recolhida já tem em cada ponta. */
internal val HUD_HANDLE_HINT_SIZE = 14.dp
private val HINT_STROKE = 3.dp
private const val HINT_RADIUS_FRACTION = 0.7f
private val HANDLE_ICON_SIZE = 16.dp
private const val PRESSED_SCALE = 0.9f

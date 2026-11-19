package com.usagemonitor.presentation.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import java.awt.Cursor

/** Marca da alça de soltar/encaixar a barra na borda (#619). */
internal const val HUD_FLOAT_HANDLE_TAG = "hudFloatHandle"

/** Rótulo da alça: descreve a ação que o clique vai fazer. */
internal fun hudFloatHandleLabel(floating: Boolean, language: AppLanguage): String =
    if (floating) {
        localized(language, "Encaixar na borda", "Dock to edge")
    } else {
        localized(language, "Soltar da borda", "Release from edge")
    }

/** Qual parte do notch recebe a alça de soltar/encaixar (#619): fora do enum de [HudNotchPart]. */
internal enum class HudFloatPart { HANDLE }

/**
 * Alça de soltar/encaixar a barra na borda (#619). Mesmo disco das outras duas
 * alças; o ícone é uma órbita (planeta cheio com a elipse da trajetória), e o
 * rótulo diz a ação do clique, não o estado.
 */
@Composable
internal fun HudFloatHandle(
    floating: Boolean,
    language: AppLanguage,
    onToggle: () -> Unit,
    interaction: MutableInteractionSource,
    modifier: Modifier = Modifier
) {
    val label = hudFloatHandleLabel(floating, language)
    HudHandleDisc(
        carrying = false,
        interaction = interaction,
        modifier = modifier
            .testTag(HUD_FLOAT_HANDLE_TAG)
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR)))
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = label,
                onClick = onToggle
            )
            .semantics { contentDescription = label }
    ) { tint ->
        Canvas(modifier = Modifier.size(HANDLE_ICON_SIZE)) { drawOrbit(tint) }
    }
}

/** Planeta cheio no centro e a órbita em traço, inclinada. */
private fun DrawScope.drawOrbit(tint: Color) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val stroke = 1.5.dp.toPx()
    rotate(degrees = -30f, pivot = center) {
        drawOval(
            color = tint,
            topLeft = Offset(size.width * 0.04f, size.height * 0.30f),
            size = Size(size.width * 0.92f, size.height * 0.40f),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
    }
    drawCircle(color = tint, radius = size.minDimension * 0.22f, center = center)
}

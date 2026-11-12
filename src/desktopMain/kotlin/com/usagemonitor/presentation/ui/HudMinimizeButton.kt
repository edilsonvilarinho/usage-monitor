package com.usagemonitor.presentation.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.presentation.ui.components.CardIconActionButton

// Ícone do controle no balão: mesmo tamanho dos ícones das ações do card.
private val HUD_MINIMIZE_ICON_SIZE = 16.dp

/** Botão Minimizar/Restaurar do balão da conta na HUD (#576). Devolve a própria chave da conta. */
@Composable
internal fun HudMinimizeButton(
    targetKey: UsageTargetKey,
    minimized: Boolean,
    language: AppLanguage,
    buttonSize: Dp,
    onToggle: (UsageTargetKey) -> Unit
) {
    val icon = if (minimized) Icons.Rounded.Add else Icons.Rounded.Remove
    CardIconActionButton(
        label = hudMinimizeActionLabel(minimized, language),
        onClick = { onToggle(targetKey) },
        buttonSize = buttonSize,
        language = language
    ) { tint ->
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(HUD_MINIMIZE_ICON_SIZE), tint = tint)
    }
}

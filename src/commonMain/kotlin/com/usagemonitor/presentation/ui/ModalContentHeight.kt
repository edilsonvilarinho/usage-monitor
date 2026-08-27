package com.usagemonitor.presentation.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

/**
 * Reporter de layout para os hosts de janela do desktop.
 *
 * O callback recebe a altura em `Dp` do conteúdo abaixo da barra de título. A
 * medição fica na tela, onde existe a informação de layout; o redimensionamento
 * da janela continua isolado em `desktopMain`.
 */
@Composable
internal fun rememberModalContentHeightReporter(
    onRequiredHeightChanged: (Dp) -> Unit
): Modifier {
    val density = LocalDensity.current
    return Modifier.onGloballyPositioned { coordinates ->
        onRequiredHeightChanged(with(density) { coordinates.size.height.toDp() })
    }
}

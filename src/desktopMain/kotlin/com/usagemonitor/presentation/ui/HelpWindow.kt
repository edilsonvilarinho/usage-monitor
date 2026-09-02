package com.usagemonitor.presentation.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.usagemonitor.ApplyWindowMinimumSize
import com.usagemonitor.ScreenWorkArea
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.fitWindowSize
import com.usagemonitor.help.rememberHelpMedia
import com.usagemonitor.presentation.ui.help.HelpCatalog
import com.usagemonitor.presentation.ui.help.HelpContent
import com.usagemonitor.presentation.ui.help.HelpTopic
import com.usagemonitor.presentation.ui.help.helpWindowTitle
import com.usagemonitor.presentation.ui.theme.AppTheme
import com.usagemonitor.presentation.ui.theme.AppThemePreset
import com.usagemonitor.uiScaleFactor

/**
 * Janela de ajuda: as funcionalidades, o que cada uma faz e como ligá-la.
 *
 * Composable própria, e não um bloco dentro do `main()`: aquele composable está
 * no limite do backend JVM. O `main()` ganha uma chamada.
 *
 * O tópico escolhido mora **aqui**, e não em `HelpContent`: é ele que decide
 * qual demo o tocador carrega. É também aqui que o laço de quadros roda — dentro
 * de `rememberHelpMedia`, em `desktopMain` —, o que mantém `HelpContent`
 * exercitável nos testes de componente, onde animação infinita trava o
 * `waitForIdle`.
 */
@Composable
internal fun HelpWindow(
    language: AppLanguage,
    themePreset: AppThemePreset,
    uiScalePercent: Int,
    iconImage: Painter?,
    screenWorkArea: ScreenWorkArea,
    onCloseRequest: () -> Unit
) {
    val title = helpWindowTitle(language)
    var selectedTopic by remember { mutableStateOf(HelpCatalog.readingOrder.first()) }

    val windowState = rememberDialogState(
        size = fitWindowSize(
            DpSize(
                width = DEFAULT_HELP_WINDOW_WIDTH * uiScaleFactor(uiScalePercent),
                height = DEFAULT_HELP_WINDOW_HEIGHT * uiScaleFactor(uiScalePercent)
            ),
            screenWorkArea
        )
    )

    DialogWindow(
        onCloseRequest = onCloseRequest,
        title = title,
        icon = iconImage,
        state = windowState,
        resizable = true,
        undecorated = true
    ) {
        ApplyWindowMinimumSize(
            window = window,
            widthDp = HELP_MIN_WINDOW_WIDTH_DP,
            heightDp = HELP_MIN_WINDOW_HEIGHT_DP,
            uiScalePercent = uiScalePercent,
            workArea = screenWorkArea
        )
        AppTheme(preset = themePreset, uiScalePercent = uiScalePercent) {
            DesktopDialogFrame(
                title = title,
                iconPainter = iconImage,
                onCloseRequest = onCloseRequest
            ) {
                HelpContent(
                    selectedTopic = selectedTopic,
                    onSelectTopic = { topic -> selectedTopic = topic },
                    language = language,
                    onClose = onCloseRequest,
                    media = rememberHelpMedia(HelpCatalog.mediaId(selectedTopic)),
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/**
 * Tamanho default da janela.
 *
 * Largo porque a demo é gravada em 1000dp — a largura de uma janela real deste
 * app — e reduzi-la tornaria ilegível o rótulo que ela existe para apontar. A
 * altura cabe a faixa da demo, a descrição e a seção "Como ativar" sem rolagem
 * no primeiro tópico. `fitWindowSize` encolhe os dois numa tela menor.
 */
private val DEFAULT_HELP_WINDOW_WIDTH = 1_180.dp
private val DEFAULT_HELP_WINDOW_HEIGHT = 780.dp

/**
 * Piso de arrasto.
 *
 * Bem abaixo do default de propósito: aqui nenhuma faixa de legendas promete
 * alinhamento de coluna — o que encolhe é a demo, que tem `ContentScale.Fit` e
 * degrada para uma imagem menor em vez de quebrar o layout. O piso existe para o
 * trilho de 200dp e o texto ao lado dele continuarem cabendo.
 */
private const val HELP_MIN_WINDOW_WIDTH_DP = 600

private const val HELP_MIN_WINDOW_HEIGHT_DP = 420

package com.usagemonitor.presentation.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.theme.AppTheme
import com.usagemonitor.ScreenWorkArea
import com.usagemonitor.fitWindowSize
import com.usagemonitor.uiScaleFactor
import com.usagemonitor.update.ReleaseNotesController

/**
 * Janela das novidades da versão.
 *
 * Composable próprio, e não mais um bloco dentro do `main()`: aquele composable
 * está no limite do backend JVM. O `main()` ganha uma chamada.
 *
 * Sem notas não há janela — e não uma janela vazia: lista vazia numa tela de
 * novidades afirma que a versão não trouxe nada.
 */
@Composable
internal fun ReleaseNotesWindow(
    controller: ReleaseNotesController,
    language: AppLanguage,
    isDark: Boolean,
    uiScalePercent: Int,
    iconImage: Painter?,
    screenWorkArea: ScreenWorkArea,
    onOpenReleasePage: (String) -> Unit
) {
    val notes = controller.notes ?: return
    val title = releaseNotesTitle(notes.version, language == AppLanguage.PT)

    DialogWindow(
        onCloseRequest = { controller.onDismiss() },
        title = title,
        icon = iconImage,
        // Mesmo tratamento das outras janelas: o literal acompanha a escala,
        // porque a 150% o conteúdo cresce e a moldura fixa o espremeria, e é
        // preso à área útil porque o diálogo é `undecorated`.
        state = rememberDialogState(
            size = fitWindowSize(
                DpSize(
                    width = 560.dp * uiScaleFactor(uiScalePercent),
                    height = 520.dp * uiScaleFactor(uiScalePercent)
                ),
                screenWorkArea
            )
        ),
        undecorated = true
    ) {
        AppTheme(isDark = isDark, uiScalePercent = uiScalePercent) {
            DesktopDialogFrame(
                title = title,
                iconPainter = iconImage,
                onCloseRequest = { controller.onDismiss() }
            ) {
                ReleaseNotesContent(
                    notes = notes,
                    language = language,
                    onOpenReleasePage = { onOpenReleasePage(notes.releasePageUrl) },
                    onClose = { controller.onDismiss() },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

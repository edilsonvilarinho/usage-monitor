package com.usagemonitor.presentation.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.rememberDialogState
import com.usagemonitor.DEFAULT_MODAL_MIN_HEIGHT
import com.usagemonitor.domain.entity.AppLanguage
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
 * novidades afirma que a versão não trouxe nada. Depois de dispensadas, as
 * notas lidas continuam na janela escondida pelo tempo da saída: o controlador
 * zera as notas no clique, e sem elas a janela esmaeceria em branco.
 */
@Composable
internal fun ReleaseNotesWindow(
    controller: ReleaseNotesController,
    language: AppLanguage,
    environment: ModalWindowEnvironment,
    onOpenReleasePage: (String) -> Unit
) {
    val notes = rememberLastNonNull(controller.notes) ?: return
    val title = releaseNotesTitle(notes.version, language == AppLanguage.PT)
    // Mesmo tratamento das outras janelas: o literal acompanha a escala, porque
    // a 150% o conteúdo cresce e a moldura fixa o espremeria, e é preso à área
    // útil porque o diálogo é `undecorated`.
    val windowState = rememberDialogState(
        size = fitWindowSize(
            DpSize(
                width = 560.dp * uiScaleFactor(environment.uiScalePercent),
                height = 520.dp * uiScaleFactor(environment.uiScalePercent)
            ),
            environment.screenWorkArea
        )
    )
    AppDialogWindow(
        visible = controller.notes != null,
        title = title,
        state = windowState,
        environment = environment,
        diagnosticName = "novidades da versão",
        minWidthDp = 320,
        minHeightDp = DEFAULT_MODAL_MIN_HEIGHT.value.toInt(),
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

package com.usagemonitor.presentation.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.rememberDialogState
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.fitWindowSize
import com.usagemonitor.help.rememberHelpMedia
import com.usagemonitor.presentation.ui.help.HelpCatalog
import com.usagemonitor.presentation.ui.help.HelpMediaState
import com.usagemonitor.presentation.ui.help.HelpTopic
import com.usagemonitor.presentation.ui.help.StartupTipCatalog
import com.usagemonitor.presentation.ui.help.StartupTipContent
import com.usagemonitor.presentation.ui.help.startupTipWindowTitle
import com.usagemonitor.uiScaleFactor

/**
 * Janela da dica do dia (#411).
 *
 * Sem dica não há janela. Depois de fechada, a última dica continua na janela
 * escondida pelo tempo da saída — o mesmo cuidado das Novidades: sem ela, a
 * janela esmaeceria em branco.
 */
@Composable
internal fun StartupTipWindow(
    tipIndex: Int?,
    tipsEnabled: Boolean,
    language: AppLanguage,
    environment: ModalWindowEnvironment,
    onTipsEnabledChange: (Boolean) -> Unit,
    onNext: () -> Unit,
    onOpenHelp: (HelpTopic) -> Unit,
    onCloseRequest: () -> Unit
) {
    val shownIndex = rememberLastNonNull(tipIndex) ?: return
    val tips = StartupTipCatalog.tips
    if (tips.isEmpty()) {
        return
    }
    val tip = tips[shownIndex.coerceIn(0, tips.lastIndex)]

    val windowState = rememberDialogState(
        size = fitWindowSize(
            DpSize(
                width = DEFAULT_TIP_WINDOW_WIDTH * uiScaleFactor(environment.uiScalePercent),
                height = DEFAULT_TIP_WINDOW_HEIGHT * uiScaleFactor(environment.uiScalePercent)
            ),
            environment.screenWorkArea
        )
    )

    AppDialogWindow(
        visible = tipIndex != null,
        title = startupTipWindowTitle(language),
        state = windowState,
        environment = environment,
        diagnosticName = "dica do dia",
        minWidthDp = TIP_MIN_WINDOW_WIDTH_DP,
        minHeightDp = TIP_MIN_WINDOW_HEIGHT_DP,
        onCloseRequest = onCloseRequest
    ) {
        val media = if (LocalModalWindowOnScreen.current) {
            rememberHelpMedia(HelpCatalog.mediaId(tip.topic))
        } else {
            HelpMediaState.Unavailable
        }
        StartupTipContent(
            tipIndex = shownIndex,
            language = language,
            tipsEnabled = tipsEnabled,
            onTipsEnabledChange = onTipsEnabledChange,
            onNext = onNext,
            onOpenHelp = onOpenHelp,
            onClose = onCloseRequest,
            media = media,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/** A demo cabe reduzida (ilustra); o texto e o interruptor ficam sem rolagem. */
private val DEFAULT_TIP_WINDOW_WIDTH = 680.dp
private val DEFAULT_TIP_WINDOW_HEIGHT = 560.dp

private const val TIP_MIN_WINDOW_WIDTH_DP = 480
private const val TIP_MIN_WINDOW_HEIGHT_DP = 420

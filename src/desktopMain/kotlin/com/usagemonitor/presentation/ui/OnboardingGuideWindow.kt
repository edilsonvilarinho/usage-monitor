package com.usagemonitor.presentation.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.rememberDialogState
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.fitWindowSize
import com.usagemonitor.help.rememberHelpMedia
import com.usagemonitor.presentation.ui.help.HelpCatalog
import com.usagemonitor.presentation.ui.help.HelpMediaState
import com.usagemonitor.presentation.ui.help.OnboardingGuideCatalog
import com.usagemonitor.presentation.ui.help.OnboardingGuideContent
import com.usagemonitor.presentation.ui.help.onboardingGuideWindowTitle
import com.usagemonitor.uiScaleFactor

/**
 * Janela do guia de introdução (#411, direção AC4).
 *
 * O cartão escolhido mora aqui, e não no conteúdo, pelo mesmo motivo da
 * [HelpWindow]: é ele que decide qual demo o tocador carrega, e o laço de
 * quadros só roda com a janela na tela ([LocalModalWindowOnScreen]). Cada
 * abertura começa do primeiro cartão — "Rever guia" é rever do começo.
 *
 * Sem pré-aquecimento: abre uma vez na vida do app, e a janela nasce na
 * primeira abertura nos três sistemas.
 */
@Composable
internal fun OnboardingGuideWindow(
    visible: Boolean,
    openGeneration: Int,
    language: AppLanguage,
    environment: ModalWindowEnvironment,
    onCloseRequest: () -> Unit
) {
    var slideIndex by remember(openGeneration) { mutableStateOf(0) }
    val slides = OnboardingGuideCatalog.slides

    val windowState = rememberDialogState(
        size = fitWindowSize(
            DpSize(
                width = DEFAULT_GUIDE_WINDOW_WIDTH * uiScaleFactor(environment.uiScalePercent),
                height = DEFAULT_GUIDE_WINDOW_HEIGHT * uiScaleFactor(environment.uiScalePercent)
            ),
            environment.screenWorkArea
        )
    )

    AppDialogWindow(
        visible = visible,
        title = onboardingGuideWindowTitle(language),
        state = windowState,
        environment = environment,
        diagnosticName = "guia de introdução",
        minWidthDp = GUIDE_MIN_WINDOW_WIDTH_DP,
        minHeightDp = GUIDE_MIN_WINDOW_HEIGHT_DP,
        onCloseRequest = onCloseRequest,
        openGeneration = openGeneration
    ) {
        val media = if (LocalModalWindowOnScreen.current) {
            rememberHelpMedia(HelpCatalog.mediaId(slides[slideIndex].topic))
        } else {
            HelpMediaState.Unavailable
        }
        OnboardingGuideContent(
            slideIndex = slideIndex,
            language = language,
            onBack = { slideIndex = (slideIndex - 1).coerceAtLeast(0) },
            onNext = { slideIndex = (slideIndex + 1).coerceAtMost(slides.lastIndex) },
            onFinish = onCloseRequest,
            media = media,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Largo pela mesma razão da Ajuda: a demo é gravada em 1000dp, e reduzi-la
 * tornaria ilegível o rótulo que ela aponta. A altura cabe a demo inteira, o
 * texto do cartão e a fila de botões; `fitWindowSize` encolhe numa tela menor.
 */
private val DEFAULT_GUIDE_WINDOW_WIDTH = 1_080.dp
private val DEFAULT_GUIDE_WINDOW_HEIGHT = 700.dp

/** Piso de arrasto: a demo encolhe com `Fit`; texto e botões continuam cabendo. */
private const val GUIDE_MIN_WINDOW_WIDTH_DP = 600
private const val GUIDE_MIN_WINDOW_HEIGHT_DP = 460

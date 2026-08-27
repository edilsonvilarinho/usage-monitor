package com.usagemonitor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.DialogState
import androidx.compose.ui.window.WindowState
import kotlinx.coroutines.delay

/** Piso comum para impedir que qualquer modal fique sem área útil recuperável. */
internal val DEFAULT_MODAL_MIN_HEIGHT = 320.dp

/** A barra própria ocupa esta altura acima do conteúdo de todas as janelas. */
internal val DESKTOP_WINDOW_TITLE_BAR_HEIGHT = 34.dp

/** Aguarda a conclusão de `animateContentSize` antes de mover a moldura. */
private const val MODAL_RESIZE_DEBOUNCE_MILLIS = 300L

internal fun targetModalWindowSize(
    currentSize: DpSize,
    requiredContentHeight: Dp,
    workArea: ScreenWorkArea,
    minimumWindowHeight: Dp = DEFAULT_MODAL_MIN_HEIGHT,
    titleBarHeight: Dp = DESKTOP_WINDOW_TITLE_BAR_HEIGHT
): DpSize {
    val requiredHeight = if (requiredContentHeight.value.isFinite()) {
        requiredContentHeight + titleBarHeight
    } else {
        currentSize.height
    }
    val minimumHeight = maxOf(minimumWindowHeight, titleBarHeight)
    val maximumHeight = workArea.size.height.let { availableHeight ->
        if (availableHeight.value.isFinite()) {
            maxOf(availableHeight - WINDOW_SCREEN_MARGIN, minimumHeight)
        } else {
            Dp.Unspecified
        }
    }
    val boundedHeight = if (maximumHeight.value.isFinite()) {
        requiredHeight.coerceIn(minimumHeight, maximumHeight)
    } else {
        maxOf(requiredHeight, minimumHeight)
    }

    return DpSize(
        width = currentSize.width,
        height = boundedHeight
    )
}

internal fun shouldApplyModalAutoSize(placement: WindowPlacement): Boolean {
    return placement == WindowPlacement.Floating
}

/**
 * Controla uma janela individual. Cada chamada mantém seu próprio estado para
 * impedir que a altura de um modal contamine outro modal aberto.
 */
@Composable
internal fun rememberModalWindowAutoSizer(
    windowState: WindowState,
    workArea: ScreenWorkArea,
    minimumWindowHeight: Dp = DEFAULT_MODAL_MIN_HEIGHT
): (Dp) -> Unit {
    var requiredContentHeight by remember { mutableStateOf<Dp?>(null) }

    LaunchedEffect(requiredContentHeight, windowState.placement, workArea, minimumWindowHeight) {
        val requiredHeight = requiredContentHeight ?: return@LaunchedEffect
        delay(MODAL_RESIZE_DEBOUNCE_MILLIS)

        if (!shouldApplyModalAutoSize(windowState.placement)) {
            return@LaunchedEffect
        }

        val targetSize = targetModalWindowSize(
            currentSize = windowState.size,
            requiredContentHeight = requiredHeight,
            workArea = workArea,
            minimumWindowHeight = minimumWindowHeight
        )
        if (targetSize != windowState.size) {
            windowState.size = targetSize
        }
    }

    return { height ->
        if (height.value.isFinite() && height.value >= 0f) {
            requiredContentHeight = height
        }
    }
}

/**
 * Variante para `DialogWindow`: a API de diálogos não expõe `WindowPlacement`,
 * portanto o estado só é redimensionado enquanto o conteúdo reportado mudar.
 */
@Composable
internal fun rememberModalWindowAutoSizer(
    windowState: DialogState,
    workArea: ScreenWorkArea,
    minimumWindowHeight: Dp = DEFAULT_MODAL_MIN_HEIGHT
): (Dp) -> Unit {
    var requiredContentHeight by remember { mutableStateOf<Dp?>(null) }

    LaunchedEffect(requiredContentHeight, workArea, minimumWindowHeight) {
        val requiredHeight = requiredContentHeight ?: return@LaunchedEffect
        delay(MODAL_RESIZE_DEBOUNCE_MILLIS)

        val targetSize = targetModalWindowSize(
            currentSize = windowState.size,
            requiredContentHeight = requiredHeight,
            workArea = workArea,
            minimumWindowHeight = minimumWindowHeight
        )
        if (targetSize != windowState.size) {
            windowState.size = targetSize
        }
    }

    return { height ->
        if (height.value.isFinite() && height.value >= 0f) {
            requiredContentHeight = height
        }
    }
}

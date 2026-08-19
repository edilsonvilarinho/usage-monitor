package com.usagemonitor

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.rememberWindowState
import com.russhwolf.settings.PreferencesSettings
import kotlin.math.roundToInt

private const val HISTORY_WINDOW_WIDTH_KEY = "historyWindowWidth"
private const val HISTORY_WINDOW_HEIGHT_KEY = "historyWindowHeight"
private const val HISTORY_WINDOW_X_KEY = "historyWindowX"
private const val HISTORY_WINDOW_Y_KEY = "historyWindowY"
private const val HISTORY_WINDOW_PLACEMENT_KEY = "historyWindowPlacement"
private const val DEFAULT_HISTORY_WINDOW_WIDTH_DP = 860
private const val DEFAULT_HISTORY_WINDOW_HEIGHT_DP = 760

internal data class PersistedHistoryWindowState(
    val widthDp: Int? = null,
    val heightDp: Int? = null,
    val xDp: Int? = null,
    val yDp: Int? = null,
    val placement: PersistedWindowPlacement = PersistedWindowPlacement.FLOATING
) {
    val composePlacement: WindowPlacement
        get() = when (placement) {
            PersistedWindowPlacement.MAXIMIZED -> WindowPlacement.Maximized
            PersistedWindowPlacement.FLOATING -> WindowPlacement.Floating
        }
}

internal data class HistoryWindowSnapshot(
    val widthDp: Float,
    val heightDp: Float,
    val xDp: Float?,
    val yDp: Float?,
    val placement: WindowPlacement
)

internal fun readPersistedHistoryWindowState(settings: PreferencesSettings): PersistedHistoryWindowState {
    val widthDp = settings.getStringOrNull(HISTORY_WINDOW_WIDTH_KEY)
        ?.toIntOrNull()
        ?.takeIf { value -> value > 0 }
    val heightDp = settings.getStringOrNull(HISTORY_WINDOW_HEIGHT_KEY)
        ?.toIntOrNull()
        ?.takeIf { value -> value > 0 }
    val xDp = settings.getStringOrNull(HISTORY_WINDOW_X_KEY)
        ?.toIntOrNull()
    val yDp = settings.getStringOrNull(HISTORY_WINDOW_Y_KEY)
        ?.toIntOrNull()
    val placement = when (settings.getStringOrNull(HISTORY_WINDOW_PLACEMENT_KEY)) {
        "MAXIMIZED" -> PersistedWindowPlacement.MAXIMIZED
        else -> PersistedWindowPlacement.FLOATING
    }

    return PersistedHistoryWindowState(
        widthDp = widthDp,
        heightDp = heightDp,
        xDp = xDp,
        yDp = yDp,
        placement = placement
    )
}

internal fun persistHistoryWindowState(
    settings: PreferencesSettings,
    snapshot: HistoryWindowSnapshot
) {
    val placement = snapshot.toPersistedPlacement()
    settings.putString(HISTORY_WINDOW_PLACEMENT_KEY, placement.historyStorageValue)

    if (placement != PersistedWindowPlacement.FLOATING) {
        return
    }

    val widthDp = snapshot.widthDp.toPersistableDp()
    val heightDp = snapshot.heightDp.toPersistableDp()
    val xDp = snapshot.xDp?.toPersistableDp()
    val yDp = snapshot.yDp?.toPersistableDp()

    if (widthDp != null) {
        settings.putString(HISTORY_WINDOW_WIDTH_KEY, widthDp.toString())
    }
    if (heightDp != null) {
        settings.putString(HISTORY_WINDOW_HEIGHT_KEY, heightDp.toString())
    }
    if (xDp != null) {
        settings.putString(HISTORY_WINDOW_X_KEY, xDp.toString())
    }
    if (yDp != null) {
        settings.putString(HISTORY_WINDOW_Y_KEY, yDp.toString())
    }
}

@Composable
internal fun rememberPersistedHistoryWindowState(
    persistedState: PersistedHistoryWindowState,
    uiScalePercent: Int,
    workArea: ScreenWorkArea
): WindowState {
    // Só o tamanho default acompanha a escala: tamanho persistido é escolha do
    // usuário. O ajuste à tela, esse, vale para os dois — janela salva num monitor
    // maior volta com a barra de título fora da tela no menor, e a barra é a única
    // que esta janela `undecorated` tem (issue #72).
    val scale = uiScaleFactor(uiScalePercent)
    val initialSize = fitWindowSize(
        DpSize(
            width = persistedState.widthDp?.dp ?: (DEFAULT_HISTORY_WINDOW_WIDTH_DP.dp * scale),
            height = persistedState.heightDp?.dp ?: (DEFAULT_HISTORY_WINDOW_HEIGHT_DP.dp * scale)
        ),
        workArea
    )
    val initialPosition = if (persistedState.xDp != null && persistedState.yDp != null) {
        fitWindowPosition(
            x = persistedState.xDp.dp,
            y = persistedState.yDp.dp,
            size = initialSize,
            workArea = workArea
        )
    } else {
        WindowPosition(Alignment.Center)
    }

    return rememberWindowState(
        placement = persistedState.composePlacement,
        position = initialPosition,
        size = initialSize
    )
}

private fun HistoryWindowSnapshot.toPersistedPlacement(): PersistedWindowPlacement {
    return if (placement == WindowPlacement.Maximized) {
        PersistedWindowPlacement.MAXIMIZED
    } else {
        PersistedWindowPlacement.FLOATING
    }
}

private val PersistedWindowPlacement.historyStorageValue: String
    get() = when (this) {
        PersistedWindowPlacement.FLOATING -> "FLOATING"
        PersistedWindowPlacement.MAXIMIZED -> "MAXIMIZED"
    }

private fun Float.toPersistableDp(): Int? {
    if (!isFinite()) {
        return null
    }

    return roundToInt()
}

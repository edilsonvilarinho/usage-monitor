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

private const val CLI_SESSIONS_WINDOW_WIDTH_KEY = "cliSessionsWindowWidth"
private const val CLI_SESSIONS_WINDOW_HEIGHT_KEY = "cliSessionsWindowHeight"
private const val CLI_SESSIONS_WINDOW_X_KEY = "cliSessionsWindowX"
private const val CLI_SESSIONS_WINDOW_Y_KEY = "cliSessionsWindowY"
private const val CLI_SESSIONS_WINDOW_PLACEMENT_KEY = "cliSessionsWindowPlacement"
private const val DEFAULT_CLI_SESSIONS_WINDOW_WIDTH_DP = 960
private const val DEFAULT_CLI_SESSIONS_WINDOW_HEIGHT_DP = 780

/**
 * Piso da janela, aplicado em `Main.kt` via `minimumSize` da janela AWT.
 *
 * A lista de sessões passou a ter uma **faixa de legendas de coluna**, e uma faixa
 * dessas só cumpre a promessa se a linha não quebrar: as seis colunas fixas mais o
 * padding, a barra de rolagem e a coluna de ação cabem em 960dp e não em menos. O
 * tamanho default já era esse; o que faltava era impedir o arrasto da borda para
 * baixo do orçamento, que é a mesma porta que a tela de presença fechou.
 *
 * A conta está em `CliSessionsScreen.SESSION_COLUMN_*`.
 */
internal const val CLI_SESSIONS_MIN_WINDOW_WIDTH_DP = 960
internal const val CLI_SESSIONS_MIN_WINDOW_HEIGHT_DP = 460

internal data class PersistedCliSessionsWindowState(
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

internal data class CliSessionsWindowSnapshot(
    val widthDp: Float,
    val heightDp: Float,
    val xDp: Float?,
    val yDp: Float?,
    val placement: WindowPlacement
)

internal fun readPersistedCliSessionsWindowState(
    settings: PreferencesSettings
): PersistedCliSessionsWindowState {
    val widthDp = settings.getStringOrNull(CLI_SESSIONS_WINDOW_WIDTH_KEY)
        ?.toIntOrNull()
        ?.takeIf { value -> value > 0 }
    val heightDp = settings.getStringOrNull(CLI_SESSIONS_WINDOW_HEIGHT_KEY)
        ?.toIntOrNull()
        ?.takeIf { value -> value > 0 }
    val xDp = settings.getStringOrNull(CLI_SESSIONS_WINDOW_X_KEY)?.toIntOrNull()
    val yDp = settings.getStringOrNull(CLI_SESSIONS_WINDOW_Y_KEY)?.toIntOrNull()
    val placement = when (settings.getStringOrNull(CLI_SESSIONS_WINDOW_PLACEMENT_KEY)) {
        "MAXIMIZED" -> PersistedWindowPlacement.MAXIMIZED
        else -> PersistedWindowPlacement.FLOATING
    }

    return PersistedCliSessionsWindowState(
        widthDp = widthDp,
        heightDp = heightDp,
        xDp = xDp,
        yDp = yDp,
        placement = placement
    )
}

internal fun persistCliSessionsWindowState(
    settings: PreferencesSettings,
    snapshot: CliSessionsWindowSnapshot
) {
    val placement = if (snapshot.placement == WindowPlacement.Maximized) {
        PersistedWindowPlacement.MAXIMIZED
    } else {
        PersistedWindowPlacement.FLOATING
    }
    settings.putString(CLI_SESSIONS_WINDOW_PLACEMENT_KEY, placement.name)

    if (placement != PersistedWindowPlacement.FLOATING) {
        return
    }

    val widthDp = snapshot.widthDp.toPersistableWindowDp()
    val heightDp = snapshot.heightDp.toPersistableWindowDp()
    val xDp = snapshot.xDp?.toPersistableWindowDp()
    val yDp = snapshot.yDp?.toPersistableWindowDp()

    if (widthDp != null) {
        settings.putString(CLI_SESSIONS_WINDOW_WIDTH_KEY, widthDp.toString())
    }
    if (heightDp != null) {
        settings.putString(CLI_SESSIONS_WINDOW_HEIGHT_KEY, heightDp.toString())
    }
    if (xDp != null) {
        settings.putString(CLI_SESSIONS_WINDOW_X_KEY, xDp.toString())
    }
    if (yDp != null) {
        settings.putString(CLI_SESSIONS_WINDOW_Y_KEY, yDp.toString())
    }
}

@Composable
internal fun rememberPersistedCliSessionsWindowState(
    persistedState: PersistedCliSessionsWindowState,
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
            width = persistedState.widthDp?.dp ?: (DEFAULT_CLI_SESSIONS_WINDOW_WIDTH_DP.dp * scale),
            height = persistedState.heightDp?.dp ?: (DEFAULT_CLI_SESSIONS_WINDOW_HEIGHT_DP.dp * scale)
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

private fun Float.toPersistableWindowDp(): Int? {
    if (!isFinite()) {
        return null
    }
    return roundToInt()
}

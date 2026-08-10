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
    persistedState: PersistedCliSessionsWindowState
): WindowState {
    val initialPosition = if (persistedState.xDp != null && persistedState.yDp != null) {
        WindowPosition(persistedState.xDp.dp, persistedState.yDp.dp)
    } else {
        WindowPosition(Alignment.Center)
    }
    val initialSize = DpSize(
        width = (persistedState.widthDp ?: DEFAULT_CLI_SESSIONS_WINDOW_WIDTH_DP).dp,
        height = (persistedState.heightDp ?: DEFAULT_CLI_SESSIONS_WINDOW_HEIGHT_DP).dp
    )

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

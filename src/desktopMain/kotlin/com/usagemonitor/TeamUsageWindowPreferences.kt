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

private const val TEAM_USAGE_WINDOW_WIDTH_KEY = "teamUsageWindowWidth"
private const val TEAM_USAGE_WINDOW_HEIGHT_KEY = "teamUsageWindowHeight"
private const val TEAM_USAGE_WINDOW_X_KEY = "teamUsageWindowX"
private const val TEAM_USAGE_WINDOW_Y_KEY = "teamUsageWindowY"
private const val TEAM_USAGE_WINDOW_PLACEMENT_KEY = "teamUsageWindowPlacement"
private const val DEFAULT_TEAM_USAGE_WINDOW_WIDTH_DP = 960
private const val DEFAULT_TEAM_USAGE_WINDOW_HEIGHT_DP = 780

/**
 * Geometria da janela de Sessões do time.
 *
 * Chaves próprias, e não compartilhadas com a janela de sessões da máquina: as
 * duas costumam ficar abertas lado a lado e herdar posição uma da outra faria
 * uma nascer exatamente em cima da outra.
 */
internal data class PersistedTeamUsageWindowState(
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

internal data class TeamUsageWindowSnapshot(
    val widthDp: Float,
    val heightDp: Float,
    val xDp: Float?,
    val yDp: Float?,
    val placement: WindowPlacement
)

internal fun readPersistedTeamUsageWindowState(
    settings: PreferencesSettings
): PersistedTeamUsageWindowState {
    val widthDp = settings.getStringOrNull(TEAM_USAGE_WINDOW_WIDTH_KEY)
        ?.toIntOrNull()
        ?.takeIf { value -> value > 0 }
    val heightDp = settings.getStringOrNull(TEAM_USAGE_WINDOW_HEIGHT_KEY)
        ?.toIntOrNull()
        ?.takeIf { value -> value > 0 }
    val xDp = settings.getStringOrNull(TEAM_USAGE_WINDOW_X_KEY)?.toIntOrNull()
    val yDp = settings.getStringOrNull(TEAM_USAGE_WINDOW_Y_KEY)?.toIntOrNull()
    val placement = when (settings.getStringOrNull(TEAM_USAGE_WINDOW_PLACEMENT_KEY)) {
        "MAXIMIZED" -> PersistedWindowPlacement.MAXIMIZED
        else -> PersistedWindowPlacement.FLOATING
    }

    return PersistedTeamUsageWindowState(
        widthDp = widthDp,
        heightDp = heightDp,
        xDp = xDp,
        yDp = yDp,
        placement = placement
    )
}

internal fun persistTeamUsageWindowState(
    settings: PreferencesSettings,
    snapshot: TeamUsageWindowSnapshot
) {
    val placement = if (snapshot.placement == WindowPlacement.Maximized) {
        PersistedWindowPlacement.MAXIMIZED
    } else {
        PersistedWindowPlacement.FLOATING
    }
    settings.putString(TEAM_USAGE_WINDOW_PLACEMENT_KEY, placement.name)

    if (placement != PersistedWindowPlacement.FLOATING) {
        return
    }

    val widthDp = snapshot.widthDp.toPersistableTeamWindowDp()
    val heightDp = snapshot.heightDp.toPersistableTeamWindowDp()
    val xDp = snapshot.xDp?.toPersistableTeamWindowDp()
    val yDp = snapshot.yDp?.toPersistableTeamWindowDp()

    if (widthDp != null) {
        settings.putString(TEAM_USAGE_WINDOW_WIDTH_KEY, widthDp.toString())
    }
    if (heightDp != null) {
        settings.putString(TEAM_USAGE_WINDOW_HEIGHT_KEY, heightDp.toString())
    }
    if (xDp != null) {
        settings.putString(TEAM_USAGE_WINDOW_X_KEY, xDp.toString())
    }
    if (yDp != null) {
        settings.putString(TEAM_USAGE_WINDOW_Y_KEY, yDp.toString())
    }
}

@Composable
internal fun rememberPersistedTeamUsageWindowState(
    persistedState: PersistedTeamUsageWindowState
): WindowState {
    val initialPosition = if (persistedState.xDp != null && persistedState.yDp != null) {
        WindowPosition(persistedState.xDp.dp, persistedState.yDp.dp)
    } else {
        WindowPosition(Alignment.Center)
    }
    val initialSize = DpSize(
        width = (persistedState.widthDp ?: DEFAULT_TEAM_USAGE_WINDOW_WIDTH_DP).dp,
        height = (persistedState.heightDp ?: DEFAULT_TEAM_USAGE_WINDOW_HEIGHT_DP).dp
    )

    return rememberWindowState(
        placement = persistedState.composePlacement,
        position = initialPosition,
        size = initialSize
    )
}

private fun Float.toPersistableTeamWindowDp(): Int? {
    if (!isFinite()) {
        return null
    }
    return roundToInt()
}

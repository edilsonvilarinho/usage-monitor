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

private const val TEAM_PRESENCE_WINDOW_WIDTH_KEY = "teamPresenceWindowWidth"
private const val TEAM_PRESENCE_WINDOW_HEIGHT_KEY = "teamPresenceWindowHeight"
private const val TEAM_PRESENCE_WINDOW_X_KEY = "teamPresenceWindowX"
private const val TEAM_PRESENCE_WINDOW_Y_KEY = "teamPresenceWindowY"
private const val TEAM_PRESENCE_WINDOW_PLACEMENT_KEY = "teamPresenceWindowPlacement"

// Igual à janela de consumo, e não menor como antes: a lista de presença tem
// cinco colunas fixas mais o botão de remover, e em 860dp o pior caso passava da
// largura útil — o `FlowRow` quebrava e as colunas deixavam de alinhar entre as
// linhas, que é justamente o que as larguras fixas existem para impedir. A conta
// está em `TeamPresenceScreen.PRESENCE_COLUMN_*`.
private const val DEFAULT_TEAM_PRESENCE_WINDOW_WIDTH_DP = 960
private const val DEFAULT_TEAM_PRESENCE_WINDOW_HEIGHT_DP = 620

/**
 * Geometria da janela de presença.
 *
 * Chaves próprias pelo mesmo motivo das outras janelas: as três costumam ficar
 * abertas ao mesmo tempo e herdar posição faria uma nascer em cima da outra.
 */
internal data class PersistedTeamPresenceWindowState(
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

internal data class TeamPresenceWindowSnapshot(
    val widthDp: Float,
    val heightDp: Float,
    val xDp: Float?,
    val yDp: Float?,
    val placement: WindowPlacement
)

internal fun readPersistedTeamPresenceWindowState(
    settings: PreferencesSettings
): PersistedTeamPresenceWindowState {
    val widthDp = settings.getStringOrNull(TEAM_PRESENCE_WINDOW_WIDTH_KEY)
        ?.toIntOrNull()
        ?.takeIf { value -> value > 0 }
    val heightDp = settings.getStringOrNull(TEAM_PRESENCE_WINDOW_HEIGHT_KEY)
        ?.toIntOrNull()
        ?.takeIf { value -> value > 0 }
    val xDp = settings.getStringOrNull(TEAM_PRESENCE_WINDOW_X_KEY)?.toIntOrNull()
    val yDp = settings.getStringOrNull(TEAM_PRESENCE_WINDOW_Y_KEY)?.toIntOrNull()
    val placement = when (settings.getStringOrNull(TEAM_PRESENCE_WINDOW_PLACEMENT_KEY)) {
        "MAXIMIZED" -> PersistedWindowPlacement.MAXIMIZED
        else -> PersistedWindowPlacement.FLOATING
    }

    return PersistedTeamPresenceWindowState(
        widthDp = widthDp,
        heightDp = heightDp,
        xDp = xDp,
        yDp = yDp,
        placement = placement
    )
}

internal fun persistTeamPresenceWindowState(
    settings: PreferencesSettings,
    snapshot: TeamPresenceWindowSnapshot
) {
    val placement = if (snapshot.placement == WindowPlacement.Maximized) {
        PersistedWindowPlacement.MAXIMIZED
    } else {
        PersistedWindowPlacement.FLOATING
    }
    settings.putString(TEAM_PRESENCE_WINDOW_PLACEMENT_KEY, placement.name)

    // Maximizada não grava tamanho: guardar a área da tela inteira faria a
    // janela restaurada nascer do tamanho do monitor.
    if (placement != PersistedWindowPlacement.FLOATING) {
        return
    }

    val widthDp = snapshot.widthDp.toPersistablePresenceWindowDp()
    val heightDp = snapshot.heightDp.toPersistablePresenceWindowDp()
    val xDp = snapshot.xDp?.toPersistablePresenceWindowDp()
    val yDp = snapshot.yDp?.toPersistablePresenceWindowDp()

    if (widthDp != null) {
        settings.putString(TEAM_PRESENCE_WINDOW_WIDTH_KEY, widthDp.toString())
    }
    if (heightDp != null) {
        settings.putString(TEAM_PRESENCE_WINDOW_HEIGHT_KEY, heightDp.toString())
    }
    if (xDp != null) {
        settings.putString(TEAM_PRESENCE_WINDOW_X_KEY, xDp.toString())
    }
    if (yDp != null) {
        settings.putString(TEAM_PRESENCE_WINDOW_Y_KEY, yDp.toString())
    }
}

@Composable
internal fun rememberPersistedTeamPresenceWindowState(
    persistedState: PersistedTeamPresenceWindowState,
    uiScalePercent: Int
): WindowState {
    val initialPosition = if (persistedState.xDp != null && persistedState.yDp != null) {
        WindowPosition(persistedState.xDp.dp, persistedState.yDp.dp)
    } else {
        WindowPosition(Alignment.Center)
    }
    // Só o tamanho default acompanha a escala: tamanho persistido é escolha do
    // usuário e é devolvido como ele deixou.
    val scale = uiScaleFactor(uiScalePercent)
    val initialSize = DpSize(
        width = persistedState.widthDp?.dp ?: (DEFAULT_TEAM_PRESENCE_WINDOW_WIDTH_DP.dp * scale),
        height = persistedState.heightDp?.dp ?: (DEFAULT_TEAM_PRESENCE_WINDOW_HEIGHT_DP.dp * scale)
    )

    return rememberWindowState(
        placement = persistedState.composePlacement,
        position = initialPosition,
        size = initialSize
    )
}

private fun Float.toPersistablePresenceWindowDp(): Int? {
    if (!isFinite()) {
        return null
    }
    return roundToInt()
}

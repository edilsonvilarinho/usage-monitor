package com.usagemonitor

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import com.russhwolf.settings.PreferencesSettings
import kotlin.math.roundToInt

private const val WINDOW_WIDTH_KEY = "windowWidth"
private const val WINDOW_HEIGHT_KEY = "windowHeight"
private const val WINDOW_PLACEMENT_KEY = "windowPlacement"
private const val WINDOW_PLACEMENT_FLOATING = "FLOATING"
private const val WINDOW_PLACEMENT_MAXIMIZED = "MAXIMIZED"

internal data class PersistedMainWindowState(
    val widthDp: Int? = null,
    val heightDp: Int? = null,
    val placement: PersistedWindowPlacement = PersistedWindowPlacement.FLOATING
) {
    val composeWidth: Dp
        get() = widthDp?.dp ?: Dp.Unspecified

    val composeHeight: Dp
        get() = heightDp?.dp ?: Dp.Unspecified

    val composePlacement: WindowPlacement
        get() = when (placement) {
            PersistedWindowPlacement.MAXIMIZED -> WindowPlacement.Maximized
            PersistedWindowPlacement.FLOATING -> WindowPlacement.Floating
        }
}

internal enum class PersistedWindowPlacement {
    FLOATING,
    MAXIMIZED
}

internal data class MainWindowSnapshot(
    val widthDp: Float,
    val heightDp: Float,
    val placement: WindowPlacement
)

internal fun readPersistedMainWindowState(settings: PreferencesSettings): PersistedMainWindowState {
    val widthDp = settings.getStringOrNull(WINDOW_WIDTH_KEY)
        ?.toIntOrNull()
        ?.takeIf { value -> value > 0 }
    val heightDp = settings.getStringOrNull(WINDOW_HEIGHT_KEY)
        ?.toIntOrNull()
        ?.takeIf { value -> value > 0 }
    val placement = when (settings.getStringOrNull(WINDOW_PLACEMENT_KEY)) {
        WINDOW_PLACEMENT_MAXIMIZED -> PersistedWindowPlacement.MAXIMIZED
        else -> PersistedWindowPlacement.FLOATING
    }

    return PersistedMainWindowState(
        widthDp = widthDp,
        heightDp = heightDp,
        placement = placement
    )
}

internal fun persistMainWindowState(
    settings: PreferencesSettings,
    snapshot: MainWindowSnapshot
) {
    val placement = snapshot.toPersistedPlacement()
    settings.putString(WINDOW_PLACEMENT_KEY, placement.storageValue)

    if (placement != PersistedWindowPlacement.FLOATING) {
        return
    }

    val widthDp = snapshot.widthDp.toPersistableDp()
    val heightDp = snapshot.heightDp.toPersistableDp()

    if (widthDp != null) {
        settings.putString(WINDOW_WIDTH_KEY, widthDp.toString())
    }
    if (heightDp != null) {
        settings.putString(WINDOW_HEIGHT_KEY, heightDp.toString())
    }
}

private fun MainWindowSnapshot.toPersistedPlacement(): PersistedWindowPlacement {
    return if (placement == WindowPlacement.Maximized) {
        PersistedWindowPlacement.MAXIMIZED
    } else {
        PersistedWindowPlacement.FLOATING
    }
}

private val PersistedWindowPlacement.storageValue: String
    get() = when (this) {
        PersistedWindowPlacement.FLOATING -> WINDOW_PLACEMENT_FLOATING
        PersistedWindowPlacement.MAXIMIZED -> WINDOW_PLACEMENT_MAXIMIZED
    }

private fun Float.toPersistableDp(): Int? {
    if (!isFinite() || this <= 0f) {
        return null
    }

    return roundToInt()
        .takeIf { value -> value > 0 }
}

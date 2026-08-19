package com.usagemonitor

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import com.russhwolf.settings.PreferencesSettings
import java.awt.GraphicsEnvironment
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

/**
 * Tamanho da janela após uma troca de escala da interface.
 *
 * A escala mexe na densidade da composição, não no tamanho da janela: sem isto,
 * subir para 150% mostraria o mesmo conteúdo maior dentro da mesma moldura, ou
 * seja, menos conteúdo. A razão é entre a escala **aplicada** e a nova, nunca
 * contra 100 — duas mudanças seguidas multiplicariam o tamanho duas vezes.
 *
 * Dimensão não especificada é devolvida como está: `Dp.Unspecified` significa que
 * a janela nunca teve tamanho próprio e multiplicá-lo daria `NaN`.
 */
internal fun scaledWindowSize(
    current: DpSize,
    fromPercent: Int,
    toPercent: Int,
    maxSize: DpSize
): DpSize {
    if (fromPercent == toPercent || fromPercent <= 0 || toPercent <= 0) {
        return current
    }

    val ratio = toPercent.toFloat() / fromPercent.toFloat()
    return DpSize(
        width = current.width.scaledBy(ratio, maxSize.width),
        height = current.height.scaledBy(ratio, maxSize.height)
    )
}

// `Dp.Unspecified` é `NaN`, e comparar NaN por igualdade é justamente o teste que
// falha em silêncio: quem decide aqui é `isFinite`.
private fun Dp.scaledBy(ratio: Float, max: Dp): Dp {
    if (!value.isFinite()) {
        return this
    }

    val scaled = this * ratio
    if (!max.value.isFinite()) {
        return scaled
    }

    return minOf(scaled, max)
}

/**
 * Área útil da tela, para a escala alta não jogar a janela para fora dela.
 *
 * `maximumWindowBounds` vem em espaço de usuário — já dividido pela escala do
 * sistema —, o que a aproxima de `Dp` mas **não** a iguala em todo monitor. É
 * rede de segurança, não medida exata: o pior caso é a janela ficar um pouco
 * maior que o desejado, nunca inacessível. Falha na consulta devolve
 * `Dp.Unspecified`, que o [scaledWindowSize] trata como "sem limite".
 */
internal fun availableWindowSizeDp(): DpSize {
    return runCatching {
        val bounds = GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds
        DpSize(bounds.width.dp, bounds.height.dp)
    }.getOrDefault(DpSize(Dp.Unspecified, Dp.Unspecified))
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

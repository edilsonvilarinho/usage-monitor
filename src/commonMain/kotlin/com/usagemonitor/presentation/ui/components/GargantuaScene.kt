package com.usagemonitor.presentation.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.usagemonitor.presentation.ui.theme.AppGargantuaTokens

/**
 * As cores do cenário Gargantua que dependem do fundo.
 *
 * O cenário nasceu para o tema escuro: parede do tubo, reflexos, lente e disco
 * são brancos ou creme translúcidos, e sobre a superfície clara somem — a trilha
 * da conta sem consumo desaparecia inteira, e o filete branco no meio do plasma
 * escuro dos acentos claros lia como tubo oco. No claro, a luz vira **tinta**:
 * a mesma geometria, com [ink] (o `onSurface` do tema) onde havia branco e os
 * dourados escuros onde havia creme. O horizonte continua preto nos dois — é o
 * buraco negro, e é ele que dá o contraste da marca.
 *
 * Decidido pela superfície, e não por um `isDark` guardado, como os acentos das
 * contas (`AccountAccents`): são 26 presets com superfícies próprias.
 */
internal class GargantuaScene(
    /** Parede do tubo de vidro, fragmentos brancos dos detritos e reflexos. */
    val ink: Color,
    val wallAlpha: Float,
    /** Multiplica a opacidade dos reflexos: a tinta pesa mais que a luz. */
    val reflectionScale: Float,
    /** Quanto o filete central do plasma clareia em direção ao branco. */
    val plasmaHighlight: Float,
    val lens: Color,
    val diskNear: Color,
    val diskMiddle: Color,
    val diskFar: Color,
    val streak: Color
) {
    companion object {
        val Dark = GargantuaScene(
            ink = Color.White,
            wallAlpha = 0.06f,
            reflectionScale = 1f,
            plasmaHighlight = 0.6f,
            lens = Color(0xFFFFE6BE),
            diskNear = AppGargantuaTokens.ember.copy(alpha = 0.25f),
            diskMiddle = AppGargantuaTokens.gold.copy(alpha = 0.55f),
            diskFar = AppGargantuaTokens.hot.copy(alpha = 0.8f),
            streak = Color(0xFFFFF8E6).copy(alpha = 0.45f)
        )

        fun light(ink: Color): GargantuaScene = GargantuaScene(
            ink = ink,
            wallAlpha = 0.10f,
            reflectionScale = 0.55f,
            plasmaHighlight = 0.22f,
            lens = AppGargantuaTokens.dust,
            diskNear = AppGargantuaTokens.ember.copy(alpha = 0.35f),
            diskMiddle = AppGargantuaTokens.gold.copy(alpha = 0.8f),
            diskFar = AppGargantuaTokens.dust.copy(alpha = 0.85f),
            streak = AppGargantuaTokens.ember.copy(alpha = 0.5f)
        )
    }
}

@Composable
internal fun gargantuaScene(): GargantuaScene {
    val scheme = MaterialTheme.colorScheme
    return if (scheme.surface.luminance() < 0.5f) GargantuaScene.Dark else GargantuaScene.light(scheme.onSurface)
}

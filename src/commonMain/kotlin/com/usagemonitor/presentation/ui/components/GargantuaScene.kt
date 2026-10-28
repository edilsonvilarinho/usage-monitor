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
    /**
     * AL1 (#517): o disco de trás dobrado por cima do horizonte, do lado que se
     * afasta ([haloNear]) ao que se aproxima ([haloFar]). Cores opacas; quem dá
     * a opacidade de cada trecho é o desenho.
     */
    val haloNear: Color,
    val haloMiddle: Color,
    val haloFar: Color,
    /** A metade da frente do disco, do lado que se afasta ao que se aproxima (Doppler). */
    val diskNear: Color,
    val diskMiddle: Color,
    val diskFar: Color,
    /** A metade de trás é a mesma luz mais longe: multiplica a opacidade da frente. */
    val diskBackFade: Float,
    val streak: Color
) {
    companion object {
        val Dark = GargantuaScene(
            ink = Color.White,
            wallAlpha = 0.06f,
            reflectionScale = 1f,
            plasmaHighlight = 0.6f,
            haloNear = AppGargantuaTokens.ember,
            haloMiddle = Color(0xFFF4CF97),
            haloFar = AppGargantuaTokens.hot,
            diskNear = AppGargantuaTokens.ember.copy(alpha = 0.4f),
            diskMiddle = AppGargantuaTokens.gold.copy(alpha = 0.65f),
            diskFar = AppGargantuaTokens.hot.copy(alpha = 0.9f),
            diskBackFade = 0.6f,
            streak = Color(0xFFFFF8E6).copy(alpha = 0.45f)
        )

        fun light(ink: Color): GargantuaScene = GargantuaScene(
            ink = ink,
            wallAlpha = 0.10f,
            reflectionScale = 0.55f,
            plasmaHighlight = 0.22f,
            haloNear = AppGargantuaTokens.ember,
            haloMiddle = AppGargantuaTokens.gold,
            haloFar = AppGargantuaTokens.dust,
            diskNear = AppGargantuaTokens.ember.copy(alpha = 0.45f),
            diskMiddle = AppGargantuaTokens.gold.copy(alpha = 0.85f),
            diskFar = AppGargantuaTokens.dust.copy(alpha = 0.9f),
            diskBackFade = 0.65f,
            streak = AppGargantuaTokens.ember.copy(alpha = 0.5f)
        )
    }
}

@Composable
internal fun gargantuaScene(): GargantuaScene {
    val scheme = MaterialTheme.colorScheme
    return if (scheme.surface.luminance() < 0.5f) GargantuaScene.Dark else GargantuaScene.light(scheme.onSurface)
}

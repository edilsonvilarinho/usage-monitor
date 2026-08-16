package com.usagemonitor.presentation.ui.theme

import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object AppMotion {
    const val fast:   Int  = 150
    const val normal: Int  = 250
    const val slow:   Int  = 350
    const val stagger: Long = 70L

    val enterEasing: Easing = FastOutSlowInEasing
    val exitEasing:  Easing = FastOutLinearInEasing
}

object AppElevation {
    val card:   Dp = 2.dp
    val dialog: Dp = 8.dp
    val banner: Dp = 1.dp
}

/**
 * Intensidade do brilho de acento de `DepthSurface`.
 *
 * Nomeia os três patamares que a app já usava de fato, espalhados como floats
 * soltos em cada chamada. Faltar um nome custou caro: a linha da tela de presença
 * simplesmente não passava o parâmetro e herdava [card], calibrado para os cards
 * do dashboard — onde a cor identifica a API e cada card tem respiro. Numa linha
 * de lista o mesmo valor lava o texto de cor.
 */
object AppGlow {
    /** Blocos de detalhe: a cor fica no dado, não no fundo. */
    const val quiet: Float = 0.06f

    /** Linha de lista: o acento orienta a varredura sem competir com o texto. */
    const val row: Float = 0.16f

    /** Card de dashboard, onde a cor é a identidade da fonte. */
    const val card: Float = 0.22f
}

object AppShapes {
    val small      = RoundedCornerShape(10.dp)
    val medium     = RoundedCornerShape(16.dp)
    val large      = RoundedCornerShape(20.dp)
    val extraLarge = RoundedCornerShape(28.dp)
}

private val appShapes = Shapes(
    extraSmall = AppShapes.small,
    small      = AppShapes.small,
    medium     = AppShapes.medium,
    large      = AppShapes.large,
    extraLarge = AppShapes.extraLarge
)

private val appTypography = Typography(
    titleLarge = TextStyle(
        fontWeight    = FontWeight.SemiBold,
        fontSize      = 20.sp,
        lineHeight    = 28.sp,
        letterSpacing = (-0.25).sp
    ),
    titleMedium = TextStyle(
        fontWeight    = FontWeight.Medium,
        fontSize      = 16.sp,
        lineHeight    = 24.sp,
        letterSpacing = (-0.15).sp
    ),
    bodyMedium = TextStyle(
        fontWeight    = FontWeight.Normal,
        fontSize      = 14.sp,
        lineHeight    = 20.sp,
        letterSpacing = 0.sp
    ),
    bodySmall = TextStyle(
        fontWeight    = FontWeight.Normal,
        fontSize      = 12.sp,
        lineHeight    = 16.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontWeight    = FontWeight.Medium,
        fontSize      = 12.sp,
        lineHeight    = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelSmall = TextStyle(
        fontWeight    = FontWeight.Medium,
        fontSize      = 10.sp,
        lineHeight    = 14.sp,
        letterSpacing = 0.5.sp
    )
)

// ── Paleta Dark ───────────────────────────────────────────────────────────────
private val DarkBackground = Color(0xFF181818)
private val DarkSurface    = Color(0xFF242424)
private val DarkOnSurface  = Color(0xFFE0E0E0)
private val DarkPrimary    = Color(0xFF82B1FF)
private val DarkSecondary  = Color(0xFF80CBC4)

private val usageMonitorDarkColorScheme = darkColorScheme(
    background        = DarkBackground,
    surface           = DarkSurface,
    onSurface         = DarkOnSurface,
    primary           = DarkPrimary,
    onPrimary         = Color(0xFF001064),
    secondary         = DarkSecondary,
    onSecondary       = Color(0xFF003731),
    surfaceVariant    = Color(0xFF2C2C2C),
    onSurfaceVariant  = Color(0xFFBDBDBD),
    surfaceTint       = DarkPrimary,
    outlineVariant    = Color(0xFF3A3A3A)
)

// ── Paleta Light ──────────────────────────────────────────────────────────────
private val usageMonitorLightColorScheme = lightColorScheme(
    background        = Color(0xFFF4F6F8),
    surface           = Color(0xFFF8F9FA),
    onSurface         = Color(0xFF1A1A1A),
    primary           = Color(0xFF1565C0),
    onPrimary         = Color.White,
    secondary         = Color(0xFF00796B),
    onSecondary       = Color.White,
    surfaceVariant    = Color(0xFFECEEF0),
    onSurfaceVariant  = Color(0xFF555555),
    surfaceTint       = Color(0xFF1565C0),
    outlineVariant    = Color(0xFFD0D5DB)
)

/**
 * Tema principal da aplicação.
 *
 * `@Composable` indica uma função que descreve parte da UI.
 * É como um componente Vue.js: recebe props e renderiza filhos.
 *
 * `content: @Composable () -> Unit` é o equivalente ao `<slot>` do Vue.
 */
@Composable
fun AppTheme(
    isDark: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (isDark) {
        usageMonitorDarkColorScheme
    } else {
        usageMonitorLightColorScheme
    }
    val accents = if (isDark) darkAppAccents else lightAppAccents

    MaterialTheme(
        colorScheme = colorScheme,
        shapes      = appShapes,
        typography  = appTypography
    ) {
        val scrollbarStyle = LocalScrollbarStyle.current.copy(
            unhoverColor = colorScheme.onSurface.copy(alpha = 0.24f),
            hoverColor   = colorScheme.onSurface.copy(alpha = 0.5f)
        )
        CompositionLocalProvider(
            LocalScrollbarStyle provides scrollbarStyle,
            LocalAppAccents provides accents
        ) {
            content()
        }
    }
}

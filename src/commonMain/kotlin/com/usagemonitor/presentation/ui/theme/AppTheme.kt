package com.usagemonitor.presentation.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Paleta Dark (#1E1E1E como background) ────────────────────────────────────
private val DarkBackground = Color(0xFF1E1E1E)
private val DarkSurface    = Color(0xFF2D2D2D)
private val DarkOnSurface  = Color(0xFFE0E0E0)
private val DarkPrimary    = Color(0xFF82B1FF)  // azul claro suave
private val DarkSecondary  = Color(0xFF80CBC4)  // verde-azulado

private val usageMonitorDarkColorScheme = darkColorScheme(
    background       = DarkBackground,
    surface          = DarkSurface,
    onSurface        = DarkOnSurface,
    primary          = DarkPrimary,
    onPrimary        = Color(0xFF001064),
    secondary        = DarkSecondary,
    onSecondary      = Color(0xFF003731),
    surfaceVariant   = Color(0xFF383838),
    onSurfaceVariant = Color(0xFFBDBDBD)
)

// ── Paleta Light (Material 3 padrão ligeiramente personalizada) ───────────────
private val usageMonitorLightColorScheme = lightColorScheme(
    background       = Color(0xFFF5F5F5),
    surface          = Color(0xFFFFFFFF),
    primary          = Color(0xFF1565C0),
    onPrimary        = Color.White,
    secondary        = Color(0xFF00796B),
    onSecondary      = Color.White
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

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

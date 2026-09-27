package com.usagemonitor.ui

import androidx.compose.runtime.Composable
import com.usagemonitor.presentation.ui.theme.AppMotionPolicy
import com.usagemonitor.presentation.ui.theme.AppTheme
import com.usagemonitor.presentation.ui.theme.AppThemePreset

/**
 * O [AppTheme] dos testes de **tela**, com movimento reduzido (issue #295).
 *
 * Sob o relógio dos testes toda transição finita é desenhada quadro a quadro
 * até o `waitForIdle` voltar, e cada quadro passa pelas sombras e pelo brilho
 * de `appDepth` no raster de CPU. Medido: um `AppDialog` custa 2,1 s com
 * [AppMotionPolicy.Static] e 0,4 s com [AppMotionPolicy.Reduced], e a suíte
 * inteira cai de ~342 s para ~224 s. Teste de tela afirma o estado final, que
 * é o mesmo nas duas políticas — `Reduced` é o `snap()` que o próprio app usa
 * com "Reduzir animações".
 *
 * **Teste que afirma movimento não usa isto.** As primitivas que animam
 * (`AppStatesTest`, `AppDialogTest`, `AppControlsTest`, `AppDepthTest`,
 * `HudNotchTest`...) continuam no [AppTheme] com a política default, senão
 * passariam sem exercitar a transição que existem para cobrir.
 */
@Composable
internal fun ScreenTestTheme(
    preset: AppThemePreset = AppThemePreset.OBSIDIANA_DARK,
    uiScalePercent: Int = 100,
    motion: AppMotionPolicy = AppMotionPolicy.Reduced,
    content: @Composable () -> Unit
) {
    AppTheme(preset = preset, uiScalePercent = uiScalePercent, motion = motion, content = content)
}

/** Mesma troca para os testes que escolhem só o modo claro/escuro. */
@Composable
internal fun ScreenTestTheme(
    isDark: Boolean,
    uiScalePercent: Int = 100,
    motion: AppMotionPolicy = AppMotionPolicy.Reduced,
    content: @Composable () -> Unit
) {
    AppTheme(isDark = isDark, uiScalePercent = uiScalePercent, motion = motion, content = content)
}

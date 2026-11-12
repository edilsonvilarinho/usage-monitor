package com.usagemonitor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

/**
 * Tamanhos da janela durante a transição de minimizar (#576, A05b). O "antes" é o último
 * estado parado, gravado em [SideEffect]; enquanto [moving] a janela segura o maior dos dois,
 * sem redimensionar a janela AWT por quadro.
 */
@Composable
internal fun rememberHudHeldWindowSizes(
    current: HudNotchSizes,
    settled: HudNotchSizes,
    moving: Boolean,
): Pair<HudNotchSizes, HudNotchSizes> {
    val stable = remember { mutableStateOf(current) }
    val stableSettled = remember { mutableStateOf(settled) }
    SideEffect {
        if (!moving) {
            stable.value = current
            stableSettled.value = settled
        }
    }
    return hudHeldNotchSizes(stable.value, current, moving) to hudHeldNotchSizes(stableSettled.value, settled, moving)
}

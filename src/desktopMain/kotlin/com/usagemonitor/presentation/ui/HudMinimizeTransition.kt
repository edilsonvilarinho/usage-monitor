package com.usagemonitor.presentation.ui


import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.presentation.ui.theme.LocalAppMotionPolicy

/**
 * Progresso da transição de minimizar (#576, A05b) por conta: 0 = cheia, 1 = minimizada.
 * [moving] é verdadeiro enquanto alguma conta ainda não chegou ao alvo.
 */
internal class HudMinimizeProgress(val values: Map<UsageTargetKey, Float>, val moving: Boolean)

/**
 * Anima por conta, de 0 a 1 (ou de 1 a 0) sobre [hudMinimizeMillis], com tween linear; a curva
 * ease-in-out mora em [hudMinimizeFrame]. Com "Reduzir animações" o alvo sai de uma vez. Conta
 * que já nasce minimizada começa em 1: não há transição no arranque.
 */
@Composable
internal fun rememberHudMinimizeProgress(accounts: List<HudAccount>): HudMinimizeProgress {
    val policy = LocalAppMotionPolicy.current
    val values = HashMap<UsageTargetKey, Float>()
    var moving = false
    for (account in accounts) {
        val target = if (account.minimized) 1f else 0f
        key(account.targetKey) {
            val progress = remember { Animatable(target) }
            LaunchedEffect(account.minimized, policy.reduced) {
                if (policy.reduced) {
                    progress.snapTo(target)
                    return@LaunchedEffect
                }
                progress.animateTo(target, tween(hudMinimizeMillis(motionReduced = false), easing = LinearEasing))
            }
            // Reduzido: o alvo sai do próprio estado, sem esperar o efeito que faz o snap.
            val value = if (policy.reduced) target else progress.value
            values[account.targetKey] = value
            if (value != target) moving = true
        }
    }
    return HudMinimizeProgress(values, moving)
}

/**
 * Tamanho do notch (#576, A05b): durante a transição ([moving]) segura o maior dos dois,
 * o último estável ([before]) e o atual ([after]), por dimensão; fora dela, [after].
 * Assim o corpo não encolhe antes do fim, sem redimensionar a janela AWT por quadro.
 */
@Composable
internal fun rememberHudMinimizeNotchSize(moving: Boolean, current: DpSize): DpSize {
    val stable = remember { mutableStateOf(current) }
    SideEffect {
        if (!moving) stable.value = current
    }
    return hudMinimizeHeldSize(before = stable.value, after = current, moving = moving)
}

/** O maior dos dois tamanhos, por dimensão, enquanto [moving]; fora dele, [after]. */
internal fun hudMinimizeHeldSize(before: DpSize, after: DpSize, moving: Boolean): DpSize =
    if (!moving) after else DpSize(maxOf(before.width, after.width), maxOf(before.height, after.height))

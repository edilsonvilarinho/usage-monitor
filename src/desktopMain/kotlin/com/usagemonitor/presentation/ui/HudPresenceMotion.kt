package com.usagemonitor.presentation.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.usagemonitor.presentation.ui.components.GargantuaFrame
import com.usagemonitor.presentation.ui.components.gargantuaBirthFrame
import com.usagemonitor.presentation.ui.components.gargantuaCollapseFrame
import com.usagemonitor.presentation.ui.theme.AppGargantuaTokens
import com.usagemonitor.presentation.ui.theme.AppMotionPolicy
import kotlinx.coroutines.delay

/**
 * A lista que a HUD desenha e mede. A geometria continua saindo dela
 * (`hudNotchSizes`): conta que nasce já ocupa o espaço, e conta que sai o
 * mantém até o colapso acabar — só então o notch encolhe, num passo só.
 * Com "Reduzir animações" é a lista viva, sem transição.
 */
@Composable
internal fun rememberHudPresence(accounts: List<HudAccount>, policy: AppMotionPolicy): List<HudAccount> {
    val animate = !policy.reduced
    var displayed by remember { mutableStateOf(mergeHudPresence(emptyList(), accounts, animate)) }
    LaunchedEffect(accounts, animate) {
        displayed = mergeHudPresence(displayed, accounts, animate)
    }
    val order = hudBirthOrder(displayed)
    displayed.forEachIndexed { index, account ->
        if (account.presence == HudPresence.SHOWN) return@forEachIndexed
        key(account.targetKey, account.presence) {
            LaunchedEffect(Unit) {
                delay(hudPresenceMillis(account.presence, order[index]).toLong())
                displayed = settleHudPresence(displayed, account.targetKey)
            }
        }
    }
    return displayed
}

/** Quanto a transição de uma conta dura, contando a espera da fila de nascimento. */
internal fun hudPresenceMillis(presence: HudPresence, birthOrder: Int?): Int = when (presence) {
    HudPresence.SHOWN -> 0
    HudPresence.ENTERING -> (birthOrder ?: 0) * HUD_BIRTH_STAGGER_MILLIS + AppGargantuaTokens.birthMillis
    HudPresence.LEAVING -> AppGargantuaTokens.collapseMillis
}

/**
 * O quadro do indicador de uma conta. Tween linear sobre o progresso: as curvas
 * moram em `gargantuaBirthFrame`/`gargantuaCollapseFrame`, que são puras.
 */
@Composable
internal fun rememberHudRingFrame(presence: HudPresence, birthOrder: Int?, policy: AppMotionPolicy): GargantuaFrame {
    val progress = remember { Animatable(if (presence == HudPresence.SHOWN) 1f else 0f) }
    LaunchedEffect(presence, policy.reduced) {
        if (policy.reduced || presence == HudPresence.SHOWN) {
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        progress.snapTo(0f)
        val millis = if (presence == HudPresence.ENTERING) {
            delay(((birthOrder ?: 0) * HUD_BIRTH_STAGGER_MILLIS).toLong())
            AppGargantuaTokens.birthMillis
        } else {
            AppGargantuaTokens.collapseMillis
        }
        progress.animateTo(1f, tween(millis, easing = LinearEasing))
    }
    return when {
        policy.reduced -> GargantuaFrame.Settled
        presence == HudPresence.ENTERING -> gargantuaBirthFrame(progress.value)
        presence == HudPresence.LEAVING -> gargantuaCollapseFrame(progress.value)
        else -> GargantuaFrame.Settled
    }
}

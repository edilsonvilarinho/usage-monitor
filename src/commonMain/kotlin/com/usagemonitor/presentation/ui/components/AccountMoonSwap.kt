package com.usagemonitor.presentation.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.usagemonitor.presentation.ui.theme.AccountEmoji
import com.usagemonitor.presentation.ui.theme.AppGargantuaTokens
import com.usagemonitor.presentation.ui.theme.LocalAppMotionPolicy
import com.usagemonitor.presentation.ui.theme.appTweenSpec

/** Estado visual da lua num instante da troca de emoji (#585). */
data class AccountMoonSwapFrame(
    /** Graus de órbita da lua velha: 0 no início, −20 quando sai. */
    val outgoingDegrees: Float,
    /** Opacidade da lua velha: some até a saída de [AppGargantuaTokens.moonSwapOutMillis]. */
    val outgoingAlpha: Float,
    /** Escala do emoji novo: 0,7 até 1, sem overshoot. */
    val incomingScale: Float,
    /** Opacidade do emoji novo: entra depois da saída da velha. */
    val incomingAlpha: Float
)

private const val OUTGOING_DEGREES = -20f
private const val INCOMING_START_SCALE = 0.7f

/**
 * Quadro puro da troca de emoji (#585) em [elapsedMillis] desde o início.
 *
 * A velha sai em `[0; moonSwapOutMillis]` com aceleração e gira −20°. O novo entra em
 * `[moonSwapOutMillis; moonSwapMillis]` com desaceleração cúbica, sem passar do alvo.
 * Antes do início vale o primeiro quadro; depois do fim, o último.
 */
fun accountMoonSwapFrame(elapsedMillis: Float): AccountMoonSwapFrame {
    val outMillis = AppGargantuaTokens.moonSwapOutMillis.toFloat()
    val totalMillis = AppGargantuaTokens.moonSwapMillis.toFloat()
    val leaving = (elapsedMillis / outMillis).coerceIn(0f, 1f)
    val entering = ((elapsedMillis - outMillis) / (totalMillis - outMillis)).coerceIn(0f, 1f)
    val leave = leaving * leaving
    val remaining = 1f - entering
    val arrive = 1f - remaining * remaining * remaining
    return AccountMoonSwapFrame(
        // `+ 0f`: `-20f * 0f` dá `-0f`, que `assertEquals(0f, ...)` distingue de `0f`.
        outgoingDegrees = 0f + OUTGOING_DEGREES * leave,
        outgoingAlpha = 1f - leave,
        incomingScale = INCOMING_START_SCALE + (1f - INCOMING_START_SCALE) * arrive,
        incomingAlpha = arrive
    )
}

/**
 * Estado da lua da conta durante a troca. [current] é o emoji que vale agora, [outgoing]
 * é o que sai; [elapsed] vai de 0 a `moonSwapMillis` e é o relógio de [accountMoonSwapFrame].
 */
@Stable
class AccountMoonSwapState internal constructor(initial: AccountEmoji?) {
    var current: AccountEmoji? by mutableStateOf(initial)
    var outgoing: AccountEmoji? by mutableStateOf(null)
    val elapsed = Animatable(AppGargantuaTokens.moonSwapMillis.toFloat())
}

/**
 * Lembra a troca de emoji da lua. Sem mudança de emoji não anima; com "reduzir animações"
 * o tween vira corte seco pela [appTweenSpec].
 */
@Composable
fun rememberAccountMoonSwap(emoji: AccountEmoji?): AccountMoonSwapState {
    val policy = LocalAppMotionPolicy.current
    val state = remember { AccountMoonSwapState(emoji) }
    LaunchedEffect(emoji) {
        if (emoji == state.current) return@LaunchedEffect
        state.outgoing = state.current
        state.current = emoji
        state.elapsed.snapTo(0f)
        state.elapsed.animateTo(
            targetValue = AppGargantuaTokens.moonSwapMillis.toFloat(),
            animationSpec = appTweenSpec(AppGargantuaTokens.moonSwapMillis, policy, easing = LinearEasing)
        )
        // Fim da troca: a velha sai da árvore. Vale também para o corte seco (#585).
        state.outgoing = null
    }
    return state
}

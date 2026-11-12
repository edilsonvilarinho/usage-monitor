package com.usagemonitor.presentation.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.unit.Dp
import com.usagemonitor.presentation.ui.theme.AppGargantuaTokens

/** Curva ease-in-out (como o `ease-in-out` do CSS): começa e termina devagar, sem passar do alvo. */
private val minimizeEasing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

/**
 * Quadro da transição de minimizar (#576, A05): diâmetro do anel e os dois alfas
 * do cross-fade entre o texto cheio e a linha de foco ao lado do anel.
 */
data class HudMinimizeFrame(val ringSize: Dp, val fullTextAlpha: Float, val focusLineAlpha: Float)

/**
 * Duração da transição. Com "reduzir animações" é zero: o quadro final sai de
 * uma vez, sem quadro intermediário.
 */
fun hudMinimizeMillis(motionReduced: Boolean): Int =
    if (motionReduced) 0 else AppGargantuaTokens.minimizeMillis

/**
 * Progresso linear da transição, em `0..1`, [elapsedMillis] depois de ela começar.
 * Sem duração (animação reduzida), o progresso já nasce no fim.
 */
fun hudMinimizeProgress(elapsedMillis: Float, motionReduced: Boolean): Float {
    val duration = hudMinimizeMillis(motionReduced)
    if (duration == 0) return 1f
    return (elapsedMillis / duration).coerceIn(0f, 1f)
}

/**
 * O quadro em [progress] (0 = cheio, 1 = minimizado). O anel vai de 64 a 28 dp
 * pela curva ease-in-out, sem overshoot; o texto cheio e a linha de foco trocam
 * alfa com o mesmo valor eased, de modo que somam 1 em todo quadro.
 */
fun hudMinimizeFrame(progress: Float): HudMinimizeFrame {
    val eased = minimizeEased(progress.coerceIn(0f, 1f))
    val from = AppGargantuaTokens.size
    val to = AppGargantuaTokens.minimizedSize
    return HudMinimizeFrame(
        ringSize = from + (to - from) * eased,
        fullTextAlpha = 1f - eased,
        focusLineAlpha = eased,
    )
}

/**
 * Tamanho da janela durante a transição, de [from] a [to] (qualquer sentido).
 * Até o fim, a janela mantém o maior dos dois: encolhendo, a área só cai no fim;
 * crescendo, já tem o tamanho maior desde o primeiro quadro (o corpo é desenhado
 * dentro dela). No fim, assume [to].
 */
fun hudMinimizeWindowSize(from: Dp, to: Dp, progress: Float): Dp =
    if (progress.coerceIn(0f, 1f) >= 1f) to else maxOf(from, to)

private fun minimizeEased(progress: Float): Float = when {
    progress <= 0f -> 0f
    progress >= 1f -> 1f
    else -> minimizeEasing.transform(progress)
}

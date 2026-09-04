package com.usagemonitor.presentation.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.floor
import com.usagemonitor.domain.entity.CliSessionHealth
import com.usagemonitor.domain.entity.SessionPulse
import com.usagemonitor.presentation.ui.healthColor
import com.usagemonitor.presentation.ui.theme.AppAccents

/** Duração de um passo do pisca — o tempo em cena de cada severidade. */
const val SESSION_PULSE_STEP_MILLIS = 1_200

/** Opacidade mínima do halo. Não chega a zero: o pisca é suave, não intermitente. */
private const val SESSION_PULSE_MIN_ALPHA = 0.30f
private const val SESSION_PULSE_MAX_ALPHA = 1.0f

/**
 * Faixa de opacidade do fundo do botão.
 *
 * Não vai a 100%: o fundo do card tem de continuar aparecendo por trás, senão o
 * botão vira um bloco chapado de cor e o ícone some.
 */
private const val SESSION_PULSE_CONTAINER_MIN_OPACITY = 0.16f
private const val SESSION_PULSE_CONTAINER_RANGE = 0.40f

/** Um quadro do pisca: qual severidade está em cena e com que opacidade. */
data class SessionPulseFrame(
    val health: CliSessionHealth,
    val alpha: Float
)

/**
 * Quadro do pisca para uma fase do ciclo.
 *
 * O ciclo tem um passo por severidade presente: com laranja e vermelho ativos ao
 * mesmo tempo, o botão alterna entre as duas cores em vez de mostrar só a pior.
 *
 * A opacidade sobe até a metade do passo e volta ao mínimo no fim dele. Isso faz
 * a troca de cor acontecer no ponto mais apagado do ciclo, sem o corte seco que
 * uma troca no auge produziria.
 *
 * Função pura de propósito: a animação decide a fase, a regra do que se vê é
 * testável sem Compose.
 */
fun sessionPulseFrame(phase: Float, severities: List<CliSessionHealth>): SessionPulseFrame? {
    if (severities.isEmpty()) {
        return null
    }

    val normalized = phase - floor(phase)
    val position = normalized * severities.size
    val step = position.toInt().coerceIn(0, severities.size - 1)
    val stepProgress = position - step
    // Triângulo: 0 nas bordas do passo, 1 no meio.
    val breath = 1f - abs(stepProgress * 2f - 1f)

    return SessionPulseFrame(
        health = severities[step],
        alpha = SESSION_PULSE_MIN_ALPHA + (SESSION_PULSE_MAX_ALPHA - SESSION_PULSE_MIN_ALPHA) * breath
    )
}

/**
 * Quadro corrente do pisca, ou `null` quando não há nada a sinalizar.
 *
 * Sem pulso **nenhuma transição infinita é criada**: uma animação que nunca
 * termina impede o `waitForIdle` dos testes de componente de retornar, e o botão
 * em repouso é o caso normal da tela.
 */
@Composable
internal fun rememberSessionPulseFrame(pulse: SessionPulse): SessionPulseFrame? {
    val severities = pulse.severities
    if (severities.isEmpty()) {
        return null
    }

    val transition = rememberInfiniteTransition(label = "sessionPulse")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = SESSION_PULSE_STEP_MILLIS * severities.size,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "sessionPulsePhase"
    )

    return sessionPulseFrame(phase, severities)
}

/**
 * Cor da severidade em cena; a mesma paleta de saúde dos modais de sessão.
 *
 * [accents] sem default: sem ele esta função ficava presa à paleta escura mesmo
 * no tema claro (2,64:1 medido) — o único caminho seguro é o chamador, que está
 * em composição, entregar `AppAccents.current`.
 */
internal fun SessionPulseFrame.color(accents: AppAccents): Color = healthColor(health, accents)

/**
 * Fundo do botão no quadro atual, ou [resting] quando não há pulso.
 *
 * É o fundo que respira, não o ícone: um ícone piscando sozinho lê como falha de
 * renderização, enquanto o botão inteiro mudando de cor lê como aviso.
 */
internal fun sessionPulseContainerColor(frame: SessionPulseFrame?, resting: Color, accents: AppAccents): Color {
    if (frame == null) {
        return resting
    }
    return frame.color(accents).copy(
        alpha = SESSION_PULSE_CONTAINER_MIN_OPACITY + SESSION_PULSE_CONTAINER_RANGE * frame.alpha
    )
}

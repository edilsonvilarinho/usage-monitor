package com.usagemonitor.presentation.ui.components

import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/**
 * Um quadro do nascimento ou do colapso do indicador. Tudo em fração: `1` é o
 * indicador parado. [flash] e as ondas são efeitos por cima, em progresso
 * `0..1` (`0` e `1` não desenham nada).
 */
data class GargantuaFrame(
    /** Escala do indicador inteiro, arcos e marca juntos. */
    val scale: Float = 1f,
    val alpha: Float = 1f,
    /** Horizonte, lente e disco crescendo do centro. */
    val core: Float = 1f,
    /** Parede de vidro dos arcos. */
    val glass: Float = 1f,
    /** Multiplica o sweep de cada cota: o plasma enche ou recolhe. */
    val arcs: Float = 1f,
    val mark: Float = 1f,
    val markScale: Float = 1f,
    /** Percentuais e pílula ao lado do anel. */
    val text: Float = 1f,
    /** Opacidade do clarão central e seu raio, como fração do raio do anel. */
    val flash: Float = 0f,
    val flashRadius: Float = 0f,
    /** Ondas de choque: progresso de cada uma. */
    val nearShock: Float = 0f,
    val farShock: Float = 0f
) {
    val settled: Boolean get() = this == Settled

    companion object {
        val Settled = GargantuaFrame()
    }
}

/**
 * S1 · nascimento com onda de choque: um ponto de luz acende e explode, duas
 * ondas saem para fora, o horizonte abre do centro, o plasma enche até o valor
 * e só então a marca e o texto aparecem. [progress] em `0..1`.
 */
fun gargantuaBirthFrame(progress: Float): GargantuaFrame {
    val t = progress.coerceIn(0f, 1f)
    if (t >= 1f) return GargantuaFrame.Settled
    val core = easeOut(span(t, 0.25f, 0.7f))
    val mark = span(t, 0.6f, 0.9f)
    val flash = span(t, 0.08f, 0.35f)
    return GargantuaFrame(
        core = core,
        glass = core,
        arcs = easeInOut(span(t, 0.5f, 1f)),
        mark = mark,
        markScale = 0.6f + 0.4f * mark,
        text = span(t, 0.7f, 1f),
        flash = pulse(flash),
        flashRadius = if (flash > 0f) 0.2f + flash * 0.9f else 0f,
        nearShock = span(t, 0.15f, 0.7f),
        farShock = span(t, 0.25f, 0.8f)
    )
}

/**
 * C2 · colapso com clarão: o plasma recolhe, o anel encolhe até um ponto e some
 * num clarão pequeno. O texto sai primeiro, para o olho não ler número morrendo.
 */
fun gargantuaCollapseFrame(progress: Float): GargantuaFrame {
    val t = progress.coerceIn(0f, 1f)
    val flash = span(t, 0.72f, 1f)
    val fade = 1f - span(t, 0.7f, 0.85f)
    return GargantuaFrame(
        scale = 1f - easeIn(span(t, 0f, 0.8f)),
        alpha = fade,
        arcs = 1f - easeOut(span(t, 0f, 0.45f)),
        mark = fade,
        text = 1f - span(t, 0f, 0.25f),
        flash = pulse(flash),
        flashRadius = if (flash > 0f) 0.1f + flash * 0.3f else 0f
    )
}

private fun span(t: Float, start: Float, end: Float): Float = ((t - start) / (end - start)).coerceIn(0f, 1f)

private fun pulse(t: Float): Float = if (t <= 0f || t >= 1f) 0f else sin(t * PI).toFloat()

private fun easeOut(t: Float): Float = 1f - (1f - t).pow(3)

private fun easeIn(t: Float): Float = t * t * t

private fun easeInOut(t: Float): Float = if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f

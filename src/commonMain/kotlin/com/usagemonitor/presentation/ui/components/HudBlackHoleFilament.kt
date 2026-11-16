package com.usagemonitor.presentation.ui.components

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Segmento da fita de maré do buraco negro (#602, AZ2). Coordenadas são deslocamento a partir do centro do buraco.
 * [progress] é o s do ponto final do segmento (0 na cabeça, 1 no horizonte); [colorMix] é o mesmo fator, usado
 * para misturar a cor (quente para dourado no escuro, dourado para poeira no claro); a cor não sai daqui.
 */
internal data class HudBlackHoleFilamentSegment(
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val progress: Float,
    val alpha: Float,
    val widthDp: Float,
    val colorMix: Float
)

/** Passos da fita: s = j / 48 para j de 0 a 48 (#602, AZ2). */
private const val HUD_BLACK_HOLE_FILAMENT_STEPS = 48

/** Raio mínimo de um ponto para virar segmento: horizonte mais meio dp (#602, AZ2). */
private const val HUD_BLACK_HOLE_FILAMENT_MIN_RADIUS = 12.9

/** Quanto a fita dá de volta ao longo do filamento, em radianos (#602, AZ2). */
private const val HUD_BLACK_HOLE_FILAMENT_SWEEP_RAD = 1.1

/** Amplitude da ondulação angular, em radianos (#602, AZ2). */
private const val HUD_BLACK_HOLE_FILAMENT_RIPPLE_RAD = 0.035

/** Velocidade de deslocamento da ondulação, em radianos por segundo (#602, AZ2). */
private const val HUD_BLACK_HOLE_FILAMENT_RIPPLE_SPEED = 2.2

/** Alfa máximo da cabeça e expoente da queda até o horizonte (#602, AZ2). */
private const val HUD_BLACK_HOLE_FILAMENT_ALPHA_MAX = 0.75
private const val HUD_BLACK_HOLE_FILAMENT_ALPHA_EXPONENT = 0.8

/** Espessura na cabeça e perda de espessura até o horizonte, em dp (#602, AZ2). */
private const val HUD_BLACK_HOLE_FILAMENT_WIDTH_HEAD_DP = 1.1
private const val HUD_BLACK_HOLE_FILAMENT_WIDTH_LOSS_DP = 0.5

/** Ponto amostrado da fita: [s] é o progresso, [x] e [y] o deslocamento a partir do centro, [raio] a distância. */
private data class PontoDaFita(val s: Double, val x: Double, val y: Double, val raio: Double)

/**
 * Fita de maré da conta em execução até o horizonte (#602, AZ2). Função pura: mesma entrada, mesma fita.
 * A cabeça parte de [orbitRadius] no ângulo [orbitAngleRad] e cai até [horizonRadius]; a ondulação some nas
 * duas pontas. Cada segmento liga o ponto anterior ao atual e só existe se o ponto atual tiver raio >= 12,9 (horizonte 12,4 + 0,5, #612 C1).
 * Alfa, espessura e mistura de cor usam o s do ponto final do segmento.
 */
internal fun hudBlackHoleFilament(
    orbitAngleRad: Double,
    orbitRadius: Float = HUD_BLACK_HOLE_ORBIT_RADIUS.value,
    horizonRadius: Float = 12.4f,
    timeMs: Long
): List<HudBlackHoleFilamentSegment> {
    val segundos = timeMs / 1000.0
    val raioDaOrbita = orbitRadius.toDouble()
    val raioDoHorizonte = horizonRadius.toDouble()
    val pontos = (0..HUD_BLACK_HOLE_FILAMENT_STEPS).map { passo ->
        pontoDaFita(passo.toDouble() / HUD_BLACK_HOLE_FILAMENT_STEPS, orbitAngleRad, raioDaOrbita, raioDoHorizonte, segundos)
    }
    return (1..HUD_BLACK_HOLE_FILAMENT_STEPS).mapNotNull { passo ->
        val atual = pontos[passo]
        if (atual.raio < HUD_BLACK_HOLE_FILAMENT_MIN_RADIUS) null else segmentoDaFita(pontos[passo - 1], atual)
    }
}

private fun pontoDaFita(
    s: Double,
    anguloDaOrbita: Double,
    raioDaOrbita: Double,
    raioDoHorizonte: Double,
    segundos: Double
): PontoDaFita {
    val raio = raioDaOrbita + (raioDoHorizonte - raioDaOrbita) * s
    val ondulacao = HUD_BLACK_HOLE_FILAMENT_RIPPLE_RAD *
        sin(9 * PI * s - HUD_BLACK_HOLE_FILAMENT_RIPPLE_SPEED * segundos) * sin(PI * s)
    val angulo = anguloDaOrbita - HUD_BLACK_HOLE_FILAMENT_SWEEP_RAD * s + ondulacao
    return PontoDaFita(s = s, x = raio * cos(angulo), y = raio * sin(angulo), raio = raio)
}

private fun segmentoDaFita(anterior: PontoDaFita, atual: PontoDaFita): HudBlackHoleFilamentSegment {
    val s = atual.s
    return HudBlackHoleFilamentSegment(
        startX = anterior.x.toFloat(),
        startY = anterior.y.toFloat(),
        endX = atual.x.toFloat(),
        endY = atual.y.toFloat(),
        progress = s.toFloat(),
        alpha = (HUD_BLACK_HOLE_FILAMENT_ALPHA_MAX * (1.0 - s).pow(HUD_BLACK_HOLE_FILAMENT_ALPHA_EXPONENT)).toFloat(),
        widthDp = (HUD_BLACK_HOLE_FILAMENT_WIDTH_HEAD_DP - HUD_BLACK_HOLE_FILAMENT_WIDTH_LOSS_DP * s).toFloat(),
        colorMix = s.toFloat()
    )
}

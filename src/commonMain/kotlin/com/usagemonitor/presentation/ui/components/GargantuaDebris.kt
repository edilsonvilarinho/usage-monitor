package com.usagemonitor.presentation.ui.components

import com.usagemonitor.presentation.ui.theme.AppGargantuaTokens
import kotlin.math.PI
import kotlin.math.roundToInt

/**
 * Um fragmento do anel de detritos (J7): a trilha de uma cota **sem projeção**.
 * Ângulos em graus na convenção do `drawArc` (0 = 3 h, sentido horário);
 * [radialOffset] e [width] são frações da espessura do arco, e [laps] é quantas
 * voltas o fragmento dá em um ciclo de [AppGargantuaTokens.debrisCycleMillis].
 */
data class GargantuaDebrisFragment(
    val startDegrees: Float,
    val sweepDegrees: Float,
    val radialOffset: Float,
    val width: Float,
    val gold: Boolean,
    val alpha: Float,
    val laps: Int
)

/** Voltas por ciclo de um fragmento na linha central: 8 × 16 s = 128 s. */
const val GARGANTUA_DEBRIS_BASE_LAPS = 8

/** Quanto o fragmento pode sair da linha central, para dentro ou para fora. */
const val GARGANTUA_DEBRIS_MAX_OFFSET = 0.4f

/** Semente fixa: o anel é o mesmo em toda conta, todo arranque e todo quadro parado. */
private const val DEBRIS_SEED = 31

/** O último fragmento para antes de fechar a volta, para não encostar no primeiro. */
private const val DEBRIS_CLOSING_GAP_DEGREES = 2.9f

/**
 * Os fragmentos do anel, na ordem em que nascem ao redor da volta: comprimento,
 * vão, espessura, raio e brilho irregulares (como código morse), cerca de
 * um em cada três dourado. Mesma semente e mesma ordem de sorteio do protótipo HTML.
 */
val gargantuaDebrisField: List<GargantuaDebrisFragment> by lazy { buildDebrisField() }

private fun buildDebrisField(): List<GargantuaDebrisFragment> {
    val random = Mulberry32(DEBRIS_SEED)
    val fragments = mutableListOf<GargantuaDebrisFragment>()
    var cursor = 0f
    while (cursor < 360f - DEBRIS_CLOSING_GAP_DEGREES) {
        val sweep = radiansToDegrees(0.04f + random.next() * 0.22f)
        val gap = radiansToDegrees(0.05f + random.next() * 0.14f)
        // No protótipo a espessura vai de 0,5 a 1,9 px sobre um arco de 2,5 px.
        val width = (0.5f + random.next() * 1.4f) / 2.5f
        val gold = random.next() > 0.7f
        val alpha = 0.12f + random.next() * 0.2f
        val offset = (random.next() - 0.5f) * 2f * GARGANTUA_DEBRIS_MAX_OFFSET
        fragments += GargantuaDebrisFragment(
            startDegrees = cursor,
            sweepDegrees = sweep,
            radialOffset = offset,
            width = width,
            gold = gold,
            alpha = alpha,
            laps = gargantuaDebrisLaps(offset)
        )
        cursor += sweep + gap
    }
    return fragments
}

/**
 * Órbita kepleriana simplificada: o fragmento de dentro gira mais rápido que o
 * de fora, então uns ultrapassam os outros. Voltas **inteiras** por ciclo: no
 * fim do laço cada fragmento está onde começou, e o anel nunca salta.
 */
fun gargantuaDebrisLaps(radialOffset: Float): Int {
    val period = 1f + radialOffset.coerceIn(-GARGANTUA_DEBRIS_MAX_OFFSET, GARGANTUA_DEBRIS_MAX_OFFSET) * 0.9f
    return (GARGANTUA_DEBRIS_BASE_LAPS / period).roundToInt()
}

/**
 * Onde o fragmento começa em [phase] (`0..1` do ciclo), em `0..360`. Com o
 * movimento contínuo desligado a fase fica em zero e o anel fica parado.
 */
fun gargantuaDebrisAngle(fragment: GargantuaDebrisFragment, phase: Float): Float {
    val turned = fragment.startDegrees + phase * fragment.laps * 360f
    return ((turned % 360f) + 360f) % 360f
}

private fun radiansToDegrees(radians: Float): Float = (radians * 180.0 / PI).toFloat()

/** Gerador mulberry32, idêntico ao `rng` dos protótipos HTML da HUD. */
private class Mulberry32(private var state: Int) {
    fun next(): Float {
        state += 0x6D2B79F5
        var t = (state xor (state ushr 15)) * (state or 1)
        t = (t + (t xor (t ushr 7)) * (t or 61)) xor t
        return ((t xor (t ushr 14)).toUInt().toDouble() / 4_294_967_296.0).toFloat()
    }
}

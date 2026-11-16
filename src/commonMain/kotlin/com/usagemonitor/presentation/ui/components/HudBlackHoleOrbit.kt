package com.usagemonitor.presentation.ui.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Raio da órbita em comboio do buraco negro, do centro até o centro do glifo (#602, A05; #612, ajuste do dono: 38 dp). */
internal val HUD_BLACK_HOLE_ORBIT_RADIUS: Dp = 38.dp

/** Diâmetro do disco do glifo em órbita (#612, escolha C1: 22 dp). */
internal val HUD_BLACK_HOLE_GLYPH_SIZE: Dp = 22.dp

/** Extensão do glifo a partir do centro dele até a borda externa; entra na conta da célula (#612, C1: 15 dp). */
internal val HUD_BLACK_HOLE_GLYPH_EXTENT: Dp = 15.dp

/** Duração de uma volta completa da cabeça do trem (#602, A05). */
internal const val HUD_BLACK_HOLE_TURN_MS: Long = 60_000L

/** Comprimento do arco de rastro atrás de cada glifo, em graus (#602, A05). */
internal const val HUD_BLACK_HOLE_TRAIL_DEG: Double = 5.0

/**
 * Posição de um membro da órbita num instante (#602, A05). [slot] é a posição no trem: 0 é a cabeça.
 * Ângulos em radianos; [x] e [y] são o deslocamento a partir do centro do buraco.
 */
internal data class HudBlackHoleOrbitEntry(
    val index: Int,
    val slot: Int,
    val angleRad: Double,
    val x: Dp,
    val y: Dp,
    val running: Boolean,
    val trailStartRad: Double,
    val trailEndRad: Double
)

/**
 * Posição de cada membro do comboio no instante [timeMs] (#602, A05). Função pura: mesma entrada, mesma órbita.
 * Contas em execução tomam os primeiros slots, na ordem da lista; as ociosas seguem na mesma ordem. A cabeça
 * percorre a volta em [HUD_BLACK_HOLE_TURN_MS] a partir de -90 graus; cada slot fica um passo atrás do anterior.
 * O passo é 360 / n para todo n (#612, ajuste do dono): a volta inteira é dividida entre os membros, sem comboio agrupado.
 * Devolve uma entrada por membro, na ordem da lista de entrada.
 */
internal fun <T> hudBlackHoleOrbit(members: List<T>, running: (T) -> Boolean, timeMs: Long): List<HudBlackHoleOrbitEntry> {
    if (members.isEmpty()) return emptyList()
    val flags = members.map { membro -> running(membro) }
    val ordem = members.indices.filter { indice -> flags[indice] } + members.indices.filterNot { indice -> flags[indice] }
    val slotDe = IntArray(members.size)
    ordem.forEachIndexed { slot, indice -> slotDe[indice] = slot }
    val passoRad = grauParaRadiano(360.0 / members.size)
    val fracaoDaVolta = (timeMs % HUD_BLACK_HOLE_TURN_MS).toDouble() / HUD_BLACK_HOLE_TURN_MS
    val cabecaRad = -PI / 2 + 2 * PI * fracaoDaVolta
    val trilhaRad = grauParaRadiano(HUD_BLACK_HOLE_TRAIL_DEG)
    val raio = HUD_BLACK_HOLE_ORBIT_RADIUS.value.toDouble()
    return members.indices.map { indice ->
        val slot = slotDe[indice]
        val angulo = cabecaRad - slot * passoRad
        HudBlackHoleOrbitEntry(
            index = indice,
            slot = slot,
            angleRad = angulo,
            x = (raio * cos(angulo)).toFloat().dp,
            y = (raio * sin(angulo)).toFloat().dp,
            running = flags[indice],
            trailStartRad = angulo - trilhaRad,
            trailEndRad = angulo
        )
    }
}

private fun grauParaRadiano(graus: Double): Double = graus * PI / 180.0

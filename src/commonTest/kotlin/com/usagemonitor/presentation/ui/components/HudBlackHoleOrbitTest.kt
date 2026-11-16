package com.usagemonitor.presentation.ui.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Órbita em comboio do buraco negro (#602, A05): posição por instante, função pura. */
class HudBlackHoleOrbitTest {

    private fun grau(graus: Double): Double = graus * PI / 180.0

    private fun perto(esperado: Double, obtido: Double, mensagem: String) {
        assertTrue(abs(esperado - obtido) < 1e-3, "$mensagem: esperado=$esperado obtido=$obtido")
    }

    private fun perto(esperado: Dp, obtido: Dp, mensagem: String) {
        assertTrue(abs(esperado.value - obtido.value) < 1e-3f, "$mensagem: esperado=$esperado obtido=$obtido")
    }

    @Test
    fun `em t zero a cabeca fica em menos noventa graus e a conta em execucao toma o slot zero`() {
        val orbita = hudBlackHoleOrbit(listOf("A", "B", "C"), running = { nome -> nome == "B" }, timeMs = 0L)

        assertEquals(3, orbita.size, "uma posicao por membro")
        assertEquals(0, orbita[1].slot, "a conta em execucao vai para a cabeca")
        assertTrue(orbita[1].running, "a flag running acompanha o membro")
        perto(grau(-90.0), orbita[1].angleRad, "cabeca em -90 graus")
        perto(0.dp, orbita[1].x, "x da cabeca e zero")
        perto(-HUD_BLACK_HOLE_ORBIT_RADIUS, orbita[1].y, "y da cabeca e -raio da orbita")
        assertEquals(1, orbita[0].slot, "A, ociosa, fica no slot 1")
        perto(grau(-210.0), orbita[0].angleRad, "slot 1 fica 120 graus atras da cabeca (360 / 3)")
        assertEquals(2, orbita[2].slot, "C, ociosa, fica no slot 2")
        perto(grau(-330.0), orbita[2].angleRad, "slot 2 fica 240 graus atras da cabeca")
    }

    @Test
    fun `em t quinze mil a cabeca esta em zero grau e o slot seguinte em menos quarenta`() {
        val orbita = hudBlackHoleOrbit(listOf("A", "B"), running = { false }, timeMs = 15_000L)

        assertEquals(2, orbita.size, "uma posicao por membro")
        perto(grau(0.0), orbita[0].angleRad, "cabeca a um quarto da volta")
        perto(HUD_BLACK_HOLE_ORBIT_RADIUS, orbita[0].x, "x da cabeca a zero grau e o raio da orbita")
        perto(0.dp, orbita[0].y, "y da cabeca a zero grau")
        perto(grau(-180.0), orbita[1].angleRad, "slot 1 fica 180 graus atras (360 / 2)")
    }

    @Test
    fun `as contas em execucao tomam os primeiros slots na ordem de publicacao e depois as ociosas`() {
        val orbita = hudBlackHoleOrbit(
            listOf("I", "R1", "J", "R2"),
            running = { nome -> nome.startsWith("R") },
            timeMs = 0L
        )

        assertEquals(listOf(2, 0, 3, 1), orbita.map { entrada -> entrada.slot }, "I=2, R1=0, J=3, R2=1")
    }

    @Test
    fun `com dez membros o passo e de 36 graus, 360 sobre n`() {
        val orbita = hudBlackHoleOrbit((0 until 10).map { indice -> "M$indice" }, running = { false }, timeMs = 0L)
            .sortedBy { entrada -> entrada.slot }

        assertEquals(10, orbita.size, "uma posicao por membro")
        for (slot in 1 until 10) {
            perto(grau(36.0), orbita[slot - 1].angleRad - orbita[slot].angleRad, "passo entre slots ${slot - 1} e $slot")
        }
    }

    @Test
    fun `com nove membros o passo e de 40 graus, 360 sobre n`() {
        val orbita = hudBlackHoleOrbit((0 until 9).map { indice -> "M$indice" }, running = { false }, timeMs = 0L)
            .sortedBy { entrada -> entrada.slot }

        assertEquals(9, orbita.size, "uma posicao por membro")
        for (slot in 1 until 9) {
            perto(grau(40.0), orbita[slot - 1].angleRad - orbita[slot].angleRad, "passo entre slots ${slot - 1} e $slot")
        }
    }

    @Test
    fun `a trilha termina no glifo e comeca cinco graus atras`() {
        val orbita = hudBlackHoleOrbit(listOf("A", "B", "C"), running = { nome -> nome == "B" }, timeMs = 15_000L)

        assertEquals(3, orbita.size, "uma posicao por membro, cada uma com trilha")
        orbita.forEach { entrada ->
            perto(entrada.angleRad, entrada.trailEndRad, "trilha termina no glifo ${entrada.index}")
            perto(entrada.angleRad - grau(5.0), entrada.trailStartRad, "trilha comeca 5 graus atras do glifo ${entrada.index}")
        }
    }

    @Test
    fun `cada glifo orbita a 38 dp do centro em qualquer instante`() {
        for (instante in listOf(0L, 15_000L, 37_000L, 59_999L)) {
            val orbita = hudBlackHoleOrbit((0 until 10).map { indice -> "M$indice" }, running = { false }, timeMs = instante)
            assertEquals(10, orbita.size, "uma posicao por membro no instante $instante")
            orbita.forEach { entrada ->
                val raio = sqrt(entrada.x.value.toDouble() * entrada.x.value + entrada.y.value.toDouble() * entrada.y.value)
                perto(HUD_BLACK_HOLE_ORBIT_RADIUS.value.toDouble(), raio, "raio no instante $instante, membro ${entrada.index}")
            }
        }
    }

    @Test
    fun `a volta de sessenta segundos devolve as mesmas posicoes`() {
        val inicio = hudBlackHoleOrbit(listOf("A", "B", "C"), running = { nome -> nome == "B" }, timeMs = 0L)
        val volta = hudBlackHoleOrbit(listOf("A", "B", "C"), running = { nome -> nome == "B" }, timeMs = 60_000L)

        inicio.indices.forEach { posicao ->
            perto(inicio[posicao].x, volta[posicao].x, "x do membro $posicao na volta")
            perto(inicio[posicao].y, volta[posicao].y, "y do membro $posicao na volta")
        }
    }

    @Test
    fun `o raio e 38 dp e o passo e 360 graus sobre n, sem teto, para tres, sete e dez membros`() {
        for (n in listOf(3, 7, 10)) {
            val orbita = hudBlackHoleOrbit((0 until n).map { indice -> "M$indice" }, running = { false }, timeMs = 0L)
                .sortedBy { entrada -> entrada.slot }

            assertEquals(n, orbita.size, "uma posicao por membro com n=$n")
            for (slot in 1 until n) {
                perto(grau(360.0 / n), orbita[slot - 1].angleRad - orbita[slot].angleRad, "passo com n=$n entre slots ${slot - 1} e $slot")
            }
        }
        perto(38.dp, HUD_BLACK_HOLE_ORBIT_RADIUS, "raio da orbita (#612, ajuste do dono)")
    }

    @Test
    fun `sem membros nao ha orbita`() {
        assertEquals(emptyList<HudBlackHoleOrbitEntry>(), hudBlackHoleOrbit(emptyList<String>(), running = { false }, timeMs = 0L))
    }
}

package com.usagemonitor.presentation.ui.components

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Filamento de maré do buraco negro (#602, AZ2): fita pura da conta em execução até o horizonte. */
class HudBlackHoleFilamentTest {

    private val angulo = 0.4

    private fun perto(esperado: Double, obtido: Double, mensagem: String) {
        assertTrue(abs(esperado - obtido) < 1e-3, "$mensagem: esperado=$esperado obtido=$obtido")
    }

    private fun perto(esperado: Float, obtido: Float, mensagem: String) {
        perto(esperado.toDouble(), obtido.toDouble(), mensagem)
    }

    private fun raioDoFim(segmento: HudBlackHoleFilamentSegment): Double {
        val x = segmento.endX.toDouble()
        val y = segmento.endY.toDouble()
        return sqrt(x * x + y * y)
    }

    @Test
    fun `com a orbita padrao a fita tem um segmento por passo acima de 20,5 dp, ou seja 47 de 48`() {
        val fita = hudBlackHoleFilament(orbitAngleRad = angulo, timeMs = 0L)

        assertEquals(47, fita.size, "passos j=1..47 têm raio >= 20,5 (raio do passo 48 é 20, no horizonte)")
    }

    @Test
    fun `com horizonte a 30 dp todos os 48 passos viram segmento`() {
        val fita = hudBlackHoleFilament(orbitAngleRad = angulo, horizonRadius = 30f, timeMs = 0L)

        assertEquals(48, fita.size, "raio mínimo 30 >= 20,5: nenhum passo cortado")
    }

    @Test
    fun `o raio cai monotonicamente da cabeca e o ultimo segmento termina acima do horizonte`() {
        val fita = hudBlackHoleFilament(orbitAngleRad = angulo, timeMs = 0L)
        assertTrue(fita.isNotEmpty(), "fita sem segmentos")
        val raios = fita.map { segmento -> raioDoFim(segmento) }

        perto(45.0 - 25.0 / 48.0, raios.first(), "primeiro fim em s=1/48")
        for (indice in 1 until raios.size) {
            assertTrue(raios[indice] < raios[indice - 1], "raio cai no segmento $indice: ${raios[indice - 1]} -> ${raios[indice]}")
        }
        assertTrue(raios.last() >= 20.5, "último fim acima de 20,5 dp, era ${raios.last()}")
    }

    @Test
    fun `no primeiro segmento alfa, espessura e mistura seguem s e em s igual a um o alfa zera`() {
        val fitaPadrao = hudBlackHoleFilament(orbitAngleRad = angulo, timeMs = 0L)
        assertTrue(fitaPadrao.isNotEmpty(), "fita sem segmentos")
        val primeiro = fitaPadrao.first()

        perto(1f / 48f, primeiro.progress, "progresso do primeiro segmento é 1/48")
        perto((0.75 * (1.0 - 1.0 / 48.0).pow(0.8)).toFloat(), primeiro.alpha, "alfa perto da cabeça")
        perto((1.1 - 0.5 / 48.0).toFloat(), primeiro.widthDp, "espessura perto da cabeça")
        perto(primeiro.progress, primeiro.colorMix, "mistura de cor é o próprio s")

        val fita = hudBlackHoleFilament(orbitAngleRad = angulo, horizonRadius = 30f, timeMs = 0L)
        assertTrue(fita.isNotEmpty(), "fita sem segmentos")
        val ultimo = fita.last()
        perto(1f, ultimo.progress, "último segmento termina em s=1")
        perto(0f, ultimo.alpha, "alfa em s=1 é zero")
        perto(0.6f, ultimo.widthDp, "espessura em s=1 é 0,6 dp")
        perto(1f, ultimo.colorMix, "mistura em s=1 é 1")
    }

    @Test
    fun `a ondulacao nao mexe na cabeca nem no ponto final do horizonte`() {
        val cabecaT0Fita = hudBlackHoleFilament(orbitAngleRad = angulo, timeMs = 0L)
        val cabecaT7Fita = hudBlackHoleFilament(orbitAngleRad = angulo, timeMs = 7_000L)
        assertTrue(cabecaT0Fita.isNotEmpty() && cabecaT7Fita.isNotEmpty(), "fita sem segmentos")
        val cabecaT0 = cabecaT0Fita.first()
        val cabecaT7 = cabecaT7Fita.first()

        perto(45.0 * cos(angulo), cabecaT0.startX.toDouble(), "início da cabeça em x, sem ondulação")
        perto(45.0 * sin(angulo), cabecaT0.startY.toDouble(), "início da cabeça em y, sem ondulação")
        perto(cabecaT0.startX, cabecaT7.startX, "início da cabeça independe de t (x)")
        perto(cabecaT0.startY, cabecaT7.startY, "início da cabeça independe de t (y)")

        val fimT0Fita = hudBlackHoleFilament(orbitAngleRad = angulo, horizonRadius = 30f, timeMs = 0L)
        val fimT7Fita = hudBlackHoleFilament(orbitAngleRad = angulo, horizonRadius = 30f, timeMs = 7_000L)
        assertTrue(fimT0Fita.isNotEmpty() && fimT7Fita.isNotEmpty(), "fita sem segmentos")
        val fimT0 = fimT0Fita.last()
        val fimT7 = fimT7Fita.last()

        perto(30.0 * cos(angulo - 1.1), fimT0.endX.toDouble(), "fim em s=1 sem ondulação (x)")
        perto(30.0 * sin(angulo - 1.1), fimT0.endY.toDouble(), "fim em s=1 sem ondulação (y)")
        perto(fimT0.endX, fimT7.endX, "fim em s=1 independe de t (x)")
        perto(fimT0.endY, fimT7.endY, "fim em s=1 independe de t (y)")
    }

    @Test
    fun `o ponto em s igual a meio segue a formula com ondulacao em t zero`() {
        val fita = hudBlackHoleFilament(orbitAngleRad = angulo, timeMs = 0L)
        assertTrue(fita.size > 23, "fita tem de ter o segmento de s=0,5, tem ${fita.size}")
        // s = 24/48 = 0,5: raio 32,5; ângulo = a - 0,55 + 0,035 * sen(4,5 pi) * sen(0,5 pi) = a - 0,515
        val meio = fita[23]
        val anguloEsperado = angulo - 0.55 + 0.035 * sin(4.5 * PI) * sin(0.5 * PI)

        perto(32.5 * cos(anguloEsperado), meio.endX.toDouble(), "x em s=0,5")
        perto(32.5 * sin(anguloEsperado), meio.endY.toDouble(), "y em s=0,5")
    }

    @Test
    fun `mesmos argumentos devolvem a mesma fita`() {
        val primeira = hudBlackHoleFilament(orbitAngleRad = angulo, timeMs = 12_345L)
        val segunda = hudBlackHoleFilament(orbitAngleRad = angulo, timeMs = 12_345L)

        assertEquals(primeira, segunda, "função pura: mesma entrada, mesma fita")
    }

    @Test
    fun `mudar t muda a ondulacao do meio da fita`() {
        val emT0 = hudBlackHoleFilament(orbitAngleRad = angulo, timeMs = 0L)
        val emT1 = hudBlackHoleFilament(orbitAngleRad = angulo, timeMs = 1_000L)

        assertNotEquals(emT0, emT1, "a ondulação depende de t: a fita tem de mudar entre t=0 e t=1 s")
    }
}

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

    // Números de C1 (#612): órbita padrão derivada da constante de produção; horizonte de 12,4 dp, igual ao default de hudBlackHoleFilament.
    private val raioOrbita = HUD_BLACK_HOLE_ORBIT_RADIUS.value.toDouble()
    private val horizonte = 12.4

    // Corte de C1: passo cujo fim fica abaixo de 12,9 dp não vira segmento.
    private val corte = 12.9

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
    fun `com a orbita padrao a fita tem um segmento por passo acima do corte de 12,9 dp, ou seja 47 de 48`() {
        val fita = hudBlackHoleFilament(orbitAngleRad = angulo, timeMs = 0L)

        // raio(j) = R + (H - R) * j / 48 com R = 38: j = 47 fica em 12,933 dp; j = 48 cai em 12,4 dp, abaixo do corte.
        assertEquals(47, fita.size, "passos j=1..47 têm raio >= 12,9; o passo 48 (12,4 = horizonte) é cortado")
    }

    @Test
    fun `com horizonte a 30 dp todos os 48 passos viram segmento`() {
        val fita = hudBlackHoleFilament(orbitAngleRad = angulo, horizonRadius = 30f, timeMs = 0L)

        assertEquals(48, fita.size, "raio mínimo 30 >= 12,9: nenhum passo cortado")
    }

    @Test
    fun `o raio cai monotonicamente da cabeca e o ultimo segmento termina acima do corte`() {
        val fita = hudBlackHoleFilament(orbitAngleRad = angulo, timeMs = 0L)
        assertTrue(fita.isNotEmpty(), "fita sem segmentos")
        val raios = fita.map { segmento -> raioDoFim(segmento) }

        perto(raioOrbita + (horizonte - raioOrbita) / 48.0, raios.first(), "primeiro fim em s=1/48")
        for (indice in 1 until raios.size) {
            assertTrue(raios[indice] < raios[indice - 1], "raio cai no segmento $indice: ${raios[indice - 1]} -> ${raios[indice]}")
        }
        assertTrue(raios.last() >= corte, "último fim acima de 12,9 dp, era ${raios.last()}")
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

        perto(raioOrbita * cos(angulo), cabecaT0.startX.toDouble(), "início da cabeça em x, sem ondulação")
        perto(raioOrbita * sin(angulo), cabecaT0.startY.toDouble(), "início da cabeça em y, sem ondulação")
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
        // s = 24/48 = 0,5: raio 22,7 (= R + (H - R) * 0,5); ângulo = a - 0,55 + 0,035 * sen(4,5 pi) * sen(0,5 pi) = a - 0,515
        val raioMeio = raioOrbita + (horizonte - raioOrbita) * 0.5
        val meio = fita[23]
        val anguloEsperado = angulo - 0.55 + 0.035 * sin(4.5 * PI) * sin(0.5 * PI)

        perto(raioMeio * cos(anguloEsperado), meio.endX.toDouble(), "x em s=0,5")
        perto(raioMeio * sin(anguloEsperado), meio.endY.toDouble(), "y em s=0,5")
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

    @Test
    fun `a fita de mare para no corte de 12,9 dp, o menor ponto desenhado e o fim do passo 47`() {
        val distancias = (0..6).flatMap { passo -> hudBlackHoleFilament(orbitAngleRad = passo * 0.5, timeMs = 0L) }
            .flatMap { segmento ->
                listOf(
                    distanciaDoCentro(segmento.startX, segmento.startY),
                    distanciaDoCentro(segmento.endX, segmento.endY),
                )
            }
        val menor = distancias.minOrNull() ?: Float.MAX_VALUE
        // Fim do passo 47: R + (H - R) * 47 / 48 = 12,933 dp.
        val esperadoMenor = (raioOrbita + (horizonte - raioOrbita) * 47.0 / 48.0).toFloat()
        assertTrue(menor >= corte.toFloat(), "raio minimo $menor abaixo do corte de 12,9")
        perto(esperadoMenor, menor, "menor ponto desenhado é o fim do passo 47")
    }

    private fun distanciaDoCentro(x: Float, y: Float): Float = sqrt(x * x + y * y)
}

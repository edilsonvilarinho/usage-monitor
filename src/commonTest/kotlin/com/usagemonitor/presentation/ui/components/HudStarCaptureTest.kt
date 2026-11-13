package com.usagemonitor.presentation.ui.components

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A03 · #590: capturas — estrela espirala para dentro de um anel, rastro curto e lampejo na borda do núcleo. */
class HudStarCaptureTest {
    private val wells = listOf(
        HudStarfieldWell(centerX = 30f, centerY = 100f, radius = 20f),
        HudStarfieldWell(centerX = 80f, centerY = 300f, radius = 15f),
        HudStarfieldWell(centerX = 30f, centerY = 500f, radius = 25f)
    )
    private val noBoxes = emptyList<HudStarfieldBox>()

    private fun flights(list: List<HudStarCapture>) = list.filterIsInstance<HudStarCaptureFlight>()

    private fun flashes(list: List<HudStarCapture>) = list.filterIsInstance<HudStarCaptureFlash>()

    private fun distance(x: Float, y: Float, well: HudStarfieldWell): Float =
        hypot(x - well.centerX, y - well.centerY)

    /** Uma única cabeça em voo no instante [t]; falha por asserção se não houver exatamente uma. */
    private fun flightAt(t: Long, boxes: List<HudStarfieldBox> = noBoxes): HudStarCaptureFlight {
        val found = flights(hudStarCaptures(t, wells, boxes))
        assertEquals(1, found.size, "uma cabeça em voo em t=$t")
        return found.single()
    }

    /** Um único lampejo no instante [t]; falha por asserção se não houver exatamente um. */
    private fun flashAt(t: Long, boxes: List<HudStarfieldBox> = noBoxes): HudStarCaptureFlash {
        val found = flashes(hudStarCaptures(t, wells, boxes))
        assertEquals(1, found.size, "um lampejo em t=$t")
        return found.single()
    }

    @Test
    fun `nenhuma captura antes de 4000 ms`() {
        assertTrue(hudStarCaptures(3_999L, wells, noBoxes).isEmpty(), "antes da primeira captura")
    }

    @Test
    fun `sem poco nao ha captura`() {
        assertTrue(hudStarCaptures(5_300L, emptyList(), noBoxes).isEmpty(), "sem poços")
    }

    @Test
    fun `captura em 5300 ms fica entre 0,55R e 2,4R do poco 0`() {
        val head = flightAt(5_300L)
        val r = wells[0].radius
        val d = distance(head.headX, head.headY, wells[0])
        assertTrue(d > 0.55f * r && d < 2.4f * r, "cabeça entre 0,55R e 2,4R, veio d=$d com R=$r")
    }

    @Test
    fun `cabeca no meio da captura segue r(s) do plano`() {
        val head = flightAt(5_300L)
        val r = wells[0].radius
        val s = 0.5f
        val expected = 2.4f * r * (1f - s).pow(1.6f) + 0.55f * r * s
        val d = distance(head.headX, head.headY, wells[0])
        assertTrue(abs(d - expected) < 0.01f * r, "r(0,5) esperado $expected, veio d=$d")
    }

    @Test
    fun `cabeca comeca a 2,4R do poco`() {
        val head = flightAt(4_000L)
        val r = wells[0].radius
        val d = distance(head.headX, head.headY, wells[0])
        assertTrue(abs(d - 2.4f * r) < 0.01f * r, "início a 2,4R, veio d=$d")
    }

    @Test
    fun `cabeca termina em 0,55R no fim da captura`() {
        val head = flightAt(4_000L + 2_599L)
        val r = wells[0].radius
        val d = distance(head.headX, head.headY, wells[0])
        assertTrue(abs(d - 0.55f * r) < 0.01f * r, "fim em 0,55R, veio d=$d")
    }

    @Test
    fun `alfa da cabeca comeca em zero e acende no meio da captura`() {
        val start = flightAt(4_000L)
        assertTrue(start.headAlpha < 1e-4f, "alfa zero no início, veio ${start.headAlpha}")
        val mid = flightAt(5_300L)
        assertTrue(abs(mid.headAlpha - 1f) < 1e-4f, "alfa cheio no meio, veio ${mid.headAlpha}")
    }

    @Test
    fun `rastro tem ao menos um ponto e no maximo 10 dp do caminho`() {
        val head = flightAt(5_300L)
        assertTrue(head.trail.isNotEmpty(), "rastro presente no meio da captura")
        val far = head.trail.maxOf { hypot(it.x - head.headX, it.y - head.headY) }
        assertTrue(far <= 10.01f, "rastro até 10 dp, veio $far")
    }

    @Test
    fun `rastro nasce com alfa de no maximo 0,9`() {
        val head = flightAt(5_300L)
        val top = head.trail.maxOf { it.alpha }
        assertTrue(top <= 0.9f + 1e-4f, "alfa do rastro até 0,9, veio $top")
    }

    @Test
    fun `lampejo so existe entre 2600 e 2800 ms de idade`() {
        assertTrue(flashes(hudStarCaptures(6_599L, wells, noBoxes)).isEmpty(), "sem lampejo antes de 2600 ms")
        assertEquals(1, flashes(hudStarCaptures(6_600L, wells, noBoxes)).size, "lampejo em 2600 ms")
        assertEquals(1, flashes(hudStarCaptures(6_799L, wells, noBoxes)).size, "lampejo até 2799 ms")
        assertTrue(hudStarCaptures(6_800L, wells, noBoxes).isEmpty(), "fora da janela em 2800 ms")
    }

    @Test
    fun `lampejo comeca em alfa 0,6 com raio 5`() {
        val flash = flashAt(6_600L)
        assertTrue(abs(flash.alpha - 0.6f) < 1e-4f, "alfa inicial do lampejo, veio ${flash.alpha}")
        assertTrue(abs(flash.radius - 5f) < 1e-4f, "raio do lampejo, veio ${flash.radius}")
    }

    @Test
    fun `capturas rodam entre os pocos na ordem n mod tamanho`() {
        val starts = listOf(4_000L, 12_000L, 20_000L, 28_000L)
        val expectedWell = listOf(0, 1, 2, 0)
        starts.forEachIndexed { i, start ->
            val head = flightAt(start + 1_300L)
            val w = wells[expectedWell[i]]
            val d = distance(head.headX, head.headY, w)
            assertTrue(
                d > 0.55f * w.radius && d < 2.4f * w.radius,
                "captura $i no poço ${expectedWell[i]}, veio d=$d"
            )
        }
    }

    @Test
    fun `ponto dentro de caixa proibida tem alfa zero`() {
        val everything = listOf(HudStarfieldBox(left = 0f, top = 0f, right = 200f, bottom = 600f))
        val head = flightAt(5_300L, everything)
        assertTrue(head.headAlpha == 0f, "cabeça em caixa proibida, alfa ${head.headAlpha}")
        assertTrue(head.trail.all { it.alpha == 0f }, "rastro em caixa proibida com alfa não zero")
        val flash = flashAt(6_600L, everything)
        assertTrue(flash.alpha == 0f, "lampejo em caixa proibida, alfa ${flash.alpha}")
    }
}

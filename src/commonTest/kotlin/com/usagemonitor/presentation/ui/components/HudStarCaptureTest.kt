package com.usagemonitor.presentation.ui.components

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A03 · #590/#592: capturas ociosas — estrela espirala para dentro de um anel, rastro curto e lampejo na borda do núcleo. */
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
    private fun flightAt(
        t: Long,
        boxes: List<HudStarfieldBox> = noBoxes,
        wellList: List<HudStarfieldWell> = wells
    ): HudStarCaptureFlight {
        val found = flights(hudStarCaptures(t, wellList, boxes))
        assertEquals(1, found.size, "uma cabeça em voo em t=$t")
        return found.single()
    }

    /** Um único lampejo no instante [t]; falha por asserção se não houver exatamente um. */
    private fun flashAt(t: Long, boxes: List<HudStarfieldBox> = noBoxes): HudStarCaptureFlash {
        val found = flashes(hudStarCaptures(t, wells, boxes))
        assertEquals(1, found.size, "um lampejo em t=$t")
        return found.single()
    }

    /** Cabeça em voo no instante [t] tem de estar no anel [well] (entre 0,55R e 2,4R do centro). */
    private fun assertFlightOnWell(t: Long, well: HudStarfieldWell, wellList: List<HudStarfieldWell>, label: String) {
        val head = flightAt(t, wellList = wellList)
        val d = distance(head.headX, head.headY, well)
        assertTrue(
            d > 0.55f * well.radius && d < 2.4f * well.radius,
            "$label: cabeça no anel esperado, veio d=$d com R=${well.radius}"
        )
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
        val head = flightAt(5_500L)
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
    fun `cabeca termina em 0,55R no fim do voo de 3000 ms`() {
        val head = flightAt(4_000L + 2_999L)
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
    fun `lampejo so existe entre 3000 e 3200 ms de idade`() {
        assertTrue(flashes(hudStarCaptures(6_999L, wells, noBoxes)).isEmpty(), "sem lampejo antes de 3000 ms")
        assertEquals(1, flashes(hudStarCaptures(7_000L, wells, noBoxes)).size, "lampejo em 3000 ms")
        assertEquals(1, flashes(hudStarCaptures(7_199L, wells, noBoxes)).size, "lampejo até 3199 ms")
        assertTrue(hudStarCaptures(7_200L, wells, noBoxes).isEmpty(), "fora da janela em 3200 ms")
    }

    @Test
    fun `lampejo comeca em alfa 0,6 com raio 5`() {
        val flash = flashAt(7_000L)
        assertTrue(abs(flash.alpha - 0.6f) < 1e-4f, "alfa inicial do lampejo, veio ${flash.alpha}")
        assertTrue(abs(flash.radius - 5f) < 1e-4f, "raio do lampejo, veio ${flash.radius}")
    }

    @Test
    fun `capturas ociosas caem a cada 30000 ms a partir de 4000 ms`() {
        val starts = listOf(4_000L, 34_000L, 64_000L, 94_000L)
        starts.forEach { start -> flightAt(start + 1_300L) }
        flightAt(4_000L + 240_000L + 1_300L)
    }

    @Test
    fun `capturas rodam entre os pocos na ordem n mod tamanho`() {
        val starts = listOf(4_000L, 34_000L, 64_000L, 94_000L)
        val expectedWell = listOf(0, 1, 2, 0)
        starts.forEachIndexed { i, start ->
            assertFlightOnWell(start + 1_300L, wells[expectedWell[i]], wells, "captura $i no poço ${expectedWell[i]}")
        }
    }

    @Test
    fun `poco em execucao nao recebe captura ociosa`() {
        val wellList = listOf(
            wells[0].copy(activeSinceMs = 0L, activeUntilMs = null),
            wells[1],
            wells[2]
        )
        assertFlightOnWell(5_300L, wells[1], wellList, "captura ociosa no poço 1, poço 0 em execução")
    }

    @Test
    fun `rodizio so pelos pocos ociosos`() {
        val wellList = listOf(
            wells[0].copy(activeSinceMs = 0L, activeUntilMs = null),
            wells[1],
            wells[2]
        )
        val expectedWell = listOf(1, 2, 1, 2)
        val starts = listOf(4_000L, 34_000L, 64_000L, 94_000L)
        starts.forEachIndexed { i, start ->
            assertFlightOnWell(start + 1_300L, wellList[expectedWell[i]], wellList, "captura $i no poço ocioso ${expectedWell[i]}")
        }
    }

    @Test
    fun `nenhum poco ocioso nao ha captura ociosa`() {
        val wellList = listOf(wells[0].copy(activeSinceMs = 0L, activeUntilMs = null))
        assertTrue(hudStarCaptures(5_300L, wellList, noBoxes).isEmpty(), "único poço em execução, nenhuma captura")
        assertTrue(hudStarCaptures(65_300L, wellList, noBoxes).isEmpty(), "único poço em execução, nenhuma captura em 65 300 ms")
    }

    @Test
    fun `janela de execucao e semiaberta no fim`() {
        val wellList = listOf(wells[0].copy(activeSinceMs = 34_000L, activeUntilMs = 64_000L))
        assertTrue(hudStarCaptures(35_300L, wellList, noBoxes).isEmpty(), "poço em execução em 34 000 ms não recebe captura")
        flightAt(65_300L, wellList = wellList)
    }

    @Test
    fun `ponto dentro de caixa proibida tem alfa zero`() {
        val everything = listOf(HudStarfieldBox(left = 0f, top = 0f, right = 200f, bottom = 600f))
        val head = flightAt(5_300L, everything)
        assertTrue(head.headAlpha == 0f, "cabeça em caixa proibida, alfa ${head.headAlpha}")
        assertTrue(head.trail.all { it.alpha == 0f }, "rastro em caixa proibida com alfa não zero")
        val flash = flashAt(7_000L, everything)
        assertTrue(flash.alpha == 0f, "lampejo em caixa proibida, alfa ${flash.alpha}")
    }

    @Test
    fun `execucao usa tempo absoluto do quadro e nao o do laco`() {
        // Poço 0 em execução desde 241 000 ms absolutos; captura n=0 começa em 244 000 ms absolutos (laço 2).
        val timeMs = 240_000L + 4_000L + 1_300L
        val wellList = listOf(
            wells[0].copy(activeSinceMs = 240_000L + 1_000L, activeUntilMs = null),
            wells[1],
            wells[2]
        )
        assertFlightOnWell(timeMs, wells[1], wellList, "poço 0 em execução no tempo absoluto, captura deve ir ao poço 1")
    }

    @Test
    fun `execucao absoluta exclui o poço 0 no segundo laço no rodizio`() {
        // Captura n=1 começa em 274 000 ms absolutos; poço 0 em execução, ociosos [1, 2], idle[1] = poço 2.
        val timeMs = 240_000L + 34_000L
        val wellList = listOf(
            wells[0].copy(activeSinceMs = 240_000L + 1_000L, activeUntilMs = null),
            wells[1],
            wells[2]
        )
        assertFlightOnWell(timeMs, wells[2], wellList, "poço 0 em execução, rodízio entre ociosos [1,2] deve dar poço 2")
    }
}

package com.usagemonitor

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Geometria pura do campo orbital do modo flutuante (issue #619, A04).
 *
 * Padrões do dono em `prototipos/issue-619/parametros-dono.json`. O crescimento do campo
 * segue o `fitRadius` do protótipo R2 (`prototipos/issue-619/r2-orbita-radial/index.html`),
 * com a largura dos rótulos como entrada: a geometria não mede texto.
 */
class HudOrbitGeometryTest {

    private val labelWidths = HudOrbitLabelWidths(complete = 60f, compact = 30f)

    /** Padrões do dono: se um valor mudar sem o JSON, o modo deixa de ser o aprovado. */
    @Test
    fun `defaults are the owner values`() {
        val p = HudOrbitParams()
        assertEquals(180.0, p.fieldD.toDouble(), "fieldD")
        assertEquals(40.0, p.orbR.toDouble(), "orbR")
        assertEquals(28.0, p.accD.toDouble(), "accD")
        assertEquals(8.0, p.markD.toDouble(), "markD")
        assertEquals(32.0, p.holeD.toDouble(), "holeD")
        assertEquals(29.0, p.tiltPercent.toDouble(), "tilt")
        assertEquals(3.0, p.diskWidthPercent.toDouble(), "diskW")
        assertEquals(3.5, p.diskSpinSeconds.toDouble(), "spin")
        assertEquals(180.0, p.orbitPeriodSeconds.toDouble(), "orbS")
        assertEquals(100.0, p.backgroundAlphaPercent.toDouble(), "bgA")
        assertEquals(100.0, p.dustDensityPercent.toDouble(), "dustD")
        assertEquals(200.0, p.dustBrightnessPercent.toDouble(), "dustB")
        assertEquals(0.0, p.borderWidth.toDouble(), "borderW")
        assertEquals(HudOrbitLabels.FULL, p.labels, "rotulos")
        assertEquals(8.0, p.textSp.toDouble(), "txt")
    }

    /** Sentido horário na tela (y para baixo): a primeira conta no topo, depois direita, base, esquerda. */
    @Test
    fun `first account starts at minus 90 degrees and the ring runs clockwise`() {
        val layout = hudOrbitLayout(HudOrbitParams(), 4, labelWidths, 0f)
        assertEquals(listOf(-90.0, 0.0, 90.0, 180.0), layout.accounts.map { it.angleDegrees.toDouble() }, "angulos")
        assertNear(0f, layout.accounts[0].x, "x0")
        assertNear(-40f, layout.accounts[0].y, "y0")
        assertNear(40f, layout.accounts[1].x, "x1")
        assertNear(0f, layout.accounts[1].y, "y1")
        assertNear(0f, layout.accounts[2].x, "x2")
        assertNear(40f, layout.accounts[2].y, "y2")
        assertNear(-40f, layout.accounts[3].x, "x3")
        assertNear(0f, layout.accounts[3].y, "y3")
    }

    /** Passo de 360/n entre contas consecutivas, para n ímpar também. */
    @Test
    fun `angular step is 360 divided by the account count`() {
        for (n in listOf(3, 5)) {
            val angles = hudOrbitLayout(HudOrbitParams(), n, labelWidths, 0f).accounts.map { it.angleDegrees }
            assertEquals(n, angles.size, "quantidade para n=$n")
            for (i in 1 until n) {
                assertNear(360f / n, angles[i] - angles[i - 1], "passo entre $i e ${i - 1} para n=$n")
            }
        }
    }

    /** A ordem das contas é a ordem da lista, sem reordenar por nome, cor ou valor. */
    @Test
    fun `slots keep the list order`() {
        val layout = hudOrbitLayout(HudOrbitParams(), 5, labelWidths, 0f)
        assertEquals(listOf(0, 1, 2, 3, 4), layout.accounts.map { it.index }, "ordem")
    }

    /** A fase da órbita gira o anel: 0,25 de volta adiciona 90 graus; uma volta inteira é a mesma posição. */
    @Test
    fun `orbit phase rotates the ring and a full turn is the same layout`() {
        val shifted = hudOrbitLayout(HudOrbitParams(), 4, labelWidths, 0.25f)
        assertEquals(4, shifted.accounts.size, "quantidade com fase")
        assertNear(0f, shifted.accounts[0].angleDegrees, "fase 0,25 conta 0")
        assertNear(90f, shifted.accounts[1].angleDegrees, "fase 0,25 conta 1")
        assertEquals(
            hudOrbitLayout(HudOrbitParams(), 4, labelWidths, 0f),
            hudOrbitLayout(HudOrbitParams(), 4, labelWidths, 1f),
            "volta inteira",
        )
    }

    /** Sem contas não há rótulo para medir: o campo fica no tamanho pedido. */
    @Test
    fun `no accounts means no slots and no growth`() {
        val layout = hudOrbitLayout(HudOrbitParams(), 0, labelWidths, 0f)
        assertEquals(emptyList(), layout.accounts, "contas")
        assertNear(180f, layout.fieldDiameter, "campo sem contas")
    }

    /** Rótulo compacto (só a cota) cabe no campo padrão: não cresce. */
    @Test
    fun `compact labels keep the owner field size`() {
        val layout = hudOrbitLayout(HudOrbitParams(labels = HudOrbitLabels.COMPACT), 4, labelWidths, 0f)
        assertNear(180f, layout.fieldDiameter, "campo compacto")
    }

    /** Rótulo completo (nome, cota e estado) cresce até caber: 262 dp pelo `fitRadius` do R2 com 60 dp de largura. */
    @Test
    fun `complete labels grow the field to fit`() {
        val layout = hudOrbitLayout(HudOrbitParams(), 4, labelWidths, 0f)
        assertNear(262f, layout.fieldDiameter, "campo completo")
    }

    /** Mesmo com rótulo estreito o completo cresce: 198 dp com 10 dp de largura (fitRadius 99). */
    @Test
    fun `complete labels grow even when narrow`() {
        val layout = hudOrbitLayout(HudOrbitParams(), 4, HudOrbitLabelWidths(complete = 10f, compact = 10f), 0f)
        assertNear(198f, layout.fieldDiameter, "campo completo estreito")
    }

    /** Crescimento depende da largura e não da quantidade de contas. */
    @Test
    fun `field growth does not depend on the account count`() {
        val one = hudOrbitLayout(HudOrbitParams(), 1, labelWidths, 0f).fieldDiameter
        val six = hudOrbitLayout(HudOrbitParams(), 6, labelWidths, 0f).fieldDiameter
        assertNear(one, six, "um e seis contas")
    }

    /** Rótulos desligados não medem nada: campo no tamanho pedido mesmo com largura completa. */
    @Test
    fun `labels off keep the owner field size`() {
        val layout = hudOrbitLayout(HudOrbitParams(labels = HudOrbitLabels.OFF), 4, labelWidths, 0f)
        assertNear(180f, layout.fieldDiameter, "campo desligado")
    }

    /** O buraco minimizado é o `holeD`, independente das contas. */
    @Test
    fun `hole diameter is the owner hole size`() {
        assertNear(32f, hudOrbitLayout(HudOrbitParams(), 3, labelWidths, 0f).holeDiameter, "buraco")
    }

    /** Mesma entrada, mesma saída, mesmo com outra chamada no meio. */
    @Test
    fun `same input gives the same layout`() {
        val first = hudOrbitLayout(HudOrbitParams(), 5, labelWidths, 0.37f)
        hudOrbitLayout(HudOrbitParams(labels = HudOrbitLabels.COMPACT), 2, labelWidths, 0.9f)
        val second = hudOrbitLayout(HudOrbitParams(), 5, labelWidths, 0.37f)
        assertEquals(first, second, "determinismo")
    }

    private fun assertNear(expected: Float, actual: Float, message: String) {
        assertEquals(expected.toDouble(), actual.toDouble(), 0.01, message)
    }
}

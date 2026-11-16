package com.usagemonitor

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.presentation.ui.HudAccount
import com.usagemonitor.presentation.ui.HudQuota
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.components.HUD_BLACK_HOLE_GLYPH_EXTENT
import com.usagemonitor.presentation.ui.components.HUD_BLACK_HOLE_GLYPH_SIZE
import com.usagemonitor.presentation.ui.components.HUD_BLACK_HOLE_ORBIT_RADIUS
import com.usagemonitor.presentation.ui.components.hudBlackHoleOrbit
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Geometria da coluna vertical com o buraco negro das minimizadas (issue #600, A03; #602, A05). */
class HudBlackHoleGeometryTest {

    private val fallback = "Carregando"

    @Test
    fun `quatro minimizadas e tres completas medem tres celulas mais uma do buraco`() {
        val completas = listOf(conta("A"), conta("B"), conta("C"))
        val minimizadas = listOf(
            conta("D", minimizada = true),
            conta("E", minimizada = true),
            conta("F", minimizada = true),
            conta("G", minimizada = true)
        )

        val base = hudNotchSizes(completas, HudEdge.LEFT, fallback, hasUpdateIndicator = false).collapsed.height
        val comBuraco = hudNotchSizes(completas + minimizadas, HudEdge.LEFT, fallback, hasUpdateIndicator = false).collapsed.height

        // Buraco = uma célula (órbita + glifo, #602), mais o vão até ela. Sem linha de contagem na coluna (C1, #612).
        assertEquals(base + HUD_BLACK_HOLE_CELL_SIZE + HUD_ITEM_GAP, comBuraco)
    }

    @Test
    fun `sem minimizadas a altura da coluna nao muda`() {
        val contas = listOf(conta("A"), conta("B"), conta("C"))

        val altura = hudNotchSizes(contas, HudEdge.LEFT, fallback, hasUpdateIndicator = false).collapsed.height

        assertEquals(verticalAlturaEsperadaSemBuraco(contas), altura)
    }

    @Test
    fun `so minimizadas mantem o piso de 64 dp da largura`() {
        val contas = listOf(conta("A", minimizada = true), conta("B", minimizada = true))

        val largura = hudNotchSizes(contas, HudEdge.LEFT, fallback, hasUpdateIndicator = false).collapsed.width

        assertTrue(largura >= HUD_RING_SIZE + HUD_NOTCH_PADDING_ACROSS * 2, "largura=$largura")
    }

    @Test
    fun `com buraco a regra de auto-minimizar ainda converge no teto`() {
        val contas = listOf(
            conta("A", 0.9f),
            conta("B", 0.8f),
            conta("C", 0.3f),
            conta("D", 0.1f),
            conta("E", 0.05f)
        )
        val teto: Dp = hudNotchSizes(
            contas.map { c -> if (c.label in setOf("C", "D", "E")) c.copy(minimized = true) else c },
            HudEdge.LEFT, fallback, hasUpdateIndicator = false
        ).collapsed.height

        val aplicadas = applyHudAutoMinimize(contas, HudEdge.LEFT, fallback, teto)
        val altura = hudNotchSizes(aplicadas, HudEdge.LEFT, fallback, hasUpdateIndicator = false).collapsed.height

        assertTrue(altura <= teto, "altura=$altura teto=$teto")
    }

    @Test
    fun `a celula do buraco mede 106 dp, a orbita de 38 dp mais o glifo de 15 dp para cada lado`() {
        assertEquals(38.dp, HUD_BLACK_HOLE_ORBIT_RADIUS)
        assertEquals(22.dp, HUD_BLACK_HOLE_GLYPH_SIZE)
        assertEquals(15.dp, HUD_BLACK_HOLE_GLYPH_EXTENT)
        assertEquals(106.dp, HUD_BLACK_HOLE_CELL_SIZE)
    }

    @Test
    fun `a celula contem a orbita e o glifo em qualquer instante`() {
        val tolerancia = 0.001f
        val metade = HUD_BLACK_HOLE_CELL_SIZE.value / 2f + tolerancia
        val folga = 15f
        for (instante in listOf(0L, 15_000L, 37_000L, 59_999L)) {
            val orbita = hudBlackHoleOrbit((0 until 10).map { indice -> "M$indice" }, running = { false }, timeMs = instante)
            assertEquals(10, orbita.size, "uma posicao por membro no instante $instante")
            orbita.forEach { entrada ->
                assertTrue(abs(entrada.x.value) + folga <= metade, "x=${entrada.x} no instante $instante")
                assertTrue(abs(entrada.y.value) + folga <= metade, "y=${entrada.y} no instante $instante")
            }
        }
    }

    @Test
    fun `com buraco a largura da coluna cabe a celula inteira`() {
        val completas = listOf(conta("A"), conta("B"), conta("C"))
        val minimizadas = (1..4).map { indice -> conta("M$indice", minimizada = true) }

        val largura = hudNotchSizes(completas + minimizadas, HudEdge.LEFT, fallback, hasUpdateIndicator = false).collapsed.width

        // A célula de 106 dp cabe na coluna: a largura da coluna não é menor que ela.
        assertTrue(largura >= HUD_BLACK_HOLE_CELL_SIZE, "largura=$largura celula=$HUD_BLACK_HOLE_CELL_SIZE")
    }

    /** Altura de referência: a coluna sem nenhuma minimizada, medida pela mesma função. */
    private fun verticalAlturaEsperadaSemBuraco(contas: List<HudAccount>): Dp =
        verticalCollapsed(contas, fallback, compact = false).height

    private fun conta(nome: String, vararg usos: Float, minimizada: Boolean = false): HudAccount {
        return HudAccount(
            targetKey = UsageTargetKey(ApiSource.ANTHROPIC, nome),
            label = nome,
            statusLabel = "Em dia",
            tone = AppTone.OK,
            quotas = usos.map { uso ->
                HudQuota("5h", "${(uso * 100).toInt()}%", uso, AppTone.OK, resetText = null, hasForecast = true)
            },
            focusIndex = 0,
            minimized = minimizada
        )
    }

    @Test
    fun `a lista do buraco mede cabecalho mais uma linha por conta`() {
        assertEquals(HUD_BLACK_HOLE_LIST_HEADER + HUD_BLACK_HOLE_LIST_ROW * 8, hudBlackHoleListHeight(8))
    }

    @Test
    fun `o balao do buraco mede pela maior entre a lista a conta mais alta com a faixa e a engrenagem`() {
        val alta = conta("Alta", 0.1f, 0.1f, 0.1f, 0.1f, 0.1f, 0.1f, 0.1f, 0.1f, minimizada = true)
        val minimizadas = listOf(alta) + (1..7).map { indice -> conta("M$indice", 0.2f, minimizada = true) }
        val maiorConta = minimizadas.maxOf { cartao -> hudBalloonHeight(cartao) }
        val esperado = maxOf(hudBlackHoleListHeight(8), maiorConta + HUD_BLACK_HOLE_BACK_STRIP, hudAppBalloonHeight(false, false))

        val altura = hudNotchSizes(minimizadas, HudEdge.LEFT, fallback, hasUpdateIndicator = false).balloon.height

        assertEquals(esperado, altura, "o balao do buraco deve medir a maior entre a lista, a conta mais alta com a faixa de volta e a engrenagem")
    }

    @Test
    fun `conta auto minimizada do buraco soma a nota de volta sozinha ao balao`() {
        val auto = conta("Auto", 0.1f, 0.1f, 0.1f, 0.1f, 0.1f, 0.1f, 0.1f, 0.1f, minimizada = true).copy(autoMinimized = true)
        val minimizadas = listOf(auto) + (1..7).map { indice -> conta("M$indice", 0.2f, minimizada = true) }
        val esperado = maxOf(
            hudBlackHoleListHeight(8),
            hudBalloonHeight(auto) + HUD_BLACK_HOLE_BACK_STRIP + HUD_BLACK_HOLE_AUTO_NOTE,
            hudAppBalloonHeight(false, false)
        )

        val altura = hudNotchSizes(minimizadas, HudEdge.LEFT, fallback, hasUpdateIndicator = false).balloon.height

        assertEquals(esperado, altura, "a nota da auto-minimizada precisa entrar na altura do balao do buraco")
    }
}

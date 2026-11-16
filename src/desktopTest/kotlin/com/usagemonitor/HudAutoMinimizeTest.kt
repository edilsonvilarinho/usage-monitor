package com.usagemonitor

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.presentation.ui.HudAccount
import com.usagemonitor.presentation.ui.HudQuota
import com.usagemonitor.presentation.ui.components.AppTone
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Auto-minimizar contas excedentes na borda vertical (issue #596). */
class HudAutoMinimizeTest {

    private val fallback = "Carregando"

    @Test
    fun `borda horizontal nunca auto-minimiza, mesmo sem caber`() {
        val contas = listOf(conta("A", 0.9f), conta("B", 0.1f))

        assertEquals(emptySet<UsageTargetKey>(), hudAutoMinimizedTargets(contas, HudEdge.TOP, fallback, 1.dp))
        assertEquals(emptySet<UsageTargetKey>(), hudAutoMinimizedTargets(contas, HudEdge.BOTTOM, fallback, 1.dp))
    }

    @Test
    fun `coluna que ja cabe no teto nao minimiza nenhuma conta`() {
        val contas = listOf(conta("A", 0.9f), conta("B", 0.1f))
        val teto = alturaCom(contas, emptySet())

        assertEquals(emptySet<UsageTargetKey>(), hudAutoMinimizedTargets(contas, HudEdge.LEFT, fallback, teto))
    }

    @Test
    fun `sem cota conta como zero e sai antes da conta de maior uso`() {
        // Com o buraco de 236 dp, a coluna só diminui a partir de três contas recolhidas: por isso são três sem cota.
        val a = conta("A", 0.9f)
        val b = conta("B", 0.6f)
        val c1 = conta("C1")
        val c2 = conta("C2")
        val c3 = conta("C3")
        val contas = listOf(a, b, c1, c2, c3)

        val tresSemCota = alturaCom(contas, setOf(c1.targetKey, c2.targetKey, c3.targetKey))
        assertEquals(
            setOf(c1.targetKey, c2.targetKey, c3.targetKey),
            hudAutoMinimizedTargets(contas, HudEdge.LEFT, fallback, tresSemCota)
        )

        val quatro = alturaCom(contas, setOf(c1.targetKey, c2.targetKey, c3.targetKey, b.targetKey))
        assertEquals(
            setOf(c1.targetKey, c2.targetKey, c3.targetKey, b.targetKey),
            hudAutoMinimizedTargets(contas, HudEdge.LEFT, fallback, quatro)
        )
    }

    @Test
    fun `o uso da conta e a maior cota, nao a primeira`() {
        // Pela maior cota a ordem é C (0,3), D (0,4), B (0,6), A (0,9). Pela primeira cota A (0,1) sairia antes.
        val a = conta("A", 0.1f, 0.9f)
        val b = conta("B", 0.6f)
        val c = conta("C", 0.3f)
        val d = conta("D", 0.4f)
        val contas = listOf(a, b, c, d)

        val teto = alturaCom(contas, setOf(c.targetKey, d.targetKey, b.targetKey))

        assertEquals(
            setOf(c.targetKey, d.targetKey, b.targetKey),
            hudAutoMinimizedTargets(contas, HudEdge.LEFT, fallback, teto)
        )
    }

    @Test
    fun `empate de uso sai a primeira da lista`() {
        val a = conta("A", 0.5f)
        val b = conta("B", 0.5f)
        val c = conta("C", 0.5f)
        val d = conta("D", 0.5f)
        val contas = listOf(a, b, c, d)

        val teto = alturaCom(contas, setOf(a.targetKey, b.targetKey, c.targetKey))

        assertEquals(
            setOf(a.targetKey, b.targetKey, c.targetKey),
            hudAutoMinimizedTargets(contas, HudEdge.LEFT, fallback, teto)
        )
    }

    @Test
    fun `ja minimizada pelo usuario fica fora da escolha, mesmo com uso zero`() {
        val x = conta("X", minimizada = true)
        val y = conta("Y", 0.6f)
        val z = conta("Z", 0.9f)
        val contas = listOf(x, y, z)

        val teto = alturaCom(contas, setOf(y.targetKey))

        assertEquals(setOf(y.targetKey), hudAutoMinimizedTargets(contas, HudEdge.LEFT, fallback, teto))
    }

    @Test
    fun `se nem todas minimizadas cabem nao minimiza nenhuma`() {
        val a = conta("A", 0.2f)
        val b = conta("B", 0.7f)
        val contas = listOf(a, b)

        val teto = alturaCom(contas, setOf(a.targetKey, b.targetKey)) - 1.dp

        assertEquals(emptySet<UsageTargetKey>(), hudAutoMinimizedTargets(contas, HudEdge.LEFT, fallback, teto))
    }

    @Test
    fun `applyHudAutoMinimize marca so as contas escolhidas, com as duas flags`() {
        val a = conta("A", 0.9f)
        val b = conta("B", 0.6f)
        val c1 = conta("C1")
        val c2 = conta("C2")
        val c3 = conta("C3")
        val contas = listOf(a, b, c1, c2, c3)
        val teto = alturaCom(contas, setOf(c1.targetKey, c2.targetKey, c3.targetKey))

        val aplicadas = applyHudAutoMinimize(contas, HudEdge.LEFT, fallback, teto)

        assertEquals(
            listOf(false to false, false to false, true to true, true to true, true to true),
            aplicadas.map { conta -> conta.minimized to conta.autoMinimized }
        )
    }

    @Test
    fun `conta minimizada pelo usuario continua com autoMinimized falso`() {
        val x = conta("X", minimizada = true)
        val y = conta("Y", 0.6f)
        val z = conta("Z", 0.9f)
        val contas = listOf(x, y, z)
        val teto = alturaCom(contas, setOf(y.targetKey))

        val aplicadas = applyHudAutoMinimize(contas, HudEdge.LEFT, fallback, teto)

        assertEquals(false, aplicadas.first { conta -> conta.targetKey == x.targetKey }.autoMinimized)
        assertEquals(true, aplicadas.first { conta -> conta.targetKey == y.targetKey }.autoMinimized)
    }

    @Test
    fun `borda horizontal devolve as contas sem marcar nenhuma`() {
        val contas = listOf(conta("A", 0.9f), conta("B", 0.1f))

        val aplicadas = applyHudAutoMinimize(contas, HudEdge.TOP, fallback, 1.dp)

        assertEquals(listOf(false, false), aplicadas.map { conta -> conta.autoMinimized })
    }

    @Test
    fun `teto ao longo da borda e a altura util, ou a largura na horizontal, sobre a escala e a fracao`() {
        val area = ScreenWorkArea(0.dp, 0.dp, DpSize(800.dp, 1000.dp))

        assertAproximado(400.dp, hudMaxAlong(HudEdge.LEFT, area, 2f))
        assertAproximado(180.dp, hudMaxAlong(HudEdge.TOP, area, 2f))
    }

    private fun assertAproximado(esperado: Dp, obtido: Dp) {
        assertTrue(abs(esperado.value - obtido.value) < 0.01f, "esperado $esperado, veio $obtido")
    }

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

    /**
     * Altura da coluna com as contas de [minimizadas] recolhidas, pela mesma conta que a regra usa
     * (`verticalCollapsed`, compacto desligado): o teto de cada cenário sai daqui, nunca de número fixo.
     */
    private fun alturaCom(contas: List<HudAccount>, minimizadas: Set<UsageTargetKey>): Dp {
        val ajustadas = contas.map { conta ->
            if (conta.targetKey in minimizadas) conta.copy(minimized = true) else conta
        }
        return verticalCollapsed(ajustadas, fallback, compact = false).height
    }
}

package com.usagemonitor

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.presentation.ui.HudAccount
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.hudMinimizeMillis
import com.usagemonitor.presentation.ui.hudMinimizeProgress
import com.usagemonitor.presentation.ui.theme.AppGargantuaTokens
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Arranjo da coluna com o buraco negro das contas minimizadas (issue #600). */
class HudBlackHoleTest {

    @Test
    fun `vertical sem minimizada nao tem buraco e mantem todas as contas`() {
        val contas = listOf(conta("A"), conta("B"))

        val arranjo = hudColumnArrangement(contas, HudEdge.LEFT)

        assertEquals(listOf(0, 1), arranjo.shown)
        assertEquals(emptyList(), arranjo.holeMembers)
        assertFalse(arranjo.hasHole)
    }

    @Test
    fun `vertical com uma minimizada tira so ela da coluna e a leva ao buraco`() {
        val contas = listOf(conta("A"), conta("B", minimizada = true), conta("C"))

        val arranjo = hudColumnArrangement(contas, HudEdge.RIGHT)

        assertEquals(listOf(0, 2), arranjo.shown)
        assertEquals(listOf(1), arranjo.holeMembers)
        assertTrue(arranjo.hasHole)
    }

    @Test
    fun `vertical com quatro minimizadas junta as quatro no buraco e deixa as completas na coluna`() {
        val contas = listOf(
            conta("A", minimizada = true),
            conta("B"),
            conta("C", minimizada = true),
            conta("D", minimizada = true),
            conta("E"),
            conta("F", minimizada = true)
        )

        val arranjo = hudColumnArrangement(contas, HudEdge.LEFT)

        assertEquals(listOf(1, 4), arranjo.shown)
        assertEquals(listOf(0, 2, 3, 5), arranjo.holeMembers)
        assertTrue(arranjo.hasHole)
    }

    @Test
    fun `automatica e manual entram no mesmo buraco na ordem da lista`() {
        val contas = listOf(
            conta("A", automatica = true),
            conta("B"),
            conta("C", minimizada = true),
            conta("D", automatica = true),
            conta("E")
        )

        val arranjo = hudColumnArrangement(contas, HudEdge.LEFT)

        assertEquals(listOf(1, 4), arranjo.shown)
        assertEquals(listOf(0, 2, 3), arranjo.holeMembers)
    }

    @Test
    fun `borda horizontal fica inalterada mesmo com minimizadas`() {
        val contas = listOf(conta("A", minimizada = true), conta("B"), conta("C", automatica = true))

        for (borda in listOf(HudEdge.TOP, HudEdge.BOTTOM)) {
            val arranjo = hudColumnArrangement(contas, borda)

            assertEquals(listOf(0, 1, 2), arranjo.shown, "borda $borda")
            assertEquals(emptyList(), arranjo.holeMembers, "borda $borda")
            assertFalse(arranjo.hasHole, "borda $borda")
        }
    }

    @Test
    fun `lista vazia nao gera coluna nem buraco`() {
        val arranjo = hudColumnArrangement(emptyList(), HudEdge.LEFT)

        assertEquals(emptyList(), arranjo.shown)
        assertEquals(emptyList(), arranjo.holeMembers)
    }

    @Test
    fun `voo do sumidouro quadro 0 fica na origem, quadro 1 chega ao alvo e o meio fica no caminho`() {
        assertEquals(100f, hudSinkFrame(0f, fromAlong = 100f, toAlong = 300f).along)
        assertEquals(300f, hudSinkFrame(1f, fromAlong = 100f, toAlong = 300f).along)
        val meio = hudSinkFrame(0.5f, fromAlong = 100f, toAlong = 300f).along
        assertTrue(meio > 100f && meio < 300f, "meio do voo fora do caminho: $meio")
    }

    @Test
    fun `voo do sumidouro avanca sem passar do alvo, nos dois sentidos`() {
        val ida = (0..100).map { passo -> hudSinkFrame(passo / 100f, fromAlong = 100f, toAlong = 300f).along }
        assertTrue(ida.zipWithNext().all { (a, b) -> b >= a }, "ida nao monotonica: $ida")
        assertTrue(ida.all { along -> along in 100f..300f }, "ida fora do trajeto: $ida")
        val volta = (0..100).map { passo -> hudSinkFrame(passo / 100f, fromAlong = 300f, toAlong = 100f).along }
        assertTrue(volta.zipWithNext().all { (a, b) -> b <= a }, "volta nao monotonica: $volta")
        assertTrue(volta.all { along -> along in 100f..300f }, "volta fora do trajeto: $volta")
    }

    @Test
    fun `anel do voo vai do tamanho cheio ao minimizado`() {
        assertEquals(AppGargantuaTokens.size, hudSinkFrame(0f, fromAlong = 100f, toAlong = 300f).ringSize)
        assertEquals(AppGargantuaTokens.minimizedSize, hudSinkFrame(1f, fromAlong = 100f, toAlong = 300f).ringSize)
    }

    @Test
    fun `conta a meio voo nao entra no buraco nem na contagem, so ao chegar em 1`() {
        val contas = listOf(conta("A"), conta("B", minimizada = true), conta("C", minimizada = true))

        val voo = hudColumnArrangement(contas, HudEdge.LEFT, mapOf(chave("B") to 0.5f, chave("C") to 1f))

        assertEquals(listOf(2), voo.holeMembers, "contagem do buraco")
        assertEquals(listOf(0, 1), voo.shown, "a conta em voo ainda ocupa a coluna")
        assertEquals(listOf(1), voo.flying, "conta em voo")
    }

    @Test
    fun `restaurar tira a conta do buraco e da contagem no inicio do voo`() {
        val contas = listOf(conta("A"), conta("B"), conta("C", minimizada = true))

        val volta = hudColumnArrangement(contas, HudEdge.LEFT, mapOf(chave("B") to 1f))

        assertEquals(listOf(2), volta.holeMembers, "B sai da contagem no primeiro quadro")
        assertEquals(listOf(1), volta.flying, "B voa de volta ao seu lugar")
    }

    @Test
    fun `reduzido nao tem quadro intermediario, a conta cai direto no buraco e no alvo`() {
        assertEquals(0, hudMinimizeMillis(motionReduced = true))
        val progresso = hudMinimizeProgress(0f, motionReduced = true)
        assertEquals(1f, progresso)
        assertEquals(300f, hudSinkFrame(progresso, fromAlong = 100f, toAlong = 300f).along)

        val contas = listOf(conta("A"), conta("B", minimizada = true))
        val arranjo = hudColumnArrangement(contas, HudEdge.LEFT, mapOf(chave("B") to progresso))

        assertEquals(listOf(1), arranjo.holeMembers)
        assertEquals(emptyList(), arranjo.flying)
    }

    private fun chave(nome: String): UsageTargetKey = UsageTargetKey(ApiSource.ANTHROPIC, nome)

    private fun conta(nome: String, minimizada: Boolean = false, automatica: Boolean = false): HudAccount {
        return HudAccount(
            targetKey = UsageTargetKey(ApiSource.ANTHROPIC, nome),
            label = nome,
            statusLabel = "Em dia",
            tone = AppTone.OK,
            quotas = emptyList(),
            focusIndex = 0,
            minimized = minimizada || automatica,
            autoMinimized = automatica
        )
    }
}

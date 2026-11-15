package com.usagemonitor

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.presentation.ui.HudAccount
import com.usagemonitor.presentation.ui.components.AppTone
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

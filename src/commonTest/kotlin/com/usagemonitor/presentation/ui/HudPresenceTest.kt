package com.usagemonitor.presentation.ui

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.components.GargantuaFrame
import com.usagemonitor.presentation.ui.components.gargantuaBirthFrame
import com.usagemonitor.presentation.ui.components.gargantuaCollapseFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HudPresenceTest {
    private fun account(source: ApiSource, presence: HudPresence = HudPresence.SHOWN) = HudAccount(
        targetKey = UsageTargetKey(source, if (source == ApiSource.ANTHROPIC) "default" else null),
        label = source.name,
        statusLabel = "Ok",
        tone = AppTone.OK,
        quotas = emptyList(),
        focusIndex = 0,
        presence = presence
    )

    private val claude = account(ApiSource.ANTHROPIC)
    private val codex = account(ApiSource.CODEX)
    private val cursor = account(ApiSource.CURSOR)

    private fun presences(list: List<HudAccount>) = list.map { it.targetKey.source to it.presence }

    @Test
    fun `a primeira lista nasce inteira para o inicio do app`() {
        val merged = mergeHudPresence(emptyList(), listOf(claude, codex), animate = true)
        assertEquals(listOf(HudPresence.ENTERING, HudPresence.ENTERING), merged.map { it.presence })
        assertEquals(listOf(0, 1), hudBirthOrder(merged))
    }

    @Test
    fun `api ativada nasce e as demais continuam paradas`() {
        val merged = mergeHudPresence(listOf(claude, cursor), listOf(claude, codex, cursor), animate = true)
        assertEquals(
            listOf(ApiSource.ANTHROPIC to HudPresence.SHOWN, ApiSource.CODEX to HudPresence.ENTERING, ApiSource.CURSOR to HudPresence.SHOWN),
            presences(merged)
        )
        assertEquals(listOf(null, 0, null), hudBirthOrder(merged))
    }

    @Test
    fun `api desativada colapsa no mesmo lugar e sai ao assentar`() {
        val merged = mergeHudPresence(listOf(claude, codex, cursor), listOf(claude, cursor), animate = true)
        assertEquals(
            listOf(ApiSource.ANTHROPIC to HudPresence.SHOWN, ApiSource.CODEX to HudPresence.LEAVING, ApiSource.CURSOR to HudPresence.SHOWN),
            presences(merged)
        )
        assertEquals(listOf(claude, cursor), settleHudPresence(merged, codex.targetKey))
    }

    @Test
    fun `nascimento assentado fica parado e dado novo nao reinicia a transicao`() {
        val entering = mergeHudPresence(listOf(claude), listOf(claude, codex), animate = true)
        val refreshed = mergeHudPresence(entering, listOf(claude, codex.copy(statusLabel = "Atenção")), animate = true)
        assertEquals(HudPresence.ENTERING, refreshed[1].presence)
        assertEquals("Atenção", refreshed[1].statusLabel)
        assertEquals(HudPresence.SHOWN, settleHudPresence(refreshed, codex.targetKey)[1].presence)
    }

    @Test
    fun `api reativada durante o colapso nasce de novo`() {
        val leaving = mergeHudPresence(listOf(claude, codex), listOf(claude), animate = true)
        val back = mergeHudPresence(leaving, listOf(claude, codex), animate = true)
        assertEquals(listOf(HudPresence.SHOWN, HudPresence.ENTERING), back.map { it.presence })
    }

    @Test
    fun `reduzir animacoes entrega a lista viva sem transicao`() {
        val merged = mergeHudPresence(listOf(claude, codex), listOf(claude, cursor), animate = false)
        assertEquals(listOf(claude, cursor), merged)
    }

    @Test
    fun `quadros comecam vazios e terminam no indicador parado`() {
        val birthStart = gargantuaBirthFrame(0f)
        assertEquals(0f, birthStart.core)
        assertEquals(0f, birthStart.arcs)
        assertEquals(0f, birthStart.text)
        assertEquals(GargantuaFrame.Settled, gargantuaBirthFrame(1f))
        val collapseEnd = gargantuaCollapseFrame(1f)
        assertEquals(0f, collapseEnd.scale)
        assertEquals(0f, collapseEnd.alpha)
        assertEquals(0f, collapseEnd.flash)
        assertEquals(GargantuaFrame.Settled.copy(), gargantuaCollapseFrame(0f))
    }

    @Test
    fun `o plasma nunca passa do valor real durante as transicoes`() {
        for (step in 0..100) {
            val t = step / 100f
            val birth = gargantuaBirthFrame(t)
            val collapse = gargantuaCollapseFrame(t)
            assertTrue(birth.arcs in 0f..1f && collapse.arcs in 0f..1f, "arco fora do valor em $t")
            assertTrue(birth.flash in 0f..1f && collapse.flash in 0f..1f)
        }
    }
}

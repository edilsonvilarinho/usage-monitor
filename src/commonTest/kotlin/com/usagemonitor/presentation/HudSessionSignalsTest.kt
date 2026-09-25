package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.ActiveSessionAlert
import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CliSessionHealth
import com.usagemonitor.domain.entity.SessionPulse
import com.usagemonitor.domain.entity.StalledCliSession
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.presentation.ui.HudSessionSignal
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.hudSessionSignals
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Os sinais de sessão CLI da HUD (issue #265): o que o app já sabia sobre as
 * sessões de cada conta, dito com as palavras do dado — e nunca com a do risco de
 * cota nem com um "aguardando você" que o app não tem como afirmar.
 */
class HudSessionSignalsTest {

    private val now = Instant.parse("2026-09-25T12:00:00Z")
    private val work = UsageTargetKey(ApiSource.ANTHROPIC, "work")
    private val personal = UsageTargetKey(ApiSource.ANTHROPIC, "personal")

    private fun pulse(vararg health: CliSessionHealth) = SessionPulse(
        health.mapIndexed { index, value -> ActiveSessionAlert("s$index", value, now) }
    )

    private fun stalled(id: String, profileId: String?, minutes: Long) = StalledCliSession(
        sessionId = id,
        projectName = "usage-monitor",
        profileId = profileId,
        pendingSince = now,
        pendingMillis = minutes * 60_000
    )

    @Test
    fun `contexto saturado e crescendo viram um sinal cada, na ordem de gravidade`() {
        val signals = hudSessionSignals(
            work,
            pulse(CliSessionHealth.ATTENTION, CliSessionHealth.SATURATED, CliSessionHealth.ATTENTION),
            emptyList(),
            AppLanguage.PT
        )

        assertEquals(
            listOf(
                HudSessionSignal("Contexto saturado · 1 sessão", AppTone.CRITICAL),
                HudSessionSignal("Contexto crescendo · 2 sessões", AppTone.WARNING)
            ),
            signals
        )
    }

    @Test
    fun `sem resposta usa a frase da tela de sessoes e com varias diz a mais longa`() {
        val one = hudSessionSignals(work, null, listOf(stalled("a", "work", 130)), AppLanguage.PT)
        val many = hudSessionSignals(
            work,
            null,
            listOf(stalled("a", "work", 130), stalled("b", "work", 45), stalled("c", "work", 200)),
            AppLanguage.EN
        )

        assertEquals(listOf(HudSessionSignal("Sem resposta há 2h10", AppTone.WARNING)), one)
        assertEquals(listOf(HudSessionSignal("3 with no reply · up to 3h20", AppTone.WARNING)), many)
    }

    /** Sessão sem perfil não é "de todas as contas": acenderia o sinal na conta errada. */
    @Test
    fun `sessao sem resposta so conta na conta dela`() {
        val sessions = listOf(stalled("a", "personal", 130), stalled("b", null, 90))

        assertEquals(emptyList(), hudSessionSignals(work, null, sessions, AppLanguage.PT))
        assertEquals(1, hudSessionSignals(personal, null, sessions, AppLanguage.PT).size)
    }

    @Test
    fun `fonte sem sessao cli nunca tem sinal`() {
        val codex = UsageTargetKey.forSource(ApiSource.CODEX)

        assertEquals(emptyList(), hudSessionSignals(codex, pulse(CliSessionHealth.SATURATED), emptyList(), AppLanguage.PT))
    }

    @Test
    fun `nada a dizer e lista vazia`() {
        assertEquals(emptyList(), hudSessionSignals(work, SessionPulse.EMPTY, emptyList(), AppLanguage.PT))
    }

    /**
     * O critério de aceite da issue: o sinal não se confunde com "aguardando
     * usuário". A sessão sem resposta é o pedido do usuário esperando o modelo, e
     * "Atenção" é a palavra do risco de cota.
     */
    @Test
    fun `nenhum sinal diz aguardando nem usa a palavra do risco de cota`() {
        val everything = listOf(AppLanguage.PT, AppLanguage.EN).flatMap { language ->
            hudSessionSignals(
                work,
                pulse(CliSessionHealth.SATURATED, CliSessionHealth.ATTENTION),
                listOf(stalled("a", "work", 130), stalled("b", "work", 30)),
                language
            ) + hudSessionSignals(work, null, listOf(stalled("a", "work", 130)), language)
        }

        assertTrue(everything.isNotEmpty())
        for (signal in everything) {
            val text = signal.text.lowercase()
            for (forbidden in listOf("aguardando", "waiting", "atenção", "warning", "crítico", "critical")) {
                assertTrue(forbidden !in text, "\"${signal.text}\" contém \"$forbidden\"")
            }
        }
    }
}

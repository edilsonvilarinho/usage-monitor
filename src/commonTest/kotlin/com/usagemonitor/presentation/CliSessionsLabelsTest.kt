package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CliSessionHealth
import com.usagemonitor.domain.entity.CliSessionHealthTally
import com.usagemonitor.domain.entity.CliSessionRange
import com.usagemonitor.presentation.ui.CliSessionsLabels
import com.usagemonitor.presentation.ui.cliSessionsTitle
import com.usagemonitor.presentation.ui.cliSessionsWindowTitle
import com.usagemonitor.presentation.ui.formatInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

private val PT = AppLanguage.PT
private val EN = AppLanguage.EN

class CliSessionsLabelsTest {

    @Test
    fun `window title names the profile only when there is one`() {
        assertEquals("CLI Sessions", cliSessionsTitle(EN))
        assertEquals("Sessões CLI — trabalho", cliSessionsWindowTitle(PT, "trabalho"))
        assertEquals("CLI Sessions", cliSessionsWindowTitle(EN, " "))
        assertEquals("1 session", CliSessionsLabels.sessionCount(1, EN))
        assertEquals("3 sessions", CliSessionsLabels.sessionCount(3, EN))
        assertEquals("1 sessão", CliSessionsLabels.sessionCount(1, PT))
    }

    @Test
    fun `estimated total says which window it covers`() {
        val endsAt = Instant.parse("2026-10-27T20:00:00Z")
        assertEquals(
            "estimated cost · 5h window since the reset",
            CliSessionsLabels.estimatedTotalInRange(CliSessionRange.LAST_5H, null, isAnchored = true, language = EN)
        )
        assertEquals(
            "custo estimado · janela 5h desde o reinício",
            CliSessionsLabels.estimatedTotalInRange(CliSessionRange.LAST_5H, null, isAnchored = true, language = PT)
        )
        assertEquals(
            "estimated cost · last 7 days",
            CliSessionsLabels.estimatedTotalInRange(CliSessionRange.LAST_7D, null, isAnchored = false, language = EN)
        )
        assertEquals(
            "custo estimado · últimas 5h",
            CliSessionsLabels.estimatedTotalInRange(CliSessionRange.LAST_5H, null, isAnchored = false, language = PT)
        )
        assertEquals(
            "custo estimado · últimos 30 dias",
            CliSessionsLabels.estimatedTotalInRange(CliSessionRange.LAST_30D, null, isAnchored = false, language = PT)
        )
        assertEquals(
            "estimated cost · 5h window until ${formatInstant(endsAt)}",
            CliSessionsLabels.estimatedTotalInRange(CliSessionRange.LAST_5H, endsAt, isAnchored = true, language = EN)
        )
        assertEquals("All time", CliSessionsLabels.rangeLabel(CliSessionRange.ALL, EN))
    }

    @Test
    fun `empty range separates the quota window from the sliding one`() {
        assertEquals(
            "No Claude Code sessions found in ~/.claude/projects.",
            CliSessionsLabels.emptyInRange(CliSessionRange.ALL, isAnchored = false, language = EN)
        )
        assertEquals(
            "No session in the current quota window (5h). Pick a wider range.",
            CliSessionsLabels.emptyInRange(CliSessionRange.LAST_5H, isAnchored = true, language = EN)
        )
        assertEquals(
            "No session active in the last 7 days. Pick a wider range.",
            CliSessionsLabels.emptyInRange(CliSessionRange.LAST_7D, isAnchored = false, language = EN)
        )
        assertEquals(
            "Nenhuma sessão com atividade nas últimas 5h. Escolha uma janela maior.",
            CliSessionsLabels.emptyInRange(CliSessionRange.LAST_5H, isAnchored = false, language = PT)
        )
        assertEquals(
            "Nenhuma sessão com atividade nos últimos 7 dias. Escolha uma janela maior.",
            CliSessionsLabels.emptyInRange(CliSessionRange.LAST_7D, isAnchored = false, language = PT)
        )
    }

    @Test
    fun `last change and health texts`() {
        assertEquals("last change 27/10 12:04 BRT", CliSessionsLabels.lastChange("27/10 12:04 BRT", EN))
        assertEquals("no changes yet", CliSessionsLabels.lastChange(null, EN))
        assertEquals("Session saturated", CliSessionsLabels.saturated(EN))
        assertEquals("Sessão saturada", CliSessionsLabels.saturated(PT))
        assertEquals("Contexto crescendo", CliSessionsLabels.healthTitle(CliSessionHealth.ATTENTION, PT))
        assertEquals("Context growing", CliSessionsLabels.healthTitle(CliSessionHealth.ATTENTION, EN))
        assertEquals(
            "Cada mensagem já reenvia bastante contexto. Um /compact reduz o custo daqui pra frente.",
            CliSessionsLabels.healthAdvice(CliSessionHealth.ATTENTION, PT)
        )
        assertEquals(
            "Each message now resends a lot of context. A /compact cuts the cost from here on.",
            CliSessionsLabels.healthAdvice(CliSessionHealth.ATTENTION, EN)
        )
        assertEquals(
            "Consider /compact or a fresh session: carrying on is expensive and the window is near its limit.",
            CliSessionsLabels.healthAdvice(CliSessionHealth.SATURATED, EN)
        )
    }

    @Test
    fun `health tally lists only what needs action`() {
        assertNull(CliSessionsLabels.healthTally(CliSessionHealthTally(), EN))
        assertEquals("2 saturated · 1 needing attention", CliSessionsLabels.healthTally(CliSessionHealthTally(2, 1), EN))
        assertEquals("1 saturada", CliSessionsLabels.healthTally(CliSessionHealthTally(saturated = 1), PT))
        assertEquals("2 saturadas · 3 em atenção", CliSessionsLabels.healthTally(CliSessionHealthTally(2, 3), PT))
        assertEquals("4 needing attention", CliSessionsLabels.healthTally(CliSessionHealthTally(attention = 4), EN))
    }

    @Test
    fun `notices in english`() {
        assertEquals("Estimated at list price. Not an invoice.", CliSessionsLabels.estimatedCostNotice(EN))
        assertEquals(
            "2 turn(s) with unknown pricing: the displayed cost is incomplete.",
            CliSessionsLabels.unpricedNotice(2, EN)
        )
        assertEquals(
            "2 turno(s) sem preço conhecido: o custo exibido está incompleto.",
            CliSessionsLabels.unpricedNotice(2, PT)
        )
        assertEquals("Transcript removed from disk; only the indexed summary remains.", CliSessionsLabels.staleNotice(EN))
        assertEquals("Transcript removido do disco; só o resumo indexado permanece.", CliSessionsLabels.staleNotice(PT))
        assertEquals(
            "3 subagent turn(s) count toward cost but stay out of the context charts.",
            CliSessionsLabels.sidechainNotice(3, EN)
        )
        assertEquals(
            "3 turno(s) de subagente somam no custo mas ficam fora dos gráficos de contexto.",
            CliSessionsLabels.sidechainNotice(3, PT)
        )
        assertEquals("When", CliSessionsLabels.columnWhen(EN))
        assertEquals("Quando", CliSessionsLabels.columnWhen(PT))
    }
}

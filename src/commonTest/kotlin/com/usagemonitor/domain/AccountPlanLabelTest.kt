package com.usagemonitor.domain

import com.usagemonitor.domain.entity.anthropicPlanLabel
import com.usagemonitor.domain.entity.codexPlanLabel
import com.usagemonitor.domain.entity.cursorPlanLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AccountPlanLabelTest {

    @Test
    fun `claude junta a assinatura e a multiplicacao do limite`() {
        assertEquals("Max 20x", anthropicPlanLabel("max", "default_claude_max_20x"))
        assertEquals("Max 5x", anthropicPlanLabel("max", "default_claude_max_5x"))
        assertEquals("Pro", anthropicPlanLabel("pro", null))
        assertEquals("Team", anthropicPlanLabel("team", ""))
    }

    /** Sem o campo não há plano a mostrar — nunca "Desconhecido". */
    @Test
    fun `sem assinatura nao ha rotulo`() {
        assertNull(anthropicPlanLabel(null, "default_claude_max_20x"))
        assertNull(anthropicPlanLabel("  ", null))
        assertNull(codexPlanLabel(""))
        assertNull(cursorPlanLabel(null))
    }

    @Test
    fun `codex diz o plano do chatgpt e cursor o da assinatura`() {
        assertEquals("ChatGPT Plus", codexPlanLabel("plus"))
        assertEquals("ChatGPT Pro", codexPlanLabel("pro"))
        assertEquals("Ultra", cursorPlanLabel("ultra"))
    }
}

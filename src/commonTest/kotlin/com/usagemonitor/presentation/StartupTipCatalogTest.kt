package com.usagemonitor.presentation

import com.usagemonitor.presentation.ui.help.StartupTipCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * O `when` exaustivo não existe aqui — a lista é dado —, então o que o
 * compilador não pega vira teste: dica vazia, idioma não traduzido, rótulo com
 * aspas pela metade e as regras de voz do design system.
 */
class StartupTipCatalogTest {

    private val tips = StartupTipCatalog.tips

    @Test
    fun `there are enough tips for a few weeks without repeating`() {
        assertTrue(tips.size >= 20, "só ${tips.size} dicas")
    }

    @Test
    fun `every tip is written in both languages and they differ`() {
        tips.forEach { tip ->
            assertTrue(tip.pt.isNotBlank() && tip.en.isNotBlank(), "dica vazia: $tip")
            assertTrue(tip.pt != tip.en, "dica não traduzida: ${tip.pt}")
        }
    }

    @Test
    fun `no two tips say the same thing`() {
        assertEquals(tips.size, tips.map { tip -> tip.pt }.toSet().size)
        assertEquals(tips.size, tips.map { tip -> tip.en }.toSet().size)
    }

    @Test
    fun `quoted labels are closed`() {
        tips.forEach { tip ->
            assertEquals(0, tip.pt.count { it == '"' } % 2, "aspas abertas: ${tip.pt}")
            assertEquals(0, tip.en.count { it == '"' } % 2, "aspas abertas: ${tip.en}")
        }
    }

    /** Design system: impessoal, sem exclamação. */
    @Test
    fun `tips follow the content voice`() {
        tips.forEach { tip ->
            assertTrue("!" !in tip.pt && "!" !in tip.en, "exclamação: $tip")
            assertTrue(Regex("\\bvocê\\b", RegexOption.IGNORE_CASE).find(tip.pt) == null, "\"você\": ${tip.pt}")
        }
    }

    @Test
    fun `consecutive tips change subject`() {
        tips.zipWithNext().forEach { (current, next) ->
            assertTrue(current.topic != next.topic, "duas dicas seguidas de ${current.topic}")
        }
    }
}

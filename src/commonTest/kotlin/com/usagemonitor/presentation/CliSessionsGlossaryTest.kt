package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.CliSessionsGlossary
import com.usagemonitor.presentation.ui.GlossaryTerm
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CliSessionsGlossaryTest {

    /**
     * Termo sem tradução vira texto em português no meio da tela em inglês, e o
     * `when` exaustivo do Kotlin não pega isso: pega a entrada faltando, não a
     * entrada vazia.
     */
    @Test
    fun `every term is defined in both languages`() {
        for (term in GlossaryTerm.entries) {
            for (language in AppLanguage.entries) {
                val entry = CliSessionsGlossary.entry(term, language)
                assertTrue(
                    entry.title.isNotBlank(),
                    "título vazio para $term em $language"
                )
                assertTrue(
                    entry.explanation.isNotBlank(),
                    "explicação vazia para $term em $language"
                )
            }
        }
    }

    /** O painel lista o glossário inteiro; um termo de fora dele fica invisível. */
    @Test
    fun `the reading order covers every term exactly once`() {
        assertEquals(
            GlossaryTerm.entries.toSet(),
            CliSessionsGlossary.readingOrder.toSet()
        )
        assertEquals(
            CliSessionsGlossary.readingOrder.size,
            CliSessionsGlossary.readingOrder.toSet().size
        )
    }

    /** PT e EN têm de ser textos distintos: repetir um deles é tradução esquecida. */
    @Test
    fun `the two languages do not share the same explanation`() {
        for (term in GlossaryTerm.entries) {
            val portuguese = CliSessionsGlossary.entry(term, AppLanguage.PT)
            val english = CliSessionsGlossary.entry(term, AppLanguage.EN)
            assertTrue(
                portuguese.explanation != english.explanation,
                "explicação idêntica nos dois idiomas para $term"
            )
        }
    }
}

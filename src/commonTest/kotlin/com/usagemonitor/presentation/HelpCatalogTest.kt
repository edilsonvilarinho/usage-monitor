package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.help.HelpCatalog
import com.usagemonitor.presentation.ui.help.HelpTopic
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HelpCatalogTest {

    /**
     * Tópico sem tradução vira português no meio da tela em inglês, e o `when`
     * exaustivo do Kotlin não pega isso: pega a entrada faltando, não a entrada
     * vazia. Mesma razão do teste equivalente do glossário das sessões.
     */
    @Test
    fun `every topic is filled in both languages`() {
        for (topic in HelpTopic.entries) {
            for (language in AppLanguage.entries) {
                val entry = HelpCatalog.entry(topic, language)
                assertTrue(entry.title.isNotBlank(), "título vazio para $topic em $language")
                assertTrue(entry.summary.isNotBlank(), "resumo vazio para $topic em $language")
                assertTrue(
                    entry.description.isNotBlank(),
                    "descrição vazia para $topic em $language"
                )
                assertTrue(entry.mediaId.isNotBlank(), "mídia vazia para $topic em $language")
            }
        }
    }

    /**
     * Descrição sem caminho de ativação descreve algo que o leitor não alcança —
     * e é justamente o "como ativar" que a issue pede.
     */
    @Test
    fun `every topic states how to enable it`() {
        for (topic in HelpTopic.entries) {
            for (language in AppLanguage.entries) {
                val steps = HelpCatalog.entry(topic, language).steps
                assertTrue(steps.isNotEmpty(), "sem passos para $topic em $language")
                assertTrue(
                    steps.none { step -> step.isBlank() },
                    "passo vazio para $topic em $language"
                )
            }
        }
    }

    /** A tela lista o catálogo inteiro; um tópico fora da ordem fica invisível. */
    @Test
    fun `the reading order covers every topic exactly once`() {
        assertEquals(HelpTopic.entries.toSet(), HelpCatalog.readingOrder.toSet())
        assertEquals(HelpCatalog.readingOrder.size, HelpCatalog.readingOrder.toSet().size)
    }

    /** PT e EN têm de ser textos distintos: repetir um deles é tradução esquecida. */
    @Test
    fun `the two languages do not share the same description`() {
        for (topic in HelpTopic.entries) {
            val portuguese = HelpCatalog.entry(topic, AppLanguage.PT)
            val english = HelpCatalog.entry(topic, AppLanguage.EN)
            assertTrue(
                portuguese.description != english.description,
                "descrição idêntica nos dois idiomas para $topic"
            )
            assertTrue(
                portuguese.title != english.title,
                "título idêntico nos dois idiomas para $topic"
            )
        }
    }

    /**
     * O identificador de mídia é o nome do recurso `help/<id>.gif`. Dois tópicos
     * com o mesmo id mostrariam a mesma demo sem erro nenhum, e o id só pode
     * conter o que serve de nome de arquivo em qualquer plataforma.
     */
    @Test
    fun `media ids are unique and file safe`() {
        val ids = HelpTopic.entries.map { topic -> HelpCatalog.mediaId(topic) }
        assertEquals(ids.size, ids.toSet().size, "id de mídia repetido: $ids")

        val allowed = Regex("[a-z0-9-]+")
        for (id in ids) {
            assertTrue(allowed.matches(id), "id de mídia fora do formato: $id")
        }
    }

    /** O id não muda com o idioma: as demos são geradas só em português. */
    @Test
    fun `the media id does not change with the language`() {
        for (topic in HelpTopic.entries) {
            assertEquals(
                HelpCatalog.entry(topic, AppLanguage.PT).mediaId,
                HelpCatalog.entry(topic, AppLanguage.EN).mediaId
            )
        }
    }
}

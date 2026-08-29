package com.usagemonitor.domain

import com.usagemonitor.domain.entity.Breadcrumb
import com.usagemonitor.domain.entity.BreadcrumbCategory
import com.usagemonitor.domain.entity.normalizeBreadcrumbMessage
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BreadcrumbTest {

    /**
     * O arquivo sobrevive a upgrades do app: o valor de fio é o contrato, e
     * renomear a constante do enum não pode mudar o que já está no disco.
     */
    @Test
    fun `every category has a stable wire value that round-trips`() {
        BreadcrumbCategory.entries.forEach { category ->
            assertEquals(category, BreadcrumbCategory.fromWireValue(category.wireValue))
        }
        assertEquals("use-case", BreadcrumbCategory.USE_CASE.wireValue)
        assertEquals("api-call", BreadcrumbCategory.API_CALL.wireValue)
    }

    @Test
    fun `an unknown wire value is null instead of an exception`() {
        assertNull(BreadcrumbCategory.fromWireValue("telemetry"))
    }

    /**
     * O arquivo é JSONL, uma linha por passo. A quebra de linha some antes de
     * qualquer serialização em vez de depender do escape de `\n`.
     */
    @Test
    fun `newlines and whitespace runs collapse into single spaces`() {
        val normalized = normalizeBreadcrumbMessage("  falhou\n  em\t\tduas   linhas ")

        assertEquals("falhou em duas linhas", normalized)
        assertFalse(normalized.contains('\n'))
    }

    @Test
    fun `a long message is cut at the ceiling, marker included`() {
        val normalized = normalizeBreadcrumbMessage("x".repeat(500))

        assertEquals(Breadcrumb.MAX_MESSAGE_LENGTH, normalized.length)
        assertTrue(normalized.endsWith("..."), normalized)
    }

    @Test
    fun `a message at the ceiling is left untouched`() {
        val exact = "y".repeat(Breadcrumb.MAX_MESSAGE_LENGTH)

        assertEquals(exact, normalizeBreadcrumbMessage(exact))
    }

    @Test
    fun `the factory normalizes and the constructor does not`() {
        val at = Instant.fromEpochMilliseconds(1_700_000_000_000L)

        val built = Breadcrumb.of(at, BreadcrumbCategory.NAVIGATION, "abriu\na janela")
        assertEquals("abriu a janela", built.message)

        // Quem lê o arquivo de volta usa o construtor: reescrever ali o que já
        // está gravado esconderia uma linha adulterada em vez de mostrá-la.
        val read = Breadcrumb(at, BreadcrumbCategory.NAVIGATION, "abriu\na janela")
        assertEquals("abriu\na janela", read.message)
    }
}

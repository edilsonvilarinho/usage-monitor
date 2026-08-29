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

private val AT = Instant.fromEpochMilliseconds(1_700_000_000_000L)

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

    /**
     * O texto **real** de `AnthropicCredentialStore.missingCredentialsMessage`: ela
     * é a falha mais rotineira do app, é a que faz alguém abrir um relatório, e
     * sozinha carregava o nome da pessoa duas vezes — no caminho e no e-mail do
     * perfil. É o caso que provou que herdar `sanitizeUiErrorMessage` não bastava.
     */
    @Test
    fun `the real missing-credentials message loses the windows path and the e-mail`() {
        val real = "Credenciais não encontradas para o perfil 'edilson.messias@example.com': " +
            "C:\\Users\\edilson\\.claude\\.credentials.json. " +
            "Execute o Claude Code CLI com esse CLAUDE_CONFIG_DIR para autenticar."

        val message = Breadcrumb.of(AT, BreadcrumbCategory.API_CALL, "ANTHROPIC: falhou — $real").message

        assertFalse(message.contains("edilson", ignoreCase = true), message)
        assertFalse(message.contains("C:\\Users"), message)
        assertFalse(message.contains("example.com"), message)
        // O que explica o defeito fica: o nome do arquivo e a frase.
        assertTrue(message.contains("<caminho>/.credentials.json"), message)
        assertTrue(message.contains("Credenciais não encontradas"), message)
        // A pontuação da frase não some junto com o diretório.
        assertTrue(message.contains(".credentials.json."), message)
    }

    @Test
    fun `the same message on linux loses the home path too`() {
        val real = "Credenciais não encontradas para o perfil 'qa@example.com': " +
            "/home/edilson/.claude/.credentials.json. Execute o Claude Code CLI."

        val message = Breadcrumb.of(AT, BreadcrumbCategory.API_CALL, real).message

        assertFalse(message.contains("edilson"), message)
        assertFalse(message.contains("/home/"), message)
        assertFalse(message.contains("qa@example.com"), message)
        assertTrue(message.contains("<caminho>/.credentials.json"), message)
    }

    @Test
    fun `macOS home paths and UNC shares are redacted as well`() {
        val macOs = Breadcrumb.of(AT, BreadcrumbCategory.ERROR, "falha em /Users/edilson/.usage-monitor/api-keys.json").message
        assertFalse(macOs.contains("edilson"), macOs)
        assertTrue(macOs.contains("<caminho>/api-keys.json"), macOs)

        val unc = Breadcrumb.of(AT, BreadcrumbCategory.ERROR, "falha em \\\\servidor\\perfis\\edilson\\dados.db").message
        assertFalse(unc.contains("edilson"), unc)
        assertTrue(unc.contains("<caminho>/dados.db"), unc)
    }

    /**
     * As raízes POSIX são enumeradas de propósito: redigir toda barra apagaria a
     * rota da API, que é o que explica a falha e não identifica ninguém.
     */
    @Test
    fun `an api route is not mistaken for a home path`() {
        val message = Breadcrumb.of(AT, BreadcrumbCategory.API_CALL, "GET /api/oauth/usage devolveu 401").message

        assertEquals("GET /api/oauth/usage devolveu 401", message)
    }

    /**
     * Cortar antes de redigir não protegeria nada: `C:\Users\<nome>` são os
     * primeiros caracteres do caminho, e o teto de 200 nunca chegaria perto dele.
     */
    @Test
    fun `redaction happens before the length cut, not after`() {
        val long = "erro ".repeat(60) + "C:\\Users\\edilson\\.claude\\.credentials.json"

        val message = Breadcrumb.of(AT, BreadcrumbCategory.ERROR, long).message

        assertTrue(message.length <= Breadcrumb.MAX_MESSAGE_LENGTH, message.length.toString())
        assertFalse(message.contains("edilson"), message)
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

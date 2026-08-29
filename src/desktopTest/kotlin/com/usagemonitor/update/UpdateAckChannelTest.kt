package com.usagemonitor.update

import com.usagemonitor.StartupOrigin
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdateAckChannelTest {

    private val workDirectory: File = Files.createTempDirectory("usage-monitor-ack").toFile()
    private val ackFile = File(workDirectory, "diretorio/update-ack")
    private val channel = UpdateAckChannel(ackFile)

    @AfterTest
    fun cleanUp() {
        workDirectory.deleteRecursively()
    }

    @Test
    fun `the token is written and confirms itself`() {
        assertTrue(channel.acknowledge("1234-1756000000"))

        assertTrue(channel.isAcknowledged("1234-1756000000"))
        assertEquals("1234-1756000000", ackFile.readText())
    }

    /**
     * O ACK sobrado de uma sessão anterior tem outro token, e nenhum token vale
     * duas vezes. É isso que dispensa comparar relógios no lado do shell.
     */
    @Test
    fun `an ack left over from another session does not confirm this one`() {
        ackFile.parentFile.mkdirs()
        ackFile.writeText("9999-1755000000")

        assertFalse(channel.isAcknowledged("1234-1756000000"))
    }

    @Test
    fun `an absent ack confirms nothing`() {
        assertFalse(channel.isAcknowledged("1234-1756000000"))
    }

    /**
     * Falhar aqui custa um rollback; derrubar o arranque custaria o app.
     */
    @Test
    fun `an unwritable path reports failure instead of throwing`() {
        val blocked = UpdateAckChannel(File(workDirectory, "arquivo/update-ack"))
        File(workDirectory, "arquivo").writeText("nao sou diretorio")

        assertFalse(blocked.acknowledge("1234-1756000000"))
    }

    /**
     * O token vem do ambiente e vira conteúdo de arquivo e comparação de
     * igualdade do outro lado: quebra de linha e espaço não podem chegar lá.
     */
    @Test
    fun `only a restricted alphabet is a token`() {
        assertTrue(isValidUpdateAckToken("1234-1756000000"))
        assertTrue(isValidUpdateAckToken("abc_DEF-09"))

        assertFalse(isValidUpdateAckToken(""))
        assertFalse(isValidUpdateAckToken("com espaco"))
        assertFalse(isValidUpdateAckToken("com\nquebra"))
        assertFalse(isValidUpdateAckToken("../../etc/passwd"))
        assertFalse(isValidUpdateAckToken("a".repeat(65)))
    }

    @Test
    fun `a token rejected by the validator is never written`() {
        assertFalse(channel.acknowledge("com espaco"))
        assertFalse(ackFile.exists())
    }

    /**
     * Argumento não é mais o caminho: um `--update-ack=X` na linha de comando
     * vazava, no launcher nativo do jpackage, como opção da própria JVM em vez
     * de argumento do app — "Unrecognized option", e a JVM nem chegava a
     * subir (medido ao vivo numa Bazzite real, issue #118). Variável de
     * ambiente não passa por parser de argv de nenhuma camada.
     */
    @Test
    fun `the token is read from the environment, not from argv`() {
        assertEquals(
            "1234-1756000000",
            updateAckTokenFromEnv { name -> "1234-1756000000".takeIf { name == UPDATE_ACK_ENV_VAR } }
        )
        assertNull(updateAckTokenFromEnv { null })
        assertNull(updateAckTokenFromEnv { name -> "com espaco".takeIf { name == UPDATE_ACK_ENV_VAR } })
    }

    /**
     * O health check não é uma terceira origem: a versão promovida pode subir
     * num arranque manual ou num autostart, e o ACK não decide isso. Ele nem
     * aparece em `argv` — só o `--autostart` de sempre chega lá.
     */
    @Test
    fun `the ack channel does not change the startup origin`() {
        assertEquals(StartupOrigin.MANUAL, StartupOrigin.from(emptyArray()))
        assertEquals(StartupOrigin.AUTOSTART, StartupOrigin.from(arrayOf("--autostart")))
    }
}

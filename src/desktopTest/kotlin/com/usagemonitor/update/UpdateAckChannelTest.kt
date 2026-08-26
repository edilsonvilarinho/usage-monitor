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
     * O token vem de `argv` e vira conteúdo de arquivo e comparação de igualdade
     * do outro lado: quebra de linha e espaço não podem chegar lá.
     */
    @Test
    fun `only a restricted alphabet is a token`() {
        assertEquals("1234-1756000000", parseUpdateAckToken("--update-ack=1234-1756000000"))
        assertEquals("abc_DEF-09", parseUpdateAckToken("--update-ack=abc_DEF-09"))

        assertNull(parseUpdateAckToken("--update-ack="))
        assertNull(parseUpdateAckToken("--update-ack=com espaco"))
        assertNull(parseUpdateAckToken("--update-ack=com\nquebra"))
        assertNull(parseUpdateAckToken("--update-ack=../../etc/passwd"))
        assertNull(parseUpdateAckToken("--update-ack=" + "a".repeat(65)))
        assertNull(parseUpdateAckToken("--autostart"))
        assertNull(parseUpdateAckToken("--update-ack-outro=x"))
    }

    @Test
    fun `a token rejected by the parser is never written`() {
        assertFalse(channel.acknowledge("com espaco"))
        assertFalse(ackFile.exists())
    }

    @Test
    fun `the token is picked from anywhere in the command line`() {
        assertEquals(
            "1234-1756000000",
            updateAckTokenFrom(arrayOf("--autostart", "--update-ack=1234-1756000000"))
        )
        assertNull(updateAckTokenFrom(arrayOf("--autostart")))
        assertNull(updateAckTokenFrom(emptyArray()))
    }

    /**
     * `StartupOrigin.from` ignora argumento que não conhece, e é isso que faz o
     * piso de versão-alvo ser requisito: uma versão anterior a este código sobe
     * normalmente com o argumento e **nunca confirma**, e o script desfaria uma
     * atualização que deu certo.
     *
     * O health check também não é uma terceira origem: a versão promovida pode
     * subir num arranque manual ou num autostart, e o token não decide isso.
     */
    @Test
    fun `the private argument does not change the startup origin`() {
        assertEquals(
            StartupOrigin.MANUAL,
            StartupOrigin.from(arrayOf("--update-ack=1234-1756000000"))
        )
        assertEquals(
            StartupOrigin.AUTOSTART,
            StartupOrigin.from(arrayOf("--update-ack=1234-1756000000", "--autostart"))
        )
    }
}

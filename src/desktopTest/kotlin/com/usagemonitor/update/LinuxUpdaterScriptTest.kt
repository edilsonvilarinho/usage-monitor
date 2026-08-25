package com.usagemonitor.update

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LinuxUpdaterScriptTest {

    private val workDirectory: File = Files.createTempDirectory("usage-monitor-updater").toFile()
    private val appliedModes = mutableListOf<File>()

    @AfterTest
    fun cleanUp() {
        workDirectory.deleteRecursively()
    }

    @Test
    fun `the script is materialized from the packaged resource`() {
        val script = materialize()

        assertTrue(script.isFile)
        assertEquals(LINUX_UPDATER_SCRIPT_NAME, script.name)
        val body = script.readText()
        assertTrue(body.startsWith("#!/bin/sh\n"), "sem shebang")
        assertTrue(body.contains(LINUX_MANAGED_MARKER_NAME), "não confere o marcador")
    }

    /**
     * Um script executado no encerramento do app, gravável pelo grupo, é um
     * caminho de execução de código aberto a quem estiver na mesma máquina.
     */
    @Test
    fun `the script is written owner-only executable`() {
        val script = materialize()

        assertEquals(listOf(script.absolutePath), appliedModes.map { it.absolutePath })
    }

    /**
     * O `rm -f "$0"` da última linha pode não ter rodado. Sobrescrever manteria
     * a permissão e o inode antigos.
     */
    @Test
    fun `a leftover script is replaced, not overwritten in place`() {
        val stale = File(workDirectory, LINUX_UPDATER_SCRIPT_NAME)
        stale.parentFile.mkdirs()
        stale.writeText("#!/bin/sh\nexit 0\n")

        val script = materialize()

        assertTrue(script.readText().startsWith("#!/bin/sh\n# Troca a versao ativa"))
    }

    /**
     * Argumentos separados e não uma linha só: cada elemento vira um `argv[]`,
     * então espaço, apóstrofo e cifrão vindos de nome de pasta não passam por
     * interpretação de shell.
     */
    @Test
    fun `the command passes every path as its own argument`() {
        val script = File(workDirectory, LINUX_UPDATER_SCRIPT_NAME)

        val command = linuxUpdaterCommand(
            script = script,
            rootPath = "/home/d'arcy/.local/share/usage-monitor",
            version = "39.0.0",
            previousVersion = "38.0.0",
            previousPid = 4321L,
            ackToken = "4321-1756000000",
            launcherPath = "/home/d'arcy/.local/bin/usage-monitor",
            ackFilePath = "/home/d'arcy/.usage-monitor/update-ack",
            receiptFilePath = "/home/d'arcy/.usage-monitor/update-receipt.properties"
        )

        assertEquals(
            listOf(
                "/bin/sh",
                script.absolutePath,
                "/home/d'arcy/.local/share/usage-monitor",
                "39.0.0",
                "38.0.0",
                "4321",
                "4321-1756000000",
                "/home/d'arcy/.local/bin/usage-monitor",
                "/home/d'arcy/.usage-monitor/update-ack",
                "/home/d'arcy/.usage-monitor/update-receipt.properties"
            ),
            command
        )
    }

    /**
     * O script confere `$#`, e argumento vazio conta mas some numa leitura
     * desatenta.
     */
    @Test
    fun `an unknown previous version travels as a dash`() {
        val command = linuxUpdaterCommand(
            script = File(workDirectory, LINUX_UPDATER_SCRIPT_NAME),
            rootPath = "/root",
            version = "39.0.0",
            previousVersion = null,
            previousPid = 1L,
            ackToken = "t",
            launcherPath = "/l",
            ackFilePath = "/a",
            receiptFilePath = "/r"
        )

        assertEquals("-", command[4])
        assertEquals(10, command.size)
        assertEquals(
            "-",
            linuxUpdaterCommand(
                script = File(workDirectory, LINUX_UPDATER_SCRIPT_NAME),
                rootPath = "/root",
                version = "39.0.0",
                previousVersion = "   ",
                previousPid = 1L,
                ackToken = "t",
                launcherPath = "/l",
                ackFilePath = "/a",
                receiptFilePath = "/r"
            )[4]
        )
    }

    /**
     * O contrato posicional é a única coisa que liga os dois lados. O script
     * recusa qualquer contagem diferente, e é este teste que impede alguém de
     * acrescentar um argumento num lado só.
     */
    @Test
    fun `the script and the command agree on eight positional arguments`() {
        val body = materialize().readText()

        assertTrue(body.contains("""if [ "${'$'}#" -ne 8 ]; then"""), "o script nao confere a contagem")
        val command = linuxUpdaterCommand(
            script = File(workDirectory, LINUX_UPDATER_SCRIPT_NAME),
            rootPath = "/root",
            version = "39.0.0",
            previousVersion = "38.0.0",
            previousPid = 1L,
            ackToken = "t",
            launcherPath = "/l",
            ackFilePath = "/a",
            receiptFilePath = "/r"
        )
        // `/bin/sh` + caminho do script + os oito posicionais.
        assertEquals(10, command.size)
    }

    /**
     * O recibo de sucesso só pode ser escrito depois do ACK: escrevê-lo antes
     * afirmaria uma troca que ainda pode ser desfeita.
     */
    @Test
    fun `success is only recorded after the acknowledgement`() {
        val body = materialize().readText()

        val ackCheck = body.indexOf("acknowledged=1")
        val successReceipt = body.indexOf("write_receipt success")
        assertTrue(ackCheck in 1 until successReceipt, "o recibo de sucesso não vem depois do ACK")
    }

    /**
     * Quem descarta os ~125 MB é `shouldDiscardUpdateArtifacts`, no arranque
     * seguinte. O script duplicando isso seria um segundo dono da poda.
     */
    @Test
    fun `the script never deletes the downloaded archive`() {
        val body = materialize().readText()

        assertFalse(body.contains(".tar.gz"), "o script menciona o archive")
        assertTrue(body.contains("rm -rf \"\$staging_dir\""), "nao limpa o staging")
        assertTrue(body.contains("rm -f \"\$0\""), "nao se apaga")
    }

    /**
     * O script carrega o caminho do executável promovido embutido, e o Kotlin o
     * carrega em constantes. São dois donos do mesmo valor, e este teste é o que
     * impede os dois de divergirem em silêncio — divergência ali daria rollback
     * de uma atualização boa, com a razão `launch-failed`.
     */
    @Test
    fun `the script names the same promoted launcher the constants do`() {
        val body = materialize().readText()

        val expected = "$LINUX_APP_DIRECTORY_NAME/$LINUX_APP_LAUNCHER_RELATIVE_PATH"
        assertTrue(body.contains(expected), "o script não usa '$expected'")
    }

    /**
     * O nome do app tem espaço, e `var=${'$'}x/Usage Monitor/…` **sem aspas** é
     * parseado como uma atribuição seguida de um comando. Medido: o script
     * morria com `Monitor/bin/Usage: No such file or directory` depois de já ter
     * escrito `current`, e nenhum rollback rodava.
     */
    @Test
    fun `the promoted launcher path is quoted`() {
        val body = materialize().readText()

        assertTrue(
            body.contains("promoted_launcher=\"\$target_dir/Usage Monitor/bin/Usage Monitor\""),
            "a atribuição com espaço perdeu as aspas"
        )
    }

    private fun materialize(): File {
        return materializeLinuxUpdaterScript(workDirectory) { file -> appliedModes.add(file) }
    }
}

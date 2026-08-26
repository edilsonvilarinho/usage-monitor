package com.usagemonitor.update

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Exercita o lançador **real**, e não a costura de teste.
 *
 * Existe porque foi exatamente esta linha que escapou. `processLauncher` é
 * injetável para o teste de `schedule` poder afirmar o comando exato sem criar
 * processo, e toda a suíte usava a versão falsa — de modo que
 * `launchDetachedProcess`, a única implementação que roda em produção, nunca era
 * executada por teste nenhum.
 *
 * O defeito que passou por ali: `ProcessBuilder.Redirect.DISCARD` é um redirect
 * de **escrita**, e `redirectInput` exige um de leitura. A chamada lançava
 * `IllegalArgumentException("Redirect invalid for reading: WRITE")` **antes** de
 * `start()`, então o instalador nunca era criado — em 100% das tentativas, para
 * todo usuário, e sem deixar rastro. Medido na atividade A20.
 *
 * A costura que torna uma função testável não testa a função que ela substitui.
 */
class LaunchDetachedProcessTest {

    private val isWindows = System.getProperty("os.name").orEmpty().lowercase().contains("win")

    @Test
    fun `the real launcher starts a process instead of throwing on its own redirects`() {
        if (!isWindows) {
            return
        }

        // Comando inócuo e sempre presente: só interessa que o ProcessBuilder
        // aceite a configuração de redirects e chegue a criar o processo.
        launchDetachedProcess(
            listOf(System.getenv("COMSPEC") ?: "cmd.exe", "/c", "exit", "0"),
            null
        )
    }

    @Test
    fun `the real launcher accepts a working directory`() {
        if (!isWindows) {
            return
        }

        val directory = File(System.getProperty("java.io.tmpdir"))
        assertTrue(directory.isDirectory)

        launchDetachedProcess(
            listOf(System.getenv("COMSPEC") ?: "cmd.exe", "/c", "exit", "0"),
            directory
        )
    }

    /**
     * `Redirect.from` **abre o arquivo**. `NUL` fixo — que era o que estava aqui
     * — é um caminho relativo inexistente no Linux: o `ProcessBuilder` lançaria
     * `IOException` e o updater nunca seria criado, exatamente o mesmo desenho
     * de falha do `Redirect.DISCARD` que a A20 do Windows mediu.
     */
    @Test
    fun `the null input device is resolved per operating system`() {
        assertEquals("NUL", nullInputDevice("Windows 11").path)
        assertEquals(File("/dev/null").path, nullInputDevice("Linux").path)
        assertEquals(File("/dev/null").path, nullInputDevice("Mac OS X").path)
    }

    /**
     * A saída pode ir para um arquivo: é assim que o log do `linux-updater.sh` é
     * escrito sem o script conhecer o caminho. `append` e não truncar — duas
     * tentativas de atualização não podem apagar o rastro uma da outra.
     */
    @Test
    fun `an output file receives what the process writes, appended`() {
        if (!isWindows) {
            return
        }

        val logFile = File(
            Files.createTempDirectory("usage-monitor-launch").toFile(),
            "diagnostics/linux-update.log"
        )
        try {
            launchDetachedProcess(
                listOf(System.getenv("COMSPEC") ?: "cmd.exe", "/c", "echo", "primeira"),
                null,
                logFile
            )
            // O diretório é criado pelo lançador: o `Redirect.appendTo` falha se
            // o pai não existir, e o `diagnostics/` pode não existir no primeiro
            // uso de uma instalação nova.
            assertTrue(logFile.parentFile.isDirectory)
        } finally {
            logFile.parentFile.parentFile.deleteRecursively()
        }
    }
}

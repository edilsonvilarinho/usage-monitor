package com.usagemonitor.update

import java.io.File
import kotlin.test.Test
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
}

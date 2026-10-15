package com.usagemonitor

import java.io.File
import java.io.IOException

/**
 * O `usage-monitor.exe` do Windows (issue #418): o launcher **de console** do
 * jpackage, ao lado do `Usage Monitor.exe`, que é do subsistema GUI.
 *
 * Exe GUI não tem console: chamado do PowerShell ele volta na hora, sem texto e
 * sem código de saída, e pelo `cmd` espera mas o texto some (medido na S0 do
 * plano `comandos-cli-418-execucao.md`). O de console imprime, espera e devolve
 * o código — serve para `--help`, `--version` e `--diagnostics`.
 *
 * **Abrir o app não pode ficar nele.** Preso ao console, fechar o terminal
 * fecharia o app; e `AutoStartManager`/`WindowsInstallOriginResolver` leem
 * `jpackage.app-path`, então a chave `Run` passaria a apontar para um exe que
 * abre console no login. Sem opção de ação, ele relança o exe GUI destacado e
 * sai — o mesmo que o `code` faz.
 */
internal object ConsoleLauncher {
    const val FILE_NAME = "usage-monitor.exe"
    const val GUI_FILE_NAME = "Usage Monitor.exe"

    /**
     * O comando que relança o app pelo exe GUI, ou `null` quando este processo
     * não veio do launcher de console. Função pura: o teste não precisa do exe.
     */
    fun guiRelaunchCommand(isWindows: Boolean, appPath: String?, arguments: Array<String>): List<String>? {
        if (!isWindows) {
            return null
        }
        val launcher = appPath?.trim()?.takeIf { path -> path.isNotEmpty() }?.let(::File) ?: return null
        if (!launcher.name.equals(FILE_NAME, ignoreCase = true)) {
            return null
        }
        val guiExecutable = File(launcher.absoluteFile.parentFile, GUI_FILE_NAME)
        return listOf(guiExecutable.path) + arguments.filter { argument -> argument.isNotBlank() }
    }

    /**
     * Sobe o exe GUI sem herdar o console e devolve o código de saída deste
     * processo. A saída do filho é descartada: com o pai encerrado, um pipe
     * aberto não teria leitor.
     */
    fun relaunch(command: List<String>): Int {
        val guiExecutable = File(command.first())
        if (!guiExecutable.isFile) {
            System.err.println("Could not find ${guiExecutable.path}. Reinstall Usage Monitor.")
            return 1
        }
        return try {
            ProcessBuilder(command)
                .directory(guiExecutable.parentFile)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            0
        } catch (error: IOException) {
            System.err.println("Could not start Usage Monitor: ${error.message}")
            1
        }
    }
}

package com.usagemonitor.terminal

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.terminal.TerminalAnsi
import com.usagemonitor.presentation.terminal.TerminalLabels
import com.usagemonitor.presentation.terminal.TerminalOutput
import com.usagemonitor.presentation.terminal.TerminalProfile
import com.usagemonitor.presentation.terminal.TerminalStatusView
import com.usagemonitor.presentation.terminal.detectTerminalProfile
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintStream
import java.nio.charset.Charset

/**
 * Executa um comando do modo terminal e devolve o código de saída. Roda antes da
 * instância única, do log e do Compose, como `--help`: precisa responder com o app aberto.
 */
internal object TerminalCommands {

    fun run(
        command: TerminalCommand,
        languageOverride: AppLanguage?,
        reader: TerminalReader = TerminalReader(),
        console: TerminalConsole = TerminalConsole.system()
    ): Int {
        val reading = reader.read(languageOverride)
        val labels = TerminalLabels(reading.language)
        val output: TerminalOutput = when (command) {
            is TerminalCommand.Status ->
                TerminalStatusView.render(reading.accounts, reading.now, reading.collectorRunning, command.maxAge, labels, console.profile)
        }
        console.print(TerminalAnsi.render(output.lines, console.profile))
        return output.exitCode
    }
}

/**
 * A saída do processo e o perfil dela. A codificação é a que o console usa de fato:
 * no Windows com console, `sun.stdout.encoding` (a página de código dele); senão, a do
 * sistema (`native.encoding`, JDK 17). Sem UTF-8, o texto já sai sem acento e com os
 * glifos ASCII, e a codificação do console só precisa saber ASCII.
 */
internal class TerminalConsole(val profile: TerminalProfile, private val out: PrintStream) {

    fun print(text: String) {
        out.println(text)
        out.flush()
    }

    companion object {
        fun system(forceTerminal: Boolean = false): TerminalConsole {
            val encoding = System.getProperty("sun.stdout.encoding") ?: System.getProperty("native.encoding") ?: Charset.defaultCharset().name()
            val profile = detectTerminalProfile(
                environment = System.getenv(),
                outputIsTerminal = forceTerminal || System.console() != null,
                outputEncoding = encoding
            )
            val charset = if (profile.unicode) Charsets.UTF_8 else Charsets.US_ASCII
            return TerminalConsole(profile, PrintStream(FileOutputStream(FileDescriptor.out), true, charset.name()))
        }
    }
}

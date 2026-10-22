package com.usagemonitor.terminal

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.terminal.TerminalAnsi
import com.usagemonitor.presentation.terminal.TerminalCheckView
import com.usagemonitor.presentation.terminal.TerminalColor
import com.usagemonitor.presentation.terminal.TerminalSpan
import com.usagemonitor.presentation.terminal.TerminalWaitStep
import com.usagemonitor.presentation.terminal.TerminalWaitView
import com.usagemonitor.presentation.terminal.terminalLine
import com.usagemonitor.presentation.terminal.TerminalLabels
import com.usagemonitor.presentation.terminal.TerminalOutput
import com.usagemonitor.presentation.terminal.TerminalProfile
import com.usagemonitor.presentation.terminal.TerminalStatusView
import com.usagemonitor.presentation.terminal.detectTerminalProfile
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintStream
import java.nio.charset.Charset
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Executa um comando do modo terminal e devolve o código de saída. Roda antes da
 * instância única, do log e do Compose, como `--help`: precisa responder com o app aberto.
 */
internal object TerminalCommands {

    /** De quanto em quanto o `wait` relê o cache. A coleta é de 60 s com sessão ativa. */
    val WAIT_INTERVAL: Duration = 15.seconds

    fun run(
        command: TerminalCommand,
        languageOverride: AppLanguage?,
        reader: TerminalReader = TerminalReader(),
        console: TerminalConsole = TerminalConsole.system(),
        pause: (Duration) -> Unit = { duration -> Thread.sleep(duration.inWholeMilliseconds) }
    ): Int {
        val reading = reader.read(languageOverride)
        val labels = TerminalLabels(reading.language)
        val output: TerminalOutput = when (command) {
            is TerminalCommand.Status ->
                TerminalStatusView.render(reading.accounts, reading.now, reading.collectorRunning, command.maxAge, labels, console.profile)
            is TerminalCommand.Check ->
                TerminalCheckView.render(reading.accounts, reading.now, command.max, command.maxAge, command.quiet, labels, console.profile)
            // O `wait` não imprime uma vez e sai: tem o laço dele, que relê o cache.
            is TerminalCommand.Wait -> return runWait(command, reading, languageOverride, reader, console, pause)
        }
        // O JSON sai sempre, mesmo sem leitura: quem lê decide pelo código e pelos campos.
        if (command is TerminalCommand.Status && command.json) {
            console.print(TerminalJson.encode(reading, asciiOnly = !console.profile.unicode))
        } else if (output.lines.isNotEmpty()) {
            console.print(TerminalAnsi.render(output.lines, console.profile))
        }
        return output.exitCode
    }

    /**
     * O laço do `wait`: relê o cache a cada [WAIT_INTERVAL] e só reescreve a linha quando o
     * percentual muda. Nenhuma requisição: quem atualiza o número é o coletor.
     */
    private fun runWait(
        command: TerminalCommand.Wait,
        firstReading: TerminalReading,
        languageOverride: AppLanguage?,
        reader: TerminalReader,
        console: TerminalConsole,
        pause: (Duration) -> Unit
    ): Int {
        var reading = firstReading
        val deadline = command.timeout?.let { timeout -> reading.now + timeout }
        var shownPercent: Int? = null
        var warnedCollector = false
        while (true) {
            val labels = TerminalLabels(reading.language)
            when (val step = TerminalWaitView.evaluate(reading.accounts, command.quotaId, command.below, reading.now, labels, console.profile)) {
                is TerminalWaitStep.Finished -> {
                    console.print(TerminalAnsi.render(step.lines, console.profile))
                    return step.exitCode
                }
                is TerminalWaitStep.Waiting -> {
                    if (!reading.collectorRunning && !warnedCollector) {
                        console.print(TerminalAnsi.render(terminalLine(TerminalSpan(console.profile.glyphs.warning + " " + labels.waitNeedsCollector, TerminalColor.Warn)), console.profile))
                        warnedCollector = true
                    }
                    if (step.percent != shownPercent) {
                        console.print(TerminalAnsi.render(step.line, console.profile))
                        shownPercent = step.percent
                    }
                }
            }
            if (deadline != null && reading.now >= deadline) {
                val timedOut = TerminalWaitView.timedOut(command.quotaId, command.below, labels)
                console.print(TerminalAnsi.render(timedOut.lines, console.profile))
                return timedOut.exitCode
            }
            pause(deadline?.let { limit -> minOf(WAIT_INTERVAL, limit - reading.now) } ?: WAIT_INTERVAL)
            reading = reader.read(languageOverride)
        }
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

package com.usagemonitor.terminal

import com.usagemonitor.data.datasource.LocalCliSessionMetricsReader
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CliSessionMetrics
import com.usagemonitor.presentation.terminal.TerminalAnsi
import com.usagemonitor.presentation.terminal.TerminalCheckView
import com.usagemonitor.presentation.terminal.TerminalColor
import com.usagemonitor.presentation.terminal.TerminalSpan
import com.usagemonitor.presentation.terminal.TerminalStatusLineView
import com.usagemonitor.presentation.terminal.TerminalTmux
import com.usagemonitor.presentation.terminal.TerminalWaitStep
import com.usagemonitor.presentation.terminal.TerminalWaitView
import com.usagemonitor.presentation.terminal.terminalLine
import com.usagemonitor.presentation.terminal.withoutAccents
import com.usagemonitor.presentation.terminal.TerminalLabels
import com.usagemonitor.presentation.terminal.TerminalOutput
import com.usagemonitor.presentation.terminal.TerminalProfile
import com.usagemonitor.presentation.terminal.TerminalStatusView
import com.usagemonitor.presentation.terminal.detectTerminalProfile
import java.io.File
import java.io.FileDescriptor
import java.io.FileOutputStream
import java.io.PrintStream
import java.nio.charset.Charset
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Executa um comando do modo terminal e devolve o código de saída. Roda antes da
 * instância única, do log e do Compose, como `--help`: precisa responder com o app aberto.
 */
internal object TerminalCommands {

    private const val SLF4J_VERBOSITY_PROPERTY = "slf4j.internal.verbosity"

    /** A linha de status sempre sai com 0: código diferente faria o Claude Code esconder a linha. */
    private const val EXIT_STATUS_LINE = 0

    /** De quanto em quanto o `wait` relê o cache. A coleta é de 60 s com sessão ativa. */
    val WAIT_INTERVAL: Duration = 15.seconds

    fun run(
        command: TerminalCommand,
        languageOverride: AppLanguage?,
        reader: TerminalReader = TerminalReader(),
        // A linha de status sai por um pipe que o Claude Code e o tmux desenham com cor.
        console: TerminalConsole = TerminalConsole.system(forceTerminal = command.isStatusLine),
        pause: (Duration) -> Unit = { duration -> Thread.sleep(duration.inWholeMilliseconds) },
        statusLineInput: () -> String = { readPipedInput() },
        sessionMetrics: (String) -> CliSessionMetrics? = LocalCliSessionMetricsReader()::read,
        environment: Map<String, String> = System.getenv(),
        home: File = File(System.getProperty("user.home").orEmpty())
    ): Int {
        // O driver do SQLite avisa no `stderr` que não há provedor de log; no app esse texto
        // vai para o `app.log`, mas no terminal sujaria a saída de quem chamou.
        System.setProperty(SLF4J_VERBOSITY_PROPERTY, "ERROR")
        quietPreferencesLog()
        return when (command) {
            is TerminalCommand.Reading -> runReading(command, languageOverride, reader, console, pause, statusLineInput, sessionMetrics, environment, home)
            is ServiceCommand -> TerminalServiceCommands.run(command, languageOverride, console)
        }
    }

    private fun runReading(
        command: TerminalCommand.Reading,
        languageOverride: AppLanguage?,
        reader: TerminalReader,
        console: TerminalConsole,
        pause: (Duration) -> Unit,
        statusLineInput: () -> String,
        sessionMetrics: (String) -> CliSessionMetrics?,
        environment: Map<String, String>,
        home: File
    ): Int {
        val reading = reader.read(languageOverride)
        val labels = TerminalLabels(reading.language)
        val output: TerminalOutput = when (command) {
            is TerminalCommand.Status ->
                TerminalStatusView.render(reading.accounts, reading.now, reading.collector, command.maxAge, labels, console.profile)
            is TerminalCommand.Check ->
                TerminalCheckView.render(reading.accounts, reading.now, command.max, command.maxAge, command.quiet, labels, console.profile)
            // O `wait` não imprime uma vez e sai: tem o laço dele, que relê o cache.
            is TerminalCommand.Wait -> return runWait(command, reading, languageOverride, reader, console, pause)
            TerminalCommand.ClaudeCodeStatusLine -> {
                val input = ClaudeCodeStatusInput.parse(statusLineInput())
                val target = anthropicTargetFor(claudeConfigDirectory(environment, input, home), reader.anthropicProfiles())
                val account = target?.let { key -> reading.accounts.firstOrNull { item -> item.storageKey == key.storageKey } }
                val line = TerminalStatusLineView.claudeCode(account, input.sessionId?.let(sessionMetrics), input.model, reading.now, labels, console.profile)
                TerminalOutput(listOf(line), EXIT_STATUS_LINE)
            }
            TerminalCommand.TmuxStatusLine -> {
                // Formato do tmux, e não ANSI: sai direto, sem passar pelo renderizador de linhas.
                console.print(TerminalTmux.render(reading.accounts, reading.now, labels, console.profile))
                return EXIT_STATUS_LINE
            }
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
 * O `java.util.prefs` avisa no `stderr` ("Created user preferences directory") na primeira
 * execução num computador novo, como um servidor recém-instalado: sujaria a saída. O
 * logger fica guardado aqui porque o `java.util.logging` só guarda referência fraca.
 */
private val preferencesLogger: java.util.logging.Logger = java.util.logging.Logger.getLogger("java.util.prefs")

internal fun quietPreferencesLog() {
    preferencesLogger.level = java.util.logging.Level.WARNING
}

private val TerminalCommand.isStatusLine: Boolean
    get() = this is TerminalCommand.ClaudeCodeStatusLine || this is TerminalCommand.TmuxStatusLine

/**
 * O que veio pela entrada, para a linha de status. Num terminal interativo (quem rodou o
 * comando à mão) não há o que ler, e esperar travaria. Com a entrada num pipe, lê até o fim,
 * mas com teto de tempo: um pipe que nunca fecha não pode segurar o prompt.
 */
internal fun readPipedInput(timeoutMillis: Long = 1_000L): String {
    if (System.console() != null) {
        return ""
    }
    val reading = CompletableFuture.supplyAsync { System.`in`.readBytes().toString(Charsets.UTF_8) }
    return runCatching { reading.get(timeoutMillis, TimeUnit.MILLISECONDS) }.getOrDefault("")
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

    /** Texto corrido (ajuda, diagnóstico): sem acento quando a saída não é UTF-8. */
    fun printText(text: String) {
        print(if (profile.unicode) text else withoutAccents(text))
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

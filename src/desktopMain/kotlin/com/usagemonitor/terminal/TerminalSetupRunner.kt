package com.usagemonitor.terminal

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.displayName
import com.usagemonitor.domain.entity.requiresLocalApiKey
import com.usagemonitor.presentation.terminal.TerminalAnsi
import com.usagemonitor.presentation.terminal.TerminalColor
import com.usagemonitor.presentation.terminal.TerminalConfigView
import com.usagemonitor.presentation.terminal.TerminalSetupLabels
import com.usagemonitor.presentation.terminal.TerminalSpan
import com.usagemonitor.presentation.terminal.terminalLine
import java.io.BufferedReader
import java.io.InputStreamReader

/** Pergunta ao usuário: a resposta com eco (`line`) ou sem (`secret`); `null` sem entrada. */
internal interface TerminalPrompt {
    fun line(prompt: String): String?

    fun secret(prompt: String): String?

    companion object {
        val SYSTEM = object : TerminalPrompt {
            private val input by lazy { BufferedReader(InputStreamReader(System.`in`, Charsets.UTF_8)) }

            override fun line(prompt: String): String? {
                val console = System.console()
                if (console != null) return console.readLine("%s ", prompt)
                return piped(prompt)
            }

            override fun secret(prompt: String): String? {
                val console = System.console()
                if (console != null) return console.readPassword("%s ", prompt)?.let { chars -> String(chars) }
                return piped(prompt)
            }

            /**
             * Resposta por pipe (um script, o `setup` num servidor): sem terminal não há o
             * enter de quem digitou, e a próxima linha grudaria na pergunta (medido na A14).
             */
            private fun piped(prompt: String): String? {
                print("$prompt ")
                System.out.flush()
                val answer = input.readLine()
                println()
                return answer
            }
        }
    }
}

/** Uma conta pronta (logada) que o assistente oferece monitorar. */
internal data class SetupAccount(val source: ApiSource, val reference: String, val monitored: Boolean)

/**
 * `usage-monitor setup` (#440, 4.1): o assistente da primeira vez, em cinco passos. Cada
 * passo chama os mesmos comandos de configurar (`accounts`, `keys`, `config`,
 * `telegram`, `service`), então o assistente não tem regra própria: só pergunta e
 * encadeia. Pode ser refeito a qualquer momento.
 */
internal class TerminalSetupRunner(
    private val prompt: TerminalPrompt,
    /** Roda um comando de configurar com o segredo já lido, e devolve o código e o texto. */
    private val config: (ConfigCommand, String?) -> Pair<Int, String>,
    /** O pareamento escreve enquanto espera: roda direto no terminal. */
    private val configLive: (ConfigCommand) -> Int,
    private val service: (ServiceCommand) -> Pair<Int, String>,
    private val readyAccounts: () -> List<SetupAccount>,
    private val currentThresholds: () -> String,
    private val currentLanguage: () -> String,
    private val isLinux: Boolean,
    private val dataRoot: String,
    private val version: String
) {

    fun run(labels: TerminalSetupLabels, console: TerminalConsole): Int {
        val glyphs = console.profile.glyphs
        fun line(vararg spans: TerminalSpan) = console.print(TerminalAnsi.render(listOf(terminalLine(*spans)), console.profile))
        fun indented(text: String) = text.trimEnd().lines().filter { row -> row.isNotEmpty() }.forEach { row -> console.print("     $row") }
        fun title(number: Int, text: String, hint: String = "") = line(TerminalSpan(labels.step(number, text), bold = true), TerminalSpan(hint, TerminalColor.Muted))

        line(TerminalSpan("Usage Monitor $version", TerminalColor.Gold, bold = true), TerminalSpan(labels.header(dataRoot), TerminalColor.Muted))
        console.print("")

        // 1/5 · contas: procura de novo e oferece monitorar as que estão logadas.
        title(1, labels.accountsTitle)
        config(AccountsCommand.Rescan, null)
        indented(config(AccountsCommand.List, null).second)
        val pending = readyAccounts().filterNot { account -> account.monitored }
        if (readyAccounts().isEmpty()) {
            indented(labels.noReadyAccounts)
        } else if (pending.isNotEmpty() && labels.answer(prompt.line("     " + labels.monitorAccounts(pending.size) + " [S/n]"), default = true)) {
            for (account in pending) {
                val command = if (account.source == ApiSource.CODEX) SourcesCommand.Switch(ApiSource.CODEX, true) else AccountsCommand.Switch(account.reference, true)
                indented(config(command, null).second)
            }
        }
        console.print("")

        // 2/5 · chaves: enter pula; a chave é testada e gravada pelo `keys set`.
        title(2, labels.keysTitle, labels.keysHint)
        for (source in ApiSource.entries.filter { candidate -> candidate.requiresLocalApiKey() }) {
            val key = prompt.secret(labels.keyPrompt(source.displayName()))?.trim()
            if (key.isNullOrEmpty()) {
                line(TerminalSpan("     " + glyphs.skipped + " " + source.displayName() + " ", TerminalColor.Muted), TerminalSpan(labels.skipped, TerminalColor.Muted))
            } else {
                indented(config(KeysCommand.Set(source), key).second)
            }
        }
        console.print("")

        // 3/5 · avisos: os limiares gerais e, se quiser, o bot do Telegram.
        title(3, labels.alertsTitle)
        prompt.line(labels.thresholdsPrompt(currentThresholds()))?.trim()?.takeIf { text -> text.isNotEmpty() }?.let { text ->
            indented(config(SettingCommand.Set("alerts.quota.thresholds", text), null).second)
        }
        var telegramReady = false
        if (labels.answer(prompt.line(labels.telegramPrompt), default = false)) {
            val token = prompt.secret(labels.telegramTokenPrompt)?.trim()
            if (!token.isNullOrEmpty()) {
                val (code, text) = config(TelegramCommand.Setup, token)
                indented(text.lines().take(1).joinToString("\n"))
                telegramReady = code == 0
                if (telegramReady) indented(labels.pairLater)
            }
        }
        console.print("")

        // 4/5 · idioma.
        prompt.line(labels.step(4, labels.languageTitle(currentLanguage())))?.trim()?.takeIf { text -> text.isNotEmpty() }?.let { text ->
            indented(config(SettingCommand.Set("language", text), null).second)
        }
        console.print("")

        // 5/5 · segundo plano: o serviço do systemd (só no Linux) e, com ele de pé, o pareamento.
        title(5, labels.backgroundTitle)
        if (!isLinux) {
            indented(labels.notLinux)
        } else if (labels.answer(prompt.line(labels.installPrompt), default = true)) {
            val (installed, text) = service(ServiceCommand.Install)
            indented(text)
            if (installed == 0 && telegramReady && labels.answer(prompt.line(labels.pairNowPrompt), default = true)) {
                configLive(TelegramCommand.Pair())
            }
        }
        console.print("")
        line(TerminalSpan(labels.done, TerminalColor.Ok), TerminalSpan(labels.seeWith), TerminalSpan("usage-monitor status", bold = true), TerminalSpan("."))
        console.print(TerminalAnsi.render(listOf(TerminalConfigView.note(labels.canRedo)), console.profile))
        return 0
    }
}

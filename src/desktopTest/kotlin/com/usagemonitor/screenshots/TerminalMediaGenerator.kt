package com.usagemonitor.screenshots

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.CollectorKind
import com.usagemonitor.presentation.terminal.TerminalColor
import com.usagemonitor.presentation.terminal.TerminalLabels
import com.usagemonitor.presentation.terminal.TerminalLine
import com.usagemonitor.presentation.terminal.TerminalPalette
import com.usagemonitor.presentation.terminal.TerminalProfile
import com.usagemonitor.presentation.terminal.TerminalSpan
import com.usagemonitor.presentation.terminal.TerminalStatusView
import com.usagemonitor.presentation.terminal.TuiKey
import com.usagemonitor.presentation.terminal.TuiLabels
import com.usagemonitor.presentation.terminal.TuiNotice
import com.usagemonitor.presentation.terminal.TuiReducer
import com.usagemonitor.presentation.terminal.TuiScreen
import com.usagemonitor.presentation.terminal.TuiSize
import com.usagemonitor.presentation.terminal.TuiState
import java.io.File

/**
 * O GIF do modo terminal no README (`gradlew generateTerminalMedia`): `usage-monitor status`
 * digitado no shell e, depois, `usage-monitor tui` passando pelas cinco abas. Cada quadro
 * sai do `TerminalStatusView` e do `TuiScreen` do app, e cada tecla passa pelo `TuiReducer`,
 * como no laço de verdade; os dados são os de [TerminalMediaFixtures]. Grava `terminal.gif`
 * (inglês) e `terminal-pt.gif` (português).
 */
fun main(args: Array<String>) {
    val output = File(args.firstOrNull() ?: "img")
    output.mkdirs()
    TerminalMediaStoryboard(AppLanguage.EN).write(File(output, "terminal.gif"))
    TerminalMediaStoryboard(AppLanguage.PT).write(File(output, "terminal-pt.gif"))
}

private class TerminalMediaStoryboard(private val language: AppLanguage) {
    private val size = TuiSize(COLUMNS, ROWS)
    private val profile = TerminalProfile(TerminalPalette.TRUECOLOR_DARK, unicode = true)
    private val labels = TuiLabels(language)
    private val fixtures = TerminalMediaFixtures(language)
    private val renderer = TerminalFrameRenderer(COLUMNS, ROWS)
    private val frames = mutableListOf<GifFrame>()

    fun write(file: File) {
        shell("usage-monitor status")
        val status = TerminalStatusView.render(fixtures.accounts, fixtures.now, CollectorKind.SERVICE, null, TerminalLabels(language), profile).lines
        show(listOf(prompt("usage-monitor status", cursor = false)) + status + listOf(TerminalLine.EMPTY, prompt("", cursor = true)), 3_200)
        shell("usage-monitor tui")
        tui()
        show(listOf(prompt("", cursor = true)), 1_200)
        GifEncoder.write(file, frames)
        println("${file.path}: ${frames.size} quadros, ${file.length() / 1024} KB")
    }

    /** O comando digitado letra a letra, depois do prompt. */
    private fun shell(command: String) {
        for (end in 0..command.length) {
            show(listOf(prompt(command.take(end), cursor = true)), if (end == command.length) 500 else TYPING_MILLIS)
        }
    }

    private fun tui() {
        var data = fixtures.data()
        var state = TuiState()
        fun press(key: TuiKey, millis: Int) {
            val step = TuiReducer.reduce(state, key, TuiScreen.context(data, size))
            state = step.state
            show(TuiScreen.render(state, data, size, labels, profile), millis)
        }
        show(TuiScreen.render(state, data, size, labels, profile), 2_200)
        press(TuiKey.Down, 1_300)
        press(TuiKey.Text('2'), 2_400)
        press(TuiKey.Text('3'), 2_200)
        // A tabela de janelas e, no `enter`, o gráfico alto da janela aberta (#463).
        press(TuiKey.Enter, 2_600)
        press(TuiKey.Escape, 300)
        press(TuiKey.Text('4'), 2_200)
        press(TuiKey.Text('5'), 900)
        press(TuiKey.Down, 350)
        press(TuiKey.Down, 600)
        press(TuiKey.Enter, 900)
        press(TuiKey.Down, 350)
        press(TuiKey.Down, 700)
        // O espaço roda o `config set` pelo serviço; o aviso e o campo relido, como na TUI.
        val step = TuiReducer.reduce(state, TuiKey.Space, TuiScreen.context(data, size))
        data = fixtures.data(resetAlerts = false)
        val applied = localizedNotice()
        state = step.state.copy(notice = applied)
        show(TuiScreen.render(state, data, size, labels, profile), 2_800)
    }

    private fun localizedNotice() =
        TuiNotice(
            "alerts.reset.enabled = false · " + if (language == AppLanguage.PT) "o serviço já está usando" else "the service is already using it",
            ok = true
        )

    private fun prompt(typed: String, cursor: Boolean): TerminalLine {
        val spans = mutableListOf(TerminalSpan("dev@server", TerminalColor.Ok, bold = true), TerminalSpan(":"), TerminalSpan("~", TerminalColor.Input, bold = true), TerminalSpan("$ "), TerminalSpan(typed))
        if (cursor) {
            spans += TerminalSpan("█", TerminalColor.Muted)
        }
        return TerminalLine(spans)
    }

    private fun show(lines: List<TerminalLine>, millis: Int) {
        frames += GifFrame(renderer.render(lines), millis)
    }

    private companion object {
        // A tabela do `status` com estes nomes passa de 100 colunas; a TUI desenha a moldura de 96 dentro delas, como num terminal largo.
        const val COLUMNS = 106
        const val ROWS = 26
        const val TYPING_MILLIS = 55
    }
}

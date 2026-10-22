package com.usagemonitor.terminal

import com.usagemonitor.presentation.terminal.TerminalAnsi
import com.usagemonitor.presentation.terminal.TerminalProfile
import com.usagemonitor.presentation.terminal.TuiData
import com.usagemonitor.presentation.terminal.TuiEffect
import com.usagemonitor.presentation.terminal.TuiLabels
import com.usagemonitor.presentation.terminal.TuiNotice
import com.usagemonitor.presentation.terminal.TuiReducer
import com.usagemonitor.presentation.terminal.TuiSettingAction
import com.usagemonitor.presentation.terminal.TuiScreen
import com.usagemonitor.presentation.terminal.TuiState
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** `usage-monitor tui` (#441): o modo interativo. */
internal data object TuiCommand : TerminalCommand

internal object TuiCommandNames {
    const val TUI = "tui"
}

/**
 * O laço do modo interativo: lê, desenha, espera uma tecla até a próxima releitura e aplica
 * a tecla pelo [TuiReducer]. Redesenho completo a cada mudança, sem animação (decisão D3);
 * a releitura é a cada [REFRESH_INTERVAL] (a do `wait`) e com `r`.
 */
internal class TuiRunner(
    private val io: TuiIo,
    private val load: (TuiState) -> TuiData,
    private val exportSessions: (TuiState) -> TuiNotice? = { null },
    /** Uma mudança da aba 5, pelo comando da 4.3 que a faz; devolve a saída dele como aviso. */
    private val applySetting: (TuiSettingAction) -> TuiNotice? = { null },
    private val labels: TuiLabels,
    private val profile: TerminalProfile,
    private val clock: Clock = Clock.System,
    private val refreshInterval: Duration = REFRESH_INTERVAL
) {
    fun run(): Int {
        var state = TuiState()
        var data = load(state)
        var nextRefresh = clock.now() + refreshInterval
        draw(state, data)
        while (true) {
            val wait = (nextRefresh - clock.now()).inWholeMilliseconds.coerceAtLeast(1L)
            val key = io.readKey(wait)
            if (key == null) {
                if (clock.now() >= nextRefresh) {
                    data = load(state)
                    nextRefresh = clock.now() + refreshInterval
                    state = state.normalized(TuiScreen.context(data, io.size()))
                    draw(state, data)
                } else if (io.consumeResize()) {
                    draw(state, data)
                }
                continue
            }
            val step = TuiReducer.reduce(state, key, TuiScreen.context(data, io.size()))
            if (TuiEffect.Quit in step.effects) {
                return EXIT_OK
            }
            state = step.state
            if (TuiEffect.ExportSessions in step.effects) {
                state = state.copy(notice = exportSessions(state))
            }
            val change = step.effects.filterIsInstance<TuiEffect.ApplySetting>().firstOrNull()
            if (change != null) {
                // Testar uma chave ou esperar o coletor leva segundos: a tela diz que está aplicando.
                draw(state.copy(notice = TuiNotice(labels.applying, ok = true, pending = true)), data)
                state = state.copy(notice = applySetting(change.action))
            }
            if (TuiEffect.Refresh in step.effects || change != null) {
                data = load(state)
                nextRefresh = clock.now() + refreshInterval
            }
            state = state.normalized(TuiScreen.context(data, io.size()))
            draw(state, data)
        }
    }

    private fun draw(state: TuiState, data: TuiData) {
        val lines = TuiScreen.render(state, data, io.size(), labels, profile)
        io.draw(lines.map { line -> TerminalAnsi.render(line, profile) })
    }

    companion object {
        const val EXIT_OK = 0
        const val EXIT_NO_TERMINAL = 1
        val REFRESH_INTERVAL: Duration = 15.seconds
    }
}

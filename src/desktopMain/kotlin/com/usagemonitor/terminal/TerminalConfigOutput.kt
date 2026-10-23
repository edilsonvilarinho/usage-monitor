package com.usagemonitor.terminal

import com.usagemonitor.ConfigOutcome
import com.usagemonitor.presentation.terminal.TerminalConfigLabels
import com.usagemonitor.presentation.terminal.TerminalConfigView
import com.usagemonitor.presentation.terminal.TerminalLine
import com.usagemonitor.presentation.terminal.TerminalOutput
import com.usagemonitor.presentation.terminal.TerminalProfile

/**
 * A saída de uma mudança entregue: `✓` com a consequência, a recusa que o comando
 * explica ([refusal]), ou o aviso genérico.
 */
internal fun delivered(
    delivery: ConfigDelivery,
    labels: TerminalConfigLabels,
    profile: TerminalProfile,
    doneText: String,
    refusal: (ConfigOutcome.Refused) -> List<TerminalLine>?
): TerminalOutput =
    when (delivery) {
        is ConfigDelivery.NotAnswered ->
            TerminalOutput(TerminalConfigView.warning(labels.notAnswered(delivery.collector, delivery.timeoutMillis), null, profile.glyphs), TerminalConfigView.EXIT_REFUSED)
        is ConfigDelivery.Answered -> when (val outcome = delivery.outcome) {
            is ConfigOutcome.Applied -> {
                val consequence = if (outcome.restartNeeded) labels.restartNeeded(delivery.collector) else labels.appliedBy(delivery.collector)
                TerminalOutput(listOf(TerminalConfigView.done(doneText, consequence, profile.glyphs)), TerminalConfigView.EXIT_OK)
            }
            is ConfigOutcome.Refused -> TerminalOutput(
                refusal(outcome) ?: TerminalConfigView.warning(labels.failed(outcome.detail), null, profile.glyphs),
                TerminalConfigView.EXIT_REFUSED
            )
        }
    }

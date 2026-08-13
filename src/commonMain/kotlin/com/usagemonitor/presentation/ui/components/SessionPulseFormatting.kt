package com.usagemonitor.presentation.ui.components

import com.usagemonitor.domain.entity.ActiveSessionAlert
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.SessionPulse
import com.usagemonitor.presentation.ui.CliSessionsLabels
import com.usagemonitor.presentation.ui.shortSessionId

/** Quantas sessões o texto detalha antes de resumir o resto em "e mais N". */
private const val SESSION_PULSE_MAX_DETAILED_ALERTS = 3

/**
 * Explica por que o botão está piscando.
 *
 * Vai para a tooltip e para o `contentDescription`: um botão que pisca sem dizer
 * o motivo obriga o usuário a abrir o modal só para descobrir o que mudou — que é
 * exatamente o clique que o semáforo deveria poupar.
 *
 * `null` quando não há nada a sinalizar, e nesse caso o rótulo do botão fica como
 * era.
 */
internal fun sessionPulseHint(pulse: SessionPulse, language: AppLanguage): String? {
    if (!pulse.isPulsing) {
        return null
    }

    val detailed = pulse.alerts.take(SESSION_PULSE_MAX_DETAILED_ALERTS)
    val remaining = pulse.alerts.size - detailed.size

    val lines = mutableListOf(sessionPulseHeadline(pulse.alerts.size, language))
    detailed.forEach { alert ->
        lines += "• ${sessionPulseAlertLine(alert, language)}"
    }
    if (remaining > 0) {
        lines += if (language == AppLanguage.PT) "• e mais $remaining" else "• and $remaining more"
    }

    return lines.joinToString(separator = "\n")
}

private fun sessionPulseHeadline(alertCount: Int, language: AppLanguage): String {
    return if (language == AppLanguage.PT) {
        if (alertCount == 1) {
            "1 sessão ativa agora pede atenção:"
        } else {
            "$alertCount sessões ativas agora pedem atenção:"
        }
    } else {
        if (alertCount == 1) {
            "1 active session needs attention:"
        } else {
            "$alertCount active sessions need attention:"
        }
    }
}

/**
 * Uma sessão em uma linha.
 *
 * Pessoa e máquina só aparecem quando o pulso vem do time: na visão local a
 * máquina é sempre esta, e repeti-la em cada linha seria ruído.
 */
private fun sessionPulseAlertLine(alert: ActiveSessionAlert, language: AppLanguage): String {
    val status = CliSessionsLabels.healthShort(alert.health, language)
    val place = alert.projectName ?: shortSessionId(alert.sessionId)
    // `machineLabel` cai no apelido quando o host é desconhecido; repetir o mesmo
    // nome dos dois lados do separador não informaria nada.
    val who = listOfNotNull(
        alert.memberAlias?.takeIf { alias -> alias.isNotBlank() },
        alert.machineLabel?.takeIf { machine -> machine.isNotBlank() }
    ).distinct().joinToString(separator = " · ")

    if (who.isEmpty()) {
        return "$status — $place"
    }
    return "$who — $status ($place)"
}

package com.usagemonitor.presentation.terminal

import com.usagemonitor.presentation.ui.displayQuotaLabel
import kotlin.time.Instant

/** Um passo da espera do `wait`: terminou (com código) ou continua esperando. */
internal sealed interface TerminalWaitStep {
    data class Finished(val lines: List<TerminalLine>, val exitCode: Int) : TerminalWaitStep

    /** Continua; [percent] é o valor de agora, para só reescrever a linha quando ele muda. */
    data class Waiting(val line: TerminalLine, val percent: Int) : TerminalWaitStep
}

/**
 * `usage-monitor wait <id> --below <n>` (tela 2.3): decide, a cada releitura do cache, se a
 * cota já caiu abaixo de n%. Função pura; o laço e a pausa ficam em `desktopMain`.
 *
 * Quem reinicia a cota é a coleta do app: com a janela já vencida e o app ainda sem coletar,
 * o número é o antigo e a espera continua. Esperar pelo relógio do reinício diria que a cota
 * caiu antes de o app saber disso.
 */
internal object TerminalWaitView {
    const val EXIT_REACHED = 0
    const val EXIT_TIMEOUT = 1
    const val EXIT_UNKNOWN_QUOTA = 2
    const val EXIT_NO_READING = 3

    fun evaluate(
        accounts: List<TerminalAccount>,
        quotaId: String,
        below: Int,
        now: Instant,
        labels: TerminalLabels,
        profile: TerminalProfile
    ): TerminalWaitStep {
        if (accounts.isEmpty()) {
            return finished(TerminalSpan(labels.noReadingWord, TerminalColor.Crit), "  " + labels.noReadingYet, EXIT_NO_READING)
        }
        val match = accounts.firstNotNullOfOrNull { account ->
            account.quotas.firstOrNull { quota -> terminalQuotaId(account, quota) == quotaId.trim().lowercase() }?.let { quota -> account to quota }
        }
        if (match == null) {
            val known = accounts.flatMap { account -> account.quotas.map { quota -> terminalQuotaId(account, quota) } }
            return finished(TerminalSpan(labels.unknownQuota(quotaId), TerminalColor.Crit), "  " + labels.knownQuotas(known), EXIT_UNKNOWN_QUOTA)
        }
        val (account, quota) = match
        val name = "${displayQuotaLabel(quota.label, labels.pt)} (${listOfNotNull(account.name, account.profile).joinToString(" · ")})"
        val percent = quota.percent
            ?: return finished(TerminalSpan(labels.noPercent(name), TerminalColor.Crit), "", EXIT_UNKNOWN_QUOTA)
        if (percent < below) {
            return finished(TerminalSpan(profile.glyphs.done + " ", TerminalColor.Ok), labels.reachedBelow(name, percent, below), EXIT_REACHED)
        }
        val reset = quota.resetsAt?.takeIf { at -> at > now }?.let { at -> " · " + labels.resetsAtIn(labels.resetAt(at, now), at - now) }.orEmpty()
        val line = terminalLine(TerminalSpan(labels.waitingFor(name, percent, below) + reset + "…", TerminalColor.Muted))
        return TerminalWaitStep.Waiting(line, percent)
    }

    fun timedOut(quotaId: String, below: Int, labels: TerminalLabels): TerminalWaitStep.Finished =
        TerminalWaitStep.Finished(listOf(terminalLine(TerminalSpan(labels.waitTimedOut(quotaId, below), TerminalColor.Warn))), EXIT_TIMEOUT)

    private fun finished(head: TerminalSpan, rest: String, exitCode: Int): TerminalWaitStep.Finished {
        val spans = listOfNotNull(head, rest.takeIf { text -> text.isNotEmpty() }?.let(::TerminalSpan))
        return TerminalWaitStep.Finished(listOf(TerminalLine(spans)), exitCode)
    }
}

package com.usagemonitor.presentation.terminal

import com.usagemonitor.domain.entity.QuotaZone
import com.usagemonitor.domain.entity.UsageSnapshotQuota
import com.usagemonitor.presentation.ui.displayQuotaLabel
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * O identificador estável de uma cota para scripts: `fonte[:conta]:cota`, em minúsculas,
 * sem acento e com hífen no lugar de espaço (`anthropic:pessoal:claude-5h`,
 * `opencode_go:go-semanal`). Sai no `status --json` e é o que o `wait` recebe.
 * Usa o rótulo gravado, e não o traduzido: o id não muda com o idioma.
 */
fun terminalQuotaId(account: TerminalAccount, quota: UsageSnapshotQuota): String =
    listOfNotNull(account.source.name, account.profile, quota.label).joinToString(":") { part -> idPart(part) }

private fun idPart(text: String): String =
    withoutAccents(text.trim().lowercase())
        .map { char -> if (char.isLetterOrDigit() || char == '_' || char == '.') char else '-' }
        .joinToString("")
        .replace(Regex("-+"), "-")
        .trim('-')

/**
 * `usage-monitor check` (tela 2.3): decide pelo código de saída, para scripts. Sem
 * `--max`, pela zona de cada cota (os limiares da conta): 0 em dia, 1 atenção, 2 crítico.
 * Com `--max <n>`, 2 quando alguma cota chegou a n%, senão 0. Sem leitura, ou leitura mais
 * velha que `--max-age`: 3. Imprime as cotas que pesaram na decisão; `--quiet` não imprime.
 *
 * O 2 de "crítico" coincide com o 2 de uso incorreto do #418. Script que precise separar
 * os dois valida o comando uma vez, ou lê o `status --json`.
 */
internal object TerminalCheckView {
    const val EXIT_OK = 0
    const val EXIT_ATTENTION = 1
    const val EXIT_CRITICAL = 2
    const val EXIT_NO_READING = 3

    fun render(
        accounts: List<TerminalAccount>,
        now: Instant,
        max: Int?,
        maxAge: Duration?,
        quiet: Boolean,
        labels: TerminalLabels,
        profile: TerminalProfile
    ): TerminalOutput {
        val lastCollectedAt = accounts.mapNotNull { account -> account.fetchedAt }.maxOrNull()
        val stale = lastCollectedAt != null && maxAge != null && now - lastCollectedAt > maxAge
        if (lastCollectedAt == null || stale) {
            val reason = if (lastCollectedAt == null) labels.noReadingYet else labels.staleReading(now - lastCollectedAt, maxAge!!)
            val line = terminalLine(TerminalSpan(labels.noReadingWord, TerminalColor.Crit), TerminalSpan("  $reason"))
            return TerminalOutput(if (quiet) emptyList() else listOf(line), EXIT_NO_READING)
        }
        val entries = accounts.flatMap { account -> account.quotas.map { quota -> Triple(account, quota, zoneFor(account, quota, max)) } }
        val flagged = entries.filter { (_, _, zone) -> zone == QuotaZone.CRITICAL || zone == QuotaZone.ATTENTION }
        val exit = when {
            flagged.any { (_, _, zone) -> zone == QuotaZone.CRITICAL } -> EXIT_CRITICAL
            flagged.isNotEmpty() -> EXIT_ATTENTION
            else -> EXIT_OK
        }
        if (quiet) {
            return TerminalOutput(emptyList(), exit)
        }
        val lines = if (flagged.isEmpty()) {
            listOf(highestLine(entries, labels, profile))
        } else {
            flagged.sortedByDescending { (_, _, zone) -> zone?.ordinal ?: -1 }.map { (account, quota, zone) ->
                val limit = max?.let { value -> " (${labels.limit(value)})" }.orEmpty()
                terminalLine(TerminalPrimitives.zoneWord(zone!!, labels, profile), TerminalSpan("  " + describe(account, quota, labels) + limit))
            }
        }
        return TerminalOutput(lines, exit)
    }

    /** Com `--max`, só existe passou ou não passou; sem ele, a zona dos limiares da conta. */
    private fun zoneFor(account: TerminalAccount, quota: UsageSnapshotQuota, max: Int?): QuotaZone? {
        val percent = quota.percent ?: return null
        if (max == null) {
            return account.zoneOf(quota)
        }
        return if (percent >= max) QuotaZone.CRITICAL else QuotaZone.ON_TRACK
    }

    private fun highestLine(entries: List<Triple<TerminalAccount, UsageSnapshotQuota, QuotaZone?>>, labels: TerminalLabels, profile: TerminalProfile): TerminalLine {
        val word = TerminalPrimitives.zoneWord(QuotaZone.ON_TRACK, labels, profile)
        val highest = entries.filter { (_, quota, _) -> quota.percent != null }.maxByOrNull { (_, quota, _) -> quota.percent ?: 0 }
            ?: return terminalLine(word)
        return terminalLine(word, TerminalSpan("  " + labels.highest(describe(highest.first, highest.second, labels))))
    }

    private fun describe(account: TerminalAccount, quota: UsageSnapshotQuota, labels: TerminalLabels): String {
        val name = listOfNotNull(account.name, account.profile).joinToString(" · ")
        return "$name · ${displayQuotaLabel(quota.label, labels.pt)} ${quota.percent}%"
    }
}

package com.usagemonitor.presentation.ui

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.UsageRiskLevel
import com.usagemonitor.domain.entity.UsageSnapshot
import com.usagemonitor.domain.entity.UsageSnapshotAccount
import com.usagemonitor.domain.entity.UsageSnapshotQuota
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Leituras do retrato que respondem uma pergunta só (#408): quando cada cota
 * reinicia, quando estoura, que conta tem folga, quanto dá para gastar hoje. Mesmo
 * HTML e mesmo escape de [TelegramBotMessages]; arquivo próprio para aquele não
 * crescer sem fim.
 */
internal object TelegramBotInsightMessages {

    /**
     * `/reinicios` (AA3): toda cota com reinício conhecido ainda por vir, na ordem
     * do relógio. Empate por conta e por cota — a lista é a mesma a cada pedido.
     */
    fun resets(snapshot: UsageSnapshot?, language: AppLanguage): String {
        val pt = language == AppLanguage.PT
        if (snapshot == null || snapshot.accounts.isEmpty()) return TelegramBotMessages.noReading(pt)
        val now = snapshot.generatedAt
        val entries = snapshot.accounts
            .flatMap { account ->
                account.quotas.mapNotNull { quota ->
                    quota.resetsAt?.takeIf { at -> at > now }?.let { at -> ResetEntry(at, account.label, quota) }
                }
            }
            .sortedWith(compareBy<ResetEntry> { entry -> entry.at }.thenBy { entry -> entry.account }.thenBy { entry -> entry.quota.label })
        if (entries.isEmpty()) {
            return if (pt) "Nenhuma cota com reinício conhecido." else "No quota with a known reset."
        }
        val header = if (pt) "<b>🔁 Próximos reinícios</b>" else "<b>🔁 Next resets</b>"
        val lines = entries.take(MAX_RESET_LINES).map { entry ->
            val value = entry.quota.percent?.let { percent -> " — <b>$percent%</b>" }.orEmpty()
            "<code>${TelegramBotMessages.resetLabel(entry.at, now, pt)}</code> " +
                "<b>${TelegramBotMessages.escape(entry.account)}</b> · ${TelegramBotMessages.escape(entry.quota.label)}$value"
        }
        val more = (entries.size - MAX_RESET_LINES).takeIf { count -> count > 0 }?.let { count ->
            "<i>" + (if (pt) "+$count mais" else "+$count more") + "</i>"
        }
        return (listOf(header) + lines + listOfNotNull(more)).joinToString("\n")
    }

    /**
     * `/quando` (AA2): a data de esgotamento da mesma projeção que pinta o arco da
     * HUD, contra o reinício. Sem projeção, a linha diz isso — nunca um palpite.
     */
    fun exhaustion(snapshot: UsageSnapshot?, language: AppLanguage): String {
        val pt = language == AppLanguage.PT
        if (snapshot == null || snapshot.accounts.isEmpty()) return TelegramBotMessages.noReading(pt)
        val now = snapshot.generatedAt
        val lines = snapshot.accounts.flatMap { account ->
            account.quotas.map { quota -> exhaustionLine(account.label, quota, now, pt) }
        }
        val header = if (pt) "<b>⏳ No ritmo atual</b>" else "<b>⏳ At the current pace</b>"
        val footer = if (pt) "<i>estimativa pelo ritmo das últimas leituras</i>" else "<i>estimate from the pace of recent readings</i>"
        return (listOf(header) + lines + footer).joinToString("\n")
    }

    private fun exhaustionLine(account: String, quota: UsageSnapshotQuota, now: Instant, pt: Boolean): String {
        val name = "<b>${TelegramBotMessages.escape(account)} · ${TelegramBotMessages.escape(quota.label)}</b>"
        val value = quota.percent?.let { percent -> " $percent%" }.orEmpty()
        val risk = quota.risk ?: return "⚪ $name$value — " + (if (pt) "sem projeção" else "no projection")
        val mark = TelegramBotMessages.riskEmoji(risk)
        val exhaustsAt = quota.exhaustsAt
        val resetsAt = quota.resetsAt
        if (resetsAt == null) {
            // Saldo pré-pago: não reinicia, a resposta é só quando acaba.
            val end = if (exhaustsAt == null) {
                if (pt) "sem data de fim" else "no end date"
            } else {
                (if (pt) "acaba por volta de " else "runs out around ") + TelegramBotMessages.resetLabel(exhaustsAt, now, pt)
            }
            return "$mark $name$value — $end"
        }
        val reset = TelegramBotMessages.resetLabel(resetsAt, now, pt)
        if (exhaustsAt == null || exhaustsAt >= resetsAt) {
            return "$mark $name$value — " + (if (pt) "não estoura antes de reiniciar ($reset)" else "does not run out before the reset ($reset)")
        }
        val early = span(resetsAt - exhaustsAt)
        val at = TelegramBotMessages.resetLabel(exhaustsAt, now, pt)
        val verdict = if (pt) {
            "chega a 100% às $at, reinicia $reset: <b>estoura $early antes</b>"
        } else {
            "hits 100% at $at, resets $reset: <b>runs out $early early</b>"
        }
        return "$mark $name$value — $verdict"
    }

    /**
     * `/qual` (AA28): por fonte, a conta com mais folga — menor risco, depois menor
     * percentual, depois rótulo. Só entra conta com cota em percentual: saldo não
     * se compara com janela. As outras da mesma fonte vêm embaixo, com o pior risco.
     */
    fun bestAccount(snapshot: UsageSnapshot?, language: AppLanguage): String {
        val pt = language == AppLanguage.PT
        if (snapshot == null || snapshot.accounts.isEmpty()) return TelegramBotMessages.noReading(pt)
        val groups = snapshot.accounts
            .filter { account -> account.quotas.any { quota -> quota.percent != null } }
            .groupBy { account -> account.source }
            .toList()
            .sortedBy { (source, _) -> source.ordinal }
        if (groups.isEmpty()) {
            return if (pt) "Nenhuma conta com cota em percentual para comparar." else "No account with a percentage quota to compare."
        }
        val blocks = groups.map { (_, accounts) ->
            val ranked = accounts.sortedWith(
                compareBy<UsageSnapshotAccount> { account -> account.worstRisk?.ordinal ?: UsageRiskLevel.ON_TRACK.ordinal }
                    .thenBy { account -> maxPercent(account) }
                    .thenBy { account -> account.label }
            )
            val best = ranked.first()
            val quotas = best.quotas.mapNotNull { quota -> quota.percent?.let { percent -> "${TelegramBotMessages.escape(quota.label)} $percent%" } }
            val head = "<b>${TelegramBotMessages.escape(best.label)}</b> — " + quotas.joinToString(" · ")
            val others = ranked.drop(1).map { account ->
                val mark = account.worstRisk?.let { level -> TelegramBotMessages.riskEmoji(level) + " " }.orEmpty()
                "$mark${TelegramBotMessages.escape(account.label)} ${maxPercent(account)}%"
            }
            val tail = if (others.isEmpty()) {
                if (pt) "<i>única conta desta fonte</i>" else "<i>only account of this source</i>"
            } else {
                "<i>" + (if (pt) "depois: " else "then: ") + others.joinToString(" · ") + "</i>"
            }
            "$head\n$tail"
        }
        val header = if (pt) "<b>🎯 Melhor conta agora</b>" else "<b>🎯 Best account now</b>"
        return header + "\n\n" + blocks.joinToString("\n\n")
    }

    private fun maxPercent(account: UsageSnapshotAccount): Int =
        account.quotas.mapNotNull { quota -> quota.percent }.maxOrNull() ?: 0

    /** "20 min", "3 h 10 min", "2 d 4 h": a antecedência em palavras curtas. */
    internal fun span(duration: Duration): String {
        val minutes = duration.inWholeMinutes.coerceAtLeast(1L)
        val days = minutes / MINUTES_PER_DAY
        val hours = (minutes % MINUTES_PER_DAY) / 60
        val rest = minutes % 60
        return when {
            days > 0 -> "$days d" + if (hours > 0) " $hours h" else ""
            hours > 0 -> "$hours h" + if (rest > 0) " $rest min" else ""
            else -> "$rest min"
        }
    }

    private class ResetEntry(val at: Instant, val account: String, val quota: UsageSnapshotQuota)

    /** Teto da lista: com muitas contas, as primeiras linhas são as que importam. */
    private const val MAX_RESET_LINES = 12

    private const val MINUTES_PER_DAY = 24 * 60L
}

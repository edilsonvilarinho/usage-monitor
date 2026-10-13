package com.usagemonitor.presentation.ui

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.UsageSnapshot
import com.usagemonitor.domain.entity.UsageSnapshotQuota
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

    private class ResetEntry(val at: Instant, val account: String, val quota: UsageSnapshotQuota)

    /** Teto da lista: com muitas contas, as primeiras linhas são as que importam. */
    private const val MAX_RESET_LINES = 12
}

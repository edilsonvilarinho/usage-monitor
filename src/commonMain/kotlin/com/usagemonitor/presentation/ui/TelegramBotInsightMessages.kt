package com.usagemonitor.presentation.ui

import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.UsageRiskLevel
import com.usagemonitor.domain.entity.UsageSnapshot
import com.usagemonitor.domain.entity.UsageSnapshotAccount
import com.usagemonitor.domain.entity.UsageSnapshotQuota
import com.usagemonitor.domain.entity.UsageSpike
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
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

    /**
     * `/ritmo` (AA27): para janela com mais de um dia até o reinício, o que resta
     * dividido pelos dias que faltam. É **estimativa** e diz isso: a API não
     * informa ritmo, e o número só serve de régua para o dia.
     */
    fun pace(snapshot: UsageSnapshot?, language: AppLanguage): String {
        val pt = language == AppLanguage.PT
        if (snapshot == null || snapshot.accounts.isEmpty()) return TelegramBotMessages.noReading(pt)
        val now = snapshot.generatedAt
        val lines = snapshot.accounts.flatMap { account ->
            account.quotas.mapNotNull { quota ->
                val percent = quota.percent ?: return@mapNotNull null
                val resetsAt = quota.resetsAt?.takeIf { at -> at - now > 24.hours } ?: return@mapNotNull null
                val days = (resetsAt - now).inWholeMinutes / MINUTES_PER_DAY.toDouble()
                val remaining = (100 - percent).coerceAtLeast(0)
                val perDay = (remaining / days).roundToInt()
                val name = "<b>${TelegramBotMessages.escape(account.label)} · ${TelegramBotMessages.escape(quota.label)}</b>"
                val reset = TelegramBotMessages.resetLabel(resetsAt, now, pt)
                val daysText = oneDecimal(days, pt)
                if (pt) {
                    "$name $percent% · reinicia $reset\nrestam $remaining pp em $daysText dias → <b>~$perDay pp por dia</b>"
                } else {
                    "$name $percent% · resets $reset\n$remaining pp left in $daysText days → <b>~$perDay pp a day</b>"
                }
            }
        }
        if (lines.isEmpty()) {
            return if (pt) {
                "Nenhuma janela com mais de um dia até o reinício. O ritmo vale para cotas semanais e mensais."
            } else {
                "No window with more than a day until its reset. The pace applies to weekly and monthly quotas."
            }
        }
        val header = if (pt) "<b>🧭 Ritmo sugerido</b> <i>(estimativa)</i>" else "<b>🧭 Suggested pace</b> <i>(estimate)</i>"
        return header + "\n\n" + lines.joinToString("\n\n")
    }

    /**
     * `/pico` (AA11): as mesmas anomalias do alerta de consumo acima do hábito
     * (`UsageSpike`), mais forte primeiro. Sem nenhuma, diz a partir de quanto o
     * aviso sai — a régua é o fator das Configurações.
     */
    fun spikes(spikes: List<UsageSpike>, minFactor: Double, language: AppLanguage): String {
        val pt = language == AppLanguage.PT
        val factor = formatSpikeFactor(minFactor, language)
        if (spikes.isEmpty()) {
            return if (pt) {
                "📈 Nada acima do seu hábito hoje. O aviso sai a partir de ${factor}× a mediana."
            } else {
                "📈 Nothing above your habit today. The alert fires from ${factor}× the median."
            }
        }
        val ordered = spikes.sortedWith(
            compareByDescending<UsageSpike> { spike -> spike.factor }
                .thenBy { spike -> spike.targetLabel }
                .thenBy { spike -> spike.quotaLabel }
        )
        val lines = ordered.map { spike ->
            val name = "<b>${TelegramBotMessages.escape(spike.targetLabel)} · ${TelegramBotMessages.escape(spike.quotaLabel)}</b>"
            val today = amount(spike.baseline.todayDelta, spike.quotaTotal)
            val usual = amount(spike.baseline.baselineDelta, spike.quotaTotal)
            val days = spike.baseline.completeDays
            if (pt) {
                "$name — <b>${formatSpikeFactor(spike.factor, language)}×</b> o normal\nhoje $today · mediana $usual ($days dias, mesma hora)"
            } else {
                "$name — <b>${formatSpikeFactor(spike.factor, language)}×</b> the usual\ntoday $today · median $usual ($days days, same hour)"
            }
        }
        val header = if (pt) "<b>📈 Hoje × seu hábito</b>" else "<b>📈 Today × your habit</b>"
        return header + "\n\n" + lines.joinToString("\n\n")
    }

    /** Em pontos percentuais da cota quando há total; sem total, a unidade crua. */
    private fun amount(delta: Long, total: Long): String =
        if (total > 0L) "+${delta * 100 / total} pp" else "+$delta"

    private fun oneDecimal(value: Double, pt: Boolean): String {
        val tenths = (value * 10).roundToInt()
        return "${tenths / 10}${if (pt) "," else "."}${tenths % 10}"
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

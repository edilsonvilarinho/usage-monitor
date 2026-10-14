package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.UsageRiskLevel
import com.usagemonitor.domain.entity.UsageSnapshot
import com.usagemonitor.domain.entity.UsageSnapshotAccount
import com.usagemonitor.domain.entity.UsageSnapshotQuota
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.domain.entity.UsageDailyBaseline
import com.usagemonitor.domain.entity.UsageSpike
import com.usagemonitor.domain.entity.UsageTargetKey
import kotlinx.datetime.LocalDate
import com.usagemonitor.presentation.ui.TelegramBotInsightMessages
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Leituras de uma pergunta só do bot (#408). */
class TelegramBotInsightMessagesTest {

    // 14:00 BRT de terça (13/10/2026).
    private val now = Instant.parse("2026-10-13T17:00:00Z")

    private fun quota(
        label: String,
        percent: Int?,
        resetsAt: Instant?,
        periodType: PeriodType = PeriodType.INTERVAL,
        risk: UsageRiskLevel? = null,
        exhaustsAt: Instant? = null
    ) = UsageSnapshotQuota(
        label = label, periodType = periodType, unit = UsageUnit.PERCENTAGE, used = percent?.toLong() ?: 0L, total = 100,
        percent = percent, resetsAt = resetsAt, currencyCode = "USD", risk = risk, exhaustsAt = exhaustsAt
    )

    private fun account(label: String, vararg quotas: UsageSnapshotQuota, source: ApiSource = ApiSource.ANTHROPIC) =
        UsageSnapshotAccount(source = source, label = label, active = false, quotas = quotas.toList())

    @Test
    fun `resets come in clock order with ties broken by account and quota`() {
        val snapshot = UsageSnapshot(
            generatedAt = now,
            accounts = listOf(
                account("Codex", quota("7d", 22, Instant.parse("2026-10-14T06:28:00Z"), PeriodType.WEEKLY), source = ApiSource.CODEX),
                account(
                    "Anthropic · <Padrão>",
                    quota("Sessão 5h", 86, Instant.parse("2026-10-13T19:00:00Z")),
                    quota("Semanal", 61, Instant.parse("2026-10-16T22:44:00Z"), PeriodType.WEEKLY),
                    // Já passou: a próxima coleta traz a janela nova, a lista não a repete.
                    quota("Opus", 10, Instant.parse("2026-10-13T16:00:00Z"))
                ),
                account("Anthropic · Trabalho", quota("Sessão 5h", 12, Instant.parse("2026-10-13T19:00:00Z")))
            )
        )

        val text = TelegramBotInsightMessages.resets(snapshot, AppLanguage.PT)

        assertEquals(
            "<b>🔁 Próximos reinícios</b>\n" +
                "<code>16:00</code> <b>Anthropic · &lt;Padrão&gt;</b> · Sessão 5h — <b>86%</b>\n" +
                "<code>16:00</code> <b>Anthropic · Trabalho</b> · Sessão 5h — <b>12%</b>\n" +
                "<code>03:28</code> <b>Codex</b> · 7d — <b>22%</b>\n" +
                "<code>sex 19:44</code> <b>Anthropic · &lt;Padrão&gt;</b> · Semanal — <b>61%</b>",
            text
        )
    }

    @Test
    fun `resets without any known reset say so`() {
        val snapshot = UsageSnapshot(now, listOf(account("DeepSeek", quota("Saldo", null, null), source = ApiSource.DEEPSEEK)))

        assertEquals("No quota with a known reset.", TelegramBotInsightMessages.resets(snapshot, AppLanguage.EN))
        assertTrue(TelegramBotInsightMessages.resets(null, AppLanguage.PT).startsWith("Sem leitura ainda."))
    }

    @Test
    fun `when compares the projected exhaustion with the reset and never guesses`() {
        val snapshot = UsageSnapshot(
            generatedAt = now,
            accounts = listOf(
                account(
                    "Padrão",
                    quota("5h", 86, Instant.parse("2026-10-13T19:00:00Z"), risk = UsageRiskLevel.WILL_EXCEED, exhaustsAt = Instant.parse("2026-10-13T18:40:00Z")),
                    quota("Semanal", 61, Instant.parse("2026-10-16T22:44:00Z"), PeriodType.WEEKLY, risk = UsageRiskLevel.ON_TRACK)
                ),
                account("MiniMax", quota("Requisições", 9, Instant.parse("2026-10-13T20:00:00Z")), source = ApiSource.MINIMAX),
                account(
                    "DeepSeek",
                    UsageSnapshotQuota(
                        "Saldo", PeriodType.REPORTED, UsageUnit.CURRENCY_USD, 100, 500, null, null, "USD",
                        UsageRiskLevel.AT_RISK, Instant.parse("2026-10-20T12:00:00Z")
                    ),
                    source = ApiSource.DEEPSEEK
                )
            )
        )

        val text = TelegramBotInsightMessages.exhaustion(snapshot, AppLanguage.PT)

        assertTrue("🔴 <b>Padrão · 5h</b> 86% — chega a 100% às 15:40, reinicia 16:00: <b>estoura 20 min antes</b>" in text, text)
        assertTrue("🟢 <b>Padrão · Semanal</b> 61% — não estoura antes de reiniciar (sex 19:44)" in text, text)
        assertTrue("⚪ <b>MiniMax · Requisições</b> 9% — sem projeção" in text, text)
        assertTrue("🟡 <b>DeepSeek · Saldo</b> — acaba por volta de ter 09:00" in text, text)
    }

    @Test
    fun `which picks the account with the lowest risk then the lowest percent per source`() {
        val snapshot = UsageSnapshot(
            generatedAt = now,
            accounts = listOf(
                account("Anthropic · Padrão", quota("5h", 86, null, risk = UsageRiskLevel.WILL_EXCEED), quota("Semanal", 61, null)),
                account("Anthropic · Trabalho", quota("5h", 12, null, risk = UsageRiskLevel.ON_TRACK), quota("Semanal", 30, null)),
                account("Anthropic · Pessoal", quota("5h", 5, null, risk = UsageRiskLevel.AT_RISK)),
                account("Codex", quota("7d", 22, null), source = ApiSource.CODEX),
                account("DeepSeek", UsageSnapshotQuota("Saldo", PeriodType.REPORTED, UsageUnit.CURRENCY_USD, 1, 5, null, null, "USD"), source = ApiSource.DEEPSEEK)
            )
        )

        val text = TelegramBotInsightMessages.bestAccount(snapshot, AppLanguage.PT)

        assertEquals(
            "<b>🎯 Melhor conta agora</b>\n\n" +
                "<b>Anthropic · Trabalho</b> — 5h 12% · Semanal 30%\n" +
                "<i>depois: 🟡 Anthropic · Pessoal 5% · 🔴 Anthropic · Padrão 86%</i>\n\n" +
                "<b>Codex</b> — 7d 22%\n" +
                "<i>única conta desta fonte</i>",
            text
        )
    }

    @Test
    fun `pace divides what is left of long windows by the days until the reset`() {
        val snapshot = UsageSnapshot(
            generatedAt = now,
            accounts = listOf(
                account(
                    "Padrão",
                    // Reinicia em menos de um dia: fora, não há o que dividir.
                    quota("5h", 86, Instant.parse("2026-10-13T19:00:00Z")),
                    // Sex 19:44 BRT: 3 d 5 h 44 min = 3,2 dias.
                    quota("Semanal", 61, Instant.parse("2026-10-16T22:44:00Z"), PeriodType.WEEKLY)
                )
            )
        )

        assertEquals(
            "<b>🧭 Ritmo sugerido</b> <i>(estimativa)</i>\n\n" +
                "<b>Padrão · Semanal</b> 61% · reinicia sex 19:44\nrestam 39 pp em 3,2 dias → <b>~12 pp por dia</b>",
            TelegramBotInsightMessages.pace(snapshot, AppLanguage.PT)
        )
        val short = UsageSnapshot(now, listOf(account("Padrão", quota("5h", 86, Instant.parse("2026-10-13T19:00:00Z")))))
        assertTrue(TelegramBotInsightMessages.pace(short, AppLanguage.PT).startsWith("Nenhuma janela com mais de um dia"))
    }

    @Test
    fun `spike lists the strongest first in quota points and says the bar when there is none`() {
        fun spike(label: String, factor: Double, today: Long, usual: Long) = UsageSpike(
            target = UsageTargetKey.forSource(ApiSource.ANTHROPIC), targetLabel = "Padrão", quotaLabel = label,
            periodType = PeriodType.WEEKLY, unit = UsageUnit.PERCENTAGE,
            baseline = UsageDailyBaseline(todayDelta = today, baselineDelta = usual, completeDays = 14),
            factor = factor, quotaTotal = 100, localDate = LocalDate(2026, 10, 13)
        )

        val text = TelegramBotInsightMessages.spikes(listOf(spike("5h", 3.0, 30, 10), spike("Semanal", 3.1, 19, 6)), 3.0, AppLanguage.PT)

        assertEquals(
            "<b>📈 Hoje × seu hábito</b>\n\n" +
                "<b>Padrão · Semanal</b> — <b>3,1×</b> o normal\nhoje +19 pp · mediana +6 pp (14 dias, mesma hora)\n\n" +
                "<b>Padrão · 5h</b> — <b>3,0×</b> o normal\nhoje +30 pp · mediana +10 pp (14 dias, mesma hora)",
            text
        )
        assertEquals(
            "📈 Nada acima do seu hábito hoje. O aviso sai a partir de 3,0× a mediana.",
            TelegramBotInsightMessages.spikes(emptyList(), 3.0, AppLanguage.PT)
        )
    }
}

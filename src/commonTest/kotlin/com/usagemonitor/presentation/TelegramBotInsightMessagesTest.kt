package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.UsageRiskLevel
import com.usagemonitor.domain.entity.UsageSnapshot
import com.usagemonitor.domain.entity.UsageSnapshotAccount
import com.usagemonitor.domain.entity.UsageSnapshotQuota
import com.usagemonitor.domain.entity.UsageUnit
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
        risk: UsageRiskLevel? = null
    ) = UsageSnapshotQuota(
        label = label, periodType = periodType, unit = UsageUnit.PERCENTAGE, used = percent?.toLong() ?: 0L, total = 100,
        percent = percent, resetsAt = resetsAt, currencyCode = "USD", risk = risk
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
}

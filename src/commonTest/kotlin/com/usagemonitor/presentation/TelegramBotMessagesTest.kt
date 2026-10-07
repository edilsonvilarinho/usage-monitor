package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.UsageRiskLevel
import com.usagemonitor.domain.entity.UsageSnapshot
import com.usagemonitor.domain.entity.UsageSnapshotAccount
import com.usagemonitor.domain.entity.UsageSnapshotQuota
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.presentation.ui.TelegramBotMessages
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Formato W1 das respostas do bot (#396). */
class TelegramBotMessagesTest {

    // 22:30 BRT de terça (6/10/2026); coleta um minuto e meio antes.
    private val now = Instant.parse("2026-10-07T01:30:00Z")

    private fun quota(label: String, percent: Int?, resetsAt: Instant?, unit: UsageUnit = UsageUnit.PERCENTAGE) = UsageSnapshotQuota(
        label = label, periodType = PeriodType.INTERVAL, unit = unit, used = 1234, total = if (unit == UsageUnit.CURRENCY_USD) 5000 else 100,
        percent = percent, resetsAt = resetsAt, currencyCode = "USD", risk = UsageRiskLevel.WILL_EXCEED
    )

    private val snapshot = UsageSnapshot(
        generatedAt = now,
        accounts = listOf(
            UsageSnapshotAccount(
                source = ApiSource.ANTHROPIC, label = "Anthropic · <Padrão>", active = true,
                fetchedAt = Instant.parse("2026-10-07T01:28:30Z"),
                quotas = listOf(
                    quota("Claude 5h", 42, Instant.parse("2026-10-07T05:10:00Z")),
                    quota("Claude 7d", 21, Instant.parse("2026-10-10T04:00:00Z"))
                )
            ),
            UsageSnapshotAccount(
                source = ApiSource.DEEPSEEK, label = "DeepSeek", active = false,
                quotas = listOf(quota("Saldo", null, null, UsageUnit.CURRENCY_USD).copy(risk = null))
            )
        )
    )

    @Test
    fun `status is one card per account with risk word, bar and reset`() {
        val text = TelegramBotMessages.status(snapshot, AppLanguage.PT)

        assertTrue(text.startsWith("<b>📊 Usage Monitor</b>\n<i>coleta 22:28 BRT · há 1 min</i>"), text)
        assertTrue("<b>Anthropic · &lt;Padrão&gt;</b> — 🔴 Crítico · ⚡ em uso" in text, text)
        assertTrue("Claude 5h · reinicia 02:10\n<code>▰▰▰▰▱▱▱▱▱▱  42%</code>" in text, text)
        // Reinício a mais de 24 h ganha o dia da semana.
        assertTrue("Claude 7d · reinicia sáb 01:00\n<code>▰▰▱▱▱▱▱▱▱▱  21%</code>" in text, text)
        assertTrue("<b>DeepSeek</b>\nSaldo\n<code>saldo US$ 37,66</code>" in text, text)
    }

    @Test
    fun `status without a collection says so instead of a dash`() {
        val text = TelegramBotMessages.status(snapshot.copy(accounts = snapshot.accounts.map { it.copy(fetchedAt = null) }), AppLanguage.PT)

        assertTrue("<i>sem coleta ainda</i>" in text, text)
        assertFalse("coleta —" in text)
    }

    @Test
    fun `bar never fills past the written percent`() {
        assertEquals("▱▱▱▱▱▱▱▱▱▱", TelegramBotMessages.bar(0))
        assertEquals("▰▰▰▰▰▰▰▰▰▱", TelegramBotMessages.bar(99))
        assertEquals("▰▰▰▰▰▰▰▰▰▰", TelegramBotMessages.bar(140))
    }

    @Test
    fun `plain preview drops the markup and restores the escaped characters`() {
        assertEquals("Anthropic · <Padrão>", TelegramBotMessages.plain("<b>Anthropic · &lt;Padrão&gt;</b>"))
    }

    @Test
    fun `invalid usage is escaped inside code`() {
        assertEquals("Não entendi. Uso: <code>/limiar 75,90 &amp; &lt;x&gt;</code>", TelegramBotMessages.invalid("/limiar 75,90 & <x>", AppLanguage.PT))
    }
}

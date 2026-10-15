package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.UsageRiskLevel
import com.usagemonitor.domain.entity.UsageSnapshot
import com.usagemonitor.domain.entity.UsageSnapshotAccount
import com.usagemonitor.domain.entity.UsageSnapshotQuota
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.domain.entity.UsageAlert
import com.usagemonitor.domain.entity.UsageAlertSettings
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.domain.entity.withAccountThreshold
import com.usagemonitor.domain.entity.TelegramDailySpend
import com.usagemonitor.presentation.ui.TelegramBotSummaryMessages
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

    /** Rodada AE, AE7: nome e risco em linhas próprias, barra de quadrados e a legenda geral no fim. */
    @Test
    fun `status is one card per account with risk word, square bar and reset`() {
        val text = TelegramBotMessages.status(snapshot, AppLanguage.PT)

        assertTrue(text.startsWith("<b>📊 Usage Monitor</b>\n<i>coleta 22:28 BRT · há 1 min</i>"), text)
        assertTrue("<b>Anthropic · &lt;Padrão&gt;</b>\n🔴 Crítico · ⚡ em uso\n" in text, text)
        assertTrue("Claude 5h · reinicia 02:10\n🟩🟩🟩🟩⬛⬛⬛⬛⬛⬛ <b>42%</b>" in text, text)
        // Reinício a mais de 24 h ganha o dia da semana.
        assertTrue("Claude 7d · reinicia sáb 01:00\n🟩🟩⬛⬛⬛⬛⬛⬛⬛⬛ <b>21%</b>" in text, text)
        assertTrue("<b>DeepSeek</b>\nSaldo\n<code>saldo US$ 37,66</code>" in text, text)
        assertTrue(text.endsWith("\n\n<i>🟩 até 75% · 🟨 até 90% · 🟥 acima</i>"), text)
        assertFalse("▰" in text || "6 h</i>" in text, text)
    }

    /** AA10 + AE7: conta com limiar próprio pinta pela dela e diz qual é; as outras seguem o geral do rodapé. */
    @Test
    fun `account with its own threshold paints by it and carries its legend`() {
        val own = snapshot.copy(accounts = snapshot.accounts.mapIndexed { index, account -> if (index == 0) account.copy(targetKey = "anthropic:padrao") else account })
        val settings = UsageAlertSettings(quotaPercents = listOf(75, 90)).withAccountThreshold("anthropic:padrao", "Padrão", listOf(30, 40))

        val text = TelegramBotMessages.status(own, AppLanguage.PT, settings)

        assertTrue("🔴 Crítico · ⚡ em uso\n<i>limiar próprio: 🟩 até 30% · 🟨 até 40% · 🟥 acima</i>\n" in text, text)
        assertTrue("Claude 5h · reinicia 02:10\n🟩🟩🟩🟨⬛⬛⬛⬛⬛⬛ <b>42%</b>" in text, text)
        assertTrue(text.endsWith("<i>🟩 até 75% · 🟨 até 90% · 🟥 acima</i>"), text)
    }

    @Test
    fun `status without a collection says so instead of a dash`() {
        val text = TelegramBotMessages.status(snapshot.copy(accounts = snapshot.accounts.map { it.copy(fetchedAt = null) }), AppLanguage.PT)

        assertTrue("<i>sem coleta ainda</i>" in text, text)
        assertFalse("coleta —" in text)
    }

    /** AE7: cheia pelo piso (nunca passa do número); a cor é quantos limiares a célula já passou. */
    @Test
    fun `square bar never fills past the written percent and colors by thresholds passed`() {
        val defaults = listOf(75, 90, 100)
        assertEquals("⬛⬛⬛⬛⬛⬛⬛⬛⬛⬛", TelegramBotMessages.zoneBar(0, defaults))
        assertEquals("🟩🟩🟩🟩🟩🟩🟩🟨🟨⬛", TelegramBotMessages.zoneBar(99, defaults))
        assertEquals("🟩🟩🟩🟩🟩🟩🟩🟨🟨🟥", TelegramBotMessages.zoneBar(140, defaults))
        assertEquals("🟩🟩🟩🟩🟩🟩🟩🟩🟩🟨", TelegramBotMessages.zoneBar(100, listOf(90)))
        assertEquals("🟩🟩🟩🟩🟩🟩🟩🟩🟩🟩", TelegramBotMessages.zoneBar(100, emptyList()))
        assertEquals("🟩 até 90% · 🟨 acima", TelegramBotMessages.zoneLegend(listOf(90), AppLanguage.PT))
        assertEquals("🟩 up to 75% · 🟨 up to 90% · 🟥 above", TelegramBotMessages.zoneLegend(defaults, AppLanguage.EN))
        assertEquals(null, TelegramBotMessages.zoneLegend(emptyList(), AppLanguage.PT))
    }

    /** AE7: o alerta de limiar leva a barra com os limiares da conta dele; alerta sem cota, não. */
    @Test
    fun `quota alert carries the square bar`() {
        val alert = UsageAlert.QuotaThreshold(
            UsageTargetKey(ApiSource.MINIMAX), "MiniMax", "5h", thresholdPercent = 75, actualPercent = 88,
            periodEndAt = Instant.parse("2026-10-13T04:00:00Z"), hasKnownResetAt = true
        )

        val lines = TelegramBotMessages.alert(alert, AppLanguage.PT).lines()

        assertEquals("🟩🟩🟩🟩🟩🟩🟩🟨⬛⬛ <b>88%</b>", lines[1])
        assertFalse("🟩" in TelegramBotMessages.alert(UsageAlert.SessionSaturated("s1", "x"), AppLanguage.PT))
    }

    @Test
    fun `plain preview drops the markup and restores the escaped characters`() {
        assertEquals("Anthropic · <Padrão>", TelegramBotMessages.plain("<b>Anthropic · &lt;Padrão&gt;</b>"))
    }

    @Test
    fun `invalid usage is escaped inside code`() {
        assertEquals("Não entendi. Uso: <code>/limiar 75,90 &amp; &lt;x&gt;</code>", TelegramBotMessages.invalid("/limiar 75,90 & <x>", AppLanguage.PT))
    }

    /** #398, Y4: duas contas por linha, risco em emoji antes do rótulo, e o cartão de uma só. */
    @Test
    fun `account picker buttons and single account card`() {
        val rows = TelegramBotMessages.accountButtons(snapshot)

        assertEquals(1, rows.size)
        assertEquals(listOf("🔴 Anthropic · <Padrão>", "DeepSeek"), rows.single().map { it.first })
        val key = rows.single()[1].second.removePrefix("acc:")
        val card = TelegramBotMessages.account(snapshot, key, AppLanguage.PT)
        assertTrue(card.startsWith("<b>DeepSeek</b>\nSaldo"), card)
        assertFalse("Anthropic" in card)
        assertTrue("saiu" !in card && "não está mais" in TelegramBotMessages.account(snapshot, "ANTHROPIC:0", AppLanguage.PT))
    }

    /** #398, Y1: cota mais cheia por conta, o que reinicia hoje e o gasto de 24 h com `+` quando parcial. */
    @Test
    fun `daily summary lists the fullest quota, resets today and the spend`() {
        val text = TelegramBotSummaryMessages.summary(snapshot, TelegramDailySpend(14_200_000L, 6, unpricedTurns = 2), AppLanguage.PT)

        assertTrue(text.startsWith("<b>☀ Resumo de ter 06/10</b>"), text)
        assertTrue("🔴 <b>Anthropic · &lt;Padrão&gt;</b> · Claude 5h <b>42%</b> · reinicia 02:10" in text, text)
        assertTrue("<b>DeepSeek</b> · Saldo saldo US$ 37,66" in text, text)
        assertTrue("<b>Claude Code, últimas 24 h</b>: US$ 14,20+ · 6 sessões" in text, text)
        // 02:10 já é amanhã (dia local 07/10): não entra em "Reinicia hoje".
        assertFalse("Reinicia hoje" in text, text)
    }

    @Test
    fun `daily summary without a measured spend drops the spend line`() {
        val text = TelegramBotSummaryMessages.summary(snapshot, spend = null, AppLanguage.EN)

        assertTrue(text.startsWith("<b>☀ Summary for Tue 06/10</b>"), text)
        assertFalse("last 24 h" in text, text)
    }

    /** #398, Y5: o painel traz a hora da coleta, não a do envio — só muda com leitura nova. */
    @Test
    fun `live panel shows the collection time and one line per account`() {
        val text = TelegramBotSummaryMessages.panel(snapshot, AppLanguage.PT)

        assertTrue(text.startsWith("<b>📌 Painel ao vivo</b>\n<i>atualizado 22:28 BRT</i>"), text)
        assertTrue("🔴 <b>Anthropic · &lt;Padrão&gt;</b> · Claude 5h\n🟩🟩🟩🟩⬛⬛⬛⬛⬛⬛ <b>42%</b>" in text, text)
        assertEquals(text, TelegramBotSummaryMessages.panel(snapshot.copy(generatedAt = now + kotlin.time.Duration.parse("5m")), AppLanguage.PT))
    }

    /** #408, AA15: uma linha com risco, conta e pior percentual (saldo com o valor); a hora da coleta na segunda. */
    @Test
    fun `compact panel fits one line per chat`() {
        val text = TelegramBotSummaryMessages.compactPanel(snapshot, AppLanguage.PT)
        val lines = text.lines()

        assertEquals(2, lines.size, text)
        assertTrue(lines[0].startsWith("🔴 Anthropic · &lt;Padrão&gt; 42% · "), text)
        assertTrue("DeepSeek saldo US$ 37,66" in lines[0], text)
        assertEquals("<i>coleta 22:28 BRT</i>", lines[1])
        assertEquals(text, TelegramBotSummaryMessages.compactPanel(snapshot.copy(generatedAt = now + kotlin.time.Duration.parse("5m")), AppLanguage.PT))
    }

    /** #408, AA1: a repetição edita a mensagem com o contador, a hora da primeira e a da última (BRT). */
    @Test
    fun `repeated alert carries the count and both times`() {
        val text = TelegramBotMessages.repeatedAlert(
            UsageAlert.SessionSaturated("s2", "alem-das-sombras"),
            count = 3,
            firstAt = Instant.parse("2026-10-06T09:55:00Z"),
            lastAt = Instant.parse("2026-10-06T14:43:00Z"),
            language = AppLanguage.PT
        )
        assertTrue(text.startsWith("<b>Sessão CLI saturada</b> · <b>3×</b> desde 06:55\n"))
        assertTrue(text.endsWith("\n<i>última: 11:43</i>"))
        assertTrue("alem-das-sombras" in text)
    }

    /** B03: chave de série em português sai traduzida no inglês; rótulo neutro sai como veio; no português, nada muda. */
    @Test
    fun `quota labels follow the app language without renaming the series key`() {
        assertEquals("Balance", com.usagemonitor.presentation.ui.displayQuotaLabel("Saldo", AppLanguage.EN))
        assertEquals("Go weekly", com.usagemonitor.presentation.ui.displayQuotaLabel("Go semanal", AppLanguage.EN))
        assertEquals("Claude 5h", com.usagemonitor.presentation.ui.displayQuotaLabel("Claude 5h", AppLanguage.EN))
        assertEquals("Saldo", com.usagemonitor.presentation.ui.displayQuotaLabel("Saldo", AppLanguage.PT))
        val status = TelegramBotMessages.status(snapshot, AppLanguage.EN)
        assertTrue("Balance" in status && "Saldo" !in status, status)
    }
}

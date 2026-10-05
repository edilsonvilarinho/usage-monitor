package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.presentation.ui.buildHudAccounts
import com.usagemonitor.presentation.ui.hudRingDescription
import com.usagemonitor.presentation.ui.hudTraySummary
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.ui.components.buildObservedUsageSummaries
import com.usagemonitor.presentation.viewmodel.HudQuotaEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class HudObservedModelTest {
    private val now = Instant.parse("2026-10-05T12:00:00Z")

    @Test
    fun `fontes observadas agrupam modelos sem transformar contagens em cotas`() {
        for (source in listOf(ApiSource.OPENCODE, ApiSource.KILO, ApiSource.GEMINI)) {
            val unit = if (source == ApiSource.GEMINI) UsageUnit.TOKENS else UsageUnit.REQUESTS
            val rows = listOf(quota("Model A 5h", 10, unit), quota("Model A 7d", 25, unit),
                quota("Model B 5h", 8, unit), quota("Model B 7d", 12, unit))
            val account = account(source, rows)
            assertEquals(2, account.observedModels.size)
            assertEquals(buildObservedUsageSummaries(rows).map { it.amountFiveHours }, account.observedModels.map { it.amountFiveHours })
            assertEquals(listOf(25L, 12L), account.observedModels.map { it.amountSevenDays })
            assertTrue(account.quotas.isEmpty())
            assertTrue(account.rings.isEmpty())
            assertFalse(account.needsAttention)
            assertEquals(AppTone.NEUTRAL, account.tone)
            assertEquals("Atividade local", account.statusLabel)
            assertEquals("5h 18 ${if (source == ApiSource.GEMINI) "tok" else "req."}", account.focusLine.text)
            assertEquals(listOf(account.focusLine), account.stripLines)
            assertTrue(hudTraySummary("Usage Monitor", listOf(account)).contains(account.focusLine.text))
            val description = hudRingDescription(account, AppLanguage.PT)
            assertTrue(description.contains("Model A · Últimas 5h: 10"))
            assertTrue(description.contains("Últimos 7 dias: 25"))
            assertFalse(description.contains("%"))
            assertFalse(description.contains("anel externo"))
        }
    }

    @Test
    fun `periodo ausente permanece zero e pluralizacao respeita o idioma`() {
        val rows = listOf(quota("Model A 7d", 1, UsageUnit.REQUESTS))
        val pt = account(ApiSource.OPENCODE, rows)
        val en = account(ApiSource.OPENCODE, rows, AppLanguage.EN)
        assertEquals(0, pt.observedModels.single().amountFiveHours.toInt())
        assertEquals("5h 0 req.", pt.focusLine.text)
        assertTrue(hudRingDescription(pt, AppLanguage.PT).contains("Últimos 7 dias: 1 requisição"))
        assertTrue(hudRingDescription(en, AppLanguage.EN).contains("Last 7 days: 1 request"))
        assertEquals("Local activity", en.statusLabel)
    }

    @Test
    fun `tokens sao abreviados e a soma nao transborda`() {
        assertEquals("5h 12K tok", account(ApiSource.GEMINI, listOf(quota("A 5h", 12_000, UsageUnit.TOKENS))).focusLine.text)
        val large = account(ApiSource.KILO, listOf(quota("A 5h", Long.MAX_VALUE, UsageUnit.REQUESTS), quota("B 5h", 1, UsageUnit.REQUESTS)))
        assertFalse(large.focusLine.text.contains("-"))
        val zero = account(ApiSource.KILO, listOf(quota("A 5h", 0, UsageUnit.REQUESTS), quota("A 7d", 0, UsageUnit.REQUESTS)))
        assertEquals("5h 0 req.", zero.focusLine.text)
        assertEquals(1, zero.observedModels.size)
    }

    @Test
    fun `fontes de cota preservam percentual e ordem escolhida`() {
        val sources = listOf(ApiSource.ANTHROPIC, ApiSource.CODEX, ApiSource.OPENCODE_GO)
        val entries = sources.flatMap { source ->
            val q = quota("Quota 5h", 37, UsageUnit.PERCENTAGE).copy(total = 100)
            val stats = ApiUsageStats(source = source, apiName = source.name, quotas = listOf(q))
            listOf(HudQuotaEntry(stats, q, null))
        }
        val order = sources.reversed().map { UsageTargetKey.forSource(it) }
        val accounts = buildHudAccounts(entries, order, AppLanguage.PT, now)
        assertEquals(order, accounts.map { it.targetKey })
        accounts.forEach { account ->
            assertTrue(account.observedModels.isEmpty())
            assertEquals("37%", account.quotas.single().percentText)
            assertEquals("Sessão 5h", account.quotas.single().title)
            assertEquals(1, account.rings.size)
        }
    }

    private fun quota(label: String, used: Long, unit: UsageUnit) = QuotaInfo(
        label, used = used, total = 0, periodEndAt = now, hasKnownResetAt = false,
        periodType = if (label.endsWith("7d")) PeriodType.WEEKLY else PeriodType.INTERVAL, unit = unit
    )

    private fun account(source: ApiSource, rows: List<QuotaInfo>, language: AppLanguage = AppLanguage.PT) =
        buildHudAccounts(
            rows.map { row -> HudQuotaEntry(ApiUsageStats(source = source, apiName = source.name, quotas = rows), row, null) },
            emptyList(), language, now
        ).single()
}

package com.usagemonitor.presentation.terminal

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.QuotaThresholdOverride
import com.usagemonitor.domain.entity.QuotaZone
import com.usagemonitor.domain.entity.UsageAlertSettings
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.domain.entity.UsageUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class TerminalAccountTest {

    private val now = Instant.parse("2026-10-21T17:23:00Z")

    private fun percent(label: String, used: Long, type: PeriodType = PeriodType.INTERVAL) =
        QuotaInfo(label = label, used = used, total = 100, periodEndAt = now + 2.hours, periodType = type, unit = UsageUnit.PERCENTAGE)

    private val codex = ApiUsageStats(source = ApiSource.CODEX, apiName = "Codex", quotas = listOf(percent("Codex 5h", 24)), fetchedAt = now)
    private val claudeWork = ApiUsageStats(
        source = ApiSource.ANTHROPIC,
        targetKey = UsageTargetKey(ApiSource.ANTHROPIC, "work"),
        apiName = "Anthropic",
        profileLabel = "trabalho",
        quotas = listOf(percent("Claude 5h", 91), percent("Claude 7d", 74, PeriodType.WEEKLY))
    )
    private val claudeDefault = ApiUsageStats(
        source = ApiSource.ANTHROPIC,
        targetKey = UsageTargetKey(ApiSource.ANTHROPIC, "default"),
        apiName = "Anthropic",
        profileLabel = "pessoal",
        quotas = listOf(percent("Claude 5h", 62))
    )
    private val balance = ApiUsageStats(
        source = ApiSource.DEEPSEEK,
        apiName = "DeepSeek",
        quotas = listOf(QuotaInfo(label = "Saldo", used = 0, total = 782, periodEndAt = now, hasKnownResetAt = false, unit = UsageUnit.CURRENCY_USD))
    )

    @Test
    fun `contas saem na ordem do painel, pela fonte e depois pelo perfil`() {
        val accounts = terminalAccounts(listOf(codex, claudeWork, balance, claudeDefault), UsageAlertSettings(), now)
        assertEquals(listOf("ANTHROPIC:default", "ANTHROPIC:work", "CODEX", "DEEPSEEK"), accounts.map { account -> account.storageKey })
    }

    @Test
    fun `fonte e apelido ficam em campos separados`() {
        val account = terminalAccounts(listOf(claudeWork), UsageAlertSettings(), now).single()
        assertEquals("Anthropic", account.name)
        assertEquals("trabalho", account.profile)
    }

    @Test
    fun `limiar proprio da conta vence o geral na palavra de estado`() {
        val settings = UsageAlertSettings(
            quotaPercents = listOf(75, 90, 100),
            quotaPercentsByTarget = mapOf("CODEX" to QuotaThresholdOverride("Codex", listOf(20, 50)))
        )
        val accounts = terminalAccounts(listOf(codex, claudeWork), settings, now)
        val work = accounts.first { account -> account.storageKey == "ANTHROPIC:work" }
        val codexAccount = accounts.first { account -> account.storageKey == "CODEX" }
        assertEquals(listOf(QuotaZone.CRITICAL, QuotaZone.ON_TRACK), work.quotas.map(work::zoneOf))
        assertEquals(QuotaZone.ATTENTION, codexAccount.zoneOf(codexAccount.quotas.single()))
    }

    @Test
    fun `saldo nao tem percentual nem palavra de estado`() {
        val account = terminalAccounts(listOf(balance), UsageAlertSettings(), now).single()
        val quota = account.quotas.single()
        assertNull(quota.percent)
        assertNull(account.zoneOf(quota))
    }

    @Test
    fun `fetchedAt da conta vem da leitura`() {
        assertEquals(now, terminalAccounts(listOf(codex), UsageAlertSettings(), now).single().fetchedAt)
    }
}

package com.usagemonitor.presentation

import com.usagemonitor.domain.entity.ApiSource
import com.usagemonitor.domain.entity.ApiUsageStats
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.PeriodType
import com.usagemonitor.domain.entity.QuotaInfo
import com.usagemonitor.domain.entity.QuotaRiskSummary
import com.usagemonitor.domain.entity.UsageRiskLevel
import com.usagemonitor.domain.entity.UsageTargetKey
import com.usagemonitor.domain.entity.UsageUnit
import com.usagemonitor.presentation.ui.MAX_HUD_RINGS
import com.usagemonitor.presentation.ui.buildHudAccounts
import com.usagemonitor.presentation.ui.components.AppTone
import com.usagemonitor.presentation.viewmodel.HudQuotaEntry
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

private val HUD_NOW = Instant.parse("2026-09-24T12:00:00Z")
private val PADRAO = UsageTargetKey(ApiSource.ANTHROPIC, "padrao")
private val SANDBOX = UsageTargetKey(ApiSource.ANTHROPIC, "sandbox")
private val CODEX = UsageTargetKey.forSource(ApiSource.CODEX)

class HudModelTest {

    @Test
    fun `uma conta por alvo, na ordem dos cards e nao na do risco`() {
        val entries = listOf(
            entry(CODEX, "Codex 5h", used = 90, risk = UsageRiskLevel.WILL_EXCEED),
            entry(PADRAO, "Sessão 5h", used = 10, risk = UsageRiskLevel.ON_TRACK),
            entry(PADRAO, "Sessão 7d", used = 5, risk = UsageRiskLevel.ON_TRACK)
        )

        val accounts = buildHudAccounts(entries, cardOrder = listOf(PADRAO, CODEX), language = AppLanguage.PT, now = HUD_NOW)

        assertEquals(listOf(PADRAO, CODEX), accounts.map { account -> account.targetKey })
        assertEquals(listOf("5h", "7d"), accounts.first().quotas.map { quota -> quota.shortLabel })
    }

    @Test
    fun `a palavra e o tom saem da pior cota da conta`() {
        val entries = listOf(
            entry(PADRAO, "Sessão 5h", used = 12, risk = UsageRiskLevel.ON_TRACK),
            entry(PADRAO, "Sessão 7d", used = 88, risk = UsageRiskLevel.WILL_EXCEED)
        )

        val account = buildHudAccounts(entries, emptyList(), AppLanguage.PT, HUD_NOW).single()

        assertEquals(AppTone.CRITICAL, account.tone)
        assertEquals("Crítico", account.statusLabel)
        assertTrue(account.needsAttention)
    }

    /** O percentual do notch é o da cota de pior risco, não o da primeira. */
    @Test
    fun `o foco e a cota de pior risco e no empate a de maior percentual`() {
        val worstSecond = listOf(
            entry(PADRAO, "Sessão 5h", used = 70, risk = UsageRiskLevel.ON_TRACK),
            entry(PADRAO, "Sessão 7d", used = 40, risk = UsageRiskLevel.AT_RISK)
        )
        assertEquals("40%", buildHudAccounts(worstSecond, emptyList(), AppLanguage.PT, HUD_NOW).single().focus?.percentText)

        val tie = listOf(
            entry(PADRAO, "Sessão 5h", used = 20, risk = UsageRiskLevel.ON_TRACK),
            entry(PADRAO, "Sessão 7d", used = 60, risk = UsageRiskLevel.ON_TRACK)
        )
        assertEquals("60%", buildHudAccounts(tie, emptyList(), AppLanguage.PT, HUD_NOW).single().focus?.percentText)
    }

    @Test
    fun `sem projecao a conta diz isso e a cota nao finge estado`() {
        val entries = listOf(entry(CODEX, "Codex 5h", used = 30, risk = null))

        val account = buildHudAccounts(entries, emptyList(), AppLanguage.PT, HUD_NOW).single()

        assertEquals("Sem projeção", account.statusLabel)
        assertEquals(AppTone.NEUTRAL, account.tone)
        assertFalse(account.quotas.single().hasForecast)
        assertFalse(account.needsAttention)
    }

    @Test
    fun `os aneis param em tres e o painel continua com todas as cotas`() {
        val entries = (1..4).map { index -> entry(SANDBOX, "Janela w$index", used = index * 10, risk = null) }

        val account = buildHudAccounts(entries, emptyList(), AppLanguage.PT, HUD_NOW).single()

        assertEquals(MAX_HUD_RINGS, account.rings.size)
        assertEquals(4, account.quotas.size)
    }

    @Test
    fun `a fracao do anel e presa a uma volta`() {
        val entries = listOf(entry(CODEX, "Codex 5h", used = 140, risk = UsageRiskLevel.WILL_EXCEED))

        val quota = buildHudAccounts(entries, emptyList(), AppLanguage.PT, HUD_NOW).single().quotas.single()

        assertEquals(1f, quota.fraction)
    }

    @Test
    fun `a sessao ativa vem do conjunto de alvos ativos`() {
        val entries = listOf(
            entry(PADRAO, "Sessão 5h", used = 10, risk = null),
            entry(CODEX, "Codex 5h", used = 10, risk = null)
        )

        val accounts = buildHudAccounts(entries, emptyList(), AppLanguage.PT, HUD_NOW, activeTargets = setOf(PADRAO))

        assertEquals(listOf(true, false), accounts.map { account -> account.sessionActive })
    }

    private fun entry(target: UsageTargetKey, label: String, used: Int, risk: UsageRiskLevel?): HudQuotaEntry {
        val quota = QuotaInfo(
            label = label,
            used = used.toLong(),
            total = 100L,
            periodEndAt = HUD_NOW + 2.hours,
            periodType = PeriodType.INTERVAL,
            unit = UsageUnit.PERCENTAGE
        )
        val stats = ApiUsageStats(
            source = target.source,
            targetKey = target,
            apiName = target.source.name,
            quotas = listOf(quota)
        )
        return HudQuotaEntry(stats, quota, risk?.let { level -> QuotaRiskSummary(level, HUD_NOW + 1.hours) })
    }
}
